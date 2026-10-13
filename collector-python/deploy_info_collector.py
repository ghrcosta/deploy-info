#!/usr/bin/env python3
"""DeployInfo collector — standalone Python port of the Gradle collector plugin.

Prerequisites: Python >= 3.10, git, an authenticated gcloud CLI.

Single-file script mirroring ``collector-java/deploy-info-collector`` one-to-one;
the sections below follow the Kotlin modules in the same order. How it works
internally: ``documentation/python-collector.md`` (usage: the README).
"""

# --- imports (stdlib only) ----------------------------------------------------
import argparse
import json
import mimetypes
import os
import platform
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import ClassVar


# --- Logger (Logger.kt) --------------------------------------------------------
class Logger:
    """Port of ``util/Logger.kt``.

    Info lines go to stdout (``Logger.i``), errors to stderr (``Logger.e``), and
    debug lines (``Logger.d`` — each executed command) only when verbose logging
    is enabled (the CLI's ``--verbose``; Gradle's ``-i`` plays that role there).
    """

    _verbose: ClassVar[bool] = False

    @classmethod
    def init(cls, verbose: bool = False) -> None:
        cls._verbose = verbose

    @classmethod
    def d(cls, message: str) -> None:
        if cls._verbose:
            print(message)

    @classmethod
    def i(cls, message: str) -> None:
        print(message)

    @classmethod
    def e(cls, message: str) -> None:
        print(message, file=sys.stderr)


class CollectorError(Exception):
    """A run failure (mirror of the Kotlin GradleException/RuntimeException): ``main`` logs it and exits 1."""


# --- OS / command utils (CommandUtils.kt, SystemUtils.kt) ----------------------

# Executables that must be called differently depending on the OS. Key is Linux, value is Windows.
_EXECUTABLE_WINDOWS_MAP = {"gcloud": "gcloud.cmd"}


def is_windows() -> bool:
    # Same check as SystemUtils.kt: "windows" in the OS name, lowercased.
    return "windows" in platform.system().lower()


def get_executable_for_os(linux_executable: str) -> str:
    if is_windows():
        return _EXECUTABLE_WINDOWS_MAP.get(linux_executable.lower(), linux_executable)
    return linux_executable


# Splits a command string into its arguments: sections enclosed in double quotes become a single
# argument and the quotes are removed. This is needed because the command is executed without a
# shell, so quotes would otherwise be passed literally to the executable (e.g.
# ``git --no-pager diff "/path with space/file"`` would make git look for a file named ``"/path``).
_TOKENIZE_PATTERN = re.compile(r'"([^"]*)"|(\S+)')


def tokenize_command(command: str) -> list[str]:
    return [quoted if quoted else bare for quoted, bare in _TOKENIZE_PATTERN.findall(command)]


@dataclass
class CommandResult:
    stdout: str
    stderr: str


def run_command(command: str, temp_dir: Path | None = None) -> str:
    """Execute ``command`` and return its stdout, swallowing spawn errors (``executeCommand``).

    A failed spawn returns a ``Command failed: ...`` string instead of raising, so callers keep
    processing. Non-zero exit codes are *not* checked anywhere (parity with the plugin).
    """
    try:
        return run_command_or_throw(command, temp_dir).stdout
    except OSError as error:
        return f"Command failed: {command}\nError: {error}"


def run_command_or_throw(command: str, temp_dir: Path | None = None) -> CommandResult:
    """Execute ``command`` without a shell and return stdout/stderr (``executeCommandOrThrowException``).

    Spawn errors (``OSError``) propagate. Stdout and stderr are each redirected to temp files
    inside ``Context.base_dir``: reading both streams to files prevents the process from blocking
    on full pipe buffers, and stderr must not be merged into stdout.
    """
    executable = command.split(" ", 1)[0]
    executable_for_os = get_executable_for_os(executable)
    command_for_os = command.replace(executable, executable_for_os, 1)

    Logger.d(f"Executing command: {command_for_os}")

    base_dir = temp_dir if temp_dir is not None else Context.get().base_dir
    stdout_file = create_empty_file("stdout", base_dir)
    stderr_file = create_empty_file("stderr", base_dir)

    with open(stdout_file, "wb") as out, open(stderr_file, "wb") as err:
        subprocess.run(tokenize_command(command_for_os), stdout=out, stderr=err)

    stdout = stdout_file.read_text(encoding="utf-8").rstrip()
    stdout_file.unlink(missing_ok=True)
    stderr = stderr_file.read_text(encoding="utf-8").rstrip()
    stderr_file.unlink(missing_ok=True)

    return CommandResult(stdout, stderr)


# --- CSV / properties writers (CsvUtils.kt; Java Properties.store format) ------

# Minimal CSV helpers for the `uuid-git.csv` upload artifact, RFC 4180 compliant: fields containing
# a comma, a double quote or a line break are wrapped in double quotes, with embedded quotes
# doubled. (Hand-rolled on purpose, like the Kotlin CsvUtils, for byte-for-byte parity.)
_CSV_ESCAPE_REQUIRED = (",", '"', "\n", "\r")


def csv_escape(csv_field: str) -> str:
    """Return the field quoted (RFC 4180) when it contains a comma, double quote or line break."""
    if any(character in csv_field for character in _CSV_ESCAPE_REQUIRED):
        return '"' + csv_field.replace('"', '""') + '"'
    return csv_field


def properties_escape(value: str, is_key: bool) -> str:
    """Escape a key/value like Java's ``Properties.saveConvert`` (``Properties.store`` format).

    Keys escape ``\\``, ``=``, ``:`` and every space; values escape ``\\`` and leading spaces only;
    control characters become ``\\t``/``\\n``/``\\r``/``\\f`` and anything outside printable ASCII
    becomes ``\\uXXXX``. The portal parses ``uuid-extra.properties`` with a Java Properties loader,
    so this escaping must be replicated exactly.
    """
    escaped: list[str] = []
    for index, character in enumerate(value):
        if character == "\\":
            escaped.append("\\\\")
        elif character in (":", "="):
            escaped.append("\\" + character if is_key else character)
        elif character == " ":
            escaped.append("\\ " if (index == 0 or is_key) else character)
        elif character == "\t":
            escaped.append("\\t")
        elif character == "\n":
            escaped.append("\\n")
        elif character == "\r":
            escaped.append("\\r")
        elif character == "\f":
            escaped.append("\\f")
        elif not 0x20 <= ord(character) <= 0x7E:
            escaped.append(f"\\u{ord(character):04x}")
        else:
            escaped.append(character)
    return "".join(escaped)


def write_properties_file(path: Path, entries: dict[str, str]) -> None:
    """Write ``entries`` as a Java ``Properties.store`` file (UTF-8, ``\\n`` line endings).

    Java writes a timestamp comment first (``#<date>``); the content is irrelevant but the
    ``#``-prefixed line must be there so Java's loader skips it.
    """
    lines = [f"#{time.ctime()}"]
    lines.extend(
        f"{properties_escape(key, is_key=True)}={properties_escape(value, is_key=False)}"
        for key, value in entries.items()
    )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


# --- DeployType / GitFileStatus / GitCollectedFile ------------------------------


class DeployType(Enum):
    """Mirrors ``CollectorTask.DeployType``."""

    GAE = "GAE"
    RUN = "RUN"


class GitFileStatus(Enum):
    """The git state of one file collected by GitCollector, classified from ``git status --porcelain``."""

    UNTRACKED = "untracked"
    NEW = "new"
    MODIFIED = "modified"

    @property
    def wire_value(self) -> str:
        """The value written to the ``status`` column of ``uuid-git.csv``."""
        return self.value

    @classmethod
    def from_porcelain_code(cls, code: str) -> "GitFileStatus":
        """Classify a 2-char XY code: ``??`` untracked; contains ``A`` (priority over ``M``) new; else modified."""
        if code == "??":
            return cls.UNTRACKED
        if "A" in code:
            return cls.NEW
        return cls.MODIFIED


@dataclass
class GitCollectedFile:
    """One file collected by GitCollector: its real path and its git state."""

    filepath: str  # absolute, "/"-separated
    status: GitFileStatus


# --- Context (Context.kt) -------------------------------------------------------


@dataclass
class Context:
    """Run-wide state, mirroring ``util/Context.kt`` (module-level singleton below)."""

    base_dir: Path  # <project>/build/collector/
    output_dir: Path  # files in this directory are uploaded to the portal
    project_root_dir: Path  # base to resolve the git/extra-file paths
    max_file_size: int  # in bytes; larger files are not collected
    user_email: str  # the gcloud account that performed the deploy and the collection
    git_uuid_map: dict[str, GitCollectedFile] = field(default_factory=dict)
    extra_uuid_map: dict[str, str] = field(default_factory=dict)

    _instance: ClassVar["Context | None"] = None

    @classmethod
    def get(cls) -> "Context":
        if cls._instance is None:
            raise RuntimeError("Context is not initialized: call Context.init() first.")
        return cls._instance

    @classmethod
    def init(
        cls,
        project_root_dir: Path,
        max_file_size_kb: int,
        deploy_type: DeployType,
    ) -> "Context":
        """Prepare the output directories and detect the gcloud account (hard prerequisite).

        The output directory name is the sole carrier of who/what/when — no metadata file is
        written (same convention as the plugin).
        """
        base_dir = create_empty_directory("collector", project_root_dir / "build")

        user_email = run_command_or_throw("gcloud config get-value account", base_dir).stdout
        now_ms = time.time_ns() // 1_000_000  # epoch millis, matching Instant.now()

        gcloud_user = user_email.split("@", 1)[0]
        output_dir = create_empty_directory(f"{gcloud_user}_{deploy_type.name}_{now_ms}", base_dir)

        cls._instance = cls(
            base_dir=base_dir,
            output_dir=output_dir,
            project_root_dir=project_root_dir,
            max_file_size=max_file_size_kb * 1024,  # 50 KB default → 51200 bytes
            user_email=user_email,
        )
        return cls._instance


# --- file helpers (FileUtils.kt) ------------------------------------------------


def get_contents(path: Path) -> str:
    """Content of ``path`` for the upload artifacts, with the plugin's placeholders."""
    if is_large(path):
        return f"<FILE IS TOO LARGE ({path.stat().st_size} bytes)>"
    if is_binary(path):
        return "<FILE IS BINARY>"
    content = path.read_text(encoding="utf-8")
    if not content.strip():  # Kotlin `ifBlank { "<FILE IS BLANK>" }`
        return "<FILE IS BLANK>"
    return content


def is_large(path: Path) -> bool:
    """Strictly greater than the limit, like the Kotlin ``File.isLarge()``."""
    return path.stat().st_size > Context.get().max_file_size


def is_binary(path: Path) -> bool:
    """Extension-based mime lookup plus the Kotlin ``File.isBinary()`` rule table.

    ``mimetypes.guess_type`` is the stdlib equivalent of Java's ``URLConnection`` mime mapping
    (filename-based). ``None`` (unknown extension) behaves like Java's ``content/unknown``:
    not binary.
    """
    mime_type = mimetypes.guess_type(str(path))[0]
    if mime_type is None:  # treated as "content/unknown"
        return False
    if mime_type.startswith("text"):
        return False
    if not (mime_type.startswith("application") or mime_type.startswith("content")):
        return True
    if any(token in mime_type for token in ("json", "xml", "html", "yaml", "unknown")):
        return False
    return True


def create_empty_file(filename: str, destination_dir: Path | None = None) -> Path:
    """Delete any existing file of the same name, then create and return an empty one."""
    destination = destination_dir if destination_dir is not None else Context.get().output_dir
    new_file = destination / filename
    new_file.unlink(missing_ok=True)
    new_file.touch()
    return new_file


def create_empty_directory(directory_name: str, destination_dir: Path | None = None) -> Path:
    """Delete an existing directory recursively, then create it empty (``createEmptyDirectory``).

    Ensures the directory is empty to prevent mixing files from different deploys.
    """
    destination = destination_dir if destination_dir is not None else Context.get().base_dir
    new_dir = destination / directory_name
    if new_dir.exists() and new_dir.is_dir():
        shutil.rmtree(new_dir)
    new_dir.mkdir(parents=True, exist_ok=True)
    return new_dir


# --- GitCollector (GitCollector.kt) ---------------------------------------------


def collect_git_status() -> None:
    """Port of ``action/GitCollector.execute()``.

    Writes ``git-status.txt``, ``git-log.txt`` and one UUID-named file per changed file into
    ``Context.output_dir``, recording them in ``Context.git_uuid_map``. On ``OSError`` (e.g. a
    failing file write) the error is logged and ``error.txt`` is written instead of failing the
    run. Non-zero git exit codes are not checked (parity with the plugin).
    """
    Logger.i("Collecting diff data...")
    try:
        git_status_output = run_command("git status --long --branch --untracked-files=normal")
        create_empty_file("git-status.txt").write_text(git_status_output, encoding="utf-8")

        git_log_output = run_command("git log --oneline")
        create_empty_file("git-log.txt").write_text(git_log_output, encoding="utf-8")

        # "git status --porcelain" is better for automations, use it to list (and process) files
        git_status_porcelain = run_command("git status --porcelain --untracked-files=normal --no-renames")
        repo_root = get_git_repo_root()
        for file_path, status in extract_changed_files(git_status_porcelain, repo_root):
            git_diff_output = ""
            if status == GitFileStatus.MODIFIED:
                git_diff_output = run_command(f'git --no-pager diff "{file_path}"')
            if not git_diff_output.strip():
                save_content_to_uuid_file(file_path, status, get_contents(Path(file_path)))
            else:
                save_content_to_uuid_file(file_path, status, git_diff_output)
    except OSError as error:
        Logger.e(f"Error: {error}")
        create_empty_file("error.txt").write_text(f"Error: {error}", encoding="utf-8")


# One entry of `git status --porcelain`: the file path plus its git state.
_PORCELAIN_LINE_PATTERN = re.compile(r"^([A-Z?!\s]{2})\s+(.*)$", re.MULTILINE)


def extract_changed_files(git_status_output: str, repo_root: str) -> list[tuple[str, GitFileStatus]]:
    """Parse `git status --porcelain` output into (absolute path, git state) pairs.

    The XY-code classification lives in ``GitFileStatus.from_porcelain_code``. Entries that do
    not resolve to an existing regular file are skipped.
    """
    changed_files: list[tuple[str, GitFileStatus]] = []
    for match in _PORCELAIN_LINE_PATTERN.finditer(git_status_output):
        # To support names with spaces, git quotes them; strip the quotes after trimming.
        file_path = match.group(2).strip().replace('"', "")
        if not file_path:
            continue
        full_file_path = parse_path_from_repo_root(file_path, repo_root)
        if Path(full_file_path).is_file():
            changed_files.append((full_file_path, GitFileStatus.from_porcelain_code(match.group(1))))
    return changed_files


def get_git_repo_root() -> str:
    """Returns the root directory of the git repository, falling back to the project root."""
    repo_root_path = run_command("git rev-parse --show-toplevel").strip()
    if repo_root_path:
        return repo_root_path
    return str(Context.get().project_root_dir)


def parse_path_from_repo_root(file_path: str, repo_root: str) -> str:
    """Convert a path relative to the git repository root into an absolute path.

    Porcelain paths always use "/" as separator; the result is normalized to the OS separator.
    """
    return os.path.abspath(os.path.join(repo_root, file_path))


def save_content_to_uuid_file(file_path: str, status: GitFileStatus, content: str) -> None:
    """Save ``content`` under a fresh UUID filename and record it in ``Context.git_uuid_map``."""
    uuid_key = str(uuid.uuid4())
    create_empty_file(uuid_key).write_text(content, encoding="utf-8")
    parsed_path = f"/{file_path}".replace("\\", "/").replace("//", "/")
    Context.get().git_uuid_map[uuid_key] = GitCollectedFile(filepath=parsed_path, status=status)


# --- ExtraFilesCollector (ExtraFilesCollector.kt) ---------------------------------


def collect_extra_files(extra_files: list[str]) -> None:
    """Port of ``action/ExtraFilesCollector.execute()``.

    Normalizes each entry (leading ``/``, ``\\`` → ``/``, ``//`` → ``/``), dedupes on the
    normalized form, resolves against the project root and copies valid files verbatim into
    ``Context.output_dir`` under fresh UUID names (recorded in ``Context.extra_uuid_map``).
    Invalid entries (missing, not a regular file, binary or too large) are logged and ignored.
    """
    for file_path in dict.fromkeys(
        f"/{entry}".replace("\\", "/").replace("//", "/") for entry in extra_files
    ):
        parsed_path = parse_path_from_project_root(file_path)
        file = Path(parsed_path)
        exists = file.exists()
        is_file = file.is_file()
        if exists and is_file and not is_binary(file) and not is_large(file):
            uuid_key = str(uuid.uuid4())
            shutil.copyfile(file, Context.get().output_dir / uuid_key)
            Context.get().extra_uuid_map[uuid_key] = file_path
        else:
            # Booleans lowercased to keep the Kotlin log wording ("true"/"false").
            Logger.e(
                f"File {parsed_path} is invalid ("
                f"exists={str(exists).lower()}, "
                f"isFile={str(is_file).lower()}, "
                f"isBinary={str(is_binary(file)).lower() if is_file else '---'}, "
                f"isLarge={str(is_large(file)).lower() if is_file else '---'}"
                "), will be ignored!"
            )


def parse_path_from_project_root(file_path: str) -> str:
    """Resolve a normalized extra-file path (``/``-separated, leading ``/``) against the project root.

    Mirrors the Kotlin string concatenation, including the ``\\/`` → separator repair for
    Windows, where the leading ``/`` of the normalized path meets the ``\\`` separator.
    """
    path = f"{Context.get().project_root_dir}{os.sep}{file_path}"
    return path.replace("\\/", os.sep)


# --- GcloudAccessTokenProvider (GcloudAccessTokenProvider.kt) ---------------------


def fetch_gcloud_access_token() -> str | None:
    """Fetch a gcloud access token for the active gcloud account, or ``None`` when unavailable.

    The token is sent as a Bearer credential so the portal can authenticate the bucket-lookup and
    trigger calls. Returns ``None`` when no token can be obtained: the collector cannot know
    whether the portal requires authentication at all (it can be disabled, e.g. in local
    development), so the requests are then simply sent without the Authorization header and a
    portal that requires authentication answers with its own actionable error. The token itself
    is never logged.
    """
    try:
        token = run_command_or_throw("gcloud auth print-access-token").stdout.strip()
        return token if token else None
    except Exception as error:  # any failure → warn and go token-less (mirror of `catch (e: Exception)`)
        Logger.e(
            f"Could not obtain a gcloud access token ({error}): the portal may reject the "
            "bucket-lookup/trigger calls with HTTP 401/403 if it requires authentication."
        )
        return None


# --- BucketLookup (BucketLookup.kt) -----------------------------------------------

CONNECT_TIMEOUT_MILLIS = 10_000
READ_TIMEOUT_MILLIS = 30_000
# urllib's timeout covers connect + blocking reads, so it mirrors the sum of the Kotlin constants.
_URLOPEN_TIMEOUT_SECONDS = (CONNECT_TIMEOUT_MILLIS + READ_TIMEOUT_MILLIS) / 1000

BUCKET_FIELD_PATTERN = re.compile(r'"bucket"\s*:\s*"([^"]*)"')


def parse_bucket_field(body: str) -> str | None:
    """Extract the ``bucket`` field from the bucket-lookup response (JSON first, regex fallback)."""
    try:
        parsed = json.loads(body)
    except ValueError:
        parsed = None
    if isinstance(parsed, dict):
        bucket = parsed.get("bucket")
        if isinstance(bucket, str) and bucket:
            return bucket
    match = BUCKET_FIELD_PATTERN.search(body)
    if match and match.group(1):
        return match.group(1)
    return None


def http_request(request: urllib.request.Request) -> tuple[int, str | None]:
    """Perform ``request`` and return ``(status, body)`` without raising on HTTP error statuses.

    Connection/timeout/read errors (``OSError`` subclasses, incl. ``URLError``) and invalid URLs
    (``ValueError``) propagate to the caller, which maps them to actionable messages.
    """
    try:
        with urllib.request.urlopen(request, timeout=_URLOPEN_TIMEOUT_SECONDS) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        with error:
            try:
                body: str | None = error.read().decode("utf-8")
            except OSError:
                body = None
        return error.code, body


def lookup_bucket(portal_url: str, access_token: str | None) -> str:
    """Resolve the Cloud Storage bucket the collector uploads go to by asking the portal.

    Calls ``GET {portalUrl}/collector/bucket`` and parses the ``bucket`` field from the JSON
    response. Any failure raises — the run aborts *before* anything is uploaded.
    """
    url = portal_url.rstrip("/") + "/collector/bucket"
    try:
        request = urllib.request.Request(url, method="GET")
        if access_token:
            request.add_header("Authorization", f"Bearer {access_token}")
        status, body = http_request(request)
    except ValueError as error:
        raise CollectorError(f"'portalUrl' is not a valid URL: '{portal_url}' ({error})") from error
    except OSError as error:
        raise CollectorError(f"Could not reach the portal at {url} to resolve the upload bucket: {error}") from error

    if status != 200:
        auth_hint = ""
        if status in (401, 403):
            auth_hint = (" The portal requires authentication: run `gcloud auth login` (or "
                         "`gcloud auth application-default login`) and retry.")
        raise CollectorError(f"The portal at {url} returned HTTP {status} when resolving the upload bucket.{auth_hint}")

    bucket = parse_bucket_field(body or "")
    if bucket is None:
        raise CollectorError(
            f"The portal at {url} returned a malformed bucket-lookup response (no 'bucket' field): {body}"
        )
    return bucket


# --- Uploader (Uploader.kt) --------------------------------------------------------


def upload_files(bucket_name: str) -> None:
    """Write the uuid-map artifacts and upload the output directory with ``gcloud storage cp``.

    Faithful port including the quirk: the upload command's exit code and stdout are not checked —
    only stderr lines starting with ``ERROR`` (deduplicated) fail the run.
    """
    git_uuid_map = Context.get().git_uuid_map
    if git_uuid_map:
        csv_lines = ["uuid,filepath,status"]
        csv_lines.extend(
            f"{csv_escape(uuid_key)},{csv_escape(collected.filepath)},{csv_escape(collected.status.wire_value)}"
            for uuid_key, collected in git_uuid_map.items()
        )
        create_empty_file("uuid-git.csv").write_text("\n".join(csv_lines) + "\n", encoding="utf-8")

    extra_uuid_map = Context.get().extra_uuid_map
    if extra_uuid_map:
        write_properties_file(create_empty_file("uuid-extra.properties"), dict(extra_uuid_map))

    Logger.i("Uploading files...")
    origin = os.path.abspath(str(Context.get().output_dir))
    result = run_command_or_throw(f"gcloud storage cp --recursive {origin} gs://{bucket_name}")

    errors = list(dict.fromkeys(line for line in result.stderr.splitlines() if line.startswith("ERROR")))
    if errors:
        raise CollectorError("Upload command returned errors:\n" + "\n".join(errors))


# --- PortalTrigger (PortalTrigger.kt, PortalTriggerDTOs.kt) -----------------------

TRIGGER_STATUS_CREATED = "created"
TRIGGER_STATUS_ALREADY_LINKED = "already-linked"
TRIGGER_STATUS_PENDING = "pending"


def trigger_portal_processing(
    portal_url: str,
    directory_name: str,
    projects: list[str],
    deploy_type: str,
    user_email: str,
    access_token: str | None,
) -> None:
    """Notify the portal that an output directory was uploaded, so it can link the deploy.

    Calls ``POST {portalUrl}/collector/handleNewDirectory`` with a JSON body
    ``{directoryName, projects, deployType, userEmail}``. The portal owns the matching logic and
    its 5-minute retry: a ``pending`` response means the portal may still link the upload (or
    delete its folder) later, so it is logged but does not fail the run. Any other unexpected
    outcome fails the run.
    """
    url = portal_url.rstrip("/") + "/collector/handleNewDirectory"
    body = json.dumps({
        "directoryName": directory_name,
        "projects": projects,
        "deployType": deploy_type,
        "userEmail": user_email,
    }).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"

    try:
        request = urllib.request.Request(url, data=body, headers=headers, method="POST")
        status, response_body = http_request(request)
    except ValueError as error:
        raise CollectorError(f"'portalUrl' is not a valid URL: '{portal_url}' ({error})") from error
    except OSError as error:
        raise CollectorError(
            f"Could not reach the portal at {url} to trigger processing of '{directory_name}': {error}"
        ) from error

    if status in (200, 202):
        report_trigger_outcome(url, status, response_body, directory_name)
    elif status == 401:
        raise CollectorError(
            f"The portal at {url} rejected the trigger for '{directory_name}' with HTTP 401: it requires "
            "authentication but no valid access token was presented. Run `gcloud auth login` (or "
            "`gcloud auth application-default login`) and retry."
        )
    elif status == 403:
        raise CollectorError(
            f"The portal at {url} rejected the trigger for '{directory_name}' with HTTP 403: "
            f"{describe_forbidden(response_body)}"
        )
    elif status == 422:
        raise CollectorError(
            f"The portal at {url} rejected the trigger for '{directory_name}': none of the listed projects "
            f"({projects}) is configured in the portal. Add at least one of them to the portal's projects."
        )
    else:
        raise CollectorError(f"The portal at {url} returned HTTP {status} when triggering processing of '{directory_name}'.")


def report_trigger_outcome(url: str, status: int, body: str | None, directory_name: str) -> None:
    """Log the trigger outcome for 200/202 responses; malformed or unexpected statuses fail the run."""
    if body is None or not body.strip():
        raise CollectorError(f"The portal at {url} returned an empty trigger response for '{directory_name}'.")
    try:
        response = json.loads(body)
    except ValueError as error:
        raise CollectorError(
            f"The portal at {url} returned a malformed trigger response for '{directory_name}': {error}"
        ) from error
    if not isinstance(response, dict):
        raise CollectorError(
            f"The portal at {url} returned a malformed trigger response for '{directory_name}': not a JSON object."
        )

    response_status = response.get("status")
    if response_status in (TRIGGER_STATUS_CREATED, TRIGGER_STATUS_ALREADY_LINKED):
        Logger.i(
            f"Deploy link result: {response_status} ('{directory_name}' -->  "
            f"[{response.get('project') or '?'}, {response.get('service') or '?'}, "
            f"{response.get('version') or '?'}])."
        )
    elif response_status == TRIGGER_STATUS_PENDING:
        Logger.i(
            f"Portal accepted the trigger for '{directory_name}' as pending (HTTP {status}): no deploy matched "
            "yet. The portal will retry matching and may still link the upload or delete its folder."
        )
    else:
        raise CollectorError(
            f"The portal at {url} returned an unexpected trigger status '{response_status}' for '{directory_name}'."
        )


def describe_forbidden(body: str | None) -> str:
    """Turn a 403 into a remediation message based on the portal's error body (see documentation/api.md)."""
    response = f" Portal response: {body[:500]}" if body is not None else " (no response body)"
    if body is not None and '"email-mismatch"' in body:
        return (
            "the request's userEmail does not match the authenticated account. This happens with gcloud "
            "service-account impersonation setups (`gcloud config set auth/impersonate_service_account`): "
            "the portal cannot attribute the upload to the deploying account then." + response
        )
    if body is not None and '"not-permitted"' in body:
        return (
            "the authenticated account is not present in the IAM of the portal's project, neither directly "
            "nor through a group. Ask the portal administrator for access." + response
        )
    if body is not None and '"portal-issue"' in body:
        return "the portal could not run its permission check (a portal-side problem, not yours)." + response
    return "the authenticated account is not permitted." + response


# --- CLI (argparse) + main() ------------------------------------------------------
# Mirrors the Gradle task's parameters and its validateParameters(); exit codes (0/1/2) are in
# the README.


def parse_arguments(argv: list[str] | None = None) -> argparse.Namespace:
    """Parse and normalize the CLI arguments (projects merged/deduped, extra files flattened)."""
    parser = argparse.ArgumentParser(
        prog="deploy_info_collector.py",
        description="Collects deploy info (git status/diff and extra files) and uploads it to the deploy-info portal.",
    )
    parser.add_argument("--deploy-type", required=True, type=DeployType, choices=list(DeployType),
                        metavar="{GAE,RUN}", help="Type of deploy. Supported values are 'GAE' and 'RUN'.")
    parser.add_argument("--portal-url", required=True,
                        help="URL of the portal backend, from which the upload bucket is resolved.")
    parser.add_argument("--projects", required=True, nargs="+", action="append", metavar="PROJECT",
                        help="GCP projects where the code may have been deployed; comma-separated and/or repeatable.")
    parser.add_argument("--project-dir", default=".", type=Path,
                        help="Project root directory; the base for git/extra-file paths and the build/collector "
                             "output (default: the current directory).")
    parser.add_argument("--max-file-size", default="50", metavar="N",
                        help="Maximum size of files that can be included, in KB (default: 50).")
    parser.add_argument("--collect-git-status", action=argparse.BooleanOptionalAction, default=True,
                        help="If git-related data must be collected (default: true).")
    parser.add_argument("--extra-files", nargs="*", action="append", default=None, metavar="PATH",
                        help="Extra files to include; paths relative to --project-dir, files only, collected "
                             "without parent directories (repeatable / space-separated).")
    parser.add_argument("--verbose", action="store_true", help="Print debug lines (each executed command).")

    args = parser.parse_args(argv)
    args.projects = merge_projects(args.projects)
    args.extra_files = [path for group in (args.extra_files or []) for path in group]
    return args


def merge_projects(raw_groups: list[list[str]] | None) -> list[str]:
    """Merge the repeatable ``--projects`` values, splitting on commas; dedupe preserving order."""
    merged: list[str] = []
    for group in raw_groups or []:
        for value in group:
            merged.extend(part.strip() for part in value.split(","))
    return list(dict.fromkeys(merged))


def parse_max_file_size_kb(raw: str) -> int:
    """Validate ``--max-file-size``: must parse as an int and be >= 1 (value in KB)."""
    try:
        value = int(raw)
    except ValueError:
        raise CollectorError(f"'maxFileSize' must be an integer (in KB), got: '{raw}'.") from None
    if value < 1:
        raise CollectorError(f"'maxFileSize' must be at least 1 (in KB), got: '{raw}'.")
    return value


def validate_parameters(args: argparse.Namespace) -> int:
    """Mirror of ``CollectorTask.validateParameters()``; returns the validated max file size in KB."""
    if not args.portal_url.strip():
        raise CollectorError("'portalUrl' must not be empty.")
    if not args.projects:
        raise CollectorError("'projects' must not be empty: list at least one GCP project where the code may be deployed.")
    if any(not project for project in args.projects):
        raise CollectorError("'projects' must not contain empty values.")
    return parse_max_file_size_kb(args.max_file_size)


def main(argv: list[str] | None = None) -> int:
    """CLI entry point: collect → token → bucket lookup → upload → trigger (mirrors ``TaskImpl.run``)."""
    args = parse_arguments(argv)
    Logger.init(verbose=args.verbose)
    try:
        max_file_size_kb = validate_parameters(args)

        # The Gradle plugin runs its commands with the project root as working directory; mirror
        # that so git resolves the right repository and relative paths behave the same.
        os.chdir(args.project_dir)
        project_root = Path.cwd()

        Context.init(project_root, max_file_size_kb, args.deploy_type)

        if args.collect_git_status:
            collect_git_status()
        if args.extra_files:
            collect_extra_files(args.extra_files)

        # One token for both portal calls; None goes header-less (see fetch_gcloud_access_token).
        access_token = fetch_gcloud_access_token()

        bucket_name = lookup_bucket(args.portal_url, access_token)
        Logger.i(f"Uploads will go to Cloud Storage bucket '{bucket_name}'.")

        upload_files(bucket_name)

        context = Context.get()
        trigger_portal_processing(
            portal_url=args.portal_url,
            directory_name=context.output_dir.name,
            projects=args.projects,
            deploy_type=args.deploy_type.name,
            user_email=context.user_email,
            access_token=access_token,
        )

        Logger.i("Done!")
    except CollectorError as error:
        Logger.e(str(error))
        return 1
    except OSError as error:
        Logger.e(f"Error: {error}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
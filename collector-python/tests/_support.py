"""Shared fixtures for the deploy-info collector's unit tests.

Python mirror of the Java suite's common test scaffolding
(`collector-java/deploy-info-collector/src/test/kotlin`):

- Java `testDir/` / `projectRootDirTest/` fixture directories → a `tempfile.TemporaryDirectory`
  (Python's `Context` always derives the build dir from the project root, `<root>/build`, so no
  separate build-dir fixture is needed).
- Java's relaxed-mock Gradle `Logger` → stdout/stderr redirected to in-memory buffers, so log
  output never pollutes the test runner.
- Java's `Context.init(...)` with a mocked `gcloud config get-value account` → `init_context()`.
- MockK's mocked `HttpURLConnection` → `patch_urlopen()`/`fake_response()` around
  `urllib.request.urlopen`.

Everything is standard library only; no git, gcloud or network access is ever performed.

Run with: `python3 -m unittest discover -s tests` (from `collector-python/`).
"""

import io
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

# Make the single-file collector importable regardless of how the tests are launched.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import deploy_info_collector as dic  # noqa: E402


class CollectorTestCase(unittest.TestCase):
    """Base class: temp directories, captured Logger output and a reset Context singleton."""

    PORTAL_URL = "https://deploy-info-portal.example.com"

    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory(prefix="deploy-info-collector-tests-")
        self.addCleanup(self._tmp.cleanup)

        self.test_dir = Path(self._tmp.name)
        self.project_root_dir = self.test_dir / "projectRootDirTest"
        self.project_root_dir.mkdir()

        # Java: Logger.init(mockk<GradleLogger>(relaxed = true)) — silence and capture the logs.
        self.stdout_capture = io.StringIO()
        self.stderr_capture = io.StringIO()
        stdout_patcher = mock.patch("sys.stdout", new=self.stdout_capture)
        stderr_patcher = mock.patch("sys.stderr", new=self.stderr_capture)
        stdout_patcher.start()
        stderr_patcher.start()
        self.addCleanup(stdout_patcher.stop)
        self.addCleanup(stderr_patcher.stop)

        # Context is a module-level singleton: reset around every test (Java instances are fresh
        # per test; here the ClassVar must be cleared explicitly).
        dic.Logger.init(verbose=False)
        dic.Context._instance = None
        self.addCleanup(setattr, dic.Context, "_instance", None)

    # --- fixture helpers ------------------------------------------------------------

    def init_context(
        self,
        *,
        max_file_size_kb: int = 50,
        max_git_log_lines: int = 30,
        deploy_type: dic.DeployType = dic.DeployType.GAE,
        user_email: str = "user@email.com",
    ) -> dic.Context:
        """Java: `Context.init(projectRootDir, buildDir, deployType)` with the gcloud account
        command mocked to return `user_email` (see the Java suites' `every { ... }` stubs)."""
        with mock.patch.object(
            dic, "run_command_or_throw", return_value=dic.CommandResult(stdout=user_email, stderr="")
        ):
            return dic.Context.init(self.project_root_dir, max_file_size_kb, deploy_type, max_git_log_lines)

    def run_collector(self, params) -> dic.Context:
        """Run ``dic.run`` end-to-end with every external effect mocked (the gcloud account
        command, the access token, the bucket lookup, the upload, the portal trigger and the
        git/extra-file collection); returns the Context the run built. Used to assert the
        argument defaults, which live in ``CollectorParams`` (not in the parser, not in ``run``).
        """
        self.addCleanup(os.chdir, os.getcwd())
        os.chdir(self.test_dir)  # run() chdirs into project_dir; keep that inside the temp dir
        with (
            mock.patch.object(dic, "run_command_or_throw", return_value=dic.CommandResult(stdout="user@email.com\n", stderr="")),
            mock.patch.object(dic, "fetch_gcloud_access_token", return_value=None),
            mock.patch.object(dic, "lookup_bucket", return_value="bucket"),
            mock.patch.object(dic, "upload_files"),
            mock.patch.object(dic, "trigger_portal_processing"),
            mock.patch.object(dic, "collect_git_status"),
            mock.patch.object(dic, "collect_extra_files"),
        ):
            exit_code = dic.run(params)
        self.assertEqual(0, exit_code)
        return dic.Context.get()

    def fake_response(self, *, status: int = 200, body: bytes = b"", read_error: Exception | None = None):
        """A mocked HTTP response usable as a `urllib.request.urlopen` return value.

        Java mirror: `mockConnection(status, body, inputStreamError)` — a relaxed
        `HttpURLConnection` whose `responseCode`/`inputStream` are stubbed.
        """
        response = mock.MagicMock()
        response.status = status
        if read_error is not None:
            response.read.side_effect = read_error
        else:
            response.read.return_value = body
        response.__enter__.return_value = response
        response.__exit__.return_value = False
        return response

    def patch_urlopen(self, *, response=None, side_effect=None):
        """Patch the collector's `urllib.request.urlopen` and return the mock.

        Java mirror: the connection-factory lambda injected into `BucketLookup`/`PortalTrigger`
        (`BucketLookup(portalUrl) { connection }`). The mock records the `urllib.request.Request`
        it was called with, which is where the Java tests assert on the configured connection.
        """
        patcher = mock.patch.object(dic.urllib.request, "urlopen")
        urlopen = patcher.start()
        self.addCleanup(patcher.stop)
        if side_effect is not None:
            urlopen.side_effect = side_effect
        elif response is not None:
            urlopen.return_value = response
        return urlopen

    def http_error(self, url: str, status: int, body: bytes = b"") -> Exception:
        """Java mirror: a mocked connection answering with an HTTP error status + error body."""
        return dic.urllib.error.HTTPError(url, status, f"HTTP Error {status}", {}, io.BytesIO(body))

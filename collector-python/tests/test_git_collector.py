"""Mirror of `collector-java/.../src/test/kotlin/action/GitCollectorTests.kt`.

Java mocks `executeCommand`/`executeCommandOrThrowException` with a command dispatch table
(`everyExecuteCommandAnswers`); the same dispatch table drives a mocked `run_command` here.
Porcelain paths are relative to the mocked repo root and joined with it, exactly like the
Java fixture (`gitRepoRootResponse = { testDir.absolutePath }`).
"""

from pathlib import Path
from unittest import mock

from _support import CollectorTestCase, dic

GIT_STATUS_RESPONSE = "git status output"
GIT_LOG_RESPONSE = "git log output"
SOME_FILE_RELATED_CONTENT = "some file related content"


class GitCollectorTests(CollectorTestCase):

    def setUp(self):
        super().setUp()
        self.init_context()  # Java: Context.init(projectRootDir, buildDir, DeployType.GAE)

    def patch_git_commands(
        self,
        *,
        porcelain: str,
        diff_response,
        repo_root: str | None = None,
    ) -> list[str]:
        """Java mirror of `everyExecuteCommandAnswers(...)`: dispatch on the executed command,
        record every `git --no-pager diff` path and fail on any unexpected command."""
        diff_paths: list[str] = []

        def fake_run_command(command: str, temp_dir=None) -> str:
            if command == "git status --long --branch --untracked-files=normal":
                return GIT_STATUS_RESPONSE
            if command == "git log --oneline":
                return GIT_LOG_RESPONSE
            if command == "git rev-parse --show-toplevel":
                return repo_root if repo_root is not None else str(self.test_dir)
            if command == "git status --porcelain --untracked-files=normal --no-renames":
                return porcelain
            if command.startswith("git --no-pager diff"):
                path = command.removeprefix("git --no-pager diff ").strip().strip('"')
                diff_paths.append(path)
                return diff_response(path)
            raise AssertionError(f"Executed unexpected command -- {command}")

        patcher = mock.patch.object(dic, "run_command", side_effect=fake_run_command)
        patcher.start()
        self.addCleanup(patcher.stop)
        return diff_paths

    def assert_git_files_are_correct(self):
        output_dir = dic.Context.get().output_dir
        self.assertEqual(GIT_STATUS_RESPONSE, (output_dir / "git-status.txt").read_text())
        self.assertEqual(GIT_LOG_RESPONSE, (output_dir / "git-log.txt").read_text())
        self.assertFalse((output_dir / "error.txt").exists())

    def assert_files_from_uuid_map_exist(self, uuid_map):
        output_dir = dic.Context.get().output_dir
        for uuid_key in uuid_map:
            self.assertTrue((output_dir / uuid_key).is_file())

    def uuid_contents_of(self, uuid_map, file_names) -> list[str]:
        output_dir = dic.Context.get().output_dir
        return [
            (output_dir / uuid_key).read_text()
            for uuid_key, collected in uuid_map.items()
            if Path(collected.filepath).name in file_names
        ]

    def test_generates_main_files_and_for_each_git_diff_that_returns_content_saves_it_to_a_file(self):
        for name in ("file01", "file02", "file03", "file04"):
            (self.project_root_dir / name).touch()

        paths = self.patch_git_commands(
            porcelain="\n".join(
                [
                    f" M {self.project_root_dir.name}/file01",
                    f"MM {self.project_root_dir.name}/file02",
                    f"?? {self.project_root_dir.name}/file03",
                    f"AM {self.project_root_dir.name}/file04",
                ]
            ),
            # Java: gitDiffResponse = { path -> paths.add(path); someFileRelatedContent }
            diff_response=lambda path: SOME_FILE_RELATED_CONTENT,
        )

        dic.collect_git_status()

        # 'git diff' is only executed for MODIFIED files; untracked/new files get their contents copied
        self.assertEqual(2, len(paths))
        self.assert_git_files_are_correct()

        git_uuid_map = dic.Context.get().git_uuid_map
        self.assertEqual(4, len(git_uuid_map))
        self.assert_files_from_uuid_map_exist(git_uuid_map)
        self.assertEqual(
            {
                "file01": dic.GitFileStatus.MODIFIED,
                "file02": dic.GitFileStatus.MODIFIED,
                "file03": dic.GitFileStatus.UNTRACKED,
                "file04": dic.GitFileStatus.NEW,  # 'AM': the staged addition wins over the worktree modification
            },
            {Path(collected.filepath).name: collected.status for collected in git_uuid_map.values()},
        )
        # The diff output (not the file content) was saved for the modified files.
        self.assertEqual(
            [SOME_FILE_RELATED_CONTENT, SOME_FILE_RELATED_CONTENT],
            self.uuid_contents_of(git_uuid_map, ("file01", "file02")),
        )

    def test_generates_main_files_and_for_each_git_diff_that_returns_empty_copies_file_contents(self):
        for name in ("file01", "file02", "file03"):
            (self.project_root_dir / name).write_text(SOME_FILE_RELATED_CONTENT)

        paths = self.patch_git_commands(
            porcelain="\n".join(
                [
                    f" M {self.project_root_dir.name}/file01",
                    f"MM {self.project_root_dir.name}/file02",
                    f"?? {self.project_root_dir.name}/file03",
                ]
            ),
            diff_response=lambda path: "",
        )

        dic.collect_git_status()

        # 'git diff' is only executed for MODIFIED files; untracked/new files get their contents copied
        self.assertEqual(2, len(paths))
        self.assert_git_files_are_correct()

        git_uuid_map = dic.Context.get().git_uuid_map
        self.assertEqual(3, len(git_uuid_map))
        self.assert_files_from_uuid_map_exist(git_uuid_map)
        # The file contents (not the empty diff) were copied for the modified files.
        self.assertEqual(
            [SOME_FILE_RELATED_CONTENT, SOME_FILE_RELATED_CONTENT],
            self.uuid_contents_of(git_uuid_map, ("file01", "file02")),
        )

    def test_generates_main_files_and_ignores_files_that_do_not_exist(self):
        (self.project_root_dir / "file01").touch()

        paths = self.patch_git_commands(
            porcelain="\n".join(
                [
                    f" M {self.project_root_dir.name}/file01",
                    f"MM {self.project_root_dir.name}/aFileThatDoesNotExist",
                    f"?? {self.project_root_dir.name}/aFileThatDoesNotExist2",
                ]
            ),
            diff_response=lambda path: SOME_FILE_RELATED_CONTENT,
        )

        dic.collect_git_status()

        self.assertEqual(1, len(paths))
        self.assert_git_files_are_correct()

        git_uuid_map = dic.Context.get().git_uuid_map
        self.assertEqual(1, len(git_uuid_map))
        self.assert_files_from_uuid_map_exist(git_uuid_map)

    def test_generates_main_files_and_repo_root_equals_the_project_root(self):
        file = self.project_root_dir / "file01"
        file.touch()

        paths = self.patch_git_commands(
            porcelain=f" M {file.name}",
            diff_response=lambda path: SOME_FILE_RELATED_CONTENT,
            repo_root=str(self.project_root_dir),
        )

        dic.collect_git_status()

        self.assertEqual(1, len(paths))
        self.assertEqual([str(file)], paths)
        self.assert_git_files_are_correct()

        git_uuid_map = dic.Context.get().git_uuid_map
        self.assertEqual(1, len(git_uuid_map))
        self.assert_files_from_uuid_map_exist(git_uuid_map)

    def test_generates_main_files_and_supports_file_names_with_spaces(self):
        file = self.project_root_dir / "file with space"
        file.touch()

        paths = self.patch_git_commands(
            porcelain=f" M {self.project_root_dir.name}/{file.name}",
            diff_response=lambda path: SOME_FILE_RELATED_CONTENT,
        )

        dic.collect_git_status()

        self.assertEqual(1, len(paths))
        self.assertEqual([str(file)], paths)
        self.assert_git_files_are_correct()

        git_uuid_map = dic.Context.get().git_uuid_map
        self.assertEqual(1, len(git_uuid_map))
        self.assert_files_from_uuid_map_exist(git_uuid_map)


if __name__ == "__main__":
    unittest.main()


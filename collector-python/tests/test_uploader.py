"""Mirror of `collector-java/.../src/test/kotlin/action/UploaderTests.kt`.

Java mocks `executeCommandOrThrowException` (the `gcloud storage cp` command) and inspects the
artifacts written into the output directory; the same is done here by mocking
`run_command_or_throw`. The stderr-ERROR failure rule (present in both implementations'
`Uploader`) is untested on the Java side — one Python test documents it.
"""

import unittest
from unittest import mock

from _support import CollectorTestCase, dic


class UploaderTests(CollectorTestCase):

    def setUp(self):
        super().setUp()
        self.init_context()  # Java: Context.init(projectRootDir, buildDir, DeployType.GAE)

    def patch_upload_command(self, *, stdout: str = "", stderr: str = "") -> list[str]:
        """Java mirror of mocking `executeCommandOrThrowException` for the upload command."""
        captured: list[str] = []

        def fake_run_or_throw(command: str, temp_dir=None) -> dic.CommandResult:
            captured.append(command)
            return dic.CommandResult(stdout=stdout, stderr=stderr)

        patcher = mock.patch.object(dic, "run_command_or_throw", side_effect=fake_run_or_throw)
        patcher.start()
        self.addCleanup(patcher.stop)
        return captured

    def test_saves_git_uuid_map_to_csv_and_extra_uuid_map_to_properties_file(self):
        git_uuid1, git_uuid2, git_uuid3 = (str(__import__("uuid").uuid4()) for _ in range(3))
        extra_uuid1 = str(__import__("uuid").uuid4())
        context = dic.Context.get()
        context.git_uuid_map[git_uuid1] = dic.GitCollectedFile(filepath="/path/to/file01", status=dic.GitFileStatus.MODIFIED)
        context.git_uuid_map[git_uuid2] = dic.GitCollectedFile(filepath="/path/to/file02", status=dic.GitFileStatus.NEW)
        context.git_uuid_map[git_uuid3] = dic.GitCollectedFile(filepath="/path/to/file04", status=dic.GitFileStatus.UNTRACKED)
        context.extra_uuid_map[extra_uuid1] = "/path/to/file03"

        captured = self.patch_upload_command()
        dic.upload_files("fakeBucketName")

        uuid_git_csv_file = dic.Context.get().output_dir / "uuid-git.csv"
        self.assertTrue(uuid_git_csv_file.is_file())
        self.assertEqual(
            [
                "uuid,filepath,status",
                f"{git_uuid1},/path/to/file01,modified",
                f"{git_uuid2},/path/to/file02,new",
                f"{git_uuid3},/path/to/file04,untracked",
            ],
            uuid_git_csv_file.read_text().splitlines(),
        )

        uuid_extra_properties_file = dic.Context.get().output_dir / "uuid-extra.properties"
        self.assertTrue(uuid_extra_properties_file.is_file())
        # Java parses the file with java.util.Properties (skipping the leading '#' comment).
        property_lines = uuid_extra_properties_file.read_text().splitlines()
        self.assertTrue(property_lines[0].startswith("#"))
        self.assertEqual([f"{extra_uuid1}=/path/to/file03"], [line for line in property_lines[1:] if line])

        # The upload command targets the output directory inside the fake bucket.
        self.assertEqual(
            f"gcloud storage cp --recursive {__import__('os').path.abspath(str(dic.Context.get().output_dir))} gs://fakeBucketName",
            captured[0],
        )


    def test_git_uuid_csv_quotes_filepaths_containing_commas_or_quotes(self):
        git_uuid1, git_uuid2 = str(__import__("uuid").uuid4()), str(__import__("uuid").uuid4())
        context = dic.Context.get()
        context.git_uuid_map[git_uuid1] = dic.GitCollectedFile(filepath="/path/with,comma", status=dic.GitFileStatus.MODIFIED)
        context.git_uuid_map[git_uuid2] = dic.GitCollectedFile(filepath='/path/with"quote', status=dic.GitFileStatus.NEW)

        self.patch_upload_command()
        dic.upload_files("fakeBucketName")

        csv_lines = (dic.Context.get().output_dir / "uuid-git.csv").read_text().splitlines()
        self.assertEqual(
            [
                "uuid,filepath,status",
                f'{git_uuid1},"/path/with,comma",modified',
                f'{git_uuid2},"/path/with""quote",new',
            ],
            csv_lines,
        )

    def test_git_uuid_csv_file_is_not_saved_if_map_is_empty(self):
        dic.Context.get().extra_uuid_map[str(__import__("uuid").uuid4())] = "/path/to/file01"

        self.patch_upload_command()
        dic.upload_files("fakeBucketName")

        output_dir = dic.Context.get().output_dir
        self.assertFalse((output_dir / "uuid-git.csv").exists())
        self.assertTrue((output_dir / "uuid-extra.properties").is_file())

    def test_extra_uuid_properties_file_is_not_saved_if_map_is_empty(self):
        dic.Context.get().git_uuid_map[str(__import__("uuid").uuid4())] = dic.GitCollectedFile(
            filepath="/path/to/file01", status=dic.GitFileStatus.MODIFIED
        )

        self.patch_upload_command()
        dic.upload_files("fakeBucketName")

        output_dir = dic.Context.get().output_dir
        self.assertTrue((output_dir / "uuid-git.csv").is_file())
        self.assertFalse((output_dir / "uuid-extra.properties").exists())

    def test_stderr_error_lines_of_the_upload_command_fail_the_run(self):
        # Present in both implementations (stderr lines starting with 'ERROR' fail the run, the
        # exit code is ignored) but untested in the Java suite — kept to document the parity.
        dic.Context.get().git_uuid_map[str(__import__("uuid").uuid4())] = dic.GitCollectedFile(
            filepath="/path/to/file01", status=dic.GitFileStatus.MODIFIED
        )
        self.patch_upload_command(stderr="ERROR: (gcloud.storage) boom\nwarning line\nERROR: (gcloud.storage) boom")

        with self.assertRaises(dic.CollectorError) as raised:
            dic.upload_files("fakeBucketName")
        self.assertIn("ERROR: (gcloud.storage) boom", str(raised.exception))
        # Duplicated ERROR lines are reported once.
        self.assertEqual(1, str(raised.exception).count("ERROR: (gcloud.storage) boom"))


if __name__ == "__main__":
    unittest.main()

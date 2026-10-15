"""Mirror of `collector-java/.../src/test/kotlin/io/github/ghrcosta/CollectorTaskTests.kt`.

Java's `trimmedInputs()` has no Python counterpart (the CLI parser hands raw strings to
`validate_parameters` via the `CollectorParams`), so the trimming/dedup behavior of the repeatable
options is covered via `merge_projects` — the closest Python surface — and the rest mirrors
`validateParameters()`.
"""

import os
import unittest
from unittest import mock

from _support import CollectorTestCase, dic


def make_params(**overrides) -> dic.CollectorParams:
    values = dict(
        deploy_type=dic.DeployType.GAE,
        portal_url="https://deploy-info-portal.example.com",
        projects=["proj-prod", "proj-dev"],
        max_file_size="50",
        max_git_log_lines="30",
    )
    values.update(overrides)
    return dic.CollectorParams(**values)


class CollectorTaskTests(unittest.TestCase):

    def test_repeated_and_comma_separated_projects_are_merged_deduped_and_trimmed(self):
        # Python counterpart of `trimmedInputs()` (whitespace trimming of collected inputs).
        self.assertEqual(
            ["proj-prod", "proj-dev", "proj-qa"],
            dic.merge_projects([["proj-prod,proj-dev"], ["  proj-qa  "], ["proj-dev"]]),
        )

    def test_whitespace_only_portal_url_is_rejected_as_empty_after_stripping(self):
        with self.assertRaises(dic.CollectorError) as raised:
            dic.validate_parameters(make_params(portal_url="   "))
        self.assertEqual("'portalUrl' must not be empty.", str(raised.exception))

    def test_empty_project_is_rejected_as_an_empty_value(self):
        with self.assertRaises(dic.CollectorError) as raised:
            dic.validate_parameters(make_params(projects=["   ", "proj-prod", ""]))
        self.assertEqual("'projects' must not contain empty values.", str(raised.exception))

    def test_empty_project_list_is_rejected(self):
        with self.assertRaises(dic.CollectorError) as raised:
            dic.validate_parameters(make_params(projects=[]))
        self.assertIn("'projects' must not be empty", str(raised.exception))

    def test_valid_parameters_return_the_max_file_size_and_git_log_lines(self):
        self.assertEqual((50, 30), dic.validate_parameters(make_params()))

    def test_non_integer_max_git_log_lines_is_rejected(self):
        with self.assertRaises(dic.CollectorError) as raised:
            dic.validate_parameters(make_params(max_git_log_lines="abc"))
        self.assertIn("'maxGitLogLines' must be an integer", str(raised.exception))

    def test_max_git_log_lines_below_one_is_rejected(self):
        with self.assertRaises(dic.CollectorError) as raised:
            dic.validate_parameters(make_params(max_git_log_lines="0"))
        self.assertIn("'maxGitLogLines' must be at least 1", str(raised.exception))

    def test_non_integer_max_file_size_is_rejected(self):
        with self.assertRaises(dic.CollectorError) as raised:
            dic.validate_parameters(make_params(max_file_size="abc"))
        self.assertIn("'maxFileSize' must be an integer", str(raised.exception))

    def test_max_file_size_below_one_is_rejected(self):
        with self.assertRaises(dic.CollectorError) as raised:
            dic.validate_parameters(make_params(max_file_size="0"))
        self.assertIn("'maxFileSize' must be at least 1", str(raised.exception))


class RunTests(CollectorTestCase):
    """End-to-end ``run()`` behavior via the fully-mocked ``run_collector`` helper."""

    def make_run_params(self, **overrides) -> dic.CollectorParams:
        values = dict(
            deploy_type=dic.DeployType.GAE,
            portal_url=self.PORTAL_URL,
            projects=["proj-prod"],
            project_dir=str(self.project_root_dir),
            max_file_size=50,
            max_git_log_lines=30,
        )
        values.update(overrides)
        return dic.CollectorParams(**values)

    def test_run_restores_the_original_working_directory_on_success(self):
        # The Gradle plugin runs in its own process; the Python run() runs in-process, so the
        # caller's cwd must be back where it was after the call. run_collector chdirs into
        # test_dir as part of its setup, so the caller's directory here is test_dir.
        self.addCleanup(os.chdir, os.getcwd())
        os.chdir(self.test_dir)
        original_cwd = os.getcwd()
        self.run_collector(self.make_run_params())
        self.assertEqual(original_cwd, os.getcwd())

    def test_run_restores_the_original_working_directory_on_failure(self):
        self.addCleanup(os.chdir, os.getcwd())
        os.chdir(self.test_dir)
        original_cwd = os.getcwd()
        with (
            mock.patch.object(dic, "run_command_or_throw", return_value=dic.CommandResult(stdout="user@email.com\n", stderr="")),
            mock.patch.object(dic, "fetch_gcloud_access_token", return_value=None),
            mock.patch.object(dic, "lookup_bucket", side_effect=dic.CollectorError("no bucket")),
        ):
            exit_code = dic.run(self.make_run_params())
        self.assertEqual(1, exit_code)
        self.assertEqual(original_cwd, os.getcwd())


if __name__ == "__main__":
    unittest.main()

"""Mirror of `collector-java/.../src/test/kotlin/io/github/ghrcosta/CollectorTaskTests.kt`.

Java's `trimmedInputs()` has no Python counterpart (the CLI parser hands raw strings to
`validate_parameters` via the `CollectorParams`), so the trimming/dedup behavior of the repeatable
options is covered via `merge_projects` — the closest Python surface — and the rest mirrors
`validateParameters()`.
"""

import unittest

from _support import dic


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


if __name__ == "__main__":
    unittest.main()

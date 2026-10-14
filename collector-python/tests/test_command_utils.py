"""Mirror of `collector-java/deploy-info-collector/src/test/kotlin/util/CommandUtilsTests.kt`."""

import unittest

from _support import dic


class CommandUtilsTests(unittest.TestCase):
    """Tests for `tokenizeCommand` → `tokenize_command`."""

    def test_tokenize_splits_simple_commands_on_spaces(self):
        self.assertEqual(
            ["git", "status", "--porcelain", "--untracked-files=normal"],
            dic.tokenize_command("git status --porcelain --untracked-files=normal"),
        )

    def test_tokenize_treats_quoted_sections_as_a_single_argument_and_removes_the_quotes(self):
        self.assertEqual(
            ["git", "--no-pager", "diff", "/repo root/file with space.txt"],
            dic.tokenize_command('git --no-pager diff "/repo root/file with space.txt"'),
        )

    def test_tokenize_keeps_unquoted_arguments_intact_and_ignores_repeated_spaces(self):
        self.assertEqual(
            ["a", "b c", "d"],
            dic.tokenize_command('  a  "b c"   d  '),
        )


if __name__ == "__main__":
    unittest.main()

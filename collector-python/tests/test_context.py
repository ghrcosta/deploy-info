"""Mirror of `collector-java/deploy-info-collector/src/test/kotlin/util/ContextTests.kt`.

Java's `Context.init(projectRootDir, buildDir, deployType, maxFileSizeInKb = 50)` mocks
`executeCommandOrThrowException("gcloud config get-value account")`; here `init_context()`
mocks the Python equivalent (`run_command_or_throw`) the same way. Python's `Context` derives
the build dir itself (`<project-root>/build`), so only the project root is given.
"""

import unittest
from unittest import mock

from _support import CollectorTestCase, dic


class ContextTests(CollectorTestCase):

    def test_context_is_initialized_without_writing_a_collector_properties_file(self):
        context = self.init_context(user_email="user@email.com")

        self.assertIsNotNone(dic.Context.get())
        self.assertEqual("user@email.com", context.user_email)
        self.assertFalse((context.output_dir / "collector.properties").exists())
        # Python-specific parity check: the output directory name carries user/type/time.
        self.assertRegex(context.output_dir.name, r"^user_GAE_\d+$")

    def test_context_converts_the_configured_max_file_size_from_kb_to_bytes(self):
        context = self.init_context(max_file_size_kb=1)

        self.assertEqual(1024, context.max_file_size)

    def test_collector_defaults_the_max_file_size_to_50kb(self):
        # Java: Context.init defaults maxFileSizeInKb to 50. Python's Context.init takes the value
        # explicitly; the 50 KB default lives in the CLI's --max-file-size, so the default is
        # asserted through the argument parser feeding Context.init.
        args = dic.parse_arguments(
            ["--deploy-type", "GAE", "--portal-url", self.PORTAL_URL, "--projects", "proj-prod"]
        )
        self.assertEqual("50", args.max_file_size)

        context = self.init_context(max_file_size_kb=dic.parse_max_file_size_kb(args.max_file_size))

        self.assertEqual(50 * 1024, context.max_file_size)

    def test_collector_defaults_the_max_git_log_lines_to_30(self):
        args = dic.parse_arguments(
            ["--deploy-type", "GAE", "--portal-url", self.PORTAL_URL, "--projects", "proj-prod"]
        )
        self.assertEqual("30", args.max_git_log_lines)

        context = self.init_context(max_git_log_lines=dic.parse_max_git_log_lines(args.max_git_log_lines))

        self.assertEqual(30, context.max_git_log_lines)

    def test_get_before_init_fails(self):
        # Python-specific: Context.get() without init raises instead of returning null.
        with self.assertRaises(RuntimeError):
            dic.Context.get()


if __name__ == "__main__":
    unittest.main()

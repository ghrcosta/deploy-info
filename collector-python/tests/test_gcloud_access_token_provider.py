"""Mirror of `collector-java/.../src/test/kotlin/action/GcloudAccessTokenProviderTests.kt`."""

from unittest import mock

from _support import CollectorTestCase, dic


class GcloudAccessTokenProviderTests(CollectorTestCase):

    def fetch_with(self, *, stdout: str = "", error: Exception | None = None):
        patcher = mock.patch.object(dic, "run_command_or_throw")
        run_or_throw = patcher.start()
        self.addCleanup(patcher.stop)
        if error is not None:
            run_or_throw.side_effect = error
        else:
            run_or_throw.return_value = dic.CommandResult(stdout=stdout, stderr="")
        return dic.fetch_gcloud_access_token()

    def test_returns_the_trimmed_stdout_of_the_gcloud_token_command(self):
        self.assertEqual("token-1", self.fetch_with(stdout="  token-1  "))

    def test_returns_null_when_the_command_outputs_nothing(self):
        self.assertIsNone(self.fetch_with(stdout="   "))

    def test_returns_null_when_the_gcloud_command_fails_the_portal_answers_with_its_own_error_then(self):
        self.assertIsNone(self.fetch_with(error=RuntimeError("not logged in")))
        # The fallback is announced on the error log.
        self.assertIn("Could not obtain a gcloud access token", self.stderr_capture.getvalue())


if __name__ == "__main__":
    unittest.main()

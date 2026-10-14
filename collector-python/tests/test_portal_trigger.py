"""Mirror of `collector-java/.../src/test/kotlin/action/PortalTriggerTests.kt`.

Java wraps a mocked `HttpURLConnection` in a fixture capturing the written request body; here
`urllib.request.urlopen` is mocked and the captured `urllib.request.Request` exposes `.data`
(the serialized JSON body), `.method` and the headers.

Not portable: Java's `Unwritable request body` test simulates a failing connection output
stream; urllib buffers the request in memory and has no equivalent seam.
"""

import json
import unittest
import urllib.error

from _support import CollectorTestCase, dic

DIRECTORY_NAME = "jdoe_GAE_1735689600000"
PROJECTS = ["proj-prod", "proj-dev"]
DEPLOY_TYPE = "GAE"
USER_EMAIL = "jdoe@example.com"


class PortalTriggerTests(CollectorTestCase):

    def trigger(self, *, portal_url: str | None = None, access_token: str | None = None):
        """Java mirror of `triggerWith(connection, accessToken)`. The urlopen mock must already
        be patched (`self.patch_urlopen(...)`)."""
        return dic.trigger_portal_processing(
            portal_url=portal_url or self.PORTAL_URL,
            directory_name=DIRECTORY_NAME,
            projects=PROJECTS,
            deploy_type=DEPLOY_TYPE,
            user_email=USER_EMAIL,
            access_token=access_token,
        )

    def created_body(self, **fields) -> bytes:
        payload = {"status": "created", "project": "proj-dev", "service": "web", "version": "v42"}
        payload.update(fields)
        return json.dumps(payload).encode("utf-8")

    def sent_request(self, urlopen):
        request = urlopen.call_args.args[0]
        return request, json.loads(request.data)

    def test_posts_the_contract_request_body_for_a_created_link(self):
        urlopen = self.patch_urlopen(response=self.fake_response(body=self.created_body()))

        self.trigger()

        request, sent = self.sent_request(urlopen)
        self.assertEqual("POST", request.method)
        self.assertEqual(self.PORTAL_URL + "/collector/handleNewDirectory", request.full_url)
        self.assertEqual("application/json", request.get_header("Content-type"))
        self.assertEqual(
            {"directoryName": DIRECTORY_NAME, "projects": PROJECTS, "deployType": DEPLOY_TYPE, "userEmail": USER_EMAIL},
            sent,
        )

    def test_special_characters_in_the_request_survive_the_json_round_trip(self):
        tricky_projects = ['p"1', "p\\2", "p\n3"]
        tricky_email = 'weird\\name"@example.com'
        urlopen = self.patch_urlopen(response=self.fake_response(body=self.created_body()))

        dic.trigger_portal_processing(
            portal_url=self.PORTAL_URL,
            directory_name=DIRECTORY_NAME,
            projects=tricky_projects,
            deploy_type=DEPLOY_TYPE,
            user_email=tricky_email,
            access_token=None,
        )

        _, sent = self.sent_request(urlopen)
        self.assertEqual(
            {"directoryName": DIRECTORY_NAME, "projects": tricky_projects, "deployType": DEPLOY_TYPE, "userEmail": tricky_email},
            sent,
        )

    def test_trims_a_trailing_slash_from_the_portal_url(self):
        urlopen = self.patch_urlopen(response=self.fake_response(body=self.created_body()))

        self.trigger(portal_url=self.PORTAL_URL + "/")

        self.assertEqual(
            self.PORTAL_URL + "/collector/handleNewDirectory",
            urlopen.call_args.args[0].full_url,
        )

    def test_already_linked_and_pending_responses_are_accepted_without_failing_the_build(self):
        already_linked = self.fake_response(body=self.created_body(status="already-linked"))
        self.patch_urlopen(response=already_linked)
        self.trigger()  # must not raise

        self.patch_urlopen(response=self.fake_response(status=202, body=json.dumps({"status": "pending"}).encode()))
        self.trigger()  # must not raise

    def test_http_422_unknown_project_fails_with_a_message_naming_the_projects(self):
        trigger_url = self.PORTAL_URL + "/collector/handleNewDirectory"
        self.patch_urlopen(
            side_effect=self.http_error(trigger_url, 422, body=json.dumps({"status": "unknown-project"}).encode())
        )

        with self.assertRaises(dic.CollectorError) as raised:
            self.trigger()

        self.assertIn("none of the listed projects", str(raised.exception))
        self.assertIn("proj-prod", str(raised.exception))

    def test_http_400_or_any_other_error_status_fails_with_an_exception_naming_the_portal_url(self):
        trigger_url = self.PORTAL_URL + "/collector/handleNewDirectory"
        for status in (400, 500):
            self.patch_urlopen(side_effect=self.http_error(trigger_url, status))

            with self.assertRaises(dic.CollectorError) as raised:
                self.trigger()

            self.assertIn(trigger_url, str(raised.exception))
            self.assertIn(str(status), str(raised.exception))

    def test_malformed_response_body_fails_with_an_exception_naming_the_portal_url(self):
        trigger_url = self.PORTAL_URL + "/collector/handleNewDirectory"
        self.patch_urlopen(response=self.fake_response(body=b'{"status": <not valid json>'))

        with self.assertRaises(dic.CollectorError) as raised:
            self.trigger()

        self.assertIn(trigger_url, str(raised.exception))
        self.assertIn("malformed", str(raised.exception))

    def test_unexpected_status_field_fails_with_an_exception(self):
        self.patch_urlopen(response=self.fake_response(body=self.created_body(status="something-else")))

        with self.assertRaises(dic.CollectorError) as raised:
            self.trigger()

        self.assertIn("something-else", str(raised.exception))

    def test_connection_failure_or_timeout_fails_with_an_exception_naming_the_portal_url(self):
        trigger_url = self.PORTAL_URL + "/collector/handleNewDirectory"
        self.patch_urlopen(side_effect=urllib.error.URLError(IOError("timed out")))

        with self.assertRaises(dic.CollectorError) as raised:
            self.trigger()

        self.assertIn(trigger_url, str(raised.exception))
        self.assertIn("Could not reach the portal", str(raised.exception))

    def test_rejects_an_invalid_portal_url(self):
        urlopen = self.patch_urlopen()

        with self.assertRaises(dic.CollectorError) as raised:
            self.trigger(portal_url="::not a url::")

        self.assertIn("'portalUrl' is not a valid URL", str(raised.exception))
        urlopen.assert_not_called()

    def test_sends_the_access_token_as_the_authorization_header_when_present(self):
        urlopen = self.patch_urlopen(response=self.fake_response(body=self.created_body()))

        self.trigger(access_token="gcloud-token")

        request = urlopen.call_args.args[0]
        self.assertEqual("Bearer gcloud-token", request.get_header("Authorization"))

    def test_sends_no_authorization_header_without_an_access_token_portal_auth_may_be_disabled(self):
        urlopen = self.patch_urlopen(response=self.fake_response(body=self.created_body()))

        self.trigger(access_token=None)

        request = urlopen.call_args.args[0]
        self.assertIsNone(request.get_header("Authorization"))

    def test_http_401_fails_with_a_gcloud_login_hint(self):
        trigger_url = self.PORTAL_URL + "/collector/handleNewDirectory"
        self.patch_urlopen(side_effect=self.http_error(trigger_url, 401, body=b"{}"))

        with self.assertRaises(dic.CollectorError) as raised:
            self.trigger(access_token="gcloud-token")

        self.assertIn("HTTP 401", str(raised.exception))
        self.assertIn("gcloud auth login", str(raised.exception))

    def test_http_403_not_permitted_fails_with_the_iam_remediation(self):
        trigger_url = self.PORTAL_URL + "/collector/handleNewDirectory"
        error_body = json.dumps({"error": "not-permitted", "email": "jdoe@example.com"}).encode()
        self.patch_urlopen(side_effect=self.http_error(trigger_url, 403, body=error_body))

        with self.assertRaises(dic.CollectorError) as raised:
            self.trigger(access_token="gcloud-token")

        self.assertIn("HTTP 403", str(raised.exception))
        self.assertIn("not present in the IAM", str(raised.exception))
        self.assertIn("jdoe@example.com", str(raised.exception))

    def test_http_403_email_mismatch_fails_with_the_impersonation_remediation(self):
        trigger_url = self.PORTAL_URL + "/collector/handleNewDirectory"
        error_body = json.dumps(
            {
                "error": "email-mismatch",
                "tokenEmail": "sa@proj.iam.gserviceaccount.com",
                "requestUserEmail": "jdoe@example.com",
            }
        ).encode()
        self.patch_urlopen(side_effect=self.http_error(trigger_url, 403, body=error_body))

        with self.assertRaises(dic.CollectorError) as raised:
            self.trigger(access_token="gcloud-token")

        self.assertIn("HTTP 403", str(raised.exception))
        self.assertIn("email-mismatch", str(raised.exception))
        self.assertIn("impersonation", str(raised.exception))

    def test_http_403_without_a_known_error_body_still_fails_with_an_actionable_message(self):
        trigger_url = self.PORTAL_URL + "/collector/handleNewDirectory"
        self.patch_urlopen(side_effect=self.http_error(trigger_url, 403))

        with self.assertRaises(dic.CollectorError) as raised:
            self.trigger(access_token="gcloud-token")

        self.assertIn("HTTP 403", str(raised.exception))
        self.assertIn("not permitted", str(raised.exception))


if __name__ == "__main__":
    unittest.main()


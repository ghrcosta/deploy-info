"""Mirror of `collector-java/.../src/test/kotlin/action/BucketLookupTests.kt`.

Java injects a mocked `HttpURLConnection` (`BucketLookup(portalUrl) { connection }`); here
`urllib.request.urlopen` is mocked the same way and the captured `urllib.request.Request` plays
the role of the configured connection.
"""

import io
import unittest
import urllib.error

from _support import CollectorTestCase, dic

BUCKET_URL = CollectorTestCase.PORTAL_URL + "/collector/bucket"


class BucketLookupTests(CollectorTestCase):

    def test_returns_the_bucket_parsed_from_the_portal_response(self):
        urlopen = self.patch_urlopen(response=self.fake_response(body=b'{"bucket":"my-bucket-name"}'))

        self.assertEqual("my-bucket-name", dic.lookup_bucket(self.PORTAL_URL, None))

        request = urlopen.call_args.args[0]
        self.assertEqual("GET", request.method)
        self.assertEqual(BUCKET_URL, request.full_url)
        self.assertEqual(40.0, urlopen.call_args.kwargs["timeout"])  # connect+read timeouts summed

    def test_trims_a_trailing_slash_from_the_portal_url(self):
        urlopen = self.patch_urlopen(response=self.fake_response(body=b'{"bucket":"my-bucket"}'))

        dic.lookup_bucket(self.PORTAL_URL + "/", None)

        self.assertEqual(BUCKET_URL, urlopen.call_args.args[0].full_url)

    def test_rejects_an_invalid_portal_url(self):
        # urllib.request.Request raises ValueError at construction, so urlopen (Java:
        # openConnection) must never be called.
        urlopen = self.patch_urlopen()

        with self.assertRaises(dic.CollectorError) as raised:
            dic.lookup_bucket("::not a url::", None)

        self.assertIn("'portalUrl' is not a valid URL", str(raised.exception))
        urlopen.assert_not_called()

    def test_http_error_status_fails_with_an_exception_naming_the_portal_url(self):
        self.patch_urlopen(side_effect=self.http_error(BUCKET_URL, 500))

        with self.assertRaises(dic.CollectorError) as raised:
            dic.lookup_bucket(self.PORTAL_URL, None)

        self.assertIn(BUCKET_URL, str(raised.exception))
        self.assertIn("500", str(raised.exception))

    def test_malformed_response_body_fails_with_an_exception_naming_the_portal_url(self):
        self.patch_urlopen(response=self.fake_response(body=b'{"notTheBucket":"oops"}'))

        with self.assertRaises(dic.CollectorError) as raised:
            dic.lookup_bucket(self.PORTAL_URL, None)

        self.assertIn(BUCKET_URL, str(raised.exception))
        self.assertIn("malformed", str(raised.exception))

    def test_blank_bucket_field_is_treated_as_a_malformed_response(self):
        self.patch_urlopen(response=self.fake_response(body=b'{"bucket":""}'))

        with self.assertRaises(dic.CollectorError) as raised:
            dic.lookup_bucket(self.PORTAL_URL, None)

        self.assertIn("malformed", str(raised.exception))

    def test_connection_failure_or_timeout_fails_with_an_exception_naming_the_portal_url(self):
        self.patch_urlopen(side_effect=urllib.error.URLError(IOError("timed out")))

        with self.assertRaises(dic.CollectorError) as raised:
            dic.lookup_bucket(self.PORTAL_URL, None)

        self.assertIn(BUCKET_URL, str(raised.exception))
        self.assertIn("Could not reach the portal", str(raised.exception))

    def test_unreadable_response_body_fails_with_an_exception_naming_the_portal_url(self):
        # Java distinguishes "Could not read ..." (inputStream fails); Python's http_request lets
        # the OSError bubble to the caller, which words it as a connection failure — the message
        # assertion is adapted accordingly.
        self.patch_urlopen(response=self.fake_response(read_error=IOError("read timed out")))

        with self.assertRaises(dic.CollectorError) as raised:
            dic.lookup_bucket(self.PORTAL_URL, None)

        self.assertIn(BUCKET_URL, str(raised.exception))


if __name__ == "__main__":
    unittest.main()

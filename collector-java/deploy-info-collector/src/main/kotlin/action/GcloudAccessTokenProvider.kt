package io.github.ghrcosta.action

import io.github.ghrcosta.util.Logger
import io.github.ghrcosta.util.executeCommandOrThrowException

/**
 * Fetches a gcloud access token for the active gcloud account (`gcloud auth print-access-token`).
 * The token is sent as a Bearer credential so the portal can authenticate the bucket-lookup and
 * trigger calls (see `documentation/api.md`).
 *
 * Returns null when no token can be obtained: the collector cannot know whether the portal requires
 * authentication at all (it can be disabled, e.g. in local development), and gcloud is already a
 * hard prerequisite for the upload step — so the request is then simply sent without the
 * Authorization header, and a portal that requires authentication answers with its own actionable
 * error. The token itself is never logged.
 */
class GcloudAccessTokenProvider(
    /** Seam for tests: how the gcloud token command runs. */
    private val printAccessToken: () -> String =
        { executeCommandOrThrowException("gcloud auth print-access-token").stdout },
) {

    fun fetch(): String? = try {
        printAccessToken().trim().takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Logger.e(
            "Could not obtain a gcloud access token (${e.message}): the portal may reject the " +
                "bucket-lookup/trigger calls with HTTP 401/403 if it requires authentication."
        )
        null
    }
}

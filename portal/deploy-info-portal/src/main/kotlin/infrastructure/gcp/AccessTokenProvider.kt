package infrastructure.gcp

import domain.Project

/** Supplies the access token used to call the GCP listing APIs on behalf of a project's service account. */
fun interface AccessTokenProvider {

    fun tokenFor(project: Project): String
}

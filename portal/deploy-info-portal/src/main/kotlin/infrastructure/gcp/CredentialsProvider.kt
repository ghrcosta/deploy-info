package infrastructure.gcp

import com.google.auth.oauth2.GoogleCredentials
import domain.Project

/** Supplies the credentials used to call the GCP listing APIs on behalf of a project's service account. */
fun interface CredentialsProvider {

    fun credentialsFor(project: Project): GoogleCredentials
}
package tests.fakes

import application.ServiceAccountIssue
import application.ServiceAccountValidator
import domain.Project

/**
 * In-memory [ServiceAccountValidator] used in unit tests instead of the real GCP-backed
 * [infrastructure.gcp.GcpServiceAccountValidator]: reports a configurable [issue] and records every
 * validated project so tests can assert whether (and with what) validation was invoked.
 */
class FakeServiceAccountValidator(
    var issue: ServiceAccountIssue = ServiceAccountIssue.NONE,
) : ServiceAccountValidator {

    val validatedProjects = mutableListOf<Project>()

    override fun validate(project: Project): ServiceAccountIssue {
        validatedProjects += project
        return issue
    }
}

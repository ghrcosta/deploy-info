import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogTitle, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';

/**
 * Error dialog shown when the backend could not validate a project's service account while adding
 * or editing a project (see `documentation/plan-service-account-validation.md`). It names the
 * missing permission and offers expandable step-by-step instructions for granting it — via the GCP
 * Console and via the gcloud CLI.
 */
@Component({
    selector: 'service-account-error-dialog',
    imports: [
        MatButtonModule,
        MatExpansionModule,
        MatDialogTitle,
        MatDialogContent,
        MatDialogActions,
    ],
    templateUrl: './service-account-error-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './service-account-error-dialog.component.scss'
})
export class ServiceAccountErrorDialogComponent {
    readonly dialogRef = inject(MatDialogRef<ServiceAccountErrorDialogComponent>);
    readonly data = inject<ServiceAccountErrorDialogData>(MAT_DIALOG_DATA);

    isMissingImpersonationPermission = () => {
        return this.data.serviceAccountIssue == ServiceAccountIssueCode.MISSING_IMPERSONATION_PERMISSION;
    }

    isMissingListingPermission = () => {
        return this.data.serviceAccountIssue == ServiceAccountIssueCode.MISSING_LISTING_PERMISSION;
    }

    /** Only the user-fixable permission issues come with grant instructions (and a retry button). */
    hasGrantInstructions = () => {
        return this.isMissingImpersonationPermission() || this.isMissingListingPermission();
    }

    /**
     * The gcloud command for the expandable instructions, interpolated by the template into a
     * `<pre><code>` block — kept here (not in the template) so the template itself can be indented
     * normally without `<pre>` rendering the source indentation.
     */
    gcloudCommand = (): string => {
        if (this.isMissingImpersonationPermission()) {
            return [
                'gcloud iam service-accounts add-iam-policy-binding \\',
                `    ${this.data.serviceAccount} \\`,
                `    --member="serviceAccount:${this.data.portalServiceAccount || 'PORTAL_SERVICE_ACCOUNT'}" \\`,
                '    --role="roles/iam.serviceAccountTokenCreator"',
            ].join('\n');
        }
        return [
            `gcloud projects add-iam-policy-binding ${this.data.projectName} \\`,
            `    --member="serviceAccount:${this.data.serviceAccount}" \\`,
            '    --role="roles/appengine.appViewer"',
            '',
            `gcloud projects add-iam-policy-binding ${this.data.projectName} \\`,
            `    --member="serviceAccount:${this.data.serviceAccount}" \\`,
            '    --role="roles/run.viewer"',
        ].join('\n');
    }

    onCloseClicked = () => {
        this.dialogRef.close();
    }

    /** Re-submits the form, so the user can retry right after fixing IAM in another tab. */
    onTryAgainClicked = () => {
        this.dialogRef.close('retry');
    }
}

/** Issue codes reported by the backend in `result.issues.serviceAccountIssue`. */
export enum ServiceAccountIssueCode {
    MISSING_IMPERSONATION_PERMISSION = 'MISSING_IMPERSONATION_PERMISSION',
    MISSING_LISTING_PERMISSION = 'MISSING_LISTING_PERMISSION',
    PORTAL_ISSUE = 'PORTAL_ISSUE'
}

export interface ServiceAccountErrorDialogData {
    /** Issue code from the backend response (`result.issues.serviceAccountIssue`). */
    serviceAccountIssue: string;
    /** Name of the project being added or edited. */
    projectName: string;
    /** Target service account email (from the form). */
    serviceAccount: string;
    /** Portal's own service account email (from the response); undefined when it could not be resolved. */
    portalServiceAccount?: string;
}

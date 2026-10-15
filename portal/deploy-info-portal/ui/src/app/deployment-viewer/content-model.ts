/**
 * Wire-level shapes of `GET /portal/deploy/content` (see `documentation/api.md`): the collected
 * files (GIT section and extras) of one upload folder.
 */
export interface DeploymentContent {
    /** The GIT section, or null when the upload contains no git data. */
    git: GitContent | null;
    /** The extra files; empty when there are none. */
    extras: ContentFile[];
}
export interface GitContent {
    /** Content of `git-log.txt`, or null when absent. */
    gitlog: string | null;
    /** Content of `git-status.txt`, or null when absent. */
    gitstatus: string | null;
    /** Per-changed-file diffs (or full content of untracked files). */
    changes: ContentFile[];
}
/** The git state of a collected file, from the collector's `git status --porcelain`. */
export type GitFileStatus = 'new' | 'untracked' | 'modified';

export interface ContentFile {
    filepath: string;
    content: string;
    /** The git state of the file: new (staged addition), untracked, or modified; extras are always modified. */
    status: GitFileStatus;
}
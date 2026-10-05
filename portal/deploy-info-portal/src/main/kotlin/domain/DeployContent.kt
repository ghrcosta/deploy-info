package domain

/** One collected file: its original path in the repository and the content stored for it. */
data class ContentFile(
    /** The original file path as recorded by the collector (uuid-mapped). */
    val filepath: String,
    /** The text content (diff or full file content; placeholders for large/binary/blank files). */
    val content: String,
)

/**
 * The GIT section of a collector upload: the human-readable log and status, plus one entry per
 * changed file (its diff, or its full content for untracked files).
 *
 * `null` when the collector did not collect git data (no `git-log.txt` / `git-status.txt` / uuid
 * files in the upload folder).
 */
data class GitContent(
    /** Content of `git-log.txt`, or null when absent. */
    val gitLog: String?,
    /** Content of `git-status.txt`, or null when absent. */
    val gitStatus: String?,
    /** Per-changed-file diffs / full contents (paths from `uuid-git.properties`). */
    val changes: List<ContentFile>,
)

/**
 * The file content of a collector upload's Cloud Storage folder: the GIT section and the extra
 * files collected by the collector, keyed back to their original file paths through the uuid maps.
 *
 * Field names mirror the frontend's `DeployData` (`git.gitlog`, `git.gitstatus`, `git.changes`,
 * `extras`), so the REST contract (Phase 1 §4) can map this 1:1.
 */
data class DeployContent(
    /** The GIT section, or null when the upload contains no git data. */
    val git: GitContent?,
    /** The extra collected files (paths from `uuid-extra.properties`); empty when there are none. */
    val extras: List<ContentFile>,
)
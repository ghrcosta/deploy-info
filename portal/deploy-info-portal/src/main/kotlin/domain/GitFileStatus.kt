package domain

/**
 * The git state of one collected file, as recorded by the collector from `git status --porcelain`
 * (`untracked`: porcelain `??`; `new`: staged addition, code contains `A`; `modified`: everything else).
 */
enum class GitFileStatus(
    /** The value of the `status` column in `uuid-git.csv`. */
    val wireValue: String,
) {
    UNTRACKED("untracked"),
    NEW("new"),
    MODIFIED("modified"),
    ;

    companion object {
        /** Returns the status with this `uuid-git.csv` `status` value, or null when unknown. */
        fun fromWireValue(value: String?): GitFileStatus? =
            entries.firstOrNull { it.wireValue == value }
    }
}

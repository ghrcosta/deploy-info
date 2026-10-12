package io.github.ghrcosta.util

/**
 * The git state of one file collected by [io.github.ghrcosta.action.GitCollector], as classified from
 * the `git status --porcelain` code.
 */
enum class GitFileStatus(
    /** The value written to the `status` column of `uuid-git.csv`. */
    val wireValue: String,
) {
    /** Untracked file (porcelain code `??`). */
    UNTRACKED("untracked"),

    /** New file: added to the index (porcelain code contains `A`, e.g. `A ` or `AM`). */
    NEW("new"),

    /** Modification of a tracked file (porcelain code contains `M` without `A`; the fallback for other codes). */
    MODIFIED("modified"),
    ;

    companion object {
        /**
         * Classifies a `git status --porcelain` 2-char XY code: `??` is untracked; a code containing
         * `A` (staged addition) is new — taking priority over `M`; otherwise it is a modification.
         */
        fun fromPorcelainCode(code: String): GitFileStatus = when {
            code == "??" -> UNTRACKED
            'A' in code -> NEW
            else -> MODIFIED
        }
    }
}

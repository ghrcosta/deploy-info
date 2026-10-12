package io.github.ghrcosta.util

/** One file collected by [io.github.ghrcosta.action.GitCollector]: its real path and its git state. */
data class GitCollectedFile(
    /** The real path of the file in the repository (absolute, with "/" separators). */
    val filepath: String,
    /** The git state classified from `git status --porcelain` (untracked / new / modified). */
    val status: GitFileStatus,
)

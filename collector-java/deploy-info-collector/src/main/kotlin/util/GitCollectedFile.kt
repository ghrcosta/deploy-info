package io.github.ghrcosta.util

/** One file collected by [io.github.ghrcosta.action.GitCollector]: its real path and its git state. */
data class GitCollectedFile(
    /** The path of the file relative to the git repository root (with "/" separators and a leading "/"). */
    val filepath: String,
    /** The git state classified from `git status --porcelain` (untracked / new / modified). */
    val status: GitFileStatus,
)

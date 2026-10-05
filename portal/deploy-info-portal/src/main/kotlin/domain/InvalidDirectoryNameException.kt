package domain

/**
 * The collector's output directory name does not follow the `<user>_<deployType>_<epochMillis>`
 * convention, so the collection timestamp cannot be derived from it (a caller issue).
 */
class InvalidDirectoryNameException(message: String, cause: Throwable? = null) : Exception(message, cause)

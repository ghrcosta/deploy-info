package domain

/** Reading a collector upload's Cloud Storage folder failed (a portal issue, not a user issue). */
class StorageReadException(message: String, cause: Throwable? = null) : Exception(message, cause)
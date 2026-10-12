package io.github.ghrcosta.util

/**
 * Minimal CSV helpers for the `uuid-git.csv` upload artifact, RFC 4180 compliant: fields containing a
 * comma, a double quote or a line break are wrapped in double quotes, with embedded quotes doubled.
 * (Hand-rolled on purpose — Gson is the plugin's only runtime dependency.)
 */
object CsvUtils {

    private val ESCAPE_REQUIRED = charArrayOf(',', '"', '\n', '\r')

    /** Returns the field quoted (RFC 4180) when it contains a comma, double quote or line break. */
    fun escape(field: String): String =
        if (field.any { it in ESCAPE_REQUIRED }) "\"${field.replace("\"", "\"\"")}\"" else field
}

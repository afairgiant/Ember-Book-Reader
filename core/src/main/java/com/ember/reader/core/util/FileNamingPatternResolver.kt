package com.ember.reader.core.util

/**
 * Kotlin port of Grimmory's frontend file-naming pattern resolver. Produces
 * filename previews identical to Grimmory's web UI so Ember and the web show
 * the same thing before committing a move.
 *
 * Ported from:
 *  - `grimmory/frontend/src/app/shared/util/pattern-resolver.ts` (placeholder logic)
 *  - `grimmory/frontend/src/app/shared/components/file-mover/file-mover-component.ts`
 *    (sanitize / formatYear / formatSeriesIndex)
 *
 * If Grimmory changes the logic, update this file to match and keep the unit
 * tests pinned against outputs derived from the TypeScript source.
 *
 * Pattern syntax:
 *   `{field}`            — substituted with `values[field]`, or empty if absent
 *   `{field:modifier}`   — modifier applied: `first`, `sort`, `initial`, `upper`, `lower`
 *   `<primary|fallback>` — if every placeholder in `primary` has a non-blank value,
 *                          render `primary`; otherwise render `fallback` (or empty if
 *                          no fallback)
 */
object FileNamingPatternResolver {

    private val MODIFIER_PLACEHOLDER_REGEX = Regex("""\{([^}:]+)(?::([^}]+))?}""")
    private val OPTIONAL_BLOCK_REGEX = Regex("""<([^<>]+)>""")
    private val SANITIZE_ILLEGAL_CHARS = Regex("""[\\/:*?"<>|]""")
    private val WHITESPACE_RUN = Regex("""\s+""")
    private val YEAR_PREFIX = Regex("""^(\d{4})""")

    fun resolve(pattern: String, values: Map<String, String>): String {
        if (pattern.isEmpty()) return ""

        // Handle optional <primary|fallback> blocks first
        val afterBlocks = OPTIONAL_BLOCK_REGEX.replace(pattern) { match ->
            val content = match.groupValues[1]
            val pipeIndex = content.indexOf('|')
            val primary = if (pipeIndex >= 0) content.substring(0, pipeIndex) else content
            val fallback = if (pipeIndex >= 0) content.substring(pipeIndex + 1) else null
            if (checkAllPlaceholdersPresent(primary, values)) {
                resolveModifierPlaceholders(primary, values)
            } else {
                fallback?.let { resolveModifierPlaceholders(it, values) } ?: ""
            }
        }

        return resolveModifierPlaceholders(afterBlocks, values).trim()
    }

    private fun checkAllPlaceholdersPresent(block: String, values: Map<String, String>): Boolean {
        val matches = MODIFIER_PLACEHOLDER_REGEX.findAll(block).toList()
        // TS behavior: `matches.every(...)` over zero matches is vacuously true.
        if (matches.isEmpty()) return true
        return matches.all { m -> values[m.groupValues[1]]?.trim().orEmpty().isNotBlank() }
    }

    private fun resolveModifierPlaceholders(
        block: String,
        values: Map<String, String>,
    ): String = MODIFIER_PLACEHOLDER_REGEX.replace(block) { match ->
        val fieldName = match.groupValues[1]
        val modifier = match.groupValues[2].takeIf { it.isNotEmpty() }
        val raw = values[fieldName].orEmpty()
        if (modifier != null) applyModifier(raw, modifier, fieldName) else raw
    }

    private fun applyModifier(value: String, modifier: String, fieldName: String): String {
        if (value.isEmpty()) return value
        return when (modifier) {
            "first" -> value.split(", ").first().trim()
            "sort" -> {
                val first = value.split(", ").first().trim()
                val lastSpace = first.lastIndexOf(' ')
                if (lastSpace > 0) {
                    "${first.substring(lastSpace + 1)}, ${first.substring(0, lastSpace)}"
                } else {
                    first
                }
            }
            "initial" -> {
                val target = if (fieldName == "authors") {
                    val firstAuthor = value.split(", ").first().trim()
                    val lastSpace = firstAuthor.lastIndexOf(' ')
                    if (lastSpace > 0) firstAuthor.substring(lastSpace + 1) else firstAuthor
                } else {
                    value
                }
                target.firstOrNull()?.uppercaseChar()?.toString() ?: ""
            }
            "upper" -> value.uppercase()
            "lower" -> value.lowercase()
            else -> value
        }
    }

    /**
     * Mirrors the `sanitize` method in Grimmory's `file-mover-component.ts`:
     * strips path-illegal characters (`\ / : * ? " < > |`), strips control
     * characters (code < 32 or == 127), collapses runs of whitespace to single
     * spaces, and trims. Matches the TS behavior byte-for-byte on the ASCII plane.
     */
    fun sanitize(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val stripped = SANITIZE_ILLEGAL_CHARS
            .replace(input, "")
            .filter { ch ->
                val code = ch.code
                code >= 32 && code != 127
            }
        return WHITESPACE_RUN.replace(stripped, " ").trim()
    }

    /**
     * Mirrors `formatYear`: first tries to match a leading 4-digit year, else
     * parses the input as an ISO date and takes the UTC year, else empty.
     */
    fun formatYear(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        YEAR_PREFIX.find(dateStr)?.let { return it.groupValues[1] }
        return runCatching {
            val iso = if (dateStr.contains('T')) dateStr else "${dateStr}T00:00:00Z"
            java.time.Instant.parse(iso).atZone(java.time.ZoneOffset.UTC).year.toString()
        }.getOrElse { "" }
    }

    /**
     * Mirrors `formatSeriesIndex`:
     *   - integer `n`     → two-digit zero-padded string
     *   - decimal `n.k`   → zero-padded integer part, then `.k` with trailing zeros stripped
     *   - `null`          → empty string
     */
    fun formatSeriesIndex(seriesNumber: Float?): String {
        if (seriesNumber == null) return ""
        if (seriesNumber == seriesNumber.toInt().toFloat()) {
            return sanitize("%02d".format(seriesNumber.toInt()))
        }
        val intPart = seriesNumber.toInt()
        val raw = "%.10f".format(seriesNumber - intPart)
        // raw looks like "0.5000000000"; drop the leading "0", then strip trailing zeros.
        val decimalPart = raw.substring(raw.indexOf('.')).trimEnd('0')
        return sanitize("%02d".format(intPart) + decimalPart)
    }
}

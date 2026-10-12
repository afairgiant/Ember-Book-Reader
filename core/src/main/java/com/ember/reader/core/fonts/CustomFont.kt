package com.ember.reader.core.fonts

/**
 * A font file the user added for reading EPUBs.
 *
 * @param name what the font is called in the app, and its CSS family name in the reader.
 * @param fileName the file in the app's fonts directory.
 */
data class CustomFont(
    val name: String,
    val fileName: String,
) {
    companion object {

        /** Where the reader page finds custom fonts, inside the publication's own URL space. */
        const val RESOURCE_PREFIX = "ember-fonts/"

        private val EXTENSIONS = setOf("ttf", "otf")
        private val UNSAFE_IN_NAME = Regex("""[^\p{L}\p{N} _-]""")
        private val UNSAFE_IN_FILE = Regex("""[^A-Za-z0-9_-]""")

        /**
         * The font a picked file would become, or null when its name does not end in a
         * supported extension. The name is kept to characters that are safe inside a quoted
         * CSS family name, and the file name to ones that need no escaping in a URL.
         */
        fun fromDisplayName(displayName: String): CustomFont? {
            val extension = displayName.substringAfterLast('.', "").lowercase()
            if (extension !in EXTENSIONS) return null
            val base = displayName.substringBeforeLast('.')
            val name = base.replace(UNSAFE_IN_NAME, " ").replace(Regex(" +"), " ").trim()
            if (name.isEmpty()) return null
            return CustomFont(name = name, fileName = base.replace(UNSAFE_IN_FILE, "_") + "." + extension)
        }

        /** True for the first bytes of a TrueType or OpenType font file. */
        fun isFontHeader(header: ByteArray): Boolean {
            if (header.size < 4) return false
            val tag = String(header, 0, 4, Charsets.ISO_8859_1)
            return tag == "OTTO" || tag == "true" || tag == "\u0000\u0001\u0000\u0000"
        }
    }
}

package com.ember.reader.core.dictionary

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dictionary provider backed by Wiktionary's REST API.
 *
 * Endpoint: https://en.wiktionary.org/api/rest_v1/page/definition/{word}
 *
 * Coverage is far broader than Free Dictionary API, including rare words,
 * archaic forms, and many proper nouns. Definitions arrive as HTML fragments
 * which we strip to plain text.
 */
@Singleton
class WiktionaryProvider @Inject constructor(
    private val httpClient: HttpClient,
) : DictionaryProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun lookup(word: String): Result<DictionaryResult> = runCatching {
        val encoded = java.net.URLEncoder.encode(word.trim(), "UTF-8")
        val response = httpClient.get("https://en.wiktionary.org/api/rest_v1/page/definition/$encoded") {
            // Wiktionary's REST API requests a descriptive User-Agent.
            header("User-Agent", "Ember-Reader/1.0 (https://github.com/ember-reader)")
            header("Accept", "application/json")
        }
        if (!response.status.isSuccess()) error("Word not found")

        val body = response.body<String>()
        val root = json.parseToJsonElement(body).jsonObject

        // Prefer English entries; fall back to the first language present.
        val languageEntries = root["en"]?.jsonArray
            ?: root.entries.firstOrNull()?.value?.jsonArray
            ?: error("No definitions found")

        val definitions = mutableListOf<Definition>()
        for (entry in languageEntries) {
            val obj = entry.jsonObject
            val partOfSpeech = obj["partOfSpeech"]?.jsonPrimitive?.content
                ?.lowercase()
                ?: continue
            val defs = obj["definitions"]?.jsonArray ?: continue
            for (def in defs) {
                val defObj = def.jsonObject
                val raw = defObj["definition"]?.jsonPrimitive?.content ?: continue
                val cleaned = stripHtml(raw).trim()
                if (cleaned.isEmpty()) continue

                // Wiktionary provides examples as an array of HTML strings.
                val example = defObj["examples"]?.jsonArray
                    ?.firstOrNull()
                    ?.jsonPrimitive
                    ?.content
                    ?.let { stripHtml(it).trim() }
                    ?.takeIf { it.isNotEmpty() }

                definitions.add(Definition(partOfSpeech, cleaned, example))
            }
        }

        if (definitions.isEmpty()) error("No definitions found")

        DictionaryResult(
            word = word,
            phonetic = null,
            definitions = definitions,
        )
    }

    /**
     * Strips HTML tags and decodes a small set of common entities. Wiktionary
     * definitions are short fragments, so a regex pass is sufficient — pulling
     * in a full HTML parser would be overkill.
     */
    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
    }
}

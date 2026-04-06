package com.ember.reader.core.dictionary

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreeDictionaryApiProvider @Inject constructor(
    private val httpClient: HttpClient,
) : DictionaryProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun lookup(word: String): Result<DictionaryResult> = runCatching {
        val encoded = java.net.URLEncoder.encode(word.trim(), "UTF-8")
        val response = httpClient.get("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded")
        if (!response.status.isSuccess()) error("Word not found")

        val body = response.body<String>()
        val entries = json.parseToJsonElement(body).jsonArray
        if (entries.isEmpty()) error("No definitions found")

        val entry = entries[0].jsonObject
        val phonetic = entry["phonetic"]?.jsonPrimitive?.content

        val definitions = mutableListOf<Definition>()
        val meanings = entry["meanings"]?.jsonArray ?: JsonArray(emptyList())
        for (meaning in meanings) {
            val obj = meaning.jsonObject
            val partOfSpeech = obj["partOfSpeech"]?.jsonPrimitive?.content ?: continue
            val defs = obj["definitions"]?.jsonArray ?: continue
            for (def in defs) {
                val defObj = def.jsonObject
                val definition = defObj["definition"]?.jsonPrimitive?.content ?: continue
                val example = defObj["example"]?.jsonPrimitive?.content
                definitions.add(Definition(partOfSpeech, definition, example))
            }
        }

        if (definitions.isEmpty()) error("No definitions found")

        DictionaryResult(
            word = entry["word"]?.jsonPrimitive?.content ?: word,
            phonetic = phonetic,
            definitions = definitions,
        )
    }
}

package com.ember.reader.core.dictionary

data class DictionaryResult(
    val word: String,
    val phonetic: String? = null,
    val definitions: List<Definition>
)

data class Definition(
    val partOfSpeech: String,
    val meaning: String,
    val example: String? = null
)

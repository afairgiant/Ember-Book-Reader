package com.ember.reader.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import timber.log.Timber

/**
 * In-memory ring buffer that captures Timber logs for the developer log viewer.
 * Planted alongside DebugTree so all logs are captured.
 */
object DevLog {

    private const val MAX_ENTRIES = 500

    data class Entry(
        val timestamp: Long,
        val level: String,
        val tag: String?,
        val message: String
    ) {
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        fun formatted(): String {
            val time = timeFormat.format(Date(timestamp))
            val t = tag?.let { "[$it] " } ?: ""
            return "$time $level $t$message"
        }
    }

    private val entries = CopyOnWriteArrayList<Entry>()

    fun getEntries(): List<Entry> = entries.toList()

    fun clear() = entries.clear()

    fun allText(): String = entries.joinToString("\n") { it.formatted() }

    val tree: Timber.Tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val level = when (priority) {
                android.util.Log.VERBOSE -> "V"
                android.util.Log.DEBUG -> "D"
                android.util.Log.INFO -> "I"
                android.util.Log.WARN -> "W"
                android.util.Log.ERROR -> "E"
                else -> "?"
            }
            val fullMessage = if (t != null) "$message\n${t.stackTraceToString()}" else message
            entries.add(Entry(System.currentTimeMillis(), level, tag, fullMessage))
            // Trim from front if over limit
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
        }
    }
}

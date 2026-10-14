package com.ember.reader.ui.reader.epub

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.getOrElse
import timber.log.Timber

/** What read aloud is doing. */
sealed interface ReadAloudState {
    data object Off : ReadAloudState

    /** The speech engine is being set up. */
    data object Starting : ReadAloudState

    /** A book is being read; [playing] is false while it is paused. */
    data class Reading(val playing: Boolean) : ReadAloudState

    /** It could not start, or stopped by itself, for [reason]. Stays until dismissed. */
    data class Failed(val reason: ReadAloudFailure) : ReadAloudState
}

enum class ReadAloudFailure {
    /** The book has no text that can be read out, such as a fixed-layout one. */
    NO_TEXT,

    /** The device has no text-to-speech engine, or it would not start. */
    NO_ENGINE,

    /** The engine has no voice installed for the book's language. */
    VOICE_MISSING,
    OTHER
}

/** The speeds read aloud offers, as multiples of the voice's own. */
internal object ReadAloudSpeed {
    const val DEFAULT = 1.0
    private val STEPS = listOf(0.75, 1.0, 1.25, 1.5, 1.75, 2.0)

    /** The next step up or down from [speed]; [speed] itself at either end. */
    fun step(speed: Double, faster: Boolean): Double {
        val index = STEPS.indexOfFirst { it >= speed - 0.001 }.takeIf { it >= 0 } ?: STEPS.lastIndex
        return STEPS[(index + if (faster) 1 else -1).coerceIn(0, STEPS.lastIndex)]
    }

    fun isSlowest(speed: Double): Boolean = speed <= STEPS.first() + 0.001
    fun isFastest(speed: Double): Boolean = speed >= STEPS.last() - 0.001

    /** "1×", "1.25×": the speed with no more decimals than it needs. */
    fun label(speed: Double): String = speed.toString().trimEnd('0').trimEnd('.') + "×"
}

/**
 * Reads a book aloud with the device's text-to-speech engine, sentence by sentence, through
 * Readium's TTS navigator.
 *
 * It lasts as long as the reader that started it. [ReadAloudService] holds its player in a
 * media session meanwhile, so that it carries on with the screen off or the app in the
 * background, and can be paused from the lock screen.
 *
 * @param loadSpeed the speed last chosen, read when reading starts.
 * @param saveSpeed keeps a newly chosen speed for next time.
 */
@OptIn(ExperimentalReadiumApi::class)
internal class ReadAloud(
    private val scope: CoroutineScope,
    private val loadSpeed: suspend () -> Double = { ReadAloudSpeed.DEFAULT },
    private val saveSpeed: suspend (Double) -> Unit = {}
) {

    private var navigator: AndroidTtsNavigator? = null
    private var application: Application? = null
    private var watching: Job? = null

    private val _state = MutableStateFlow<ReadAloudState>(ReadAloudState.Off)
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()

    private val _spoken = MutableStateFlow<Locator?>(null)

    /** The sentence being spoken, for the reader to mark and keep on screen. */
    val spoken: StateFlow<Locator?> = _spoken.asStateFlow()

    private val _speed = MutableStateFlow(ReadAloudSpeed.DEFAULT)
    val speed: StateFlow<Double> = _speed.asStateFlow()

    /** Starts reading [publication] from [from], or from its beginning. */
    fun start(application: Application, publication: Publication, from: Locator?) {
        if (_state.value is ReadAloudState.Starting || _state.value is ReadAloudState.Reading) return
        _state.value = ReadAloudState.Starting
        scope.launch {
            _speed.value = loadSpeed()
            val factory = TtsNavigatorFactory(application, publication)
                ?: return@launch fail(ReadAloudFailure.NO_TEXT)
            val listener = object : TtsNavigator.Listener {
                override fun onStopRequested() = stop()
            }
            val created = factory.createNavigator(listener, from, AndroidTtsPreferences(speed = _speed.value))
                .getOrElse { error ->
                    Timber.w("Read aloud: could not start: %s", error.message)
                    val reason = when (error) {
                        is TtsNavigatorFactory.Error.EngineInitialization -> ReadAloudFailure.NO_ENGINE
                        is TtsNavigatorFactory.Error.UnsupportedPublication -> ReadAloudFailure.NO_TEXT
                    }
                    return@launch fail(reason)
                }
            // Stopped while the engine was starting.
            if (_state.value != ReadAloudState.Starting) {
                created.close()
                return@launch
            }
            navigator = created
            this@ReadAloud.application = application
            ReadAloudPlayback.player = created.asMedia3Player()
            ReadAloudPlayback.stop = ::stop
            runCatching { ReadAloudPlayback.startService(application) }
                .onFailure { Timber.w(it, "Read aloud: no playback service; it will stop with the screen") }
            watching = scope.launch {
                launch { created.location.collect { _spoken.value = it.utteranceLocator } }
                created.playback.collect { playback ->
                    when (val playbackState = playback.state) {
                        is TtsNavigator.State.Ended -> stop()
                        is TtsNavigator.State.Failure -> {
                            Timber.w("Read aloud: stopped: %s", playbackState.error.message)
                            stop()
                            fail(failureOf(playbackState.error))
                        }
                        else -> _state.value = ReadAloudState.Reading(playing = playback.playWhenReady)
                    }
                }
            }
            created.play()
        }
    }

    fun togglePlaying() {
        val reading = _state.value as? ReadAloudState.Reading ?: return
        if (reading.playing) navigator?.pause() else navigator?.play()
    }

    fun previousSentence() {
        navigator?.skipToPreviousUtterance()
    }

    fun nextSentence() {
        navigator?.skipToNextUtterance()
    }

    /** Steps the speed up or down; nothing happens at either end of the range. */
    fun changeSpeed(faster: Boolean) {
        val speed = ReadAloudSpeed.step(_speed.value, faster)
        _speed.value = speed
        navigator?.submitPreferences(AndroidTtsPreferences(speed = speed))
        scope.launch { saveSpeed(speed) }
    }

    /** Stops reading and lets go of the speech engine. Also clears a failure. */
    fun stop() {
        watching?.cancel()
        watching = null
        ReadAloudPlayback.player = null
        ReadAloudPlayback.stop = null
        application?.let { runCatching { ReadAloudPlayback.stopService(it) } }
        application = null
        navigator?.close()
        navigator = null
        _spoken.value = null
        _state.value = ReadAloudState.Off
    }

    private fun fail(reason: ReadAloudFailure) {
        _state.value = ReadAloudState.Failed(reason)
    }

    private fun failureOf(error: TtsNavigator.Error): ReadAloudFailure = when (error) {
        is TtsNavigator.Error.EngineError<*> -> when (error.cause) {
            is AndroidTtsEngine.Error.LanguageMissingData,
            AndroidTtsEngine.Error.NotInstalledYet -> ReadAloudFailure.VOICE_MISSING
            else -> ReadAloudFailure.OTHER
        }
        is TtsNavigator.Error.ContentError -> ReadAloudFailure.OTHER
    }
}

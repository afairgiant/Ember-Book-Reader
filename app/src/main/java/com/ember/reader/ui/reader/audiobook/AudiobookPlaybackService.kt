package com.ember.reader.ui.reader.audiobook

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.ember.reader.core.repository.AppPreferencesRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the ExoPlayer instance and MediaSession so audio continues
 * when the app is backgrounded or the screen is off.
 *
 * It also keeps the sleep timer, for the same reason: the player screen may be long gone
 * by the time the timer runs out. The timer is set with [SleepTimer.SET_COMMAND] and shown
 * to the screen through the session's extras.
 *
 * Other apps (the lock screen, the notification, headsets) get the player through a
 * [SkipPlayer], so that their buttons skip by the lengths chosen in the player rather than
 * by whole tracks.
 */
@AndroidEntryPoint
class AudiobookPlaybackService : MediaSessionService() {

    @Inject lateinit var appPreferencesRepository: AppPreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null
    private var skipPlayer: SkipPlayer? = null

    private var sleepTimer: SleepTimer = SleepTimer.Off
    private val handler = Handler(Looper.getMainLooper())
    private val sleepTimerTick = object : Runnable {
        override fun run() {
            val player = mediaSession?.player ?: return
            val now = SystemClock.elapsedRealtime()
            if (sleepTimer.isDue(now, player.currentMediaItemIndex, player.currentPosition)) {
                Timber.d("AudiobookPlaybackService: sleep timer stopped playback")
                player.pause()
                setSleepTimer(SleepTimer.Off)
            } else {
                player.volume = sleepTimer.volume(now)
                handler.postDelayed(this, SLEEP_TIMER_TICK_MS)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Timber.d("AudiobookPlaybackService: onCreate")

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                // handleAudioFocus
                true
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones disconnected
            .build()
        val skipPlayer = SkipPlayer(player).also { skipPlayer = it }

        val session = MediaSession.Builder(this, skipPlayer)
            .setCallback(SessionCallback())
            .setMediaButtonPreferences(skipButtons(this, skipPlayer.lengths))
            .build()
        mediaSession = session

        scope.launch {
            combine(
                appPreferencesRepository.audiobookSkipBackSecondsFlow,
                appPreferencesRepository.audiobookSkipForwardSecondsFlow,
                ::SkipLengths,
            ).distinctUntilChanged().collect { lengths ->
                skipPlayer.lengths = lengths
                session.setMediaButtonPreferences(skipButtons(this@AudiobookPlaybackService, lengths))
            }
        }
    }

    /** Starts, replaces or (with [SleepTimer.Off]) cancels the sleep timer, and tells the screen. */
    private fun setSleepTimer(timer: SleepTimer) {
        handler.removeCallbacks(sleepTimerTick)
        sleepTimer = timer
        // A timer that was fading out may have left the volume down.
        mediaSession?.player?.volume = 1f
        mediaSession?.setSessionExtras(timer.toBundle())
        if (timer != SleepTimer.Off) handler.post(sleepTimerTick)
    }

    @OptIn(UnstableApi::class)
    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SleepTimer.SET_COMMAND)
                        .build()
                )
                .setSessionExtras(sleepTimer.toBundle())
            if (session.isMediaNotificationController(controller)) {
                // Without previous and next, the lock screen and notification put the skip
                // buttons in their place.
                result.setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .removeAll(
                            Player.COMMAND_SEEK_TO_PREVIOUS,
                            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                            Player.COMMAND_SEEK_TO_NEXT,
                            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        )
                        .build()
                )
            }
            return result.build()
        }

        /**
         * A headset's next and previous keys skip too. They would otherwise reach the player
         * as previous and next, which the lock screen's controller is not allowed.
         */
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            val player = skipPlayer ?: return false
            if (event == null) return false
            val skip: (() -> Unit)? = when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> player::seekForward
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> player::seekBack
                else -> null
            }
            if (skip == null) return false
            if (event.action == KeyEvent.ACTION_DOWN) skip()
            return true
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != SleepTimer.SET_COMMAND.customAction) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            setSleepTimer(SleepTimer.fromBundle(args))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Timber.d("AudiobookPlaybackService: onDestroy")
        handler.removeCallbacks(sleepTimerTick)
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val SLEEP_TIMER_TICK_MS = 500L
    }
}

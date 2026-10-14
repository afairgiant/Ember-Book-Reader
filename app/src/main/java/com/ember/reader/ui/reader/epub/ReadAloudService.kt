package com.ember.reader.ui.reader.epub

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ember.reader.MainActivity
import timber.log.Timber

/**
 * What is being read aloud, handed from [ReadAloud] to [ReadAloudService]. The speech engine
 * belongs to the reader that started it; the service only needs its player to put a media
 * session around.
 */
internal object ReadAloudPlayback {
    @Volatile
    var player: Player? = null

    /** Stops the reading; set by whoever started it. Called when the task is swiped away. */
    @Volatile
    var stop: (() -> Unit)? = null

    fun startService(context: Context) {
        context.startService(Intent(context, ReadAloudService::class.java))
    }

    fun stopService(context: Context) {
        context.stopService(Intent(context, ReadAloudService::class.java))
    }
}

/**
 * Keeps read aloud going with the screen off or the app in the background, and gives it
 * lock-screen and notification controls, by holding its player in a media session.
 *
 * It lasts as long as the reading does: [ReadAloud] starts it when speech begins and stops it
 * when speech ends.
 */
class ReadAloudService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val player = ReadAloudPlayback.player
        if (player == null) {
            // Nothing to read: the reader that started this has gone.
            stopSelf()
            return START_NOT_STICKY
        }
        if (session?.player !== player) {
            session?.let {
                removeSession(it)
                it.release()
            }
            val openApp = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Its own id: the audiobook player's session may exist beside it.
            session = MediaSession.Builder(this, player)
                .setId(SESSION_ID)
                .setSessionActivity(openApp)
                .build()
                .also(::addSession)
            Timber.d("ReadAloudService: session opened")
        }
        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The reader is gone with the task, so there is nothing left to follow the speech.
        ReadAloudPlayback.stop?.invoke()
        stopSelf()
    }

    override fun onDestroy() {
        Timber.d("ReadAloudService: onDestroy")
        // The player is the reader's to close, not this session's.
        session?.release()
        session = null
        super.onDestroy()
    }

    private companion object {
        const val SESSION_ID = "read-aloud"
    }
}

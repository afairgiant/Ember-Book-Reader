package com.ember.reader.ui.reader.audiobook

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import com.ember.reader.R
import com.ember.reader.core.repository.DEFAULT_AUDIOBOOK_SKIP_SECONDS

/** The skip lengths the player screen shows, in seconds. */
internal data class SkipLengths(
    val backSeconds: Int = DEFAULT_AUDIOBOOK_SKIP_SECONDS,
    val forwardSeconds: Int = DEFAULT_AUDIOBOOK_SKIP_SECONDS,
)

/**
 * The audiobook player as other apps see it: the lock screen, the notification, headsets
 * and cars. Their seek back and forward jump by the lengths chosen in the player, and their
 * previous and next buttons do the same, since a track in an audiobook is usually a whole
 * chapter and jumping one by accident loses the listener's place.
 *
 * The player screen moves between chapters itself, with the media-item calls, which this
 * leaves alone.
 */
@OptIn(UnstableApi::class)
internal class SkipPlayer(player: Player) : ForwardingPlayer(player) {

    @Volatile
    var lengths: SkipLengths = SkipLengths()

    override fun getSeekBackIncrement(): Long = lengths.backSeconds * 1000L

    override fun getSeekForwardIncrement(): Long = lengths.forwardSeconds * 1000L

    override fun seekBack() {
        seekTo((currentPosition - seekBackIncrement).coerceAtLeast(0))
    }

    override fun seekForward() {
        val target = currentPosition + seekForwardIncrement
        val duration = duration
        seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }

    override fun seekToPrevious() = seekBack()

    override fun seekToNext() = seekForward()
}

/** The skip back and forward buttons for the lock screen and notification, in place of previous and next. */
@OptIn(UnstableApi::class)
internal fun skipButtons(context: Context, lengths: SkipLengths): List<CommandButton> = listOf(
    CommandButton.Builder(skipBackIcon(lengths.backSeconds))
        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
        .setDisplayName(context.getString(R.string.audiobook_skip_back_cd, lengths.backSeconds))
        .setSlots(CommandButton.SLOT_BACK)
        .build(),
    CommandButton.Builder(skipForwardIcon(lengths.forwardSeconds))
        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
        .setDisplayName(context.getString(R.string.audiobook_skip_forward_cd, lengths.forwardSeconds))
        .setSlots(CommandButton.SLOT_FORWARD)
        .build(),
)

/** Media3's numbered skip-back icon for [seconds], or its plain one when there is none. */
@OptIn(UnstableApi::class)
internal fun skipBackIcon(seconds: Int): Int = when (seconds) {
    5 -> CommandButton.ICON_SKIP_BACK_5
    10 -> CommandButton.ICON_SKIP_BACK_10
    15 -> CommandButton.ICON_SKIP_BACK_15
    30 -> CommandButton.ICON_SKIP_BACK_30
    else -> CommandButton.ICON_SKIP_BACK
}

/** Media3's numbered skip-forward icon for [seconds], or its plain one when there is none. */
@OptIn(UnstableApi::class)
internal fun skipForwardIcon(seconds: Int): Int = when (seconds) {
    5 -> CommandButton.ICON_SKIP_FORWARD_5
    10 -> CommandButton.ICON_SKIP_FORWARD_10
    15 -> CommandButton.ICON_SKIP_FORWARD_15
    30 -> CommandButton.ICON_SKIP_FORWARD_30
    else -> CommandButton.ICON_SKIP_FORWARD
}

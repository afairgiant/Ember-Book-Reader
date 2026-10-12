package com.ember.reader.ui.reader.common

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ember.reader.R

/** Where a repair of a book's highlights or bookmarks stands. */
sealed interface CfiRepairState {
    data object Idle : CfiRepairState
    data object Running : CfiRepairState
    data class Done(val repaired: Int, val unresolved: Int) : CfiRepairState
    data object Failed : CfiRepairState
}

/** The wording of a [CfiRepairRow]: one set for highlights, one for bookmarks. */
class CfiRepairText(
    @PluralsRes val offer: Int,
    @StringRes val running: Int,
    @PluralsRes val done: Int,
    @PluralsRes val unresolved: Int,
    @StringRes val failed: Int,
) {
    companion object {
        val Highlights = CfiRepairText(
            offer = R.plurals.highlight_repair_offer,
            running = R.string.highlight_repair_running,
            done = R.plurals.highlight_repair_done,
            unresolved = R.plurals.highlight_repair_unmatched,
            failed = R.string.highlight_repair_failed,
        )
        val Bookmarks = CfiRepairText(
            offer = R.plurals.bookmark_repair_offer,
            running = R.string.bookmark_repair_running,
            done = R.plurals.bookmark_repair_done,
            unresolved = R.plurals.bookmark_repair_unplaced,
            failed = R.string.bookmark_repair_failed,
        )
    }
}

/**
 * Offers the repair when some items have no real CFI, and reports how it went. Those items
 * are the ones Grimmory's web reader cannot show.
 */
@Composable
fun CfiRepairRow(lackingCfi: Int, state: CfiRepairState, text: CfiRepairText, onRepair: () -> Unit) {
    val message = when (state) {
        CfiRepairState.Running -> stringResource(text.running)
        CfiRepairState.Failed -> stringResource(text.failed)
        is CfiRepairState.Done -> {
            val done = pluralStringResource(text.done, state.repaired, state.repaired)
            if (state.unresolved == 0) {
                done
            } else {
                done + " " + pluralStringResource(text.unresolved, state.unresolved, state.unresolved)
            }
        }
        CfiRepairState.Idle ->
            if (lackingCfi == 0) {
                return
            } else {
                pluralStringResource(text.offer, lackingCfi, lackingCfi)
            }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (state == CfiRepairState.Idle || state == CfiRepairState.Failed) {
            TextButton(onClick = onRepair) {
                Text(stringResource(R.string.highlight_repair_action))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CfiRepairRowPreview() {
    CfiRepairRow(lackingCfi = 3, state = CfiRepairState.Idle, text = CfiRepairText.Bookmarks, onRepair = {})
}

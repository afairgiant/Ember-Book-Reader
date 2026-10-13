package com.ember.reader.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.ui.settings.components.SettingsCard
import com.ember.reader.ui.theme.EmberTheme

/** Why Home has no book to continue, which decides what it offers in that book's place. */
sealed interface HomeEmpty {

    /** Nothing has been set up: no server, and no book on the device. */
    data object Welcome : HomeEmpty

    /** There are books to be had, and none has been started. */
    data class NothingInProgress(val hasServer: Boolean) : HomeEmpty
}

/**
 * What Home shows where the book being read would be; null when there is such a book.
 *
 * @param reading how many books are in progress.
 * @param servers how many servers are set up.
 * @param downloaded how many books are on the device.
 */
internal fun homeEmptyOf(reading: Int, servers: Int, downloaded: Int): HomeEmpty? = when {
    reading > 0 -> null
    servers == 0 && downloaded == 0 -> HomeEmpty.Welcome
    else -> HomeEmpty.NothingInProgress(hasServer = servers > 0)
}

/** The card in the hero's place when no book is in progress: what to do to get one. */
@Composable
fun HomeEmptyCard(
    empty: HomeEmpty,
    onAddServer: () -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val welcome = empty is HomeEmpty.Welcome
    SettingsCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(if (welcome) R.string.home_welcome_title else R.string.home_idle_title),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(if (welcome) R.string.home_welcome_body else R.string.home_idle_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (empty) {
                    HomeEmpty.Welcome -> {
                        Button(onClick = onAddServer) { Text(stringResource(R.string.add_server)) }
                        OutlinedButton(onClick = onOpenLibrary) { Text(stringResource(R.string.home_import_book)) }
                    }
                    is HomeEmpty.NothingInProgress -> if (empty.hasServer) {
                        Button(onClick = onOpenBrowse) { Text(stringResource(R.string.home_browse)) }
                        OutlinedButton(onClick = onOpenLibrary) { Text(stringResource(R.string.home_open_library)) }
                    } else {
                        Button(onClick = onOpenLibrary) { Text(stringResource(R.string.home_open_library)) }
                        OutlinedButton(onClick = onAddServer) { Text(stringResource(R.string.add_server)) }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeEmptyCardPreview() {
    EmberTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            HomeEmptyCard(HomeEmpty.Welcome, {}, {}, {})
            HomeEmptyCard(HomeEmpty.NothingInProgress(hasServer = true), {}, {}, {})
        }
    }
}

package com.ember.reader.ui.server

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.ui.common.CoverImage
import com.ember.reader.ui.settings.components.SettingsLabel
import com.ember.reader.ui.theme.EmberTheme
import kotlin.math.roundToInt

private val HeroShape = RoundedCornerShape(28.dp)
private val CoverShape = RoundedCornerShape(12.dp)

/**
 * The book the reader was last in, as the first thing on Home: cover, title, progress and a
 * button back into it. The whole card opens the book. On Android 12 and later a blurred copy
 * of the cover sits behind the card's content.
 */
@Composable
fun HomeHeroCard(recent: RecentBook, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val book = recent.book
    val isAudiobook = book.format == BookFormat.AUDIOBOOK
    Surface(
        onClick = onContinue,
        modifier = modifier.fillMaxWidth(),
        shape = HeroShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box {
            if (book.coverUrl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                CoverImage(
                    coverUrl = book.coverUrl,
                    title = book.title,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(40.dp)
                        .alpha(0.5f)
                )
                // Keeps the text readable whatever the cover's colours are.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f))
                )
            }
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(
                    coverUrl = book.coverUrl,
                    title = book.title,
                    modifier = Modifier
                        .width(108.dp)
                        .aspectRatio(0.67f)
                        .shadow(10.dp, CoverShape)
                        .clip(CoverShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SettingsLabel(stringResource(if (isAudiobook) R.string.continue_listening else R.string.continue_reading))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    book.author?.let { author ->
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { recent.percentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                    Text(
                        text = "${(recent.percentage * 100).roundToInt()}% ${if (isAudiobook) "listened" else "read"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onContinue, shape = CircleShape) {
                        Icon(
                            if (isAudiobook) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeHeroCardPreview() {
    EmberTheme(darkTheme = true) {
        HomeHeroCard(
            recent = RecentBook(
                book = Book(id = "1", title = "A Big Hand for the Doctor", author = "Eoin Colfer", format = BookFormat.EPUB),
                percentage = 0.3f
            ),
            onContinue = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

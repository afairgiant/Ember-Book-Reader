package com.ember.reader.ui.book.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ember.reader.core.hardcover.HardcoverStatus
import com.ember.reader.core.hardcover.HardcoverUserBookEntry
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.repository.ServerAppearance
import com.ember.reader.ui.common.BookCoverImage
import com.ember.reader.ui.library.components.SourceBadge
import com.ember.reader.ui.library.components.SourceBadgeStyle
import kotlin.math.roundToInt

@Composable
fun BookDetailHero(
    book: Book,
    hardcoverUserEntry: HardcoverUserBookEntry?,
    progress: ReadingProgress?,
    sourceAppearance: ServerAppearance?,
    onCoverClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .aspectRatio(0.67f),
        ) {
            BookCoverImage(
                book = book,
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp)),
                onClick = if (book.coverUrl != null) onCoverClick else null,
            )
            SourceBadge(
                appearance = sourceAppearance,
                style = SourceBadgeStyle.GRID,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = book.title,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        book.author?.let { author ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = author,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        book.series?.let { series ->
            Spacer(modifier = Modifier.height(4.dp))
            val idx = book.seriesIndex
            val seriesText = if (idx != null) {
                if (idx == idx.toLong().toFloat()) "$series #${idx.toLong()}" else "$series #$idx"
            } else {
                series
            }
            Text(
                text = seriesText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (hardcoverUserEntry != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Hardcover: ${HardcoverStatus.label(hardcoverUserEntry.statusId)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        val pct = progress?.percentage
        if (pct != null && pct > 0f) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${(pct * 100).roundToInt()}% read",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A blurred, dimmed copy of the cover, drawn behind the top of the book page. It fades into
 * the page background at both ends so there is no edge under the status bar. Blur needs Android 12; older versions and books with no cover get
 * the plain background.
 */
@Composable
fun BookCoverBackdrop(book: Book, modifier: Modifier = Modifier) {
    if (book.coverUrl == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val background = MaterialTheme.colorScheme.background
    Box(modifier = modifier) {
        BookCoverImage(
            book = book,
            modifier = Modifier
                .fillMaxSize()
                .blur(48.dp)
                .alpha(0.6f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to background,
                        0.3f to background.copy(alpha = 0.4f),
                        0.7f to background.copy(alpha = 0.8f),
                        1f to background,
                    ),
                ),
        )
    }
}

@Composable
internal fun FullscreenCoverOverlay(book: Book, onDismiss: () -> Unit) {
    if (book.coverUrl == null) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        BookCoverImage(
            book = book,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

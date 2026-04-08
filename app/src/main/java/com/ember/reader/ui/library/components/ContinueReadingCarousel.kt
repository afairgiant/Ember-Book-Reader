package com.ember.reader.ui.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.ui.common.BookCoverPlaceholderColors
import com.ember.reader.ui.common.bookCoverColorIndex
import kotlin.math.roundToInt

@Composable
fun ContinueReadingCarousel(
    books: List<Book>,
    progressMap: Map<String, Float>,
    coverAuthHeaders: Map<Long, String>,
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (books.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.continue_reading),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(books, key = { it.id }) { book ->
                ContinueReadingCard(
                    book = book,
                    progress = progressMap[book.id] ?: 0f,
                    coverAuthHeader = book.serverId?.let { coverAuthHeaders[it] },
                    onClick = { onBookClick(book) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ContinueReadingCard(
    book: Book,
    progress: Float,
    coverAuthHeader: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            if (book.coverUrl != null) {
                val context = LocalContext.current
                val imageModel = remember(book.coverUrl, coverAuthHeader) {
                    val url = if (coverAuthHeader?.startsWith("jwt:") == true) {
                        "${book.coverUrl}?token=${coverAuthHeader.removePrefix("jwt:")}"
                    } else book.coverUrl
                    ImageRequest.Builder(context)
                        .data(url)
                        .apply {
                            if (coverAuthHeader != null && !coverAuthHeader.startsWith("jwt:")) {
                                addHeader("Authorization", coverAuthHeader)
                            }
                        }
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BookCoverPlaceholderColors[bookCoverColorIndex(book.title)]),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = book.title.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF5D4037),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

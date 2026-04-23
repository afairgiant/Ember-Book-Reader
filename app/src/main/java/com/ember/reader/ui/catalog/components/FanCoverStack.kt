package com.ember.reader.ui.catalog.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ember.reader.core.grimmory.SeriesCoverBook
import com.ember.reader.core.grimmory.grimmoryCoverUrl

/**
 * A fanned stack of book covers, matching Grimmory web's "Browse All Series" tile.
 *
 * Up to [maxCovers] books from [coverBooks] (skipping any without `coverUpdatedOn`)
 * are drawn in a shallow fan: the first cover is centered and upright, and successive
 * covers alternate left/right with increasing offset, rotation, and transparency.
 *
 * The container is sized at roughly 1.6 × [coverWidth] on both axes to leave room for
 * the fan's spread and rotation; callers should place it inside a parent that provides
 * that bounding box.
 */
@Composable
fun FanCoverStack(
    coverBooks: List<SeriesCoverBook>,
    baseUrl: String,
    coverWidth: Dp,
    modifier: Modifier = Modifier,
    maxCovers: Int = 5,
) {
    val visible = remember(coverBooks, maxCovers) {
        coverBooks
            .filter { it.bookId != null && it.coverUpdatedOn != null }
            .take(maxCovers)
    }
    val layers = remember(visible.size) { fanLayers(visible.size) }

    Box(
        modifier = modifier.size(coverWidth * 1.6f),
        contentAlignment = Alignment.Center,
    ) {
        if (visible.isEmpty()) {
            FanCoverPlaceholder(coverWidth = coverWidth)
            return@Box
        }

        // Draw bottom-up so index 0 (center) ends up on top.
        for (i in visible.indices.reversed()) {
            val layer = layers[i]
            FanCover(
                book = visible[i],
                baseUrl = baseUrl,
                coverWidth = coverWidth * layer.scale,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = (coverWidth * layer.translateXFactor).toPx()
                        rotationZ = layer.rotationDeg
                        alpha = layer.alpha
                    },
            )
        }
    }
}

@Composable
private fun FanCover(
    book: SeriesCoverBook,
    baseUrl: String,
    coverWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val bookId = book.bookId ?: return
    val url = remember(baseUrl, bookId, book.coverUpdatedOn) {
        grimmoryCoverUrl(baseUrl, bookId, book.coverUpdatedOn)
    }

    Card(
        modifier = modifier
            .width(coverWidth)
            .aspectRatio(2f / 3f),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)),
        )
    }
}

@Composable
private fun FanCoverPlaceholder(coverWidth: Dp) {
    Card(
        modifier = Modifier
            .width(coverWidth)
            .aspectRatio(2f / 3f),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.CollectionsBookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(coverWidth * 0.4f),
            )
        }
    }
}

private data class FanLayer(
    val translateXFactor: Float,
    val rotationDeg: Float,
    val alpha: Float,
    val scale: Float = 1f,
)

private fun fanLayers(count: Int): List<FanLayer> = when (count) {
    1 -> listOf(FanLayer(0f, 0f, 1f, scale = 1.1f))
    2 -> listOf(
        FanLayer(0.08f, 3f, 1f),
        FanLayer(-0.08f, -3f, 0.9f),
    )
    else -> listOf(
        FanLayer(0f, 0f, 1f),
        FanLayer(0.14f, 4f, 0.9f),
        FanLayer(-0.14f, -4f, 0.85f),
        FanLayer(0.28f, 8f, 0.8f),
        FanLayer(-0.28f, -8f, 0.75f),
    ).take(count)
}


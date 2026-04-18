package com.ember.reader.ui.book.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BookRatingsCard(
    personalRating: Int?,
    goodreadsRating: Double?,
    goodreadsReviewCount: Int?,
    amazonRating: Double?,
    amazonReviewCount: Int?,
    hardcoverRating: Double?,
    hardcoverReviewCount: Int?,
    onRatingChange: (Int?) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Ratings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            goodreadsRating?.let { rating ->
                val reviews = goodreadsReviewCount?.let { " ($it)" } ?: ""
                InfoRow("Goodreads", "%.1f$reviews".format(rating))
            }
            amazonRating?.let { rating ->
                val reviews = amazonReviewCount?.let { " ($it)" } ?: ""
                InfoRow("Amazon", "%.1f$reviews".format(rating))
            }
            hardcoverRating?.let { rating ->
                val reviews = hardcoverReviewCount?.let { " ($it)" } ?: ""
                InfoRow("Hardcover", "%.1f$reviews".format(rating))
            }
            if (goodreadsRating != null || amazonRating != null || hardcoverRating != null) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = "Your Rating",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            PersonalRatingStars(
                value = personalRating ?: 0,
                onChange = onRatingChange,
            )
        }
    }
}

// 10-star rating input matching Grimmory's web metadata-viewer. Tap a star to set
// the rating to that position; tap the currently selected star again to clear.
// Relaxes Material's 48dp minimum tap target so 10 stars fit in the card width.
@Composable
private fun PersonalRatingStars(
    value: Int,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            for (star in 1..10) {
                val filled = star <= value
                IconButton(
                    onClick = { onChange(if (value == star) null else star) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Rate $star out of 10",
                        tint = if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

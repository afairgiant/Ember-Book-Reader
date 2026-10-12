package com.ember.reader.ui.book.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.ui.settings.components.SettingsGroup

@Composable
fun BookInfoCard(
    format: String,
    series: String?,
    seriesIndex: Float?,
    pageCount: Int?,
    language: String?,
    published: String?,
    publisher: String?,
    isbn: String?,
) {
    SettingsGroup(title = "Details") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            series?.let {
                val seriesText = if (seriesIndex != null) {
                    val idx = if (seriesIndex == seriesIndex.toLong().toFloat()) {
                        "${seriesIndex.toLong()}"
                    } else {
                        "$seriesIndex"
                    }
                    "$it #$idx"
                } else {
                    it
                }
                InfoRow(stringResource(R.string.info_series), seriesText)
            }
            InfoRow(stringResource(R.string.info_format), format)
            pageCount?.let { InfoRow(stringResource(R.string.info_pages), "$it") }
            language?.let { InfoRow(stringResource(R.string.info_language), it.uppercase()) }
            published?.let { InfoRow(stringResource(R.string.info_published), it) }
            publisher?.let { InfoRow(stringResource(R.string.info_publisher), it) }
            isbn?.let { InfoRow(stringResource(R.string.info_isbn), it) }
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A long value wraps under itself, clear of the label.
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

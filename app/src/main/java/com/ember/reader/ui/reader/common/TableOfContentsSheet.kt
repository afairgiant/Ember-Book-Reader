package com.ember.reader.ui.reader.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOfContentsSheet(
    publication: Publication,
    currentLocator: Locator?,
    onNavigate: (Locator) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tocEntries = remember(publication) { flattenToc(publication.tableOfContents) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.table_of_contents),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn {
                items(tocEntries) { entry ->
                    TocItem(
                        entry = entry,
                        isCurrent = currentLocator?.href?.toString() == entry.link.href?.toString(),
                        onClick = {
                            val locator = publication.locatorFromLink(entry.link)
                            if (locator != null) onNavigate(locator)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TocItem(entry: TocEntry, isCurrent: Boolean, onClick: () -> Unit) {
    Text(
        text = entry.link.title ?: entry.link.href?.toString() ?: "",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = (16 + entry.depth * 16).dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp
            )
    )
}

private data class TocEntry(
    val link: Link,
    val depth: Int
)

private fun flattenToc(links: List<Link>, depth: Int = 0): List<TocEntry> = links.flatMap { link ->
    listOf(TocEntry(link, depth)) + flattenToc(link.children, depth + 1)
}

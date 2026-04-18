package com.ember.reader.ui.editmetadata.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ember.reader.core.grimmory.GrimmoryBookMetadata
import com.ember.reader.core.grimmory.MetadataProvider
import com.ember.reader.core.grimmory.searchableProviders
import com.ember.reader.ui.editmetadata.SearchForm
import com.ember.reader.ui.editmetadata.SearchPhase

@Composable
fun SearchMetadataSection(
    form: SearchForm,
    phase: SearchPhase,
    results: List<GrimmoryBookMetadata>,
    error: String?,
    selectedCandidateId: Long?,
    applyingCoverUrl: String?,
    onFormChange: ((SearchForm) -> SearchForm) -> Unit,
    onToggleProvider: (MetadataProvider) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onSelect: (GrimmoryBookMetadata) -> Unit,
    onCoverClick: (String) -> Unit,
    onApplyCover: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Search metadata providers",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (expanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { v -> onFormChange { it.copy(title = v) } },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = form.author,
                    onValueChange = { v -> onFormChange { it.copy(author = v) } },
                    label = { Text("Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = form.isbn,
                    onValueChange = { v -> onFormChange { it.copy(isbn = v) } },
                    label = { Text("ISBN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = form.asin,
                    onValueChange = { v -> onFormChange { it.copy(asin = v) } },
                    label = { Text("ASIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    searchableProviders.forEach { p ->
                        FilterChip(
                            selected = p in form.providers,
                            onClick = { onToggleProvider(p) },
                            label = { Text(p.name) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(
                        onClick = { if (phase == SearchPhase.Running) onCancel() else onStart() },
                    ) {
                        Text(if (phase == SearchPhase.Running) "Cancel" else "Search")
                    }
                    if (phase == SearchPhase.Running) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (results.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("${results.size} result(s)", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    val grouped = results.groupBy { it.provider }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        grouped.forEach { (provider, candidates) ->
                            ProviderResultGroup(
                                providerName = provider?.name ?: "Unknown",
                                candidates = candidates,
                                selectedCandidateId = selectedCandidateId,
                                applyingCoverUrl = applyingCoverUrl,
                                onSelect = onSelect,
                                onCoverClick = onCoverClick,
                                onApplyCover = onApplyCover,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderResultGroup(
    providerName: String,
    candidates: List<GrimmoryBookMetadata>,
    selectedCandidateId: Long?,
    applyingCoverUrl: String?,
    onSelect: (GrimmoryBookMetadata) -> Unit,
    onCoverClick: (String) -> Unit,
    onApplyCover: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(8.dp)) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    providerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${candidates.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                ) {
                    candidates.forEach { candidate ->
                        CandidateRow(
                            metadata = candidate,
                            selected = candidate.bookId != null && candidate.bookId == selectedCandidateId,
                            applyingCoverUrl = applyingCoverUrl,
                            onClick = { onSelect(candidate) },
                            onCoverClick = onCoverClick,
                            onApplyCover = onApplyCover,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    metadata: GrimmoryBookMetadata,
    selected: Boolean,
    applyingCoverUrl: String?,
    onClick: () -> Unit,
    onCoverClick: (String) -> Unit = {},
    onApplyCover: (String) -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val coverUrl = metadata.thumbnailUrl
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 40.dp, height = 60.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onCoverClick(coverUrl) },
                )
                Spacer(Modifier.width(6.dp))
                // "Use this cover" button — stops propagation so it doesn't select the candidate.
                val uploadingThis = applyingCoverUrl == coverUrl
                val anyUploading = applyingCoverUrl != null
                IconButton(
                    onClick = { onApplyCover(coverUrl) },
                    enabled = !anyUploading,
                    modifier = Modifier.size(32.dp),
                ) {
                    if (uploadingThis) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = "Use this cover",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    metadata.title.orEmpty().ifEmpty { "(no title)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                metadata.authors?.joinToString(", ")?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val details = buildList {
                    metadata.publishedDate?.let { add(it) }
                    metadata.isbn13?.let { add("ISBN: $it") }
                    metadata.pageCount?.let { add("$it pages") }
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

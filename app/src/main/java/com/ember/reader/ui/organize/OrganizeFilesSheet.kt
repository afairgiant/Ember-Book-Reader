package com.ember.reader.ui.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet that drives a Grimmory file-move. The caller owns the VM
 * lifecycle — construct it via [OrganizeFilesViewModel.Factory] keyed by the
 * set of selected book IDs, and call [OrganizeFilesViewModel.onDispose] from
 * the dismissal path.
 *
 * On success, [onSuccess] fires with the moved count and target library name
 * so the parent can show a snackbar and refresh its list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeFilesSheet(
    viewModel: OrganizeFilesViewModel,
    onDismiss: () -> Unit,
    onSuccess: (movedCount: Int, targetLibraryName: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    // When the VM reports Success, fire the callback and animate the sheet out.
    LaunchedEffect(state) {
        val success = state as? OrganizeFilesUiState.Success ?: return@LaunchedEffect
        onSuccess(success.movedCount, success.targetLibraryName)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    // Cancel VM work when the sheet leaves composition.
    DisposableEffect(Unit) {
        onDispose { viewModel.onDispose() }
    }

    ModalBottomSheet(
        onDismissRequest = {
            val ready = state as? OrganizeFilesUiState.Ready
            if (ready?.submitting != true) onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Organize Files",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                OrganizeFilesUiState.Loading -> LoadingContent()
                is OrganizeFilesUiState.Ready -> ReadyContent(
                    state = s,
                    onLibrarySelected = viewModel::onLibrarySelected,
                    onPathSelected = viewModel::onPathSelected,
                    onConfirm = viewModel::onConfirm,
                    onCancel = onDismiss,
                )
                is OrganizeFilesUiState.Error -> ErrorContent(
                    state = s,
                    onRetry = { viewModel.retryLoad() },
                    onCancel = onDismiss,
                )
                is OrganizeFilesUiState.Success -> Unit // dismissed in LaunchedEffect
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.padding(start = 16.dp))
        Text("Loading book details…")
    }
}

@Composable
private fun ReadyContent(
    state: OrganizeFilesUiState.Ready,
    onLibrarySelected: (Long) -> Unit,
    onPathSelected: (Long) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        "Move to library",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    LibraryPickerButton(
        label = state.selectedLibrary?.name ?: "Choose a library",
        libraries = state.libraries,
        enabled = !state.submitting,
        onSelected = onLibrarySelected,
    )

    if (state.showPathPicker) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Path",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        PathPickerButton(
            label = state.selectedPath?.path ?: "",
            paths = state.selectedLibrary?.paths.orEmpty(),
            enabled = !state.submitting,
            onSelected = onPathSelected,
        )
    }

    Spacer(Modifier.height(16.dp))
    Text(
        "${state.previews.size} book${if (state.previews.size == 1) "" else "s"}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp, max = 320.dp),
    ) {
        items(state.previews, key = { it.bookId }) { preview ->
            PreviewRow(preview)
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(onClick = onCancel, enabled = !state.submitting) {
            Text("Cancel")
        }
        Spacer(Modifier.padding(start = 8.dp))
        Button(
            onClick = onConfirm,
            enabled = state.anythingToMove
                && state.selectedLibraryId != null
                && state.selectedPathId != null
                && !state.submitting,
        ) {
            Text(
                when {
                    state.submitting -> "Moving…"
                    !state.anythingToMove -> "Nothing to move"
                    else -> {
                        val n = state.previews.size
                        "Move $n book${if (n == 1) "" else "s"}"
                    }
                }
            )
        }
    }
}

@Composable
private fun LibraryPickerButton(
    label: String,
    libraries: List<com.ember.reader.core.grimmory.GrimmoryLibraryFull>,
    enabled: Boolean,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            libraries.forEach { lib ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(lib.name)
                            if (lib.paths.isEmpty()) {
                                Text(
                                    "No paths configured",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    enabled = lib.paths.isNotEmpty(),
                    onClick = {
                        onSelected(lib.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PathPickerButton(
    label: String,
    paths: List<com.ember.reader.core.grimmory.GrimmoryLibraryPath>,
    enabled: Boolean,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            paths.forEach { path ->
                DropdownMenuItem(
                    text = { Text(path.path) },
                    onClick = {
                        onSelected(path.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PreviewRow(preview: BookMovePreview) {
    val titleColor = if (preview.isNoChange) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            preview.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            preview.currentPath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (preview.isNoChange) {
            Text(
                "No change",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Text(
                "→ ${preview.newPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    state: OrganizeFilesUiState.Error,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(
            state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            if (state.kind != OrganizeFilesUiState.Error.Kind.Permission) {
                Spacer(Modifier.padding(start = 8.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

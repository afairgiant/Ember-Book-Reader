package com.ember.reader.ui.upload

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.core.grimmory.GrimmoryAppLibraryWithPaths

private val uploadMimeTypes = arrayOf(
    "application/epub+zip",
    "application/pdf",
    "application/vnd.comicbook+zip",
    "application/x-cbz",
    "application/vnd.comicbook-rar",
    "application/x-cbr",
    "application/x-mobipocket-ebook",
    "application/vnd.amazon.ebook",
    "application/x-fictionbook+xml",
    "application/octet-stream",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadBookScreen(
    onNavigateBack: () -> Unit,
    viewModel: UploadBookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(viewModel::setFileFromUri) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.uploadedSuccessfully) {
        if (state.uploadedSuccessfully) {
            val name = state.selectedFile?.name ?: "Book"
            snackbarHostState.showSnackbar("$name uploaded")
            viewModel.consumeSuccess()
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = if (state.serverName.isNotBlank()) {
                        "Upload to ${state.serverName}"
                    } else {
                        "Upload book"
                    }
                    Text(title, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            DestinationSelector(
                destination = state.destination,
                enabled = !state.isUploading,
                onChange = viewModel::setDestination,
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (state.destination) {
                UploadDestination.Library -> LibrarySection(
                    libraries = state.libraries,
                    librariesLoading = state.librariesLoading,
                    selectedLibrary = state.selectedLibrary,
                    selectedPathId = state.selectedPathId,
                    enabled = !state.isUploading,
                    onSelectLibrary = viewModel::selectLibrary,
                    onSelectPath = viewModel::selectPath,
                )
                UploadDestination.BookDrop -> BookDropSection()
            }

            Spacer(modifier = Modifier.height(16.dp))

            FilePickerCard(
                picked = state.selectedFile,
                enabled = !state.isUploading,
                onChoose = { filePickerLauncher.launch(uploadMimeTypes) },
            )

            Spacer(modifier = Modifier.height(24.dp))

            UploadActionArea(
                state = state,
                onUpload = viewModel::upload,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationSelector(
    destination: UploadDestination,
    enabled: Boolean,
    onChange: (UploadDestination) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = destination == UploadDestination.Library,
            onClick = { onChange(UploadDestination.Library) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text("Library") }
        SegmentedButton(
            selected = destination == UploadDestination.BookDrop,
            onClick = { onChange(UploadDestination.BookDrop) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text("Book Drop") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySection(
    libraries: List<GrimmoryAppLibraryWithPaths>,
    librariesLoading: Boolean,
    selectedLibrary: GrimmoryAppLibraryWithPaths?,
    selectedPathId: Long?,
    enabled: Boolean,
    onSelectLibrary: (Long) -> Unit,
    onSelectPath: (Long) -> Unit,
) {
    if (librariesLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }

    if (libraries.isEmpty()) {
        Text(
            text = "No libraries available on this server.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    DropdownField(
        label = "Library",
        selectedLabel = selectedLibrary?.name.orEmpty(),
        enabled = enabled,
        items = libraries,
        itemLabel = { it.name },
        onSelect = { onSelectLibrary(it.id) },
    )

    Spacer(modifier = Modifier.height(12.dp))

    val paths = selectedLibrary?.paths.orEmpty()
    DropdownField(
        label = "Path",
        selectedLabel = paths.firstOrNull { it.id == selectedPathId }?.path.orEmpty(),
        enabled = enabled && paths.isNotEmpty(),
        items = paths,
        itemLabel = { it.path },
        onSelect = { onSelectPath(it.id) },
    )
}

@Composable
private fun <T> DropdownField(
    label: String,
    selectedLabel: String,
    enabled: Boolean,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay that intercepts taps on the read-only field.
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (enabled) {
                        Modifier.clickable { expanded = true }
                    } else {
                        Modifier
                    }
                ),
        )
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun BookDropSection() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Text(
            text = "The file will land in Book Drop. Review it there to finalize the import.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun FilePickerCard(
    picked: PickedFile?,
    enabled: Boolean,
    onChoose: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "File",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (picked == null) {
                OutlinedButton(onClick = onChoose, enabled = enabled) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Choose file")
                }
            } else {
                Text(
                    text = picked.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val sizeLabel = if (picked.size > 0) {
                    Formatter.formatFileSize(context, picked.size)
                } else {
                    "Size unknown"
                }
                Text(
                    text = sizeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onChoose, enabled = enabled) {
                    Text("Choose a different file")
                }
            }
        }
    }
}

@Composable
private fun UploadActionArea(
    state: UploadBookUiState,
    onUpload: () -> Unit,
) {
    val progress = state.uploadProgress
    if (progress != null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            val percent = (progress * 100).toInt().coerceIn(0, 100)
            val fileName = state.selectedFile?.name.orEmpty()
            Text(
                text = "Uploading $fileName… $percent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Button(
            onClick = onUpload,
            enabled = state.canUpload,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Upload")
        }
    }
}


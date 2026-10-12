package com.ember.reader.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.repository.ThemeMode
import com.ember.reader.ui.reader.common.ReaderPreferencesContent
import com.ember.reader.ui.settings.components.SettingsDetailRow
import com.ember.reader.ui.settings.components.SettingsGroup
import com.ember.reader.ui.settings.components.SettingsToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    readerDefaultsViewModel: ReaderDefaultsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val readerDefaults by readerDefaultsViewModel.preferences.collectAsStateWithLifecycle()
    val customFonts by readerDefaultsViewModel.customFonts.collectAsStateWithLifecycle()
    val fontImportFailed by readerDefaultsViewModel.fontImportFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(readerDefaultsViewModel::importFont)
    }
    LaunchedEffect(fontImportFailed) {
        if (fontImportFailed) {
            Toast.makeText(context, R.string.font_import_failed, Toast.LENGTH_LONG).show()
            readerDefaultsViewModel.onFontImportFailureShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsGroup(title = "Theme") {
                SettingsDetailRow(
                    icon = Icons.Default.DarkMode,
                    title = "Theme"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.updateThemeMode(mode) },
                                label = { Text(mode.displayName) }
                            )
                        }
                    }
                }
            }

            SettingsGroup(title = "Display") {
                SettingsToggleRow(
                    icon = Icons.Default.ScreenLockPortrait,
                    title = stringResource(R.string.keep_screen_on),
                    subtitle = "Prevent screen timeout while reading",
                    checked = keepScreenOn,
                    onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                )
            }

            SettingsGroup(title = "Reader Defaults") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "These settings apply to every book unless that book has its own customizations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    ReaderPreferencesContent(
                        preferences = readerDefaults,
                        onPreferencesChanged = readerDefaultsViewModel::updatePreferences,
                        isPdf = false,
                        customFonts = customFonts
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.custom_fonts_title)) {
                customFonts.forEach { font ->
                    SettingsDetailRow(icon = Icons.Default.TextFields, title = font.name) {
                        IconButton(onClick = { readerDefaultsViewModel.deleteFont(font) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.custom_font_remove, font.name)
                            )
                        }
                    }
                }
                SettingsDetailRow(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.custom_font_add),
                    subtitle = stringResource(R.string.custom_font_add_hint),
                    // Font files have no dependable MIME type across file providers.
                    modifier = Modifier.clickable { fontPicker.launch(arrayOf("*/*")) }
                )
            }
        }
    }
}

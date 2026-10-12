package com.ember.reader.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.repository.AppAccent
import com.ember.reader.core.repository.ThemeMode
import com.ember.reader.ui.reader.common.ReaderPreferencesContent
import com.ember.reader.ui.settings.components.SettingsChoiceChips
import com.ember.reader.ui.settings.components.SettingsDetailRow
import com.ember.reader.ui.settings.components.SettingsGroup
import com.ember.reader.ui.settings.components.SettingsLabel
import com.ember.reader.ui.settings.components.SettingsScreenHeader
import com.ember.reader.ui.settings.components.SettingsToggleRow
import com.ember.reader.ui.theme.seed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    readerDefaultsViewModel: ReaderDefaultsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val oledBlack by viewModel.oledBlack.collectAsStateWithLifecycle()
    val accent by viewModel.accent.collectAsStateWithLifecycle()
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsScreenHeader(overline = "Settings", title = "Appearance", onNavigateBack = onNavigateBack)
            SettingsGroup(title = "Theme & accent") {
                SettingsChoiceChips(
                    options = ThemeMode.entries,
                    selected = themeMode,
                    onSelect = viewModel::updateThemeMode,
                    labelOf = { it.displayName }
                )
                SettingsToggleRow(
                    icon = Icons.Default.DarkMode,
                    title = "OLED black",
                    subtitle = "Pure black background in the dark theme",
                    checked = oledBlack,
                    onCheckedChange = viewModel::updateOledBlack
                )
                SettingsLabel("Accent", modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                AccentSwatches(selected = accent, onSelect = viewModel::updateAccent)
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

/** One round swatch per accent; the chosen one has a ring around it. */
@Composable
private fun AccentSwatches(selected: AppAccent, onSelect: (AppAccent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppAccent.entries.forEach { accent ->
            val isSelected = accent == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(accent.seed)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(accent) }
                    )
                    .semantics { contentDescription = accent.displayName }
            )
        }
    }
}

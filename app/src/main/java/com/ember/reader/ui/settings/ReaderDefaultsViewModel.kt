package com.ember.reader.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.fonts.CustomFont
import com.ember.reader.core.fonts.CustomFontRepository
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.repository.ReaderPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Appearance > Reader Defaults screen. Reads and writes the global
 * reader preferences directly — independent of any per-book overrides.
 */
@HiltViewModel
class ReaderDefaultsViewModel @Inject constructor(
    private val readerPreferencesRepository: ReaderPreferencesRepository,
    private val customFontRepository: CustomFontRepository
) : ViewModel() {

    val customFonts: StateFlow<List<CustomFont>> = customFontRepository.fonts

    private val _fontImportFailed = MutableStateFlow(false)

    /** True after an import was refused, until [onFontImportFailureShown]. */
    val fontImportFailed: StateFlow<Boolean> = _fontImportFailed.asStateFlow()

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            customFontRepository.import(uri).onFailure { _fontImportFailed.value = true }
        }
    }

    fun onFontImportFailureShown() {
        _fontImportFailed.value = false
    }

    fun deleteFont(font: CustomFont) {
        viewModelScope.launch { customFontRepository.delete(font) }
    }

    val preferences: StateFlow<ReaderPreferences> =
        readerPreferencesRepository.preferencesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences())

    fun updatePreferences(prefs: ReaderPreferences) {
        viewModelScope.launch { readerPreferencesRepository.updatePreferences(prefs) }
    }
}

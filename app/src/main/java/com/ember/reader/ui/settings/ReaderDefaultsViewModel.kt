package com.ember.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.repository.ReaderPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Appearance > Reader Defaults screen. Reads and writes the global
 * reader preferences directly — independent of any per-book overrides.
 */
@HiltViewModel
class ReaderDefaultsViewModel @Inject constructor(
    private val readerPreferencesRepository: ReaderPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<ReaderPreferences> =
        readerPreferencesRepository.preferencesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences())

    fun updatePreferences(prefs: ReaderPreferences) {
        viewModelScope.launch { readerPreferencesRepository.updatePreferences(prefs) }
    }
}

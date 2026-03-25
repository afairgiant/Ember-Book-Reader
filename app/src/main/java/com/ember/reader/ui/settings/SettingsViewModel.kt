package com.ember.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.SyncFrequency
import com.ember.reader.core.repository.SyncPreferencesRepository
import com.ember.reader.core.sync.worker.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val syncFrequency: StateFlow<SyncFrequency> =
        syncPreferencesRepository.syncFrequencyFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncFrequency.ON_OPEN_CLOSE)

    fun updateSyncFrequency(frequency: SyncFrequency) {
        viewModelScope.launch {
            syncPreferencesRepository.updateSyncFrequency(frequency)
            syncScheduler.applyFrequency(frequency)
        }
    }

    fun syncNow() {
        syncScheduler.syncNow()
    }
}

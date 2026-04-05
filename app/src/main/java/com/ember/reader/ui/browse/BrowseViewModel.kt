package com.ember.reader.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.hardcover.HardcoverTokenManager
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class BrowseViewModel @Inject constructor(
    serverRepository: ServerRepository,
    private val hardcoverTokenManager: HardcoverTokenManager,
) : ViewModel() {
    val servers: StateFlow<List<Server>> = serverRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isHardcoverConnected: Boolean
        get() = hardcoverTokenManager.isConnected()
}

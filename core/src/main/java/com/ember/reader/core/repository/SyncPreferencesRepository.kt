package com.ember.reader.core.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ember.reader.core.model.SyncFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncPreferencesDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "sync_preferences")

@Singleton
class SyncPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val SYNC_FREQUENCY = stringPreferencesKey("sync_frequency")
    }

    val syncFrequencyFlow: Flow<SyncFrequency> =
        context.syncPreferencesDataStore.data.map { prefs ->
            prefs[Keys.SYNC_FREQUENCY]?.let { SyncFrequency.valueOf(it) }
                ?: SyncFrequency.ON_OPEN_CLOSE
        }

    suspend fun updateSyncFrequency(frequency: SyncFrequency) {
        context.syncPreferencesDataStore.edit { prefs ->
            prefs[Keys.SYNC_FREQUENCY] = frequency.name
        }
    }
}

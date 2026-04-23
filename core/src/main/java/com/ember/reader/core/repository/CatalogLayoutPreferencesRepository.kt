package com.ember.reader.core.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.catalogLayoutPreferencesDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "catalog_layout_preferences")

enum class CatalogSeriesViewMode { GRID, LIST }

@Singleton
class CatalogLayoutPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val SERIES_VIEW_MODE = stringPreferencesKey("series_view_mode")
    }

    val seriesViewModeFlow: Flow<CatalogSeriesViewMode> =
        context.catalogLayoutPreferencesDataStore.data.map { p ->
            p[Keys.SERIES_VIEW_MODE]
                ?.let { runCatching { CatalogSeriesViewMode.valueOf(it) }.getOrNull() }
                ?: CatalogSeriesViewMode.GRID
        }

    suspend fun setSeriesViewMode(mode: CatalogSeriesViewMode) {
        context.catalogLayoutPreferencesDataStore.edit { p ->
            p[Keys.SERIES_VIEW_MODE] = mode.name
        }
    }
}

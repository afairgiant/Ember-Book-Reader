package com.ember.reader.ui.reader.pdf

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.core.readium.toLocator
import com.ember.reader.ui.common.ErrorScreen
import com.ember.reader.ui.common.LoadingScreen
import com.ember.reader.ui.reader.common.BookmarksSheet
import com.ember.reader.ui.reader.common.NavigatorContainer
import com.ember.reader.ui.reader.common.ReaderPreferencesSheet
import com.ember.reader.ui.reader.common.ReaderScaffold
import com.ember.reader.ui.reader.common.ReaderUiState
import com.ember.reader.ui.reader.common.ReaderViewModel
import com.ember.reader.ui.reader.common.SyncConflictDialog
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

private const val FRAGMENT_TAG = "pdf_navigator"
private const val CONTAINER_ID = 0x7F_FF_00_02

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun PdfReaderScreen(onNavigateBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chromeVisible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val currentLocator by viewModel.currentLocator.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val syncConflict by viewModel.syncConflict.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()

    // Keep screen on while reading
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            view.keepScreenOn = true
        }
        onDispose { view.keepScreenOn = false }
    }

    // Apply orientation lock
    val context = LocalContext.current
    DisposableEffect(preferences.orientationLock) {
        val activity = context as? android.app.Activity
        if (activity != null) {
            activity.requestedOrientation = when (preferences.orientationLock) {
                com.ember.reader.core.model.OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                com.ember.reader.core.model.OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                com.ember.reader.core.model.OrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            (context as? android.app.Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Apply brightness setting to window
    DisposableEffect(preferences.brightness) {
        val activity = context as? android.app.Activity
        if (activity != null && preferences.brightness >= 0) {
            val params = activity.window.attributes
            params.screenBrightness = preferences.brightness
            activity.window.attributes = params
        }
        onDispose {
            val act = context as? android.app.Activity
            if (act != null) {
                val params = act.window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window.attributes = params
            }
        }
    }

    var showBookmarks by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var navigator by remember { mutableStateOf<PdfNavigatorFragment<*, *>?>(null) }
    val scope = rememberCoroutineScope()

    when (val state = uiState) {
        ReaderUiState.Loading -> LoadingScreen()
        is ReaderUiState.Error -> ErrorScreen(state.message)
        is ReaderUiState.Ready -> {
            val hasBookmark = currentLocator?.let { loc ->
                bookmarks.any { it.locatorJson.contains(loc.href.toString()) }
            } ?: false

            ReaderScaffold(
                title = state.book.title,
                chromeVisible = chromeVisible,
                currentLocator = currentLocator,
                hasBookmarkAtCurrentPosition = hasBookmark,
                onNavigateBack = onNavigateBack,
                onToggleBookmark = viewModel::addBookmark,
                onOpenTableOfContents = {},
                onOpenPreferences = { showPreferences = true }
            ) {
                NavigatorContainer(
                    key = state.publication,
                    containerId = CONTAINER_ID,
                    fragmentTag = FRAGMENT_TAG,
                    fragmentClass = PdfNavigatorFragment::class.java,
                    fragmentFactory = PdfNavigatorFactory(
                        publication = state.publication,
                        pdfEngineProvider = PdfiumEngineProvider()
                    ).createFragmentFactory(
                        initialLocator = state.initialLocator,
                        initialPreferences = PdfiumPreferences(pageSpacing = 4.0)
                    ),
                    locatorFlow = { fragment ->
                        (fragment as? PdfNavigatorFragment<*, *>)?.currentLocator
                    },
                    onLocatorChanged = viewModel::onLocatorChanged,
                    onNavigatorReady = { fragment ->
                        navigator = fragment as? PdfNavigatorFragment<*, *>
                        // Set dark background on the fragment's view so page gaps are visible
                        fragment.view?.setBackgroundColor(android.graphics.Color.DKGRAY)
                    }
                )
            }

            if (showBookmarks) {
                BookmarksSheet(
                    bookmarks = bookmarks,
                    onNavigate = { bookmark ->
                        bookmark.locatorJson.toLocator()?.let { locator ->
                            // PDF navigator doesn't support go() the same way
                        }
                        showBookmarks = false
                    },
                    onDelete = viewModel::deleteBookmark,
                    onDismiss = { showBookmarks = false }
                )
            }

            if (showPreferences) {
                ReaderPreferencesSheet(
                    preferences = preferences,
                    onPreferencesChanged = viewModel::updatePreferences,
                    onDismiss = { showPreferences = false }
                )
            }

            syncConflict?.let { conflict ->
                SyncConflictDialog(
                    conflict = conflict,
                    onAcceptRemote = viewModel::acceptRemoteProgress,
                    onKeepLocal = viewModel::dismissSyncConflict
                )
            }
        }
    }
}

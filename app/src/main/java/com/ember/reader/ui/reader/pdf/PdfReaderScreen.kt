package com.ember.reader.ui.reader.pdf

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import org.readium.adapter.pdfium.navigator.PdfiumDocumentFragment
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.adapter.pdfium.navigator.PdfiumSettings
import org.readium.r2.navigator.preferences.Fit
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

    // Save progress on pause (screen lock, app background)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                viewModel.saveCurrentProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var navigator by remember { mutableStateOf<PdfNavigatorFragment<PdfiumSettings, PdfiumPreferences>?>(null) }
    val scope = rememberCoroutineScope()

    // Volume button page turning
    DisposableEffect(preferences.volumePageTurn, navigator) {
        val nav = navigator
        if (preferences.volumePageTurn && nav != null) {
            com.ember.reader.MainActivity.volumeKeyHandler = { forward ->
                scope.launch {
                    if (forward) nav.goForward() else nav.goBackward()
                }
            }
        } else {
            com.ember.reader.MainActivity.volumeKeyHandler = null
        }
        onDispose {
            com.ember.reader.MainActivity.volumeKeyHandler = null
        }
    }

    when (val state = uiState) {
        ReaderUiState.Loading -> LoadingScreen()
        is ReaderUiState.Error -> ErrorScreen(state.message)
        is ReaderUiState.Ready -> {
            val hasBookmark = currentLocator?.let { loc ->
                val href = loc.href.toString()
                val hrefEscaped = href.replace("/", "\\/")
                val currentPos = loc.locations.position
                bookmarks.any { bm ->
                    val inSameFile = bm.locatorJson.contains(href) || bm.locatorJson.contains(hrefEscaped)
                    if (!inSameFile) return@any false
                    // For PDFs, match by page position
                    val bmLocator = bm.locatorJson.toLocator() ?: return@any false
                    bmLocator.locations.position == currentPos
                }
            } ?: false

            // Adjust the locator's totalProgression for page-based display
            val totalPages = state.publication.metadata.numberOfPages ?: 0
            val loc = currentLocator
            val displayLocator = if (loc != null && totalPages > 0) {
                // Derive current page from totalProgression (more reliable than position)
                val totalProg = loc.locations.totalProgression ?: 0.0
                // ceil works for most pages, but the last page never reaches 1.0
                // so check if we're past the start of the last page
                val lastPageStart = (totalPages - 1).toDouble() / totalPages
                val currentPage = if (totalProg >= lastPageStart - 0.01) {
                    totalPages
                } else {
                    kotlin.math.ceil(totalProg * totalPages).toInt().coerceIn(1, totalPages)
                }
                val pageBasedProgression = if (totalPages <= 1) 1.0
                    else ((currentPage - 1).toDouble() / (totalPages - 1)).coerceIn(0.0, 1.0)
                loc.copy(
                    locations = loc.locations.copy(
                        totalProgression = pageBasedProgression
                    ),
                    title = "Page $currentPage of $totalPages"
                )
            } else {
                loc
            }

            ReaderScaffold(
                title = state.book.title,
                chromeVisible = chromeVisible,
                currentLocator = displayLocator,
                hasBookmarkAtCurrentPosition = hasBookmark,
                onNavigateBack = onNavigateBack,
                onToggleBookmark = {
                    val removed = viewModel.toggleBookmark()
                    if (!removed) showBookmarkDialog = true
                },
                onOpenBookmarks = { showBookmarks = true },
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
                        initialPreferences = preferences.toPdfiumPreferences()
                    ),
                    locatorFlow = { fragment ->
                        (@Suppress("UNCHECKED_CAST")
                        (fragment as? PdfNavigatorFragment<PdfiumSettings, PdfiumPreferences>))?.currentLocator
                    },
                    onLocatorChanged = viewModel::onLocatorChanged,
                    onNavigatorReady = { fragment ->
                        navigator = @Suppress("UNCHECKED_CAST")
                        (fragment as? PdfNavigatorFragment<PdfiumSettings, PdfiumPreferences>)
                        // Find the PDFView inside the fragment and set its background
                        // so page spacing shows as a visible divider
                        fragment.view?.let { root ->
                            findPdfView(root)?.setBackgroundColor(android.graphics.Color.DKGRAY)
                                ?: root.setBackgroundColor(android.graphics.Color.DKGRAY)
                        }
                    }
                )
            }

            // Apply PDF preferences and set up tap-to-toggle-chrome
            androidx.compose.runtime.LaunchedEffect(preferences, navigator) {
                val nav = navigator ?: return@LaunchedEffect
                nav.submitPreferences(preferences.toPdfiumPreferences())

                nav.addInputListener(object : org.readium.r2.navigator.input.InputListener {
                    override fun onTap(event: org.readium.r2.navigator.input.TapEvent): Boolean {
                        viewModel.toggleChrome()
                        return true
                    }
                })
            }

            if (showBookmarkDialog) {
                var bookmarkName by remember { mutableStateOf("") }
                val pageTitle = currentLocator?.title ?: ""
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showBookmarkDialog = false },
                    title = { androidx.compose.material3.Text("Add Bookmark") },
                    text = {
                        Column {
                            if (pageTitle.isNotBlank()) {
                                androidx.compose.material3.Text(
                                    pageTitle,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            androidx.compose.material3.OutlinedTextField(
                                value = bookmarkName,
                                onValueChange = { bookmarkName = it },
                                label = { androidx.compose.material3.Text("Name (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            viewModel.addBookmark(bookmarkName.ifBlank { pageTitle })
                            showBookmarkDialog = false
                        }) { androidx.compose.material3.Text("Save") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showBookmarkDialog = false
                        }) { androidx.compose.material3.Text("Cancel") }
                    },
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
                    onDismiss = { showPreferences = false },
                    isPdf = true,
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

private fun com.ember.reader.core.model.ReaderPreferences.toPdfiumPreferences(): PdfiumPreferences {
    val fit = when (pdfFitMode) {
        com.ember.reader.core.model.PdfFitMode.WIDTH -> Fit.WIDTH
        com.ember.reader.core.model.PdfFitMode.CONTAIN -> Fit.CONTAIN
    }
    return runCatching {
        PdfiumPreferences(
            fit = fit,
            pageSpacing = pdfPageSpacing.toDouble(),
        )
    }.getOrElse {
        timber.log.Timber.w(it, "PdfiumPreferences creation failed, using defaults")
        PdfiumPreferences()
    }
}

/** Recursively find the PDFView (com.github.barteksc.pdfviewer.PDFView) in the view tree */
private fun findPdfView(view: android.view.View): android.view.View? {
    if (view.javaClass.name.contains("PDFView")) return view
    if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            findPdfView(view.getChildAt(i))?.let { return it }
        }
    }
    return null
}

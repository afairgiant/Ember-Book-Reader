package com.ember.reader.ui.reader.epub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme
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
import com.ember.reader.ui.reader.common.TableOfContentsSheet
import kotlinx.coroutines.launch
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.services.locateProgression

private const val FRAGMENT_TAG = "epub_navigator"
private const val CONTAINER_ID = 0x7F_FF_00_01

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubReaderScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chromeVisible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val currentLocator by viewModel.currentLocator.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val syncConflict by viewModel.syncConflict.collectAsStateWithLifecycle()
    val pendingNavigation by viewModel.pendingNavigation.collectAsStateWithLifecycle()
    var showToc by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var dirNavAdapter by remember { mutableStateOf<DirectionalNavigationAdapter?>(null) }
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
                onOpenTableOfContents = { showToc = true },
                onOpenPreferences = { showPreferences = true },
                onSeekToProgression = { progression ->
                    scope.launch {
                        state.publication.locateProgression(progression.toDouble())?.let {
                            navigator?.go(it)
                        }
                    }
                },
            ) {
                NavigatorContainer(
                    key = state.publication,
                    containerId = CONTAINER_ID,
                    fragmentTag = FRAGMENT_TAG,
                    fragmentClass = EpubNavigatorFragment::class.java,
                    fragmentFactory = EpubNavigatorFactory(
                        publication = state.publication,
                    ).createFragmentFactory(
                        initialLocator = state.initialLocator,
                        initialPreferences = preferences.toEpubPreferences(),
                    ),
                    locatorFlow = { fragment ->
                        (fragment as? EpubNavigatorFragment)?.currentLocator
                    },
                    onLocatorChanged = viewModel::onLocatorChanged,
                    onNavigatorReady = { fragment ->
                        val nav = fragment as? EpubNavigatorFragment ?: return@NavigatorContainer
                        navigator = nav
                        // Center tap to toggle chrome
                        nav.addInputListener(object : InputListener {
                            override fun onTap(event: TapEvent): Boolean {
                                viewModel.toggleChrome()
                                return true
                            }
                        })
                    },
                )
            }

            // Apply preferences to the Readium navigator whenever they change
            LaunchedEffect(preferences, navigator) {
                val nav = navigator ?: return@LaunchedEffect
                nav.submitPreferences(preferences.toEpubPreferences())

                // Only enable edge-tap/swipe page turns in paginated mode
                dirNavAdapter?.let { nav.removeInputListener(it) }
                if (preferences.isPaginated) {
                    val adapter = DirectionalNavigationAdapter(nav)
                    dirNavAdapter = adapter
                    nav.addInputListener(adapter)
                } else {
                    dirNavAdapter = null
                }
            }

            // Handle sync navigation (accept remote progress)
            LaunchedEffect(pendingNavigation, navigator) {
                val progression = pendingNavigation ?: return@LaunchedEffect
                val nav = navigator ?: return@LaunchedEffect
                state.publication.locateProgression(progression.toDouble())?.let {
                    nav.go(it)
                }
                viewModel.onNavigationHandled()
            }

            if (showToc) {
                TableOfContentsSheet(
                    publication = state.publication,
                    currentLocator = currentLocator,
                    onNavigate = { locator ->
                        scope.launch { navigator?.go(locator) }
                        showToc = false
                    },
                    onDismiss = { showToc = false },
                )
            }

            if (showBookmarks) {
                BookmarksSheet(
                    bookmarks = bookmarks,
                    onNavigate = { bookmark ->
                        bookmark.locatorJson.toLocator()?.let { locator ->
                            scope.launch { navigator?.go(locator) }
                        }
                        showBookmarks = false
                    },
                    onDelete = viewModel::deleteBookmark,
                    onDismiss = { showBookmarks = false },
                )
            }

            if (showPreferences) {
                ReaderPreferencesSheet(
                    preferences = preferences,
                    onPreferencesChanged = viewModel::updatePreferences,
                    onDismiss = { showPreferences = false },
                )
            }

            syncConflict?.let { conflict ->
                SyncConflictDialog(
                    conflict = conflict,
                    onAcceptRemote = viewModel::acceptRemoteProgress,
                    onKeepLocal = viewModel::dismissSyncConflict,
                )
            }
        }
    }
}

private fun ReaderPreferences.toEpubPreferences(): EpubPreferences = EpubPreferences(
    fontFamily = fontFamily.cssValue?.let { ReadiumFontFamily(it) },
    fontSize = fontSize.toDouble() / 16.0, // Readium uses a scale factor (1.0 = default)
    lineHeight = lineHeight.toDouble(),
    scroll = !isPaginated,
    theme = when (theme) {
        ReaderTheme.LIGHT -> Theme.LIGHT
        ReaderTheme.DARK -> Theme.DARK
        ReaderTheme.SEPIA -> Theme.SEPIA
        ReaderTheme.SYSTEM -> null
    },
)

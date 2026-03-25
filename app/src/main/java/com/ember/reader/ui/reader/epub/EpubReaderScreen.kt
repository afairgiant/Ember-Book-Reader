package com.ember.reader.ui.reader.epub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

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
    var showToc by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }

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
            ) {
                NavigatorContainer(
                    key = state.publication,
                    containerId = CONTAINER_ID,
                    fragmentTag = FRAGMENT_TAG,
                    fragmentClass = EpubNavigatorFragment::class.java,
                    fragmentFactory = EpubNavigatorFragment.createFactory(
                        publication = state.publication,
                        initialLocator = state.initialLocator,
                    ),
                    locatorFlow = { fragment ->
                        (fragment as? EpubNavigatorFragment)?.currentLocator
                    },
                    onLocatorChanged = viewModel::onLocatorChanged,
                )
            }

            if (showToc) {
                TableOfContentsSheet(
                    publication = state.publication,
                    currentLocator = currentLocator,
                    onNavigate = { showToc = false },
                    onDismiss = { showToc = false },
                )
            }

            if (showBookmarks) {
                BookmarksSheet(
                    bookmarks = bookmarks,
                    onNavigate = { showBookmarks = false },
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

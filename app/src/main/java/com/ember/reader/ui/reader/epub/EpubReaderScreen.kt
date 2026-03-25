package com.ember.reader.ui.reader.epub

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.ui.reader.common.PreferencesMapper
import com.ember.reader.ui.reader.common.ReaderScaffold
import com.ember.reader.ui.reader.common.ReaderUiState
import com.ember.reader.ui.reader.common.ReaderViewModel
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import timber.log.Timber

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
    var showToc by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }

    when (val state = uiState) {
        ReaderUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ReaderUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is ReaderUiState.Ready -> {
            val hasBookmark = bookmarks.any { bookmark ->
                currentLocator?.let { loc ->
                    bookmark.locatorJson.contains(loc.href.toString())
                } ?: false
            }

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
                EpubNavigatorContainer(
                    publication = state.publication,
                    initialLocator = state.initialLocator,
                    onLocatorChanged = viewModel::onLocatorChanged,
                    onTap = viewModel::toggleChrome,
                )
            }

            if (showToc) {
                com.ember.reader.ui.reader.common.TableOfContentsSheet(
                    publication = state.publication,
                    currentLocator = currentLocator,
                    onNavigate = { locator ->
                        showToc = false
                    },
                    onDismiss = { showToc = false },
                )
            }

            if (showBookmarks) {
                com.ember.reader.ui.reader.common.BookmarksSheet(
                    bookmarks = bookmarks,
                    onNavigate = { bookmark ->
                        showBookmarks = false
                    },
                    onDelete = viewModel::deleteBookmark,
                    onDismiss = { showBookmarks = false },
                )
            }

            if (showPreferences) {
                com.ember.reader.ui.reader.common.ReaderPreferencesSheet(
                    preferences = preferences,
                    onPreferencesChanged = viewModel::updatePreferences,
                    onDismiss = { showPreferences = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun EpubNavigatorContainer(
    publication: Publication,
    initialLocator: Locator?,
    onLocatorChanged: (Locator) -> Unit,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return

    val fragmentManager = activity.supportFragmentManager

    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = CONTAINER_ID
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    DisposableEffect(publication) {
        val existingFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG)
        if (existingFragment == null) {
            val factory = EpubNavigatorFragment.createFactory(
                publication = publication,
                initialLocator = initialLocator,
                listener = object : EpubNavigatorFragment.Listener {
                    override fun onTap(navigator: org.readium.r2.navigator.VisualNavigator, event: org.readium.r2.navigator.input.TapEvent): Boolean {
                        onTap()
                        return true
                    }
                },
            )
            activity.supportFragmentManager.fragmentFactory = factory
            fragmentManager.commit {
                add(CONTAINER_ID, EpubNavigatorFragment::class.java, null, FRAGMENT_TAG)
            }
        }

        val locatorJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val fragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG) as? EpubNavigatorFragment
            fragment?.currentLocator?.collect { locator ->
                onLocatorChanged(locator)
            }
        }

        onDispose {
            locatorJob.cancel()
            fragmentManager.findFragmentByTag(FRAGMENT_TAG)?.let { fragment ->
                fragmentManager.commit { remove(fragment) }
            }
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launch(
    block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit,
): kotlinx.coroutines.Job = kotlinx.coroutines.launch(block = block)

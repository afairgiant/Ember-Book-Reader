package com.ember.reader.ui.reader.epub

import androidx.compose.runtime.Composable
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.ember.reader.ui.reader.common.SearchSheet
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
import org.readium.r2.navigator.preferences.Color as ReadiumColor
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

    var showToc by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var dirNavAdapter by remember { mutableStateOf<DirectionalNavigationAdapter?>(null) }
    var currentTapListener by remember { mutableStateOf<InputListener?>(null) }
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
                onOpenSearch = { showSearch = true },
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
                    },
                )
            }

            // Apply preferences to the Readium navigator whenever they change
            LaunchedEffect(preferences, navigator) {
                val nav = navigator ?: return@LaunchedEffect
                nav.submitPreferences(preferences.toEpubPreferences())

                // Remove old listeners
                dirNavAdapter?.let { nav.removeInputListener(it) }
                dirNavAdapter = null
                currentTapListener?.let { nav.removeInputListener(it) }

                // Configurable tap zone handler
                val tapPrefs = preferences
                val listener = object : InputListener {
                    override fun onTap(event: TapEvent): Boolean {
                        val view = nav.requireView()
                        val viewWidth = view.width.toFloat()
                        val viewHeight = view.height.toFloat()
                        val tapX = event.point.x
                        val tapY = event.point.y

                        // Determine which zone was tapped
                        val zone = when {
                            // Top strip (configurable height, default 15%)
                            tapY < viewHeight * tapPrefs.topZoneHeight -> tapPrefs.topTapZone
                            // Left zone (configurable width)
                            tapX < viewWidth * tapPrefs.leftZoneWidth -> tapPrefs.leftTapZone
                            // Right zone (configurable width)
                            tapX > viewWidth * (1f - tapPrefs.rightZoneWidth) -> tapPrefs.rightTapZone
                            // Center (everything else)
                            else -> tapPrefs.centerTapZone
                        }

                        when (zone) {
                            com.ember.reader.core.model.TapZoneBehavior.PREVIOUS_PAGE ->
                                scope.launch { nav.goBackward() }
                            com.ember.reader.core.model.TapZoneBehavior.NEXT_PAGE ->
                                scope.launch { nav.goForward() }
                            com.ember.reader.core.model.TapZoneBehavior.TOGGLE_CHROME ->
                                viewModel.toggleChrome()
                            com.ember.reader.core.model.TapZoneBehavior.NOTHING -> {}
                        }
                        return true
                    }
                }
                currentTapListener = listener
                nav.addInputListener(listener)
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

            if (showSearch) {
                SearchSheet(
                    publication = state.publication,
                    onNavigate = { locator ->
                        scope.launch { navigator?.go(locator) }
                        showSearch = false
                    },
                    onDismiss = { showSearch = false },
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

private fun ReaderPreferences.toEpubPreferences(): EpubPreferences {
    // Built-in themes use Readium's Theme enum; custom themes pass colors directly
    val readiumTheme = when (theme) {
        ReaderTheme.LIGHT -> Theme.LIGHT
        ReaderTheme.DARK -> Theme.DARK
        ReaderTheme.SEPIA -> Theme.SEPIA
        ReaderTheme.SYSTEM -> null
        else -> null // Custom themes don't use the Theme enum
    }
    val bgColor = if (!theme.isBuiltIn) ReadiumColor(theme.backgroundColor.toInt()) else null
    val fgColor = if (!theme.isBuiltIn) ReadiumColor(theme.foregroundColor.toInt()) else null

    return EpubPreferences(
        fontFamily = fontFamily.cssValue?.let { ReadiumFontFamily(it) },
        fontSize = fontSize.toDouble() / 16.0,
        lineHeight = lineHeight.toDouble(),
        scroll = !isPaginated,
        theme = readiumTheme,
        backgroundColor = bgColor,
        textColor = fgColor,
        hyphens = hyphenate,
        textAlign = when (textAlign) {
            com.ember.reader.core.model.TextAlign.START -> org.readium.r2.navigator.preferences.TextAlign.START
            com.ember.reader.core.model.TextAlign.JUSTIFY -> org.readium.r2.navigator.preferences.TextAlign.JUSTIFY
            com.ember.reader.core.model.TextAlign.CENTER -> org.readium.r2.navigator.preferences.TextAlign.CENTER
        },
        publisherStyles = publisherStyles,
        pageMargins = pageMargins.toDouble(),
        wordSpacing = wordSpacing.toDouble(),
        letterSpacing = letterSpacing.toDouble(),
    )
}

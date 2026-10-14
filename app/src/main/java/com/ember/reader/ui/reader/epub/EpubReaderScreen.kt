package com.ember.reader.ui.reader.epub

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.fonts.CustomFont
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.readium.toJsonString
import com.ember.reader.core.readium.toLocator
import com.ember.reader.ui.common.ErrorScreen
import com.ember.reader.ui.common.LoadingScreen
import com.ember.reader.ui.reader.common.AnnotationDialog
import com.ember.reader.ui.reader.common.BookmarksSheet
import com.ember.reader.ui.reader.common.DictionarySheet
import com.ember.reader.ui.reader.common.FootnoteSheet
import com.ember.reader.ui.reader.common.HighlightsSheet
import com.ember.reader.ui.reader.common.NavigatorContainer
import com.ember.reader.ui.reader.common.ReaderPreferencesSheet
import com.ember.reader.ui.reader.common.ReaderScaffold
import com.ember.reader.ui.reader.common.ReaderUiState
import com.ember.reader.ui.reader.common.ReaderViewModel
import com.ember.reader.ui.reader.common.SearchSheet
import com.ember.reader.ui.reader.common.SyncConflictDialog
import com.ember.reader.ui.reader.common.TableOfContentsSheet
import com.ember.reader.ui.reader.common.toEpubPreferences
import com.ember.reader.ui.reader.epub.components.AutoScrollControls
import com.ember.reader.ui.reader.epub.components.BookmarkNameDialog
import com.ember.reader.ui.reader.epub.components.ReadAloudControls
import com.ember.reader.ui.reader.epub.components.ReadAloudFailedDialog
import com.ember.reader.ui.reader.epub.components.SelectionColorPickerDialog
import com.ember.reader.ui.reader.epub.components.TapZoneHintOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import timber.log.Timber

private const val FRAGMENT_TAG = "epub_navigator"
private const val CONTAINER_ID = 0x7F_FF_00_01

/** How long the next chapter is given to come up before auto-scroll takes the book as ended. */
private const val NEXT_CHAPTER_TIMEOUT_MS = 3_000L
private const val READ_ALOUD_DECORATION = "read-aloud"

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubReaderScreen(onNavigateBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chromeVisible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val currentLocator by viewModel.currentLocator.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val hasBookOverride by viewModel.hasBookOverride.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val syncConflict by viewModel.syncConflict.collectAsStateWithLifecycle()
    val pendingNavigation by viewModel.pendingNavigation.collectAsStateWithLifecycle()
    val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val showTapZoneHint by viewModel.showTapZoneHint.collectAsStateWithLifecycle()
    val autoScrolling by viewModel.autoScrolling.collectAsStateWithLifecycle()
    val readAloudState by viewModel.readAloud.state.collectAsStateWithLifecycle()
    val readAloudSpeed by viewModel.readAloud.speed.collectAsStateWithLifecycle()
    val readAloudSpoken by viewModel.readAloud.spoken.collectAsStateWithLifecycle()
    val autoScrollingNow by rememberUpdatedState(autoScrolling)
    val autoScrollSpeed by viewModel.autoScrollSpeed.collectAsStateWithLifecycle()

    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    // Save progress and manage reading session on lifecycle changes
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    // Pull directly from the fragment; the Compose collector in
                    // NavigatorContainer can lag behind fast page turns.
                    val latest = navigator?.currentLocator?.value
                    if (latest != null) {
                        viewModel.saveProgressLatest(latest)
                    } else {
                        viewModel.saveCurrentProgress()
                    }
                    viewModel.stopAutoScroll()
                    viewModel.onSessionPause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    viewModel.onSessionResume()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep screen on while reading
    val view = LocalView.current
    val speakingAloud = (readAloudState as? ReadAloudState.Reading)?.playing == true
    DisposableEffect(keepScreenOn, autoScrolling, speakingAloud) {
        // A page moving by itself, or being read out, gets no touches to keep the screen awake.
        if (keepScreenOn || autoScrolling || speakingAloud) {
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
    var showHighlights by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var dirNavAdapter by remember { mutableStateOf<DirectionalNavigationAdapter?>(null) }
    var currentTapListener by remember { mutableStateOf<InputListener?>(null) }
    var highlightManager by remember { mutableStateOf<HighlightDecorationManager?>(null) }
    var cfiBridge by remember { mutableStateOf<CfiBridge?>(null) }
    var footnoteResolver by remember { mutableStateOf<FootnoteResolver?>(null) }
    var footnote by remember { mutableStateOf<Footnote?>(null) }
    val highlightRepair by viewModel.highlightRepair.collectAsStateWithLifecycle()
    val bookmarkRepair by viewModel.bookmarkRepair.collectAsStateWithLifecycle()
    var pendingSelection by remember { mutableStateOf<Selection?>(null) }
    var pendingSelectionCfi by remember { mutableStateOf<String?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showAnnotationDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var editingHighlight by remember { mutableStateOf<Highlight?>(null) }
    var dictionaryWord by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // A tapped link that refers to a note opens the note over the page. Finding out means
    // asking the page, which takes a moment, so the link is held back and followed
    // afterwards if it is not a note. Readium passes a context with its own copy of the
    // note for links the book marks as references; those are resolved the same way as the
    // rest, so every note is found and tidied by the same rules.
    val linkListener = remember {
        object : EpubNavigatorFragment.Listener {
            override fun shouldFollowInternalLink(
                link: Link,
                context: HyperlinkNavigator.LinkContext?
            ): Boolean {
                viewModel.stopAutoScroll()
                val url = link.url()
                val fragment = url.fragment ?: return true
                val resolver = footnoteResolver ?: return true
                scope.launch {
                    val html = resolver.noteFor(url.removeFragment().toString(), fragment)
                    if (html != null) footnote = Footnote(html, link) else navigator?.go(link)
                }
                return false
            }

            // Links out of the book did nothing before this listener existed; still so.
            override fun onExternalLinkActivated(url: AbsoluteUrl) = Unit
        }
    }

    // Selection action handler — called from ActionMode.Callback
    val selectionActionHandler = remember { mutableStateOf<((Int) -> Unit)?>(null) }

    // Custom text selection menu: Highlight, Add Note, Copy
    val selectionCallback = remember {
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                // Text cannot be picked out of a page that is moving.
                viewModel.stopAutoScroll()
                menu.clear()
                menu.add(0, R.id.action_highlight, 0, R.string.highlight_action)
                menu.add(0, R.id.action_add_note, 1, R.string.add_note)
                menu.add(0, R.id.action_copy, 2, R.string.copy)
                menu.add(0, R.id.action_define, 3, R.string.define)
                menu.add(0, R.id.action_search_web, 4, R.string.search_web)
                menu.add(0, R.id.action_translate, 5, R.string.translate)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                selectionActionHandler.value?.invoke(item.itemId)
                mode.finish()
                return true
            }
            override fun onDestroyActionMode(mode: ActionMode) {}
        }
    }

    when (val state = uiState) {
        ReaderUiState.Loading -> LoadingScreen()
        is ReaderUiState.Error -> ErrorScreen(state.message)
        is ReaderUiState.Ready -> {
            val hasBookmark = currentLocator?.let { loc ->
                val href = loc.href.toString()
                val hrefEscaped = href.replace("/", "\\/")
                val currentProg = loc.locations.progression ?: -1.0
                bookmarks.any { bm ->
                    val inSameChapter = bm.locatorJson.contains(href) || bm.locatorJson.contains(hrefEscaped)
                    if (!inSameChapter) return@any false
                    // Check if bookmark is on the same page (within 1 page-worth of progression)
                    val bmLocator = bm.locatorJson.toLocator() ?: return@any false
                    val bmProg = bmLocator.locations.progression ?: return@any false
                    kotlin.math.abs(currentProg - bmProg) < 0.02 // ~1 page tolerance
                }
            } ?: false

            ReaderScaffold(
                title = state.book.title,
                chromeVisible = chromeVisible,
                currentLocator = currentLocator,
                hasBookmarkAtCurrentPosition = hasBookmark,
                onNavigateBack = onNavigateBack,
                onToggleBookmark = {
                    val removed = viewModel.toggleBookmark()
                    if (!removed) showBookmarkDialog = true
                },
                onOpenBookmarks = { showBookmarks = true },
                onOpenTableOfContents = { showToc = true },
                onOpenPreferences = { showPreferences = true },
                onOpenHighlights = { showHighlights = true },
                onOpenSearch = { showSearch = true },
                onSeekToProgression = { progression ->
                    scope.launch {
                        state.publication.locateProgression(progression.toDouble())?.let {
                            navigator?.go(it)
                        }
                    }
                },
                // A page turned at a time has nothing to scroll.
                onStartAutoScroll = if (preferences.isPaginated) null else viewModel::startAutoScroll,
                onStartReadAloud = {
                    scope.launch {
                        // From the first thing on screen, not the start of the chapter.
                        val from = navigator?.firstVisibleElementLocator() ?: navigator?.currentLocator?.value
                        viewModel.startReadAloud(context.applicationContext as Application, from)
                    }
                },
                brightness = preferences.brightness,
                onBrightnessChange = { newBrightness -> viewModel.updatePreferences(preferences.copy(brightness = newBrightness)) },
                edgeBrightnessSwipe = preferences.edgeBrightnessSwipe,
                streaming = state.streaming,
                showProgressIndicator = preferences.showProgressIndicator,
                overlay = {
                    (readAloudState as? ReadAloudState.Reading)?.let { reading ->
                        ReadAloudControls(
                            playing = reading.playing,
                            speed = readAloudSpeed,
                            onPrevious = viewModel.readAloud::previousSentence,
                            onTogglePlaying = viewModel.readAloud::togglePlaying,
                            onNext = viewModel.readAloud::nextSentence,
                            onSlower = { viewModel.readAloud.changeSpeed(faster = false) },
                            onFaster = { viewModel.readAloud.changeSpeed(faster = true) },
                            onStop = { viewModel.readAloud.stop() },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                    if (autoScrolling) {
                        AutoScrollControls(
                            speed = autoScrollSpeed,
                            onSlower = { viewModel.changeAutoScrollSpeed(faster = false) },
                            onFaster = { viewModel.changeAutoScrollSpeed(faster = true) },
                            onStop = { viewModel.stopAutoScroll(showChrome = true) },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                },
            ) {
                NavigatorContainer(
                    key = state.publication,
                    containerId = CONTAINER_ID,
                    fragmentTag = FRAGMENT_TAG,
                    fragmentClass = EpubNavigatorFragment::class.java,
                    fragmentFactory = EpubNavigatorFactory(
                        publication = state.publication
                    ).createFragmentFactory(
                        listener = linkListener,
                        initialLocator = state.initialLocator,
                        // Seed with the real, DataStore-backed prefs captured at
                        // load-time so Readium doesn't render with defaults and
                        // then reflow (which emits a shifted locator).
                        initialPreferences = state.preferences.toEpubPreferences(customFonts),
                        configuration = EpubNavigatorFragment.Configuration(
                            selectionActionModeCallback = selectionCallback
                        ).apply { declareCustomFonts(customFonts) }
                    ),
                    locatorFlow = { fragment ->
                        (fragment as? EpubNavigatorFragment)?.currentLocator
                    },
                    onLocatorChanged = viewModel::onLocatorChanged,
                    onNavigatorReady = { fragment ->
                        val nav = fragment as? EpubNavigatorFragment ?: return@NavigatorContainer
                        navigator = nav
                        val manager = HighlightDecorationManager(nav, state.publication, state.spine)
                        highlightManager = manager
                        cfiBridge = state.spine?.let { CfiBridge.create(context, nav, it) }
                        footnoteResolver = FootnoteResolver.create(context, nav, state.publication)

                        // Set up selection action handler
                        selectionActionHandler.value = { actionId ->
                            scope.launch {
                                val selection = manager.currentSelection() ?: return@launch
                                // Read while the text is still selected: the dialogs
                                // that follow clear the selection before a highlight is saved.
                                pendingSelectionCfi = cfiBridge?.selectionCfi()
                                Timber.d("CFI selection: %s", pendingSelectionCfi)
                                when (actionId) {
                                    R.id.action_highlight -> {
                                        pendingSelection = selection
                                        showColorPicker = true
                                    }
                                    R.id.action_add_note -> {
                                        pendingSelection = selection
                                        showAnnotationDialog = true
                                    }
                                    R.id.action_copy -> {
                                        val text = selection.locator.text.highlight ?: ""
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("highlight", text))
                                        manager.clearSelection()
                                    }
                                    R.id.action_define -> {
                                        val text = selection.locator.text.highlight ?: ""
                                        if (text.isNotBlank()) {
                                            // Use the first word for single-word lookup, or full text for phrases
                                            dictionaryWord = text.trim()
                                        }
                                        manager.clearSelection()
                                    }
                                    R.id.action_search_web -> {
                                        val text = selection.locator.text.highlight ?: ""
                                        if (text.isNotBlank()) {
                                            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                                                putExtra(android.app.SearchManager.QUERY, text)
                                            }
                                            val fallbackUrl = "https://www.google.com/search?q=${java.net.URLEncoder.encode(text, "UTF-8")}"
                                            context.launchIntentOrFallback(searchIntent, fallbackUrl)
                                        }
                                        manager.clearSelection()
                                    }
                                    R.id.action_translate -> {
                                        val text = selection.locator.text.highlight ?: ""
                                        if (text.isNotBlank()) {
                                            val translateIntent = Intent(Intent.ACTION_VIEW).apply {
                                                data = android.net.Uri.parse("https://translate.google.com/?sl=auto&tl=en&text=${java.net.URLEncoder.encode(text, "UTF-8")}")
                                            }
                                            context.startActivity(translateIntent)
                                        }
                                        manager.clearSelection()
                                    }
                                }
                            }
                        }

                        // Set up decoration tap listener
                        manager.addActivationListener { decorationId, _ ->
                            val highlightId = decorationId.toLongOrNull() ?: return@addActivationListener
                            editingHighlight = highlights.find { it.id == highlightId }
                        }
                    }
                )
                }

            // Apply preferences to the Readium navigator whenever they change
            LaunchedEffect(preferences, navigator) {
                val nav = navigator ?: return@LaunchedEffect
                nav.submitPreferences(preferences.toEpubPreferences(customFonts))

                // Remove old listeners
                dirNavAdapter?.let { nav.removeInputListener(it) }
                dirNavAdapter = null
                currentTapListener?.let { nav.removeInputListener(it) }

                // Configurable tap zone handler
                val tapPrefs = preferences
                val listener = object : InputListener {
                    override fun onTap(event: TapEvent): Boolean {
                        // Any tap stops a page that is moving by itself.
                        if (autoScrollingNow) {
                            viewModel.stopAutoScroll(showChrome = true)
                            return true
                        }
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

            // Move the page by itself while auto-scroll is on. Leaving scroll mode ends it.
            LaunchedEffect(autoScrolling, navigator, preferences.isPaginated) {
                val nav = navigator ?: return@LaunchedEffect
                if (!autoScrolling) return@LaunchedEffect
                if (preferences.isPaginated) {
                    viewModel.stopAutoScroll()
                    return@LaunchedEffect
                }
                AutoScrollController(
                    evaluate = nav::evaluateJavascript,
                    goToNextChapter = {
                        val chapter = nav.currentLocator.value.href
                        nav.goForward()
                        withTimeoutOrNull(NEXT_CHAPTER_TIMEOUT_MS) {
                            nav.currentLocator.first { it.href != chapter }
                        } != null
                    },
                    onCheckpoint = {
                        // The CFI is asked for here because the usual lookup waits for the
                        // page to have been still for longer than a checkpoint holds it.
                        val locator = nav.currentLocator.value
                        viewModel.onPositionCfi(locator, cfiBridge?.visibleStartCfi())
                        viewModel.saveProgressLatest(locator)
                    }
                ).run(viewModel.autoScrollSpeed)
                // It only returns at the end of the book.
                viewModel.stopAutoScroll(showChrome = true)
            }

            // Mark the sentence being read aloud and keep it on screen.
            val readAloudTint = MaterialTheme.colorScheme.primary.toArgb()
            LaunchedEffect(readAloudSpoken, navigator) {
                val nav = navigator ?: return@LaunchedEffect
                val spoken = readAloudSpoken
                val decorations = listOfNotNull(
                    spoken?.let {
                        Decoration(
                            id = READ_ALOUD_DECORATION,
                            locator = it,
                            style = Decoration.Style.Underline(tint = readAloudTint)
                        )
                    }
                )
                nav.applyDecorations(decorations, READ_ALOUD_DECORATION)
                spoken?.let { nav.go(it, animated = false) }
            }

            (readAloudState as? ReadAloudState.Failed)?.let { failed ->
                ReadAloudFailedDialog(
                    reason = failed.reason,
                    onInstallVoice = {
                        AndroidTtsEngine.requestInstallVoice(context)
                        viewModel.readAloud.stop()
                    },
                    onDismiss = { viewModel.readAloud.stop() }
                )
            }

            // In scroll mode, a swipe on past the end of a chapter opens the next, and one
            // back past its start opens the one before at its end.
            LaunchedEffect(navigator, preferences.isPaginated, autoScrolling, readAloudState is ReadAloudState.Reading) {
                val nav = navigator ?: return@LaunchedEffect
                if (preferences.isPaginated || autoScrolling) return@LaunchedEffect
                // The page is being moved to each sentence as it is spoken.
                if (readAloudState is ReadAloudState.Reading) return@LaunchedEffect
                ChapterPullController(
                    evaluate = nav::evaluateJavascript,
                    goToNextChapter = { nav.goForward() },
                    goToPreviousChapter = {
                        state.publication.locatorAtEndOfChapterBefore(nav.currentLocator.value.href)
                            ?.let { nav.go(it) }
                    }
                ).run()
            }

            // Inject CSS margins into the WebView whenever the chapter changes or margin
            // preferences change. evaluateJavascript() awaits the page load automatically,
            // so this is safe to call immediately after a chapter navigation.
            // currentLocator?.href changes on every chapter boundary (not on every page turn),
            // ensuring we re-inject into each new WebView without redundant calls.
            val currentChapterHref = currentLocator?.href?.toString()
            LaunchedEffect(navigator, currentChapterHref, preferences.marginTop, preferences.marginBottom,) {
                val nav = navigator ?: return@LaunchedEffect
                nav.evaluateJavascript(buildMarginCssScript(preferences.marginTop, preferences.marginBottom))
            }

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


            // Handle sync navigation (accept remote progress)
            LaunchedEffect(pendingNavigation, navigator) {
                val pending = pendingNavigation ?: return@LaunchedEffect
                val nav = navigator ?: return@LaunchedEffect
                val openedByCfi = pending.cfi != null &&
                    nav.goToCfi(pending.cfi, state.publication, state.spine, cfiBridge)
                if (!openedByCfi) {
                    pending.percentage?.let { state.publication.locateProgression(it.toDouble()) }?.let {
                        nav.go(it)
                    }
                }
                viewModel.onNavigationHandled()
            }

            // Once a page has settled, work out its real CFI so the progress saved for it
            // can carry one. Progress is saved a few seconds after a page turn, by which
            // time this has answered.
            LaunchedEffect(cfiBridge, currentLocator) {
                val bridge = cfiBridge ?: return@LaunchedEffect
                val locator = currentLocator ?: return@LaunchedEffect
                delay(500)
                val cfi = bridge.visibleStartCfi()
                Timber.d("CFI position: %s", cfi)
                viewModel.onPositionCfi(locator, cfi)
            }

            // Place highlights and bookmarks synced from Grimmory once the chapter they
            // belong to is on screen. Placing rewrites their stored locators, which
            // re-runs this with nothing left to do for the chapter.
            val placer = remember(cfiBridge, state.spine) {
                val bridge = cfiBridge ?: return@remember null
                val spine = state.spine ?: return@remember null
                SyncedPositionPlacer(bridge, spine) { href ->
                    Url(href)?.let { state.publication.linkWithHref(it)?.url()?.toString() } ?: href
                }
            }
            LaunchedEffect(placer, currentChapterHref, highlights, bookmarks) {
                val href = currentChapterHref ?: return@LaunchedEffect
                placer ?: return@LaunchedEffect
                viewModel.placeSyncedPositions(
                    highlights = placer.place(href, highlights.associate { it.id to it.locatorJson }, requireText = true),
                    bookmarks = placer.place(href, bookmarks.associate { it.id to it.locatorJson }, requireText = false),
                )
            }

            // Apply highlight decorations when navigator ready or highlights change
            LaunchedEffect(highlightManager, highlights) {
                highlightManager?.applyHighlights(highlights)
            }

            // Color picker dialog (after selecting text and tapping Highlight)
            if (showColorPicker && pendingSelection != null) {
                SelectionColorPickerDialog(
                    onColorSelected = { color ->
                        val selection = pendingSelection ?: return@SelectionColorPickerDialog
                        val locatorJson = selection.locator.toJsonString()
                        val selectedText = selection.locator.text.highlight
                        viewModel.addHighlight(locatorJson, color, selectedText = selectedText, cfi = pendingSelectionCfi)
                        highlightManager?.clearSelection()
                        showColorPicker = false
                        pendingSelection = null
                    },
                    onDismiss = {
                        showColorPicker = false
                        pendingSelection = null
                    },
                )
            }

            // Annotation dialog (after selecting text and tapping Add Note)
            if (showAnnotationDialog && pendingSelection != null) {
                AnnotationDialog(
                    onSave = { annotation, color ->
                        val selection = pendingSelection ?: return@AnnotationDialog
                        val locatorJson = selection.locator.toJsonString()
                        val selectedText = selection.locator.text.highlight
                        viewModel.addHighlight(locatorJson, color, annotation, selectedText, pendingSelectionCfi)
                        highlightManager?.clearSelection()
                        showAnnotationDialog = false
                        pendingSelection = null
                    },
                    onDismiss = {
                        showAnnotationDialog = false
                        pendingSelection = null
                    }
                )
            }

            // Edit highlight dialog (after tapping an existing highlight)
            editingHighlight?.let { highlight ->
                AnnotationDialog(
                    initialAnnotation = highlight.annotation ?: "",
                    initialColor = highlight.color,
                    onSave = { annotation, color ->
                        viewModel.updateHighlight(highlight, annotation, color)
                        editingHighlight = null
                    },
                    onDelete = {
                        viewModel.deleteHighlight(highlight.id)
                        editingHighlight = null
                    },
                    onDismiss = { editingHighlight = null }
                )
            }

            if (showToc) {
                TableOfContentsSheet(
                    publication = state.publication,
                    currentLocator = currentLocator,
                    onNavigate = { locator ->
                        scope.launch { navigator?.go(locator) }
                        showToc = false
                    },
                    onDismiss = { showToc = false }
                )
            }

            // Bookmark name dialog
            if (showBookmarkDialog) {
                BookmarkNameDialog(
                    chapterTitle = currentLocator?.title ?: "",
                    onSave = { name ->
                        scope.launch { viewModel.addBookmark(name, cfiBridge?.visibleStartCfi()) }
                        showBookmarkDialog = false
                    },
                    onDismiss = { showBookmarkDialog = false },
                )
            }

            if (showBookmarks) {
                BookmarksSheet(
                    bookmarks = bookmarks,
                    onNavigate = { bookmark ->
                        scope.launch {
                            navigator?.goToStored(bookmark.locatorJson, state.publication, state.spine, cfiBridge)
                        }
                        showBookmarks = false
                    },
                    onDelete = viewModel::deleteBookmark,
                    onDismiss = { showBookmarks = false },
                    repairState = bookmarkRepair,
                    onRepair = state.spine?.let { spine ->
                        {
                            val resolver = OffscreenCfiResolver(context, state.publication, spine)
                            viewModel.repairBookmarks(resolver::cfisForBookmarks)
                        }
                    }
                )
            }

            if (showSearch) {
                SearchSheet(
                    publication = state.publication,
                    onNavigate = { locator ->
                        scope.launch { navigator?.go(locator) }
                        showSearch = false
                    },
                    onDismiss = { showSearch = false }
                )
            }

            footnote?.let { note ->
                FootnoteSheet(
                    noteHtml = note.html,
                    onGoToNote = {
                        footnote = null
                        scope.launch { navigator?.go(note.link) }
                    },
                    onDismiss = { footnote = null }
                )
            }

            dictionaryWord?.let { word ->
                DictionarySheet(
                    word = word,
                    dictionaryRepository = viewModel.dictionaryRepository,
                    onDismiss = { dictionaryWord = null }
                )
            }

            if (showPreferences) {
                ReaderPreferencesSheet(
                    preferences = preferences,
                    onPreferencesChanged = viewModel::updatePreferences,
                    onDismiss = { showPreferences = false },
                    customFonts = customFonts,
                    hasOverride = hasBookOverride,
                    onResetToDefaults = viewModel::resetPreferencesToDefaults
                )
            }

            if (showHighlights) {
                HighlightsSheet(
                    highlights = highlights,
                    repairState = highlightRepair,
                    onRepair = state.spine?.let { spine ->
                        {
                            val resolver = OffscreenCfiResolver(context, state.publication, spine)
                            viewModel.repairHighlights(resolver::cfisFor)
                        }
                    },
                    onNavigate = { highlight ->
                        scope.launch {
                            navigator?.goToStored(highlight.locatorJson, state.publication, state.spine, cfiBridge)
                        }
                        showHighlights = false
                    },
                    onEdit = { editingHighlight = it },
                    onDelete = viewModel::deleteHighlight,
                    onDismiss = { showHighlights = false }
                )
            }

            syncConflict?.let { conflict ->
                SyncConflictDialog(
                    conflict = conflict,
                    onAcceptRemote = viewModel::acceptRemoteProgress,
                    onKeepLocal = viewModel::dismissSyncConflict
                )
            }

            // Tap zone hint overlay (shown on first book open)
            if (showTapZoneHint) {
                TapZoneHintOverlay(
                    preferences = preferences,
                    onDismiss = viewModel::dismissTapZoneHint
                )
            }
        }
    }
}

/**
 * Tells Readium about the user's fonts. Each is served from the publication's own URL
 * space (see `CustomFontContainer`), so it loads from the same origin as the chapters.
 */
@OptIn(ExperimentalReadiumApi::class)
private fun EpubNavigatorFragment.Configuration.declareCustomFonts(fonts: List<CustomFont>) {
    fonts.forEach { font ->
        val source = Url(PUBLICATION_BASE_URL + CustomFont.RESOURCE_PREFIX + font.fileName) ?: return@forEach
        addFontFamilyDeclaration(ReadiumFontFamily(font.name)) {
            addFontFace { addSource(source) }
        }
    }
}

/** Where Readium's navigator serves a publication's resources to its WebViews. */
private const val PUBLICATION_BASE_URL = "https://readium/publication/"

/**
 * Builds a JS snippet that applies top/bottom margins to the EPUB reading area.
 *
 * Readium's own setCSSProperties() works by calling
 *   document.documentElement.style.setProperty(name, value, "important")
 * — i.e. inline style with !important on :root, which is the highest-priority
 * position in the CSS cascade. A <style> element (even with !important) loses to
 * an existing inline !important set by Readium's own CSS engine.
 *
 * We use the identical mechanism so our padding wins the cascade reliably.
 * When both margins are zero the properties are removed to restore Readium's defaults.
 */
private fun buildMarginCssScript(marginTop: Int, marginBottom: Int): String {
    val topValue = if (marginTop > 0) "${marginTop}px" else ""
    val bottomValue = if (marginBottom > 0) "${marginBottom}px" else ""
    // language=JavaScript
    return """
        (function() {
            var root = document.documentElement;
            if ('$topValue'.length > 0) {
                root.style.setProperty('padding-top', '$topValue', 'important');
            } else {
                root.style.removeProperty('padding-top');
            }
            if ('$bottomValue'.length > 0) {
                root.style.setProperty('padding-bottom', '$bottomValue', 'important');
            } else {
                root.style.removeProperty('padding-bottom');
            }
        })();
    """.trimIndent()
}

private fun android.content.Context.launchIntentOrFallback(intent: Intent, fallbackUrl: String) {
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fallbackUrl)))
    }
}

/** A note opened from a link in the text, and the link to where it sits in the book. */
private data class Footnote(val html: String, val link: Link)

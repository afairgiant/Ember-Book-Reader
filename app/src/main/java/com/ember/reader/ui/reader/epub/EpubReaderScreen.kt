package com.ember.reader.ui.reader.epub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.HighlightColor
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme
import com.ember.reader.core.readium.toJsonString
import com.ember.reader.core.readium.toLocator
import com.ember.reader.ui.common.ErrorScreen
import com.ember.reader.ui.common.LoadingScreen
import com.ember.reader.ui.reader.common.AnnotationDialog
import com.ember.reader.ui.reader.common.BookmarksSheet
import com.ember.reader.ui.reader.common.DictionarySheet
import com.ember.reader.ui.reader.common.HighlightColorPicker
import com.ember.reader.ui.reader.common.HighlightsSheet
import com.ember.reader.ui.reader.common.NavigatorContainer
import com.ember.reader.ui.reader.common.ReaderPreferencesSheet
import com.ember.reader.ui.reader.common.ReaderScaffold
import com.ember.reader.ui.reader.common.ReaderUiState
import com.ember.reader.ui.reader.common.ReaderViewModel
import com.ember.reader.ui.reader.common.SearchSheet
import com.ember.reader.ui.reader.common.SyncConflictDialog
import com.ember.reader.ui.reader.common.TableOfContentsSheet
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.services.locateProgression

private const val FRAGMENT_TAG = "epub_navigator"
private const val CONTAINER_ID = 0x7F_FF_00_01

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
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val showTapZoneHint by viewModel.showTapZoneHint.collectAsStateWithLifecycle()

    // Save progress and manage reading session on lifecycle changes
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    viewModel.saveCurrentProgress()
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
    var showHighlights by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var dirNavAdapter by remember { mutableStateOf<DirectionalNavigationAdapter?>(null) }
    var currentTapListener by remember { mutableStateOf<InputListener?>(null) }
    var highlightManager by remember { mutableStateOf<HighlightDecorationManager?>(null) }
    var pendingSelection by remember { mutableStateOf<Selection?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showAnnotationDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var editingHighlight by remember { mutableStateOf<Highlight?>(null) }
    var dictionaryWord by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Selection action handler — called from ActionMode.Callback
    val selectionActionHandler = remember { mutableStateOf<((Int) -> Unit)?>(null) }

    // Custom text selection menu: Highlight, Add Note, Copy
    val selectionCallback = remember {
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
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
                brightness = preferences.brightness,
                onBrightnessChange = { newBrightness -> viewModel.updatePreferences(preferences.copy(brightness = newBrightness)) }
            ) {
                NavigatorContainer(
                    key = state.publication,
                    containerId = CONTAINER_ID,
                    fragmentTag = FRAGMENT_TAG,
                    fragmentClass = EpubNavigatorFragment::class.java,
                    fragmentFactory = EpubNavigatorFactory(
                        publication = state.publication
                    ).createFragmentFactory(
                        initialLocator = state.initialLocator,
                        initialPreferences = preferences.toEpubPreferences(),
                        configuration = EpubNavigatorFragment.Configuration(
                            selectionActionModeCallback = selectionCallback
                        )
                    ),
                    locatorFlow = { fragment ->
                        (fragment as? EpubNavigatorFragment)?.currentLocator
                    },
                    onLocatorChanged = viewModel::onLocatorChanged,
                    onNavigatorReady = { fragment ->
                        val nav = fragment as? EpubNavigatorFragment ?: return@NavigatorContainer
                        navigator = nav
                        val manager = HighlightDecorationManager(nav)
                        highlightManager = manager

                        // Set up selection action handler
                        selectionActionHandler.value = { actionId ->
                            scope.launch {
                                val selection = manager.currentSelection() ?: return@launch
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
                val progression = pendingNavigation ?: return@LaunchedEffect
                val nav = navigator ?: return@LaunchedEffect
                state.publication.locateProgression(progression.toDouble())?.let {
                    nav.go(it)
                }
                viewModel.onNavigationHandled()
            }

            // Apply highlight decorations when navigator ready or highlights change
            LaunchedEffect(highlightManager, highlights) {
                highlightManager?.applyHighlights(highlights, state.publication)
            }

            // Color picker dialog (after selecting text and tapping Highlight)
            if (showColorPicker && pendingSelection != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        showColorPicker = false
                        pendingSelection = null
                    },
                    title = {
                        androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.highlight_action))
                    },
                    text = {
                        HighlightColorPicker(
                            selectedColor = HighlightColor.YELLOW,
                            onColorSelected = { color ->
                                val selection = pendingSelection ?: return@HighlightColorPicker
                                val locatorJson = selection.locator.toJsonString()
                                val selectedText = selection.locator.text.highlight
                                viewModel.addHighlight(locatorJson, color, selectedText = selectedText)
                                highlightManager?.clearSelection()
                                showColorPicker = false
                                pendingSelection = null
                            }
                        )
                    },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showColorPicker = false
                            pendingSelection = null
                        }) {
                            androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.cancel))
                        }
                    }
                )
            }

            // Annotation dialog (after selecting text and tapping Add Note)
            if (showAnnotationDialog && pendingSelection != null) {
                AnnotationDialog(
                    onSave = { annotation, color ->
                        val selection = pendingSelection ?: return@AnnotationDialog
                        val locatorJson = selection.locator.toJsonString()
                        val selectedText = selection.locator.text.highlight
                        viewModel.addHighlight(locatorJson, color, annotation, selectedText)
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
                var bookmarkName by remember { mutableStateOf("") }
                val chapterTitle = currentLocator?.title ?: ""
                AlertDialog(
                    onDismissRequest = { showBookmarkDialog = false },
                    title = { Text("Add Bookmark") },
                    text = {
                        Column {
                            if (chapterTitle.isNotBlank()) {
                                Text(
                                    chapterTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            OutlinedTextField(
                                value = bookmarkName,
                                onValueChange = { bookmarkName = it },
                                label = { Text("Name (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.addBookmark(bookmarkName.ifBlank { chapterTitle })
                            showBookmarkDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showBookmarkDialog = false
                        }) { Text("Cancel") }
                    }
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
                    onDismiss = { showBookmarks = false }
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
                    hasOverride = hasBookOverride,
                    onResetToDefaults = viewModel::resetPreferencesToDefaults
                )
            }

            if (showHighlights) {
                HighlightsSheet(
                    highlights = highlights,
                    onNavigate = { highlight ->
                        highlight.locatorJson.toLocator()?.let { locator ->
                            scope.launch { navigator?.go(locator) }
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

@Composable
private fun TapZoneHintOverlay(preferences: ReaderPreferences, onDismiss: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
    ) {
        Canvas(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val topH = h * preferences.topZoneHeight
            val leftW = w * preferences.leftZoneWidth
            val rightW = w * preferences.rightZoneWidth

            drawRect(
                color = androidx.compose.ui.graphics.Color(0x44FF9800),
                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                size = androidx.compose.ui.geometry.Size(w, topH)
            )
            drawRect(
                color = androidx.compose.ui.graphics.Color(0x442196F3),
                topLeft = androidx.compose.ui.geometry.Offset(0f, topH),
                size = androidx.compose.ui.geometry.Size(leftW, h - topH)
            )
            drawRect(
                color = androidx.compose.ui.graphics.Color(0x444CAF50),
                topLeft = androidx.compose.ui.geometry.Offset(w - rightW, topH),
                size = androidx.compose.ui.geometry.Size(rightW, h - topH)
            )
            drawRect(
                color = androidx.compose.ui.graphics.Color(0x229C27B0),
                topLeft = androidx.compose.ui.geometry.Offset(leftW, topH),
                size = androidx.compose.ui.geometry.Size(w - leftW - rightW, h - topH)
            )
        }

        val labelStyle = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
            color = androidx.compose.ui.graphics.Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        androidx.compose.material3.Text(
            text = preferences.topTapZone.displayName,
            style = labelStyle,
            modifier = androidx.compose.ui.Modifier
                .align(androidx.compose.ui.Alignment.TopCenter)
                .padding(top = 24.dp)
        )
        androidx.compose.material3.Text(
            text = preferences.leftTapZone.displayName,
            style = labelStyle,
            modifier = androidx.compose.ui.Modifier
                .align(androidx.compose.ui.Alignment.CenterStart)
                .padding(start = 16.dp)
        )
        androidx.compose.material3.Text(
            text = preferences.rightTapZone.displayName,
            style = labelStyle,
            modifier = androidx.compose.ui.Modifier
                .align(androidx.compose.ui.Alignment.CenterEnd)
                .padding(end = 16.dp)
        )
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.Center),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                text = preferences.centerTapZone.displayName,
                style = labelStyle
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = androidx.compose.ui.Modifier.height(8.dp)
            )
            androidx.compose.material3.Text(
                text = "Tap anywhere to dismiss",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                )
            )
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
        letterSpacing = letterSpacing.toDouble()
    )
}

private fun android.content.Context.launchIntentOrFallback(intent: Intent, fallbackUrl: String) {
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fallbackUrl)))
    }
}

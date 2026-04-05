package com.ember.reader.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ember.reader.ui.book.BookDetailScreen
import com.ember.reader.ui.reader.audiobook.AudiobookPlayerScreen
import com.ember.reader.ui.catalog.CatalogScreen
import com.ember.reader.ui.library.LibraryScreen
import com.ember.reader.ui.library.LocalLibraryScreen
import com.ember.reader.ui.reader.epub.EpubReaderScreen
import com.ember.reader.ui.reader.pdf.PdfReaderScreen
import com.ember.reader.ui.server.ServerFormScreen
import com.ember.reader.ui.server.ServerListScreen
import com.ember.reader.ui.settings.DevLogScreen
import com.ember.reader.ui.settings.StorageScreen

object Routes {
    const val ARG_SERVER_ID = "serverId"
    const val ARG_BOOK_ID = "bookId"
    const val ARG_PATH = "path"

    // Top-level (bottom nav)
    const val HOME = "home"
    const val BROWSE = "browse"
    const val LOCAL_LIBRARY = "local_library"
    const val APP_SETTINGS = "app_settings"

    // Settings sub-pages
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_SYNC = "settings/sync"
    const val SETTINGS_DOWNLOADS = "settings/downloads"
    const val HARDCOVER = "hardcover"

    // Detail screens
    const val SERVER_FORM = "server_form?$ARG_SERVER_ID={$ARG_SERVER_ID}"
    const val CATALOG = "catalog/{$ARG_SERVER_ID}?$ARG_PATH={$ARG_PATH}"
    const val LIBRARY = "library/{$ARG_SERVER_ID}?$ARG_PATH={$ARG_PATH}"
    const val EPUB_READER = "reader/epub/{$ARG_BOOK_ID}"
    const val PDF_READER = "reader/pdf/{$ARG_BOOK_ID}"
    const val AUDIOBOOK_READER = "reader/audiobook/{$ARG_BOOK_ID}"
    const val BOOK_DETAIL = "book_detail/{$ARG_BOOK_ID}"
    const val STORAGE = "storage"
    const val STATS = "stats"
    const val DEV_LOG = "dev_log"

    fun audiobookReader(bookId: String): String = "reader/audiobook/$bookId"
    fun bookDetail(bookId: String): String = "book_detail/$bookId"

    fun serverForm(serverId: Long? = null): String =
        if (serverId != null) "server_form?$ARG_SERVER_ID=$serverId" else "server_form"

    fun catalog(serverId: Long, path: String? = null): String = if (path != null) {
        "catalog/$serverId?$ARG_PATH=${java.net.URLEncoder.encode(path, "UTF-8")}"
    } else {
        "catalog/$serverId"
    }

    fun library(serverId: Long, path: String? = null): String = if (path != null) {
        "library/$serverId?$ARG_PATH=${java.net.URLEncoder.encode(path, "UTF-8")}"
    } else {
        "library/$serverId"
    }

    fun epubReader(bookId: String): String = "reader/epub/$bookId"
    fun pdfReader(bookId: String): String = "reader/pdf/$bookId"
}

private enum class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(Routes.HOME, "Home", Icons.Default.Home),
    BROWSE(Routes.BROWSE, "Browse", Icons.Default.Explore),
    LIBRARY(Routes.LOCAL_LIBRARY, "Library", Icons.AutoMirrored.Filled.LibraryBooks),
    SETTINGS(Routes.APP_SETTINGS, "Settings", Icons.Default.Settings)
}

// Routes where the bottom nav should be visible
private val bottomNavRoutes = setOf(Routes.HOME, Routes.BROWSE, Routes.LOCAL_LIBRARY, Routes.APP_SETTINGS)

@Composable
fun EmberNavHost(
    modifier: Modifier = Modifier,
    initialRoute: String? = null,
    navController: NavHostController = rememberNavController()
) {
    // Navigate to deep link route from notification
    androidx.compose.runtime.LaunchedEffect(initialRoute) {
        if (initialRoute != null) {
            navController.navigate(initialRoute)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    BottomNavTab.entries.forEach { tab ->
                        val selected = navBackStackEntry?.destination?.hierarchy?.any {
                            it.route == tab.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            // Top-level: Home
            composable(Routes.HOME) {
                ServerListScreen(
                    onOpenSettings = {
                        navController.navigate(Routes.APP_SETTINGS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenReader = { bookId, format ->
                        navigateToReader(navController, bookId, format)
                    },
                    onOpenBookDetail = { bookId ->
                        navController.navigate(Routes.bookDetail(bookId))
                    },
                )
            }

            // Top-level: Browse
            composable(Routes.BROWSE) {
                com.ember.reader.ui.browse.BrowseScreen(
                    onOpenLibrary = { serverId -> navController.navigate(Routes.catalog(serverId)) },
                )
            }

            // Top-level: Library
            composable(Routes.LOCAL_LIBRARY) {
                LocalLibraryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenReader = { bookId, format ->
                        navigateToReader(navController, bookId, format)
                    },
                    onOpenBookDetail = { bookId ->
                        navController.navigate(Routes.bookDetail(bookId))
                    }
                )
            }

            // Top-level: Settings
            composable(Routes.APP_SETTINGS) {
                com.ember.reader.ui.settings.SettingsHubScreen(
                    onEditServer = { serverId -> navController.navigate(Routes.serverForm(serverId)) },
                    onAddServer = { navController.navigate(Routes.serverForm()) },
                    onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                    onOpenSync = { navController.navigate(Routes.SETTINGS_SYNC) },
                    onOpenDownloads = { navController.navigate(Routes.SETTINGS_DOWNLOADS) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenHardcover = { navController.navigate(Routes.HARDCOVER) },
                    onOpenDevLog = { navController.navigate(Routes.DEV_LOG) },
                )
            }

            // Settings sub-pages
            composable(Routes.SETTINGS_APPEARANCE) {
                com.ember.reader.ui.settings.AppearanceSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS_SYNC) {
                com.ember.reader.ui.settings.SyncSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS_DOWNLOADS) {
                com.ember.reader.ui.settings.DownloadSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenStorage = { navController.navigate(Routes.STORAGE) },
                )
            }
            composable(Routes.HARDCOVER) {
                com.ember.reader.ui.hardcover.HardcoverScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSearchGrimmory = { serverId, query ->
                        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                        navController.navigate(Routes.library(serverId, "grimmory:search=$encodedQuery"))
                    },
                )
            }

            // Detail: Server Form
            composable(
                route = Routes.SERVER_FORM,
                arguments = listOf(
                    navArgument(Routes.ARG_SERVER_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getLong("serverId")?.takeIf { it != -1L }
                ServerFormScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Detail: Catalog
            composable(
                route = Routes.CATALOG,
                arguments = listOf(
                    navArgument(Routes.ARG_SERVER_ID) { type = NavType.LongType },
                    navArgument(Routes.ARG_PATH) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getLong(Routes.ARG_SERVER_ID) ?: return@composable
                CatalogScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToFeed = { path ->
                        navController.navigate(Routes.catalog(serverId, path))
                    },
                    onNavigateToBooks = { path ->
                        navController.navigate(Routes.library(serverId, path))
                    }
                )
            }

            // Detail: Library (server books)
            composable(
                route = Routes.LIBRARY,
                arguments = listOf(
                    navArgument(Routes.ARG_SERVER_ID) { type = NavType.LongType },
                    navArgument(Routes.ARG_PATH) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getLong(Routes.ARG_SERVER_ID) ?: return@composable
                LibraryScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenReader = { bookId, format ->
                        navigateToReader(navController, bookId, format)
                    },
                    onOpenBookDetail = { bookId ->
                        navController.navigate(Routes.bookDetail(bookId))
                    }
                )
            }

            // Detail: Readers
            composable(
                route = Routes.EPUB_READER,
                arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType })
            ) {
                EpubReaderScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.PDF_READER,
                arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType })
            ) {
                PdfReaderScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.AUDIOBOOK_READER,
                arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType })
            ) {
                AudiobookPlayerScreen(onNavigateBack = { navController.popBackStack() })
            }

            // Detail: Book Detail
            composable(
                route = Routes.BOOK_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType })
            ) {
                BookDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenReader = { bookId, format ->
                        navigateToReader(navController, bookId, format)
                    }
                )
            }

            // Detail: Storage
            composable(Routes.STORAGE) {
                StorageScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Routes.DEV_LOG) {
                DevLogScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Routes.STATS) {
                com.ember.reader.ui.settings.StatsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun navigateToReader(
    navController: NavHostController,
    bookId: String,
    format: com.ember.reader.core.model.BookFormat
) {
    when (format) {
        com.ember.reader.core.model.BookFormat.EPUB ->
            navController.navigate(Routes.epubReader(bookId))
        com.ember.reader.core.model.BookFormat.PDF ->
            navController.navigate(Routes.pdfReader(bookId))
        com.ember.reader.core.model.BookFormat.AUDIOBOOK ->
            navController.navigate(Routes.audiobookReader(bookId))
    }
}

package com.ember.reader.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import com.ember.reader.ui.catalog.CatalogScreen
import com.ember.reader.ui.library.LibraryScreen
import com.ember.reader.ui.library.LocalLibraryScreen
import com.ember.reader.ui.reader.epub.EpubReaderScreen
import com.ember.reader.ui.reader.pdf.PdfReaderScreen
import com.ember.reader.ui.server.ServerFormScreen
import com.ember.reader.ui.server.ServerListScreen
import com.ember.reader.ui.settings.DevLogScreen
import com.ember.reader.ui.settings.SettingsScreen
import com.ember.reader.ui.settings.StorageScreen

object Routes {
    const val ARG_SERVER_ID = "serverId"
    const val ARG_BOOK_ID = "bookId"
    const val ARG_PATH = "path"

    // Top-level (bottom nav)
    const val HOME = "home"
    const val LOCAL_LIBRARY = "local_library"
    const val SETTINGS = "settings"

    // Detail screens
    const val SERVER_FORM = "server_form?$ARG_SERVER_ID={$ARG_SERVER_ID}"
    const val CATALOG = "catalog/{$ARG_SERVER_ID}?$ARG_PATH={$ARG_PATH}"
    const val LIBRARY = "library/{$ARG_SERVER_ID}?$ARG_PATH={$ARG_PATH}"
    const val EPUB_READER = "reader/epub/{$ARG_BOOK_ID}"
    const val PDF_READER = "reader/pdf/{$ARG_BOOK_ID}"
    const val BOOK_DETAIL = "book_detail/{$ARG_BOOK_ID}"
    const val STORAGE = "storage"
    const val STATS = "stats"
    const val DEV_LOG = "dev_log"

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
    LIBRARY(Routes.LOCAL_LIBRARY, "Library", Icons.AutoMirrored.Filled.LibraryBooks),
    SETTINGS(Routes.SETTINGS, "Profile", Icons.Default.Person)
}

// Routes where the bottom nav should be visible
private val bottomNavRoutes = setOf(Routes.HOME, Routes.LOCAL_LIBRARY, Routes.SETTINGS)

@Composable
fun EmberNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
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
                    onAddServer = { navController.navigate(Routes.serverForm()) },
                    onEditServer = { serverId -> navController.navigate(Routes.serverForm(serverId)) },
                    onOpenLibrary = { serverId -> navController.navigate(Routes.catalog(serverId)) },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenReader = { bookId, format ->
                        navigateToReader(navController, bookId, format)
                    },
                    onOpenStats = { navController.navigate(Routes.STATS) }
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

            // Top-level: Settings/Profile
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenStorage = { navController.navigate(Routes.STORAGE) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenDevLog = { navController.navigate(Routes.DEV_LOG) }
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
        com.ember.reader.core.model.BookFormat.AUDIOBOOK -> {}
    }
}

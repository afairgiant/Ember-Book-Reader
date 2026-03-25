package com.ember.reader.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ember.reader.ui.library.LibraryScreen
import com.ember.reader.ui.reader.epub.EpubReaderScreen
import com.ember.reader.ui.reader.pdf.PdfReaderScreen
import com.ember.reader.ui.server.ServerFormScreen
import com.ember.reader.ui.server.ServerListScreen
import com.ember.reader.ui.settings.SettingsScreen

object Routes {
    const val ARG_SERVER_ID = "serverId"
    const val ARG_BOOK_ID = "bookId"

    const val SERVER_LIST = "servers"
    const val SERVER_FORM = "server_form?$ARG_SERVER_ID={$ARG_SERVER_ID}"
    const val LIBRARY = "library/{$ARG_SERVER_ID}"
    const val EPUB_READER = "reader/epub/{$ARG_BOOK_ID}"
    const val PDF_READER = "reader/pdf/{$ARG_BOOK_ID}"
    const val SETTINGS = "settings"

    fun serverForm(serverId: Long? = null): String =
        if (serverId != null) "server_form?$ARG_SERVER_ID=$serverId" else "server_form"

    fun library(serverId: Long): String = "library/$serverId"
    fun epubReader(bookId: String): String = "reader/epub/$bookId"
    fun pdfReader(bookId: String): String = "reader/pdf/$bookId"
}

@Composable
fun EmberNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SERVER_LIST,
        modifier = modifier,
    ) {
        composable(Routes.SERVER_LIST) {
            ServerListScreen(
                onAddServer = { navController.navigate(Routes.serverForm()) },
                onEditServer = { serverId -> navController.navigate(Routes.serverForm(serverId)) },
                onOpenLibrary = { serverId -> navController.navigate(Routes.library(serverId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.SERVER_FORM,
            arguments = listOf(
                navArgument(Routes.ARG_SERVER_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId")?.takeIf { it != -1L }
            ServerFormScreen(
                serverId = serverId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.LIBRARY,
            arguments = listOf(
                navArgument(Routes.ARG_SERVER_ID) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: return@composable
            LibraryScreen(
                serverId = serverId,
                onNavigateBack = { navController.popBackStack() },
                onOpenReader = { bookId, format ->
                    when (format) {
                        com.ember.reader.core.model.BookFormat.EPUB ->
                            navController.navigate(Routes.epubReader(bookId))
                        com.ember.reader.core.model.BookFormat.PDF ->
                            navController.navigate(Routes.pdfReader(bookId))
                        com.ember.reader.core.model.BookFormat.AUDIOBOOK -> { /* Future */ }
                    }
                },
            )
        }

        composable(
            route = Routes.EPUB_READER,
            arguments = listOf(
                navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType },
            ),
        ) {
            EpubReaderScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PDF_READER,
            arguments = listOf(
                navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType },
            ),
        ) {
            PdfReaderScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

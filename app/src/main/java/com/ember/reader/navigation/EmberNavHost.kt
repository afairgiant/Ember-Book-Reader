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
import com.ember.reader.ui.server.ServerFormScreen
import com.ember.reader.ui.server.ServerListScreen
import com.ember.reader.ui.settings.SettingsScreen

object Routes {
    const val SERVER_LIST = "servers"
    const val SERVER_FORM = "server_form?serverId={serverId}"
    const val LIBRARY = "library/{serverId}"
    const val SETTINGS = "settings"

    fun serverForm(serverId: Long? = null): String =
        if (serverId != null) "server_form?serverId=$serverId" else "server_form"

    fun library(serverId: Long): String = "library/$serverId"
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
                navArgument("serverId") {
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
                navArgument("serverId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: return@composable
            LibraryScreen(
                serverId = serverId,
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

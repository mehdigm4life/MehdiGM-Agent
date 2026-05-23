package com.gs.agent.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gs.agent.agent.tools.registerDefaultTools
import com.gs.agent.ui.screens.ChatScreen
import com.gs.agent.ui.screens.ConversationsScreen
import com.gs.agent.ui.screens.PermissionsScreen
import com.gs.agent.ui.screens.SettingsScreen
import com.gs.agent.ui.explorer.FileExplorerScreen

object Routes {
    const val PERMISSIONS = "permissions"
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{id}"
    const val SETTINGS = "settings"
    const val EXPLORER = "explorer"
    fun chat(id: String) = "chat/$id"
    fun explorer(path: String) = "explorer?path=$path"
}

@Composable
fun AppRoot() {
    LaunchedEffect(Unit) { registerDefaultTools() }
    val navController = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(navController = navController, startDestination = Routes.PERMISSIONS) {
            composable(Routes.PERMISSIONS) {
                PermissionsScreen(onContinue = {
                    navController.navigate(Routes.CONVERSATIONS) {
                        popUpTo(Routes.PERMISSIONS) { inclusive = true }
                    }
                })
            }
            composable(Routes.CONVERSATIONS) {
                ConversationsScreen(
                    onOpen = { id -> navController.navigate(Routes.chat(id)) },
                    onSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.CHAT) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ChatScreen(
                    conversationId = id,
                    onBack = { navController.popBackStack() },
                    onSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            // Explorer route – opens file explorer at given path (default root)
            composable(Routes.EXPLORER) { backStackEntry ->
                val path = backStackEntry.arguments?.getString("path") ?: "/storage/emulated/0"
                FileExplorerScreen(startPath = path, onBack = { navController.popBackStack() })
            }
        }
    }
}

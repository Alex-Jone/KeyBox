package com.keybox.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.keybox.app.ui.home.HomeScreen
import com.keybox.app.ui.home.ItemDetailScreen
import com.keybox.app.ui.home.ItemEditScreen
import com.keybox.app.ui.home.TrashScreen
import com.keybox.app.ui.lock.LockScreen
import com.keybox.app.ui.lock.SetupScreen
import com.keybox.app.ui.settings.BackupScreen
import com.keybox.app.ui.settings.SecurityCenterScreen

@Composable
fun KeyBoxApp(appViewModel: AppViewModel) {
    val state by appViewModel.state.collectAsState()
    val nav = rememberNavController()

    // 锁定（或需要重设主密码）时，强制清空导航栈回到 root，
    // 避免「状态已锁定但编辑/详情页仍停留在栈顶」导致的解密崩溃。
    LaunchedEffect(state) {
        if (state == AppViewModel.AppState.Locked || state == AppViewModel.AppState.NeedsSetup) {
            nav.popBackStack("root", inclusive = false)
        }
    }

    NavHost(navController = nav, startDestination = "root") {
        composable("root") {
            when (state) {
                AppViewModel.AppState.NeedsSetup -> SetupScreen(appViewModel)
                AppViewModel.AppState.Locked -> LockScreen(appViewModel)
                AppViewModel.AppState.Unlocked -> {
                    HomeScreen(
                        appViewModel = appViewModel,
                        onOpenItem = { nav.navigate("detail/$it") },
                        onAddItem = { nav.navigate("edit/0") },
                        onOpenTrash = { nav.navigate("trash") },
                        onOpenBackup = { nav.navigate("backup") },
                        onOpenSecurity = { nav.navigate("security") }
                    )
                }
            }
        }
        composable("detail/{id}") { backStack ->
            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: 0L
            ItemDetailScreen(
                appViewModel = appViewModel,
                itemId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("edit/$id") }
            )
        }
        composable("edit/{id}") { backStack ->
            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: 0L
            ItemEditScreen(
                appViewModel = appViewModel,
                itemId = id,
                onBack = { nav.popBackStack() }
            )
        }
        composable("trash") {
            TrashScreen(
                appViewModel = appViewModel,
                onBack = { nav.popBackStack() }
            )
        }
        composable("backup") {
            BackupScreen(
                appViewModel = appViewModel,
                onBack = { nav.popBackStack() }
            )
        }
        composable("security") {
            SecurityCenterScreen(
                appViewModel = appViewModel,
                onBack = { nav.popBackStack() }
            )
        }
    }
}

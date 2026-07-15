package com.example.countdowndays.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.countdowndays.ui.about.AboutScreen
import com.example.countdowndays.ui.detail.EventDetailScreen
import com.example.countdowndays.ui.editevent.AddEditEventScreen
import com.example.countdowndays.ui.main.MainScreen
import com.example.countdowndays.ui.settings.SettingsScreen

@Composable
fun AppNav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MAIN) {

        composable(Routes.MAIN) {
            MainScreen(
                onAddEvent = { navController.navigate(Routes.addEdit(null)) },
                onOpenEvent = { id -> navController.navigate(Routes.detail(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(
            route = Routes.ADD_EDIT,
            arguments = listOf(
                navArgument(Routes.ARG_EVENT_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { entry ->
            val idArg = entry.arguments?.getString(Routes.ARG_EVENT_ID)
            val eventId = idArg?.takeIf { it.isNotBlank() }?.toLongOrNull()
            AddEditEventScreen(
                eventId = eventId,
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_EVENT_ID) { type = NavType.LongType }
            )
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_EVENT_ID) ?: 0L
            EventDetailScreen(
                eventId = id,
                onBack = { navController.popBackStack() },
                onEdit = { eid -> navController.navigate(Routes.addEdit(eid)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

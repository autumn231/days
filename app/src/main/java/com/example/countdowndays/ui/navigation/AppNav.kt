package com.example.countdowndays.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.countdowndays.ui.about.AboutScreen
import com.example.countdowndays.ui.archive.ArchiveScreen
import com.example.countdowndays.ui.calendar.CalendarScreen
import com.example.countdowndays.ui.detail.EventDetailScreen
import com.example.countdowndays.ui.editevent.AddEditEventScreen
import com.example.countdowndays.ui.main.MainScreen
import com.example.countdowndays.ui.settings.SettingsScreen
import com.example.countdowndays.ui.showcase.ShowcaseScreen
import com.example.countdowndays.ui.tools.DateCalcScreen

private const val DURATION = 260

@Composable
fun AppNav(
    startEventId: Long? = null,
    onStartEventHandled: () -> Unit = {}
) {
    val nav = rememberNavController()

    // 从通知点进来：直接打开对应事件
    LaunchedEffect(startEventId) {
        if (startEventId != null && startEventId > 0) {
            nav.navigate(Routes.detail(startEventId))
            onStartEventHandled()
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.MAIN,
        enterTransition = {
            slideInHorizontally(tween(DURATION)) { it / 5 } + fadeIn(tween(DURATION))
        },
        exitTransition = { fadeOut(tween(DURATION / 2)) },
        popEnterTransition = { fadeIn(tween(DURATION / 2)) },
        popExitTransition = {
            slideOutHorizontally(tween(DURATION)) { it / 5 } + fadeOut(tween(DURATION))
        }
    ) {

        composable(Routes.MAIN) {
            MainScreen(
                onAddEvent = { nav.navigate(Routes.addEdit(null)) },
                onOpenEvent = { id -> nav.navigate(Routes.detail(id)) },
                onOpenShowcase = { id -> nav.navigate(Routes.showcase(id)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenCalendar = { nav.navigate(Routes.CALENDAR) }
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
                onSaved = { nav.popBackStack() },
                onCancel = { nav.popBackStack() }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_EVENT_ID) { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_EVENT_ID) ?: 0L
            EventDetailScreen(
                eventId = id,
                onBack = { nav.popBackStack() },
                onEdit = { eid -> nav.navigate(Routes.addEdit(eid)) },
                onShowcase = { eid -> nav.navigate(Routes.showcase(eid)) }
            )
        }

        composable(
            route = Routes.SHOWCASE,
            arguments = listOf(navArgument(Routes.ARG_EVENT_ID) { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_EVENT_ID) ?: 0L
            ShowcaseScreen(
                eventId = id,
                onBack = { nav.popBackStack() },
                onEdit = { eid -> nav.navigate(Routes.addEdit(eid)) },
                onAddNew = { nav.navigate(Routes.addEdit(null)) }
            )
        }

        composable(Routes.CALENDAR) {
            CalendarScreen(
                onBack = { nav.popBackStack() },
                onOpenEvent = { id -> nav.navigate(Routes.detail(id)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenArchive = { nav.navigate(Routes.ARCHIVE) },
                onOpenDateCalc = { nav.navigate(Routes.DATE_CALC) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.ARCHIVE) {
            ArchiveScreen(
                onBack = { nav.popBackStack() },
                onOpenEvent = { id -> nav.navigate(Routes.detail(id)) }
            )
        }

        composable(Routes.DATE_CALC) {
            DateCalcScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { nav.popBackStack() })
        }
    }
}

package com.timestablequest.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.timestablequest.app.ui.calculator.CalculatorHistoryScreen
import com.timestablequest.app.ui.calculator.CalculatorScreen
import com.timestablequest.app.ui.history.HistoryScreen
import com.timestablequest.app.ui.map.MapScreen
import com.timestablequest.app.ui.practice.MixedSetupScreen
import com.timestablequest.app.ui.practice.NeedsPracticeScreen
import com.timestablequest.app.ui.practice.PracticeScreen
import com.timestablequest.app.ui.practice.PracticeViewModel
import com.timestablequest.app.ui.progress.ProgressScreen
import com.timestablequest.app.ui.results.ResultsScreen
import com.timestablequest.app.ui.results.ResultsViewModel
import com.timestablequest.app.ui.row.RowScreen
import com.timestablequest.app.ui.row.RowViewModel
import com.timestablequest.app.ui.settings.PrivacyScreen
import com.timestablequest.app.ui.settings.SettingsScreen
import com.timestablequest.app.ui.study.StudyScreen
import com.timestablequest.app.ui.study.StudyViewModel
import com.timestablequest.app.ui.theme.Atlas

object Routes {
    const val MAP = "map"
    const val PROGRESS = "progress"
    const val HISTORY = "history"
    const val CALCULATOR = "calculator"
    const val CALC_HISTORY = "calculator/history"
    const val ROW = "row/{${RowViewModel.ARG_ROW}}"
    const val STUDY = "study/{${StudyViewModel.ARG_ROW}}/{${StudyViewModel.ARG_COLUMN}}"
    const val PRACTICE = "practice/{${PracticeViewModel.ARG_SESSION_ID}}"
    const val RESULTS = "results/{${ResultsViewModel.ARG_SESSION_ID}}"
    const val MIXED_SETUP = "mixed"
    const val NEEDS_PRACTICE = "needs-practice"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun row(row: Int) = "row/$row"
    fun study(row: Int, column: Int) = "study/$row/$column"
    fun practice(id: Long) = "practice/$id"
    fun results(id: Long) = "results/$id"
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

private val Tabs = listOf(
    Tab(Routes.MAP, "Map") { Icon(Icons.Filled.Home, contentDescription = null) },
    Tab(Routes.PROGRESS, "Progress") { Icon(Icons.Filled.Star, contentDescription = null) },
    Tab(Routes.HISTORY, "History") { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
    Tab(Routes.CALCULATOR, "Calculator") {
        Box(Modifier.size(24.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Text("±", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    },
)

private const val USE_VALUE_KEY = "calc_use_value"

/** Opens a session above the current screen; Back from practice returns there with the session kept. */
private fun NavHostController.openSession(id: Long) {
    navigate(Routes.practice(id)) { launchSingleTop = true }
}

/** Replaces the practice screen with its results, so Back from results goes to where practice started. */
private fun NavHostController.showResults(id: Long) {
    navigate(Routes.results(id)) {
        popUpTo(Routes.PRACTICE) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.backToMap() {
    if (!popBackStack(Routes.MAP, inclusive = false)) navigate(Routes.MAP)
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTabs = Tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabs) {
                NavigationBar(containerColor = Atlas.PaperDeep) {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = tab.icon,
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Atlas.Yellow),
                        )
                    }
                }
            }
        },
    ) { padding: PaddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAP,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.MAP) {
                MapScreen(
                    onOpenRow = { r -> navController.navigate(Routes.row(r)) },
                    onStudyRow = { r ->
                        navController.navigate(Routes.row(r))
                        navController.navigate(Routes.study(r, 1))
                    },
                    onOpenSession = { id -> navController.openSession(id) },
                    onMixed = { navController.navigate(Routes.MIXED_SETUP) },
                    onNeedsPractice = { navController.navigate(Routes.NEEDS_PRACTICE) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(onOpenRow = { r -> navController.navigate(Routes.row(r)) })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpen = { id -> navController.navigate(Routes.results(id)) })
            }
            composable(Routes.CALCULATOR) { entry ->
                val pending by entry.savedStateHandle.getStateFlow<String?>(USE_VALUE_KEY, null).collectAsStateWithLifecycle()
                CalculatorScreen(
                    onHistory = { navController.navigate(Routes.CALC_HISTORY) },
                    pendingValue = pending,
                    onPendingConsumed = { entry.savedStateHandle[USE_VALUE_KEY] = null },
                )
            }
            composable(Routes.CALC_HISTORY) {
                CalculatorHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onUseResult = { value ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(USE_VALUE_KEY, value)
                        navController.popBackStack()
                    },
                )
            }
            composable(
                Routes.ROW,
                arguments = listOf(navArgument(RowViewModel.ARG_ROW) { type = NavType.IntType }),
            ) {
                RowScreen(
                    onBack = { navController.popBackStack() },
                    onStudy = { r, c -> navController.navigate(Routes.study(r, c)) },
                    onOpenSession = { id -> navController.openSession(id) },
                )
            }
            composable(
                Routes.STUDY,
                arguments = listOf(
                    navArgument(StudyViewModel.ARG_ROW) { type = NavType.IntType },
                    navArgument(StudyViewModel.ARG_COLUMN) { type = NavType.IntType },
                ),
            ) {
                StudyScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.PRACTICE,
                arguments = listOf(navArgument(PracticeViewModel.ARG_SESSION_ID) { type = NavType.LongType }),
            ) {
                PracticeScreen(
                    onBack = { navController.popBackStack() },
                    onFinished = { id -> navController.showResults(id) },
                    onOpenSession = { id ->
                        navController.navigate(Routes.practice(id)) {
                            popUpTo(Routes.PRACTICE) { inclusive = true }
                        }
                    },
                    onBackToMap = { navController.backToMap() },
                )
            }
            composable(
                Routes.RESULTS,
                arguments = listOf(navArgument(ResultsViewModel.ARG_SESSION_ID) { type = NavType.LongType }),
            ) {
                ResultsScreen(
                    onBack = { navController.popBackStack() },
                    onBackToMap = { navController.backToMap() },
                    onOpenSession = { id -> navController.openSession(id) },
                )
            }
            composable(Routes.MIXED_SETUP) {
                MixedSetupScreen(
                    onBack = { navController.popBackStack() },
                    onStarted = { id ->
                        navController.navigate(Routes.practice(id)) {
                            popUpTo(Routes.MIXED_SETUP) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.NEEDS_PRACTICE) {
                NeedsPracticeScreen(
                    onBack = { navController.popBackStack() },
                    onStarted = { id ->
                        navController.navigate(Routes.practice(id)) {
                            popUpTo(Routes.NEEDS_PRACTICE) { inclusive = true }
                        }
                    },
                    onOpenRow = { r -> navController.navigate(Routes.row(r)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                )
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

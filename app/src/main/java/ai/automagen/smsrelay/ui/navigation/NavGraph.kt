package ai.automagen.smsrelay.ui.navigation

import ai.automagen.smsrelay.ui.screens.about.AboutScreen
import ai.automagen.smsrelay.ui.screens.home.HomeScreen
import ai.automagen.smsrelay.ui.screens.settings.SettingsScreen
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object About : Screen("about")
}

@Composable
fun AppNavGraph(navController: NavHostController, drawerState: DrawerState) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) { HomeScreen(drawerState = drawerState) }
        composable(Screen.Settings.route) { SettingsScreen(drawerState = drawerState) }
        composable(Screen.About.route) { AboutScreen() }
    }
}

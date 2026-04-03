package com.childfilter.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.childfilter.app.data.AppPreferences
import com.childfilter.app.ui.screens.ActivityLogScreen
import com.childfilter.app.ui.screens.ChildrenScreen
import com.childfilter.app.ui.screens.GroupSelectionScreen
import com.childfilter.app.ui.screens.HomeScreen
import com.childfilter.app.ui.screens.PermissionsScreen
import com.childfilter.app.ui.screens.SettingsScreen
import com.childfilter.app.ui.screens.SplashScreen
import com.childfilter.app.ui.screens.MatchedPhotosScreen
import com.childfilter.app.ui.screens.PhotoDetailScreen
import com.childfilter.app.ui.screens.SetReferencePhotoScreen
import com.childfilter.app.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    // Holds the pending deep-link destination so recomposition can pick it up
    private val pendingNavigateTo = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = AppPreferences.getInstance(this)
        val hasCompletedOnboarding = runBlocking { prefs.hasCompletedOnboarding().first() }
        pendingNavigateTo.value = intent?.getStringExtra("navigate_to")

        setContent {
            val darkMode by prefs.getDarkMode().collectAsState(initial = false)
            val navigateTo by pendingNavigateTo
            AppTheme(darkTheme = darkMode) {  // nude palette — no dynamic color
                AppNavigation(
                    hasCompletedOnboarding = hasCompletedOnboarding,
                    navigateTo = navigateTo,
                    onNavigateConsumed = { pendingNavigateTo.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Push the new destination into state so the running Compose tree re-navigates
        pendingNavigateTo.value = intent.getStringExtra("navigate_to")
    }
}

@Composable
fun AppNavigation(
    hasCompletedOnboarding: Boolean,
    navigateTo: String? = null,
    onNavigateConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val startDestination = if (!hasCompletedOnboarding) "permissions" else "splash"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("permissions") { PermissionsScreen(navController) }
        composable("splash") { SplashScreen(navController) }
        composable("home") { HomeScreen(navController) }
        composable("set_reference") { SetReferencePhotoScreen(navController) }
        composable("matched_photos") { MatchedPhotosScreen(navController) }
        composable("photo_detail/{uri}") { backStackEntry ->
            PhotoDetailScreen(navController, backStackEntry.arguments?.getString("uri") ?: "")
        }
        composable("group_selection") { GroupSelectionScreen(navController) }
        composable("children") { ChildrenScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
        composable("activity_log") { ActivityLogScreen(navController) }
    }

    // Handle deep navigation from notification tap (works for both cold start and onNewIntent)
    if (navigateTo != null) {
        LaunchedEffect(navigateTo) {
            navController.navigate(navigateTo) {
                popUpTo("home") { inclusive = false }
                launchSingleTop = true
            }
            onNavigateConsumed()
        }
    }
}

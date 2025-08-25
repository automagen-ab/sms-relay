package ai.automagen.smsrelay.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.navigation.NavController
import ai.automagen.smsrelay.ui.navigation.Screen
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(navController: NavController, drawerState: DrawerState) {
    val scope = rememberCoroutineScope()
    val windowInfo = LocalWindowInfo.current
    val drawerWidth = (windowInfo.containerSize.width * 0.3f).dp

    ModalDrawerSheet(
        modifier = Modifier.width(drawerWidth)
    ) {
        Spacer(Modifier.height(16.dp))

        NavigationDrawerItem(
            label = { Text("Home") },
            selected = false,
            onClick = {
                navController.navigate(Screen.Home.route)
                scope.launch { drawerState.close() }
            },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedContainerColor = Color.Transparent,
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = MaterialTheme.shapes.medium
        )

        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            onClick = {
                navController.navigate(Screen.Settings.route)
                scope.launch { drawerState.close() }
            },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            colors = NavigationDrawerItemDefaults.colors(),
            shape = MaterialTheme.shapes.medium
        )

//        HorizontalDivider(Modifier.padding(vertical = 8.dp))

//        NavigationDrawerItem(
//            label = { Text("FAQ") },
//            selected = false,
//            onClick = {
//                // TODO: open FAQ link
//                scope.launch { drawerState.close() }
//            },
//            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
//            colors = NavigationDrawerItemDefaults.colors(),
//            shape = MaterialTheme.shapes.medium
//        )

//        NavigationDrawerItem(
//            label = { Text("Rate the App") },
//            selected = false,
//            onClick = {
//                // TODO: open Play Store link
//                scope.launch { drawerState.close() }
//            },
//            icon = { Icon(Icons.Default.Star, contentDescription = null) },
//            colors = NavigationDrawerItemDefaults.colors(),
//            shape = MaterialTheme.shapes.medium
//        )

        NavigationDrawerItem(
            label = { Text("About") },
            selected = false,
            onClick = {
                navController.navigate(Screen.About.route)
                scope.launch { drawerState.close() }
            },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            colors = NavigationDrawerItemDefaults.colors(),
            shape = MaterialTheme.shapes.medium
        )

        Spacer(modifier = Modifier.weight(1f))

        // Footer
        Text(
            text = "v${versionText()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun versionText(): String? {
    val context = LocalContext.current
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    } catch (e: Exception) {
        "unknown"
    }
}
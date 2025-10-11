package ai.automagen.smsrelay

import ai.automagen.smsrelay.ui.components.AppDrawer
import ai.automagen.smsrelay.ui.navigation.AppNavGraph
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ai.automagen.smsrelay.ui.theme.AutomagenSMSRelayTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutomagenSMSRelayTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)

                // Handle the deep link intent
                LaunchedEffect(Unit) {
                    intent?.data?.let { uri ->
                        navController.handleDeepLink(
                            NavDeepLinkRequest.Builder.fromUri(uri).build()
                        )
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawer(navController, drawerState)
                    }
                ) {
                    AppNavGraph(navController = navController, drawerState = drawerState)
                }
            }
        }
    }
}

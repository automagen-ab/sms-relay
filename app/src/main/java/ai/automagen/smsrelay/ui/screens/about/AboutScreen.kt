package ai.automagen.smsrelay.ui.screens.about

import ai.automagen.smsrelay.BuildConfig
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DrawerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.net.toUri

private const val TAG = "AboutScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(drawerState: DrawerState) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val repoOwner = "automagen-ab"
    val repoName = "sms-relay"

    var latestVersion by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    suspend fun fetchLatestRelease(): String? = withContext(Dispatchers.IO) {
        val urlString = "https://api.github.com/repos/${repoOwner}/${repoName}/releases/latest"
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 15000
                readTimeout = 10000
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "GitHub API error: ${connection.responseMessage}")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response).getString("tag_name")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching latest release", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0..2) {
            if (latestParts.getOrElse(i) { 0 } > currentParts.getOrElse(i) { 0 }) return true
            if (latestParts.getOrElse(i) { 0 } < currentParts.getOrElse(i) { 0 }) return false
        }
        return false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        isCheckingUpdate = true
                        val latest = fetchLatestRelease()
                        val current = BuildConfig.VERSION_NAME
                        isCheckingUpdate = false

                        if (latest != null && isNewerVersion(latest, current)) {
                            latestVersion = latest
                            showUpdateDialog = true
                        } else {
                            snackbarHostState.showSnackbar("No updates available")
                        }
                    }
                }
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(12.dp).size(24.dp)
                    )
                } else {
                    Icon(Icons.Default.SystemUpdate, contentDescription = "Check for Updates")
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text("Automagen SMS Relay", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Automagen SMS Relay is an Android application that forwards incoming SMS messages " +
                        "to a remote server or endpoint of your choice. Developed and maintained by Automagen, " +
                        "this app is lightweight, easy to set up, and designed for simplicity.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            Text("Important:", style = MaterialTheme.typography.titleMedium)
            Text(
                "Depending on your country, intercepting or forwarding SMS messages may be subject to legal restrictions. " +
                        "Please ensure you comply with all applicable laws and regulations.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text("License:", style = MaterialTheme.typography.titleMedium)
            Text(
                "This project is open-source and licensed under the GNU General Public License v3.0 (GPL-3.0). " +
                        "You are free to use, modify, and distribute this software under the terms of the GPL-3.0 license.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "View the source code on GitHub",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { uriHandler.openUri("https://github.com/${repoOwner}/${repoName}") }
            )
        }

        if (showUpdateDialog && latestVersion != null) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = { Text("Update Available") },
                text = { Text("A new version $latestVersion is available!") },
                confirmButton = {
                    Button(onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/${repoOwner}/${repoName}/releases/latest".toUri()
                        )
                        context.startActivity(intent)
                        showUpdateDialog = false
                    }) {
                        Text("Update")
                    }
                },
                dismissButton = {
                    Button(onClick = { showUpdateDialog = false }) {
                        Text("Later")
                    }
                }
            )
        }
    }
}

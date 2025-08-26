package ai.automagen.smsrelay.ui.screens.settings

import ai.automagen.smsrelay.data.local.RemoteConfig
import ai.automagen.smsrelay.service.ForegroundService
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import android.util.Patterns
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(drawerState: DrawerState, settingsViewModel: SettingsViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    val remotes by settingsViewModel.remotes.observeAsState(initial = emptyList())
    val showDeleteConfirmationDialog by settingsViewModel.showDeleteConfirmationDialog.observeAsState(
        false
    )
    val snackbarMessage by settingsViewModel.snackbarMessage.observeAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            settingsViewModel.consumeSnackbarMessage()
        }
    }

    var editingRemote by remember { mutableStateOf<RemoteConfig?>(null) }
    var addingRemote by remember { mutableStateOf<RemoteConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") }, navigationIcon = {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                addingRemote = RemoteConfig()
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Remote")
            }
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text("Manage Remotes", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (remotes.isEmpty()) {
                item {
                    Text("No remotes configured.\nClick the '+' button to add one.")
                }
            } else {
                itemsIndexed(remotes) { _, remote ->
                    RemoteConfigItem(
                        remote = remote,
                        onEdit = { editingRemote = remote },
                        onDelete = { settingsViewModel.deleteRemote(remote.id) }
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text("General", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                val foregroundEnabled by settingsViewModel.foregroundNotificationEnabled.observeAsState(
                    true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Foreground Notification", fontWeight = FontWeight.Bold)
                        Text(
                            "Persistent notification when app is running.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = foregroundEnabled,
                        onCheckedChange = { checked ->
                            settingsViewModel.setForegroundNotificationEnabled(checked)
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                    != PackageManager.PERMISSION_GRANTED
                                ) {
                                    ActivityCompat.requestPermissions(
                                        context as Activity,
                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                        101
                                    )
                                }
                            }

                            if (checked) {
                                val intent = Intent(context, ForegroundService::class.java).apply {
                                    action = "START"
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            } else {
                                val intent = Intent(context, ForegroundService::class.java).apply {
                                    action = "STOP"
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            }
                        }
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text("Data Management", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { settingsViewModel.onDeleteHistoryClicked() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete SMS Forwarding History")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirmationDialog) {
        DeleteConfirmationDialog(
            onConfirm = { settingsViewModel.confirmDeleteHistory() },
            onDismiss = { settingsViewModel.dismissDeleteConfirmationDialog() }
        )
    }

    editingRemote?.let { remoteToEdit ->
        EditRemoteDialog(
            remoteConfig = remoteToEdit,
            onDismiss = { editingRemote = null },
            onSave = { updatedConfig ->
                settingsViewModel.updateRemote(updatedConfig)
                editingRemote = null
            }
        )
    }


    addingRemote?.let { remoteToAdd ->
        EditRemoteDialog(
            remoteConfig = remoteToAdd,
            onDismiss = { addingRemote = null },
            onSave = { updatedConfig ->
                settingsViewModel.addRemote(updatedConfig)
                addingRemote = null
            }
        )
    }
}

@Composable
fun RemoteConfigItem(
    remote: RemoteConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(remote.name, fontWeight = FontWeight.Bold)
                Text(
                    "URL: ${remote.url.take(30)}${if (remote.url.length > 30) "..." else ""}",
                    fontSize = 14.sp
                )
                Text(
                    "Filter: ${remote.regexFilter.take(30)}${if (remote.regexFilter.length > 30) "..." else ""}",
                    fontSize = 14.sp
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Remote")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Remote")
                }
            }
        }
    }
}


@Composable
fun EditRemoteDialog(
    remoteConfig: RemoteConfig,
    onDismiss: () -> Unit,
    onSave: (RemoteConfig) -> Unit
) {
    var name by remember { mutableStateOf(remoteConfig.name) }
    var url by remember { mutableStateOf(remoteConfig.url) }
    var regexFilter by remember { mutableStateOf(remoteConfig.regexFilter) }
    var pushFields by remember { mutableStateOf(remoteConfig.pushFields) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var regexError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        var valid = true

        // Validate name
        if (name.isBlank()) {
            nameError = "Name cannot be empty"
            valid = false
        } else {
            nameError = null
        }

        // Validate URL
        if (url.isBlank()) {
            urlError = "URL cannot be empty"
            valid = false
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            urlError = "URL must start with http:// or https://"
            valid = false
        } else if (!Patterns.WEB_URL.matcher(url).matches()) {
            urlError = "Invalid URL format"
            valid = false
        } else {
            urlError = null
        }

        // Validate regex filter (if provided)
        if (regexFilter.isNotBlank()) {
            try {
                Pattern.compile(regexFilter)
                regexError = null
            } catch (e: PatternSyntaxException) {
                regexError = "Invalid regex pattern"
                valid = false
            }
        } else {
            regexError = null
        }

        return valid
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (remoteConfig.url.isEmpty()) "Add New Remote" else "Edit Remote") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Remote Name (e.g., Home Server)") },
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL Address") },
                    isError = urlError != null,
                    supportingText = { urlError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = regexFilter,
                    onValueChange = { regexFilter = it },
                    label = { Text("SMS Body Filter (Optional)") },
                    placeholder = { Text("e.g., ^OTP: \\d{6}$") },
                    isError = regexError != null,
                    supportingText = { regexError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pushFields,
                    onValueChange = { pushFields = it },
                    label = { Text("POST Body Fields (JSON)") },
                    placeholder = { Text("{\n  \"message\": \"{sms_body}\",\n ...\n}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 200.dp),
                    singleLine = false
                )
                Text(
                    "Use {sms_body}, {sms_sender}, {sms_timestamp}, {sms_checksum} as placeholders in POST body.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validate()) {
                        onSave(
                            remoteConfig.copy(
                                name = name,
                                url = url,
                                regexFilter = regexFilter,
                                pushFields = pushFields
                            )
                        )
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Deletion") },
        text = { Text("Are you sure you want to delete all SMS forwarding history? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
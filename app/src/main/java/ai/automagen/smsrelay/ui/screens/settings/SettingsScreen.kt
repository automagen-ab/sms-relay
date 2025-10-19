package ai.automagen.smsrelay.ui.screens.settings

import ai.automagen.smsrelay.data.JsonDataParser
import ai.automagen.smsrelay.data.local.RemoteConfig
import ai.automagen.smsrelay.service.ForegroundService
import ai.automagen.smsrelay.ui.components.JsonHighlightTextField
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.text.TextStyle


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    drawerState: DrawerState,
    deepLinkJson: String? = null,
    settingsViewModel: SettingsViewModel = viewModel()
) {
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

    // --- State Management for Bottom Sheet ---
    var currentRemote by remember { mutableStateOf<RemoteConfig?>(null) }
    var isSheetOpen by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    fun openAddSheet(remote: RemoteConfig? = RemoteConfig()) {
        currentRemote = remote
        isSheetOpen = true
        isEditing = false
    }

    fun openEditSheet(remote: RemoteConfig) {
        currentRemote = remote
        isSheetOpen = true
        isEditing = true
    }

    LaunchedEffect(deepLinkJson) {
        deepLinkJson?.let { encodedJson ->
            val remote = JsonDataParser.parseSingleRemoteFromUri(encodedJson)
            if (remote != null) {
                // Open the bottom sheet pre-filled
                openAddSheet(remote)
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Invalid deep link data. Could not import remote.")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") }, navigationIcon = {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },actions = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import Remote from Clipboard") },
                            onClick = {
                                settingsViewModel.importRemotesFromClipboard()
                                showMenu = false
                            }
                        )
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                openAddSheet()
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
                items(remotes) { remote ->
                    RemoteConfigItem(
                        remote = remote,
                        onEdit = { openEditSheet(remote) },
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

    // --- Bottom Sheet Invocation ---
    if (isSheetOpen) {
        currentRemote?.let { remote ->
            EditRemoteSheet(
                remoteConfig = remote,
                onDismiss = { isSheetOpen = false },
                onSave = { updatedConfig ->
                    if (isEditing) {
                        settingsViewModel.updateRemote(updatedConfig)
                    } else {
                        settingsViewModel.addRemote(updatedConfig)
                    }
                    isSheetOpen = false
                },
                isNewRemote = !isEditing,
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRemoteSheet(
    remoteConfig: RemoteConfig,
    onDismiss: () -> Unit,
    onSave: (RemoteConfig) -> Unit,
    isNewRemote: Boolean = false,
) {
    val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(remoteConfig.id) { mutableStateOf(remoteConfig.name) }
    var regexFilter by remember(remoteConfig.id) { mutableStateOf(remoteConfig.regexFilter) }
    var method by remember(remoteConfig.id) { mutableStateOf(remoteConfig.method) }
    var url by remember(remoteConfig.id) { mutableStateOf(remoteConfig.url) }
    var useFormData by remember(remoteConfig.id) { mutableStateOf(remoteConfig.useFormData) }
    var formDataParameters by remember(remoteConfig.id) { mutableStateOf(remoteConfig.formDataParameters.map { it.first to it.second }) }
    var postJsonBody by remember(remoteConfig) { mutableStateOf(remoteConfig.postJsonBody) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var regexError by remember { mutableStateOf<String?>(null) }
    var postJsonBodyError by remember { mutableStateOf<String?>(null) }
    var isJsonValid by remember { mutableStateOf(true) }

    val placeholders = listOf("{sms_body}", "{sms_sender}", "{sms_timestamp}", "{sms_checksum}")

    fun validate(): Boolean {
        var valid = true
        nameError = if (name.isBlank()) "Name cannot be empty".also { valid = false } else null

        urlError = when {
            url.isBlank() -> "URL cannot be empty".also { valid = false }
            !url.startsWith("http://") && !url.startsWith("https://") -> "URL must start with http:// or https://".also {
                valid = false
            }

            !Patterns.WEB_URL.matcher(url).matches() -> "Invalid URL format".also { valid = false }
            else -> null
        }

        regexError = if (regexFilter.isNotBlank()) {
            try {
                Pattern.compile(regexFilter); null
            } catch (e: PatternSyntaxException) {
                "Invalid regex pattern".also { valid = false }
            }
        } else null
        return valid
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalSheetState,
        // The modifier on ModalBottomSheet itself is for a different purpose (like padding from window insets).
        // To control the content's height, modify its container inside.
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        // Apply height constraint and other modifiers to the content's parent container.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            item {
                Text(
                    if (isNewRemote) "Add New Remote" else "Edit Remote",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Remote Name (e.g., Home Server)") },
                    isError = nameError != null,
                    supportingText = {
                        nameError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = regexFilter,
                    onValueChange = { regexFilter = it },
                    label = { Text("SMS Body Filter (Optional)") },
                    placeholder = { Text("e.g., ^OTP: \\d{6}$") },
                    isError = regexError != null,
                    supportingText = {
                        regexError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var methodMenuExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = methodMenuExpanded,
                        onExpandedChange = { methodMenuExpanded = !methodMenuExpanded },
                        modifier = Modifier.weight(0.3f)
                    ) {
                        OutlinedTextField(
                            value = method,
                            textStyle = TextStyle(fontSize = 14.sp),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Method") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodMenuExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = methodMenuExpanded,
                            onDismissRequest = { methodMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("POST") },
                                onClick = {
                                    method = "POST"
                                    methodMenuExpanded = false
                                })
                            DropdownMenuItem(
                                text = { Text("GET") },
                                onClick = {
                                    method = "GET"
                                    methodMenuExpanded = false
                                    useFormData = true
                                })
                        }
                    }

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL Address") },
                        isError = urlError != null,
                        supportingText = {
                            urlError?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier.weight(0.7f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (method == "POST" && !useFormData) "POST Body (JSON)" else "Form Data",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (method == "POST") {
                        TextButton(onClick = { useFormData = !useFormData }) {
                            Text(if (useFormData) "Use JSON" else "Use form data")
                        }
                    }

                    if (useFormData) {
                        TextButton(onClick = {
                            formDataParameters = formDataParameters + ("" to "")
                        }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
            }

            if (method == "POST" && !useFormData) {
                item {
                    JsonHighlightTextField(
                        label = { Text("JSON Body") },
                        value = postJsonBody,
                        onValueChange = { postJsonBody = it },
                        isError = postJsonBodyError != null || (!isJsonValid && postJsonBody.isNotBlank()),
                        supportingText = {
                            if (!isJsonValid && postJsonBody.isNotBlank()) {
                                Text(
                                    "The provided text is not valid JSON.",
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                postJsonBodyError?.let {
                                    Text(
                                        it,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        onValidation = { isValid ->
                            isJsonValid = isValid
                        },
                    )
                }
            } else {
                itemsIndexed(formDataParameters) { index, pair ->
                    val currentValues = formDataParameters.map { it.second }
                    val availablePlaceholders =
                        placeholders.filter { it !in currentValues || it == pair.second }
                    var valueDropdownExpanded by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = pair.first, // Directly use the value from the list
                                onValueChange = { newKey ->
                                    val newList = formDataParameters.toMutableList()
                                    newList[index] = newKey to pair.second
                                    formDataParameters = newList
                                },
                                label = { Text("Key") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            ExposedDropdownMenuBox(
                                expanded = valueDropdownExpanded,
                                onExpandedChange = {
                                    if (availablePlaceholders.isNotEmpty()) {
                                        valueDropdownExpanded = !valueDropdownExpanded
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = pair.second, // Directly use the value from the list
                                    onValueChange = { newValue ->
                                        val newList = formDataParameters.toMutableList()
                                        newList[index] = pair.first to newValue
                                        formDataParameters = newList
                                    },
                                    label = { Text("Value") },
                                    trailingIcon = {
                                        if (availablePlaceholders.isNotEmpty()) {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = valueDropdownExpanded)
                                        }
                                    },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable),
                                    singleLine = true
                                )
                                if (availablePlaceholders.isNotEmpty()) {
                                    ExposedDropdownMenu(
                                        expanded = valueDropdownExpanded,
                                        onDismissRequest = { valueDropdownExpanded = false }) {
                                        availablePlaceholders.forEach { placeholder ->
                                            DropdownMenuItem(
                                                text = { Text(placeholder) },
                                                onClick = {
                                                    val newList = formDataParameters.toMutableList()
                                                    newList[index] = pair.first to placeholder
                                                    formDataParameters = newList
                                                    valueDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(onClick = {
                                formDataParameters =
                                    formDataParameters.toMutableList().also { it.removeAt(index) }
                            }) {
                                Icon(Icons.Default.Delete, "Delete Parameter")
                            }
                        }
                    }
                }
            }


            item {
                Text(
                    "Use {sms_body}, {sms_sender}, etc. as placeholders in URL or parameter values.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(24.dp)) // Extra space before buttons
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        if (validate()) {
                            onSave(
                                remoteConfig.copy(
                                    name = name,
                                    url = url,
                                    regexFilter = regexFilter,
                                    method = method,
                                    useFormData = useFormData,
                                    formDataParameters = formDataParameters,
                                    postJsonBody = postJsonBody
                                )
                            )
                        }
                    }) {
                        Text("Save")
                    }
                }
                Spacer(Modifier.height(32.dp)) // Space to push content up from nav bar
            }
        }
    }
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
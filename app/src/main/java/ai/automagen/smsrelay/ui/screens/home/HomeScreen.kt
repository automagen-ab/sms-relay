package ai.automagen.smsrelay.ui.screens.home

import ai.automagen.smsrelay.data.local.RemoteConfig
import ai.automagen.smsrelay.data.local.SmsLog
import ai.automagen.smsrelay.ui.components.SmsPermissionCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    drawerState: DrawerState,
    smsLogViewModel: SmsLogViewModel = viewModel(),
) {
    val scope = rememberCoroutineScope()
    val smsLogs by smsLogViewModel.getEntries().observeAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Automagen SMS Relay") }, navigationIcon = {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            }, actions = {
                val onSearchClick = {
                    // TODO: search
                }
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            })
        }, contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SmsPermissionCard()
            IgnoreBatteryOptimizationCard()
            if (smsLogs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "No SMS Logs to display\n Wait for new messages, or check your remote configurations and filters in settings.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), // Grayed out
                            textAlign = TextAlign.Center // Center text alignment
                        ),
                        modifier = Modifier.fillMaxWidth() // Allow text to wrap if long
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(smsLogs) { smsLog: SmsLog ->
                        var workInfo: WorkInfo? = null
                        if (smsLog.workRequestId != null) workInfo =
                            smsLogViewModel.getWorkInfoById(smsLog.workRequestId)
                                .observeAsState().value
                        val remoteConfig: RemoteConfig? =
                            smsLogViewModel.getRemoteConfigById(smsLog.remoteId)

                        SmsLogItem(
                            smsLog = smsLog,
                            workInfo = workInfo,
                            remoteConfig = remoteConfig,
                            onRetryClick = {
                                smsLogViewModel.retrySmsForwarding(smsLog)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmsLogItem(
    smsLog: SmsLog,
    workInfo: WorkInfo?,
    remoteConfig: RemoteConfig?,
    onRetryClick: () -> Unit
) {
    var showDetailsDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetailsDialog = true },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIndicator(workInfo?.state)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) { // Row for Sender and Arrow
                    Text(
                        text = smsLog.sender,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                        // Removed bottom padding to align better with potential icon
                    )
                    // Add Arrow and Remote Name if remoteConfig exists
                    if (remoteConfig != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forwarded to",
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = remoteConfig.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Text(
                    text = smsLog.messageBody,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp) // Add top padding if sender line is shorter
                )
            }
        }
    }

    if (showDetailsDialog) {
        SmsLogDetailsDialog(
            smsLog = smsLog,
            workInfo = workInfo,
            remoteConfig = remoteConfig,
            onDismiss = { showDetailsDialog = false },
            onRetryClick = {
                onRetryClick()
                showDetailsDialog = false
            }
        )
    }
}

@Composable
fun StatusIndicator(state: WorkInfo.State?) {
    val statusColor = when (state) {
        WorkInfo.State.ENQUEUED -> MaterialTheme.colorScheme.primary
        WorkInfo.State.RUNNING -> MaterialTheme.colorScheme.tertiary
        WorkInfo.State.SUCCEEDED -> Color(0xFF4CAF50) // Keep successful green distinct
        WorkInfo.State.FAILED -> MaterialTheme.colorScheme.error
        WorkInfo.State.BLOCKED -> MaterialTheme.colorScheme.secondary
        WorkInfo.State.CANCELLED -> Color.Gray
        else -> MaterialTheme.colorScheme.outline
    }

    if (state == WorkInfo.State.RUNNING) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = statusColor,
            strokeWidth = 2.dp
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Info, // Generic info, or choose specific icons per status
            contentDescription = "Status: ${state?.name ?: "Unknown"}",
            tint = statusColor,
            modifier = Modifier.size(24.dp)
        )
    }
}


@Composable
fun SmsLogDetailsDialog(
    smsLog: SmsLog,
    workInfo: WorkInfo?,
    remoteConfig: RemoteConfig?,
    onDismiss: () -> Unit,
    onRetryClick: () -> Unit
) {
    val formattedTime = remember(smsLog.smsTimestamp) {
        formatTimestamp(smsLog.smsTimestamp, "EEE, MMM d, yyyy HH:mm:ss")
    }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Email,
                    contentDescription = "Sender",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("SMS Details")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(bottom = 16.dp), // Add some padding at the bottom if content is long
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailItem(label = "From", value = smsLog.sender)
                DetailItem(label = "Received", value = formattedTime)
                Text(
                    text = "Message:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = smsLog.messageBody,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(8.dp)
                        .fillMaxWidth()
                )

                if (workInfo != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Forward:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val statusColor = when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> MaterialTheme.colorScheme.primary
                        WorkInfo.State.RUNNING -> MaterialTheme.colorScheme.tertiary
                        WorkInfo.State.SUCCEEDED -> Color(0xFF4CAF50)
                        WorkInfo.State.FAILED -> MaterialTheme.colorScheme.error
                        WorkInfo.State.BLOCKED -> MaterialTheme.colorScheme.secondary
                        WorkInfo.State.CANCELLED -> Color.Gray
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                statusColor.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Status Icon",
                            tint = statusColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "Status: ${workInfo.state.name.lowercase().replaceFirstChar { it.uppercaseChar() }}",
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (workInfo.state == WorkInfo.State.FAILED) {
                        val errorMessage = workInfo.outputData.getString("ERROR_MESSAGE_KEY")
                        val response = smsLog.lastResponse
                        Text(
                            text = "Details: ${errorMessage ?: response ?: "Unknown error"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            if (workInfo?.state == WorkInfo.State.FAILED) {
                Button(
                    onClick = onRetryClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Retry",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Retry")
                }
            }
        }
    )
}

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}


fun formatTimestamp(timestamp: Long, pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
    return try {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "Invalid Date"
    }
}

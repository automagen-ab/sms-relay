package ai.automagen.smsrelay.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SmsPermissionCard() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showSettingsButton by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        // Check if user denied permanently
        showSettingsButton = if (!isGranted) {
            activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it, Manifest.permission.RECEIVE_SMS
                )
            } ?: false
        } else false
    }

    // re-check permission on lifecycle resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED
                if (currentPermission != hasPermission) {
                    hasPermission = currentPermission
                    showSettingsButton = if (!currentPermission) {
                        activity?.let {
                            !ActivityCompat.shouldShowRequestPermissionRationale(
                                it, Manifest.permission.RECEIVE_SMS
                            )
                        } ?: false
                    } else false
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!hasPermission) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "SMS Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This app functions as an SMS forwarder. " + "Granting this permission allows the app to read incoming messages " + "and forward them according to your settings. ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                ) {
                    if (showSettingsButton) {
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            }, colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                contentColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text("Open Settings")
                        }
                    } else {
                        Button(
                            onClick = { launcher.launch(Manifest.permission.RECEIVE_SMS) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                contentColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
    }
}

package ai.automagen.smsrelay.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.automagen.smsrelay.ui.theme.BatteryGreen
import ai.automagen.smsrelay.ui.theme.BatteryGreenDark
import ai.automagen.smsrelay.ui.theme.BatteryGreenLight

@SuppressLint("BatteryLife")
@Composable
fun IgnoreBatteryOptimizationCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isIgnoringOptimizations by remember { mutableStateOf(true) }

    // re-check on lifecycle resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringOptimizations =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val pm = context.getSystemService(PowerManager::class.java)
                        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                    } else {
                        true
                    }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!isIgnoringOptimizations) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BatteryGreenLight),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Background Access Needed",
                    style = MaterialTheme.typography.titleLarge,
                    color = BatteryGreenDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To ensure reliable background operation, please allow this app " +
                            "to ignore battery optimizations. Otherwise, remote requests " +
                            "will fail when the device is idle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BatteryGreenDark
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent =
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BatteryGreen,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Allow Background Usage")
                    }
                }
            }
        }
    }
}

package ai.automagen.smsrelay.receiver

import ai.automagen.smsrelay.data.local.PreferencesManager
import ai.automagen.smsrelay.service.ForegroundService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PreferencesManager(context).isForegroundNotificationEnabled()) {
                val serviceIntent = Intent(context, ForegroundService::class.java).apply {
                    action = "START"
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}

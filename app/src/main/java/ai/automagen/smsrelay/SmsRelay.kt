package ai.automagen.smsrelay

import ai.automagen.smsrelay.data.local.PreferencesManager
import android.app.Application
import android.content.Context
import androidx.core.content.edit

class SmsRelay : Application() {

    override fun onCreate() {
        super.onCreate()
        checkAppUpdate()
    }

    private fun checkAppUpdate() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val lastVersionCode = prefs.getInt("last_version_code", -1)

        @Suppress("DEPRECATION")
        val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        } else {
            packageManager.getPackageInfo(packageName, 0).versionCode
        }

        val prefsManager = PreferencesManager(this)
        prefsManager.migrateRemoteConfigsFromV1ToV2()

        if (currentVersionCode != lastVersionCode) {
            // App installed or updated
            prefs.edit { putInt("last_version_code", currentVersionCode) }
        }
    }
}

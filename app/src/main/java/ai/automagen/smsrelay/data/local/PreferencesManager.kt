package ai.automagen.smsrelay.data.local

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferencesManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getRemoteConfigs(): List<RemoteConfig> {
        val json = sharedPreferences.getString("remote_configs", null)
        return try {
            if (json != null) {
                val type = object : TypeToken<List<RemoteConfig>>() {}.type
                gson.fromJson(json, type)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getRemoteConfigById(id: String): RemoteConfig? {
        return getRemoteConfigs().firstOrNull { it.id == id }
    }

    private fun saveRemoteConfigs(remotes: List<RemoteConfig>) {
        val json = gson.toJson(remotes)
        sharedPreferences.edit { putString("remote_configs", json) }
    }

    fun addRemote(newRemote: RemoteConfig): RemoteConfig {
        val updated = getRemoteConfigs().toMutableList()
        updated.add(newRemote)
        saveRemoteConfigs(updated)
        return newRemote
    }

    fun updateRemote(updatedRemote: RemoteConfig): RemoteConfig? {
        val updated = getRemoteConfigs().toMutableList()
        val index = updated.indexOfFirst { it.id == updatedRemote.id }
        return if (index != -1) {
            updated[index] = updatedRemote
            saveRemoteConfigs(updated)
            updatedRemote
        } else null
    }

    fun deleteRemote(remoteId: String): Boolean {
        val updated = getRemoteConfigs().filterNot { it.id == remoteId }
        val removed = updated.size != getRemoteConfigs().size
        saveRemoteConfigs(updated)
        return removed
    }
}

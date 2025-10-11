package ai.automagen.smsrelay.data.local

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.util.UUID

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
        val currentConfigs = getRemoteConfigs()
        val updated = currentConfigs.filterNot { it.id == remoteId }
        val removed = updated.size != currentConfigs.size
        if (removed) {
            saveRemoteConfigs(updated)
        }
        return removed
    }

    fun setForegroundNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("foreground_notification_enabled", enabled) }
    }

    fun isForegroundNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean("foreground_notification_enabled", false)
    }

    fun migrateRemoteConfigsFromV1ToV2() {
        val json = sharedPreferences.getString("remote_configs", null) ?: return

        // If the JSON already contains a 'version' field, we assume the data is already V2 or newer.
        if (json.contains("\"version\"")) {
            return
        }

        val jsonArray = try {
            gson.fromJson(json, JsonArray::class.java)
        } catch (e: JsonSyntaxException) {
            // Data is not a valid JSON array, so we can't migrate it.
            return
        }

        val migratedList = jsonArray.mapNotNull { element ->
            if (element !is JsonObject) return@mapNotNull null

            // This is a safety check; if an object somehow has a version, deserialize it normally.
            if (element.has("version")) {
                return@mapNotNull gson.fromJson(element, RemoteConfig::class.java)
            }

            val id = element.get("id")?.asString ?: UUID.randomUUID().toString()
            val name = element.get("name")?.asString ?: "New Remote"
            val url = element.get("url")?.asString ?: ""
            val regexFilter = element.get("regexFilter")?.asString ?: ""

            // The old 'pushFields' from V1 is migrated to 'postJsonBody' in V2.
            val postJsonBody = element.get("pushFields")?.asString ?: """
            {
              "message": "{sms_body}",
              "sender": "{sms_sender}",
              "timestamp": "{sms_timestamp}"
            }
            """.trimIndent()

            RemoteConfig(
                id = id,
                name = name,
                url = url,
                regexFilter = regexFilter,
                method = "POST", // Default for old configs
                useFormData = false, // Old configs used JSON body, so this is false
                formDataParameters = listOf( // Provide default form data parameters
                    "message" to "{sms_body}",
                    "sender" to "{sms_sender}",
                    "timestamp" to "{sms_timestamp}"
                ),
                postJsonBody = postJsonBody,
                version = 2
            )
        }

        // Save the newly migrated list back to SharedPreferences.
        saveRemoteConfigs(migratedList)
    }
}

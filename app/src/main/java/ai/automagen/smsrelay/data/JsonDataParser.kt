package ai.automagen.smsrelay.data

import ai.automagen.smsrelay.data.local.RemoteConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URLDecoder

object JsonDataParser {

    private val gson = Gson()

    /**
     * Parses a single remote config from a URL-encoded JSON string.
     */
    fun parseSingleRemoteFromUri(encodedJson: String): RemoteConfig? {
        return try {
            val decoded = URLDecoder.decode(encodedJson, "UTF-8")
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(decoded, type)

            // Merge with defaults to handle missing fields gracefully
            val defaultRemote = RemoteConfig()
            val rawParams = map["formDataParameters"]
            val formDataParams: List<Pair<String, String>> =
                if (rawParams is List<*>) {
                    rawParams.mapNotNull { entry ->
                        if (entry is Map<*, *>) {
                            val key = entry["key"] as? String
                            val value = entry["value"] as? String
                            if (key != null && value != null) key to value else null
                        } else null
                    }
                } else defaultRemote.formDataParameters

            defaultRemote.copy(
                id = map["id"] as? String ?: defaultRemote.id,
                name = map["name"] as? String ?: defaultRemote.name,
                regexFilter = map["regexFilter"] as? String ?: defaultRemote.regexFilter,
                method = map["method"] as? String ?: defaultRemote.method,
                url = map["url"] as? String ?: defaultRemote.url,
                useFormData = map["useFormData"] as? Boolean ?: defaultRemote.useFormData,
                formDataParameters = formDataParams,
                postJsonBody = map["postJsonBody"] as? String ?: defaultRemote.postJsonBody,
                version = (map["version"] as? Double)?.toInt() ?: defaultRemote.version
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

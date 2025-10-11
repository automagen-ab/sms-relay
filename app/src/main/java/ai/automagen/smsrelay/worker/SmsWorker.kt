package ai.automagen.smsrelay.worker

import ai.automagen.smsrelay.data.local.AppDatabase
import ai.automagen.smsrelay.data.local.PreferencesManager
import ai.automagen.smsrelay.data.local.SmsLog
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

class SmsWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SmsWorker"
    }

    private val preferencesManager = PreferencesManager(appContext)
    private val database = AppDatabase.getDatabase(applicationContext)
    private val smsLogDao = database.smsLogDao()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Background Worker"
            val descriptionText = "Shows notifications while forwarding SMS."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(TAG, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, TAG)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SMS Relay")
            .setContentText("Forwarding SMS messages...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return ForegroundInfo(1, notification)
    }

    override suspend fun doWork(): Result {
        val workRequestIdForLog = inputData.getString("work_request_id")
        if (workRequestIdForLog == null) {
            Log.d(TAG, "No work request id, Skipping work.")
            return Result.failure()
        }

        val smsLog = smsLogDao.getSmsLogByWorkRequestId(workRequestIdForLog)
        if (smsLog == null) {
            Log.d(TAG, "No SMS log found for work request ID: $workRequestIdForLog")
            return Result.failure()
        }

        // Load remote config from SharedPreferences
        val remote = preferencesManager.getRemoteConfigById(smsLog.remoteId)
        if (remote == null) {
            Log.d(TAG, "No remote configuration found for ID: ${smsLog.remoteId}")
            return Result.failure()
        }

        var overallSuccess = false
        var response: String?
        try {
            // Replace placeholders in URL
            val url = replacePlaceholders(remote.url, smsLog, encode = true)


            when (remote.method.uppercase()) {
                "GET" -> {
                    val urlWithParams = buildUrlWithParams(url, remote.formDataParameters, smsLog)
                    response = sendGet(urlWithParams)
                }
                "POST" -> {
                    if (remote.useFormData) {
                        val formData = buildFormData(remote.formDataParameters, smsLog)
                        response = sendPost(url, formData, "application/x-www-form-urlencoded; charset=utf-8")
                    } else {
                        val postBody = buildJsonBody(remote.postJsonBody, smsLog)
                        response = sendPost(url, postBody, "application/json; charset=utf-8")
                    }
                }
                else -> throw IllegalArgumentException("Unsupported method: ${remote.method}")
            }
            Log.d(TAG, "Forwarded SMS to ${remote.name} successfully.")
            overallSuccess = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to forward SMS to ${remote.name}: ${e.message}")
            response = "Failed to forward SMS to ${remote.name}: ${e.message}"
        }

        val retryCount = runAttemptCount
        withContext(Dispatchers.IO) {
            val status =
                if (overallSuccess) "SUCCESS" else if (retryCount >= 3) "FAILED" else "RETRYING"
            smsLogDao.updateSmsLogStatusByWorkRequestId(
                workRequestIdForLog, status, response, System.currentTimeMillis()
            )
        }

        return when {
            overallSuccess -> Result.success()
            retryCount >= 3 -> Result.failure()
            else -> Result.retry()
        }
    }

    @Suppress("IMPLICIT_CAST_TO_ANY")
    private fun buildJsonBody(jsonTemplate: String, smsLog: SmsLog): String {
        try {
            // Try to parse as valid JSON and recursively replace values
            val json = if (jsonTemplate.trim().startsWith("[")) {
                JSONArray(jsonTemplate)
            } else {
                JSONObject(jsonTemplate)
            }
            replacePlaceholdersInJson(json, smsLog)
            return json.toString()
        } catch (e: JSONException) {
            // Fallback to old method if JSON is invalid
            Log.w(TAG, "Invalid JSON template. Falling back to simple string replacement.")
            return jsonTemplate
                .replace("{sms_body}", jsonEscapeWithoutQuotes(smsLog.messageBody))
                .replace("{sms_sender}", jsonEscapeWithoutQuotes(smsLog.sender))
                .replace("{sms_timestamp}", jsonEscapeWithoutQuotes(smsLog.smsTimestamp.toString()))
                .replace("{sms_checksum}", jsonEscapeWithoutQuotes(smsLog.messageChecksum))
        }
    }

    private fun replacePlaceholdersInJson(json: Any, smsLog: SmsLog) {
        when (json) {
            is JSONObject -> {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.opt(key)
                    if (value is String) {
                        json.put(key, replacePlaceholders(value, smsLog, encode = false))
                    } else if (value is JSONObject || value is JSONArray) {
                        replacePlaceholdersInJson(value, smsLog)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    val value = json.opt(i)
                    if (value is String) {
                        json.put(i, replacePlaceholders(value, smsLog, encode = false))
                    } else if (value is JSONObject || value is JSONArray) {
                        replacePlaceholdersInJson(value, smsLog)
                    }
                }
            }
        }
    }


    private fun replacePlaceholders(input: String, smsLog: SmsLog, encode: Boolean): String {
        val body = smsLog.messageBody
        val sender = smsLog.sender
        val timestamp = smsLog.smsTimestamp.toString()
        val checksum = smsLog.messageChecksum

        var result = input
        if (input.contains("{sms_body}"))
            result = result.replace("{sms_body}", if (encode) URLEncoder.encode(body, "UTF-8") else body)
        if (input.contains("{sms_sender}"))
            result = result.replace("{sms_sender}", if (encode) URLEncoder.encode(sender, "UTF-8") else sender)
        if (input.contains("{sms_timestamp}"))
            result = result.replace("{sms_timestamp}", if (encode) URLEncoder.encode(timestamp, "UTF-8") else timestamp)
        if (input.contains("{sms_checksum}"))
            result = result.replace("{sms_checksum}", if (encode) URLEncoder.encode(checksum, "UTF-8") else checksum)
        return result
    }

    private fun buildUrlWithParams(baseUrl: String, params: List<Pair<String, String>>, smsLog: SmsLog): String {
        val queryString = params.joinToString("&") { (key, value) ->
            val finalValue = replacePlaceholders(value, smsLog, encode = true)
            "${URLEncoder.encode(key, "UTF-8")}=${finalValue}"
        }
        return if (queryString.isNotEmpty()) "$baseUrl?$queryString" else baseUrl
    }

    private fun buildFormData(params: List<Pair<String, String>>, smsLog: SmsLog): String {
        return params.joinToString("&") { (key, value) ->
            val finalValue = replacePlaceholders(value, smsLog, encode = true)
            "${URLEncoder.encode(key, "UTF-8")}=${finalValue}"
        }
    }

    private fun sendGet(urlString: String): String {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 10000
        }

        try {
            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

            if (responseCode !in 200..299) {
                throw Exception("Server error: $responseCode $responseMessage. Response: $response")
            }
            Log.d(TAG, "Server response: $response")
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun sendPost(urlString: String, body: String, contentType: String): String {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 10000
        }

        try {
            OutputStreamWriter(connection.outputStream).use { it.write(body) }

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

            if (responseCode !in 200..299) {
                throw Exception("Server error: $responseCode $responseMessage. Response: $response")
            }

            Log.d(TAG, "Server response: $response")
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun jsonEscapeWithoutQuotes(text: String): String {
        val quoted = JSONObject.quote(text)
        return if (quoted.length >= 2 && quoted.startsWith("\"") && quoted.endsWith("\"")) {
            quoted.substring(1, quoted.length - 1)
        } else {
            quoted
        }
    }
}

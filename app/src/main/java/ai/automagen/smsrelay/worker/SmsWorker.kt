package ai.automagen.smsrelay.worker

import ai.automagen.smsrelay.data.local.AppDatabase
import ai.automagen.smsrelay.data.local.PreferencesManager
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
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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
        var response: String? = null
        try {
            val postBody =
                remote.pushFields.replace("{sms_body}", jsonEscapeWithoutQuotes(smsLog.messageBody))
                    .replace("{sms_sender}", jsonEscapeWithoutQuotes(smsLog.sender))
                    .replace(
                        "{sms_timestamp}",
                        jsonEscapeWithoutQuotes(smsLog.smsTimestamp.toString())
                    )
                    .replace("{sms_checksum}", jsonEscapeWithoutQuotes(smsLog.messageChecksum))

            // Send POST request
            response = sendPost(remote.url, postBody)
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

    private fun sendPost(urlString: String, jsonBody: String): String? {
        var connection: HttpURLConnection? = null
        val response: String?
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 10000
            }

            OutputStreamWriter(connection.outputStream).use { it.write(jsonBody) }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
                throw Exception("Server error: ${connection.responseMessage}")
            }

            response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "Server response: $response")
        } finally {
            connection?.disconnect()
        }

        return response
    }

    fun jsonEscapeWithoutQuotes(text: String): String {
        val quoted = JSONObject.quote(text)
        return if (quoted.length >= 2 && quoted.startsWith("\"") && quoted.endsWith("\"")) {
            quoted.substring(1, quoted.length - 1)
        } else {
            quoted
        }
    }
}

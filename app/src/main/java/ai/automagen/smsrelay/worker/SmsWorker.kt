package ai.automagen.smsrelay.worker

import ai.automagen.smsrelay.data.local.AppDatabase
import ai.automagen.smsrelay.data.local.PreferencesManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            val postBody = remote.pushFields.replace("{sms_body}", smsLog.messageBody)
                .replace("{sms_sender}", smsLog.sender)
                .replace("{sms_timestamp}", smsLog.smsTimestamp.toString())
                .replace("{sms_checksum}", smsLog.messageChecksum)

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
                if (overallSuccess) "SUCCESS" else if (retryCount >= 5) "FAILED" else "RETRYING"
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
}

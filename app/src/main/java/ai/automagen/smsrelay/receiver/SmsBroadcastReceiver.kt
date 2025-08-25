package ai.automagen.smsrelay.receiver

import ai.automagen.smsrelay.data.local.AppDatabase
import ai.automagen.smsrelay.data.local.PreferencesManager
import ai.automagen.smsrelay.data.local.RemoteConfig
import ai.automagen.smsrelay.data.local.SmsLog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ai.automagen.smsrelay.worker.SmsWorker
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import java.security.MessageDigest

class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "SmsReceiver"
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val preferencesManager = PreferencesManager(context.applicationContext)

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNotEmpty()) {
                val sender = messages[0].originatingAddress
                val timestamp = messages[0].timestampMillis

                // Concatenate all message parts
                val fullMessageBody = messages.joinToString(separator = "") { it.messageBody }
                val messageBodyChecksumBytes =
                    MessageDigest.getInstance("MD5").digest(fullMessageBody.toByteArray())
                val messageBodyChecksum =
                    messageBodyChecksumBytes.joinToString("") { "%02x".format(it) }

                Log.d(TAG, "SMS Received:")
                Log.d(TAG, "  Sender: $sender")
                Log.d(TAG, "  Body: $fullMessageBody")
                Log.d(TAG, "  Timestamp: $timestamp")

                val remoteConfigs: List<RemoteConfig> = preferencesManager.getRemoteConfigs()
                if (remoteConfigs.isEmpty()) {
                    Log.d(TAG, "No remote configurations found. Skipping forwarding.")
                    return
                }


                val matchingRemotes = remoteConfigs.filter { remote ->
                    if (remote.regexFilter.isNotBlank()) {
                        val regex = Regex(remote.regexFilter)
                        val matches = regex.containsMatchIn(fullMessageBody)
                        if (!matches) {
                            Log.d(
                                TAG,
                                "SMS did not match regex for ${remote.name}: ${remote.regexFilter}"
                            )
                        }
                        matches
                    } else {
                        true
                    }
                }

                if (matchingRemotes.isEmpty()) {
                    Log.d(
                        TAG, "No remote configurations found. Skipping forwarding."
                    )
                    return
                }

                val database = AppDatabase.getDatabase(context.applicationContext)
                val smsLogDao = database.smsLogDao()
                GlobalScope.launch {
                    for (remote in matchingRemotes) {
                        val workRequestId = UUID.randomUUID().toString()
                        val newSmsLog = SmsLog(
                            workRequestId = workRequestId,
                            remoteId = remote.id,
                            sender = sender ?: "Unknown Sender",
                            messageBody = fullMessageBody,
                            messageChecksum = messageBodyChecksum,
                            smsTimestamp = timestamp,
                            forwardingStatus = "PENDING"
                        )
                        val newRowId = smsLogDao.insertSmsLog(newSmsLog)
                        Log.d(
                            TAG,
                            "SMS logged to Room with ID: $newRowId and WorkRequest ID: $workRequestId"
                        )

                        val data =
                            Data.Builder().putString("work_request_id", workRequestId).build()

                        val workRequest =
                            OneTimeWorkRequestBuilder<SmsWorker>()
                                .setInputData(data)
                                .addTag(TAG)
                                .setId(UUID.fromString(workRequestId))
                                .setConstraints(
                                    Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                        .build()
                                )
                                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                                .build()
                        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
                    }
                }
            }
        }
    }
}
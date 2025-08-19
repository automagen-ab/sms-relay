package ai.automagen.smsrelay.ui.screens.home

import ai.automagen.smsrelay.data.local.AppDatabase
import ai.automagen.smsrelay.data.local.RemoteConfig
import ai.automagen.smsrelay.data.local.SmsLog
import ai.automagen.smsrelay.data.local.SmsLogDao
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID
import ai.automagen.smsrelay.data.local.PreferencesManager
import ai.automagen.smsrelay.receiver.SmsReceiver.Companion.TAG
import ai.automagen.smsrelay.worker.SmsWorker
import androidx.lifecycle.application
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import android.util.Log

class SmsLogViewModel(application: Application) : AndroidViewModel(application) {
    private val smsLogDao: SmsLogDao
    private val workManager = WorkManager.getInstance(application)
    private val preferencesManager = PreferencesManager(application)

    init {
        val database = AppDatabase.getDatabase(application)
        smsLogDao = database.smsLogDao()
    }

    fun getEntries(): LiveData<List<SmsLog>> {
        return smsLogDao.getAllSmsLogs()
    }

    fun getWorkInfoById(id: String): LiveData<WorkInfo?> {
        return workManager.getWorkInfoByIdLiveData(UUID.fromString(id))
    }

    fun getRemoteConfigById(id: String): RemoteConfig? {
        return preferencesManager.getRemoteConfigById(id)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun retrySmsForwarding(smsLog: SmsLog) {
        GlobalScope.launch {
            val workRequestId = UUID.randomUUID().toString()
            val newSmsLog = SmsLog(
                workRequestId = workRequestId,
                remoteId = smsLog.remoteId,
                sender = smsLog.sender,
                messageBody = smsLog.messageBody,
                messageChecksum = smsLog.messageChecksum,
                smsTimestamp = smsLog.smsTimestamp,
                forwardingStatus = "PENDING"
            )
            val newRowId = smsLogDao.insertSmsLog(newSmsLog)
            Log.d(
                TAG,
                "Retry SMS logged to Room with ID: $newRowId and WorkRequest ID: $workRequestId"
            )

            val data =
                Data.Builder().putString("work_request_id", workRequestId).build()

            val workRequest =
                OneTimeWorkRequestBuilder<SmsWorker>().setInputData(data).addTag(TAG)
                    .setId(UUID.fromString(workRequestId)).build()

            WorkManager.getInstance(application).enqueue(workRequest)
        }
    }
}

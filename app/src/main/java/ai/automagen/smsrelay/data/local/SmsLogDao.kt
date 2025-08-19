package ai.automagen.smsrelay.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SmsLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsLog(smsLog: SmsLog): Long

    @Update
    suspend fun updateSmsLog(smsLog: SmsLog)

    @Query("SELECT * FROM sms_logs WHERE id = :id")
    fun getSmsLogById(id: Long): LiveData<SmsLog?>

    @Query("SELECT * FROM sms_logs WHERE work_request_id = :workRequestId")
    suspend fun getSmsLogByWorkRequestId(workRequestId: String): SmsLog?

    @Query("SELECT * FROM sms_logs ORDER BY creation_timestamp DESC")
    fun getAllSmsLogs(): LiveData<List<SmsLog>>

    @Query("SELECT * FROM sms_logs WHERE forwarding_status = :status ORDER BY sms_timestamp DESC")
    fun getSmsLogsByStatus(status: String): LiveData<List<SmsLog>>

    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteSmsLogById(id: Long)

    @Query("UPDATE sms_logs SET forwarding_status = :newStatus, last_response = :response, last_attempt_timestamp = :attemptTime WHERE work_request_id = :workRequestId")
    suspend fun updateSmsLogStatusByWorkRequestId(
        workRequestId: String,
        newStatus: String,
        response: String?,
        attemptTime: Long
    )

    @Query("DELETE FROM sms_logs")
    suspend fun deleteAllSmsLogs()
}

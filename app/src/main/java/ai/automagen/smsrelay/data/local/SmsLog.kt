package ai.automagen.smsrelay.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "sms_logs")
data class SmsLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "work_request_id")
    val workRequestId: String?, // link to WorkManager request

    @ColumnInfo(name = "remote_id")
    var remoteId: String,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "message_body")
    val messageBody: String,

    @ColumnInfo(name = "message_checksum")
    val messageChecksum: String,

    @ColumnInfo(name = "sms_timestamp")
    val smsTimestamp: Long, // Original SMS timestamp

    @ColumnInfo(name = "forwarding_status")
    var forwardingStatus: String = "PENDING", // PENDING, SUCCESS, FAILED, RETRYING

    @ColumnInfo(name = "last_response")
    var lastResponse: String? = null,

    @ColumnInfo(name = "last_attempt_timestamp")
    var lastAttemptTimestamp: Long? = null,

    @ColumnInfo(name = "creation_timestamp")
    val creationTimestamp: Long = System.currentTimeMillis()
)

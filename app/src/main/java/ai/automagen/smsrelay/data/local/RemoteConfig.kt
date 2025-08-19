package ai.automagen.smsrelay.data.local

import java.util.UUID

data class RemoteConfig(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New Remote",
    var url: String = "",
    var regexFilter: String = "",
    var pushFields: String = """
        {
          "message": "{sms_body}",
          "sender": "{sms_sender}",
          "timestamp": "{sms_timestamp}"
        }
    """.trimIndent() // Default JSON structure
)

package ai.automagen.smsrelay.data.local

import java.util.UUID

data class RemoteConfig(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New Remote",
    var regexFilter: String = "",
    val method: String = "POST", // "GET" or "POST"
    var url: String = "",
    var useFormData: Boolean = true,
    val formDataParameters: List<Pair<String, String>> = listOf(
        "message" to "{sms_body}",
        "sender" to "{sms_sender}",
        "timestamp" to "{sms_timestamp}"
    ),
    var postJsonBody: String = """
        {
          "message": "{sms_body}",
          "sender": "{sms_sender}",
          "timestamp": "{sms_timestamp}"
        }
    """.trimIndent(),
    var version: Int = 2
)

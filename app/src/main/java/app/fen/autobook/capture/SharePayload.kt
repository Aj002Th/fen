package app.fen.autobook.capture

data class SharePayload(
    val action: String?,
    val type: String?,
    val streamUris: List<String>,
    val clipUris: List<String>,
    val dataUri: String?,
)

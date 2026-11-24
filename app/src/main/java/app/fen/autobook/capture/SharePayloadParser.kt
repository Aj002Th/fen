package app.fen.autobook.capture

object SharePayloadParser {
    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    private const val ACTION_VIEW = "android.intent.action.VIEW"

    fun firstImage(payload: SharePayload): String? {
        if (payload.type?.startsWith("image/") != true) return null
        return when (payload.action) {
            ACTION_SEND -> payload.streamUris.firstOrNull() ?: payload.clipUris.firstOrNull()
            ACTION_SEND_MULTIPLE -> payload.clipUris.firstOrNull() ?: payload.streamUris.firstOrNull()
            ACTION_VIEW -> payload.dataUri
            else -> payload.streamUris.firstOrNull()
                ?: payload.clipUris.firstOrNull()
                ?: payload.dataUri
        }
    }

    fun allImages(payload: SharePayload): List<String> {
        if (payload.type?.startsWith("image/") != true) return emptyList()
        val uris = when (payload.action) {
            ACTION_SEND -> payload.streamUris + payload.clipUris
            ACTION_SEND_MULTIPLE -> payload.clipUris + payload.streamUris
            ACTION_VIEW -> listOfNotNull(payload.dataUri)
            else -> payload.streamUris + payload.clipUris + listOfNotNull(payload.dataUri)
        }
        return uris.distinct()
    }
}

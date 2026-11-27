package app.fen.mcp.tools

import java.util.Base64

object Cursor {

    fun encode(occurredAt: Long, id: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$occurredAt|$id".toByteArray(Charsets.UTF_8))

    fun decode(raw: String): Pair<Long, String>? {
        val text = try {
            String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val separator = text.indexOf('|')
        if (separator <= 0 || separator == text.lastIndex) return null
        val at = text.substring(0, separator).toLongOrNull() ?: return null
        return at to text.substring(separator + 1)
    }
}

package app.fen.mcp.auth

import java.security.MessageDigest
import java.util.Base64

object TokenAuth {

    fun parseBearer(header: String?): String? {
        if (header == null) return null
        val parts = header.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size != 2) return null
        if (!parts[0].equals("Bearer", ignoreCase = true)) return null
        return parts[1].takeIf { it.isNotBlank() }
    }

    fun matches(expected: String, presented: String?): Boolean {
        if (presented.isNullOrEmpty()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            presented.toByteArray(Charsets.UTF_8),
        )
    }

    /** 审计里只存指纹，不存 token 原文。 */
    fun fingerprint(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(8)

    /** MCP 头部允许 =?base64?...?= 哨兵，工具名正常不会用到，但按规范解析。 */
    fun decodeHeaderValue(value: String): String? {
        if (!value.startsWith("=?base64?") || !value.endsWith("?=")) return value
        return try {
            val encoded = value.removePrefix("=?base64?").removeSuffix("?=")
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

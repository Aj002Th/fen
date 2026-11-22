package app.fen.mcp

import android.content.Context
import app.fen.BuildConfig
import java.security.SecureRandom
import java.util.Base64

class McpConfig(private val context: Context) {

    private val prefs = context.getSharedPreferences("fen_mcp", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var port: Int
        get() = prefs.getInt("port", defaultPort())
        set(value) = prefs.edit().putInt("port", value.coerceIn(1024, 65535)).apply()

    val token: String
        get() = prefs.getString("token", null) ?: newToken().also {
            prefs.edit().putString("token", it).apply()
        }

    var writeUntil: Long?
        get() = prefs.getLong("write_until", 0L).takeIf { it > 0L }
        set(value) = prefs.edit().putLong("write_until", value ?: 0L).apply()

    fun resetToken(): String = newToken().also {
        prefs.edit().putString("token", it).apply()
    }

    fun defaultPort(): Int = if (BuildConfig.DEBUG) {
        18067 + (kotlin.math.abs(context.packageName.hashCode()) % 900)
    } else {
        8067
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

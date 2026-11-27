package app.fen.mcp.protocol

import java.util.UUID

data class LegacySession(
    val id: String,
    val version: String,
    val client: ClientInfo?,
    val createdAt: Long,
    var lastSeen: Long,
)

class SessionStore(
    private val clock: () -> Long,
    private val ttlMs: Long = 30 * 60_000,
) {

    private val sessions = HashMap<String, LegacySession>()

    @Synchronized
    fun create(version: String, client: ClientInfo?): LegacySession {
        val now = clock()
        val session = LegacySession(
            id = UUID.randomUUID().toString(),
            version = version,
            client = client,
            createdAt = now,
            lastSeen = now,
        )
        sessions[session.id] = session
        return session
    }

    @Synchronized
    fun get(id: String): LegacySession? {
        val session = sessions[id] ?: return null
        val now = clock()
        if (now - session.lastSeen > ttlMs) {
            sessions.remove(id)
            return null
        }
        session.lastSeen = now
        return session
    }

    @Synchronized
    fun delete(id: String) {
        sessions.remove(id)
    }

    @Synchronized
    fun clear() {
        sessions.clear()
    }

    @Synchronized
    fun size(): Int = sessions.size
}

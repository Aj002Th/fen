package app.fen.mcp.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStoreTest {

    private var now = 1_000_000L
    private val store = SessionStore(clock = { now }, ttlMs = 1_000)

    @Test
    fun createsAndFindsSession() {
        val session = store.create(LEGACY_PROTOCOL_VERSION, ClientInfo("test", "1"))

        assertNotNull(store.get(session.id))
        assertEquals(1, store.size())
    }

    @Test
    fun expiresAfterTtl() {
        val session = store.create(LEGACY_PROTOCOL_VERSION, null)
        now += 1_001

        assertNull(store.get(session.id))
        assertEquals(0, store.size())
    }

    @Test
    fun accessRefreshesTtl() {
        val session = store.create(LEGACY_PROTOCOL_VERSION, null)
        now += 900
        assertNotNull(store.get(session.id))
        now += 900
        assertNotNull(store.get(session.id))
    }

    @Test
    fun clearInvalidatesAll() {
        val session = store.create(LEGACY_PROTOCOL_VERSION, null)
        store.clear()

        assertNull(store.get(session.id))
    }
}

package app.fen.mcp.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CursorTest {

    @Test
    fun roundTrips() {
        val encoded = Cursor.encode(1_700_000_000_000L, "tx-1")
        assertEquals(1_700_000_000_000L to "tx-1", Cursor.decode(encoded))
    }

    @Test
    fun rejectsMalformed() {
        assertNull(Cursor.decode("not-base64"))
        assertNull(Cursor.decode("aGVsbG8="))
        assertNull(Cursor.decode(""))
    }
}

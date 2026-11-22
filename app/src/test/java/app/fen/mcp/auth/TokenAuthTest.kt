package app.fen.mcp.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenAuthTest {

    @Test
    fun parsesBearerCaseInsensitively() {
        assertEquals("abc", TokenAuth.parseBearer("Bearer abc"))
        assertEquals("abc", TokenAuth.parseBearer("bearer   abc"))
        assertNull(TokenAuth.parseBearer("Basic abc"))
        assertNull(TokenAuth.parseBearer("Bearer"))
        assertNull(TokenAuth.parseBearer(null))
    }

    @Test
    fun comparesExactly() {
        assertTrue(TokenAuth.matches("token", "token"))
        assertFalse(TokenAuth.matches("token", "Token"))
        assertFalse(TokenAuth.matches("token", null))
        assertFalse(TokenAuth.matches("token", ""))
    }

    @Test
    fun fingerprintIsShortAndStable() {
        val fp = TokenAuth.fingerprint("token")
        assertEquals(8, fp.length)
        assertEquals(fp, TokenAuth.fingerprint("token"))
        assertFalse(fp.contains("token"))
    }

    @Test
    fun decodesBase64Sentinel() {
        assertEquals("get_overview", TokenAuth.decodeHeaderValue("=?base64?Z2V0X292ZXJ2aWV3?="))
        assertEquals("plain", TokenAuth.decodeHeaderValue("plain"))
        assertNull(TokenAuth.decodeHeaderValue("=?base64?%%%?="))
    }
}

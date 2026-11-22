package app.fen.autobook.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharePayloadParserTest {
    @Test
    fun `send returns first stream image`() {
        val payload = SharePayload(
            action = "android.intent.action.SEND",
            type = "image/png",
            streamUris = listOf("content://a"),
            clipUris = emptyList(),
            dataUri = null,
        )

        assertEquals("content://a", SharePayloadParser.firstImage(payload))
    }

    @Test
    fun `send multiple prefers clip uris`() {
        val payload = SharePayload(
            action = "android.intent.action.SEND_MULTIPLE",
            type = "image/*",
            streamUris = emptyList(),
            clipUris = listOf("content://a", "content://b"),
            dataUri = null,
        )

        assertEquals("content://a", SharePayloadParser.firstImage(payload))
    }

    @Test
    fun `view returns data uri`() {
        val payload = SharePayload(
            action = "android.intent.action.VIEW",
            type = "image/jpeg",
            streamUris = emptyList(),
            clipUris = emptyList(),
            dataUri = "content://v",
        )

        assertEquals("content://v", SharePayloadParser.firstImage(payload))
    }

    @Test
    fun `non image returns null`() {
        val payload = SharePayload(
            action = "android.intent.action.SEND",
            type = "text/plain",
            streamUris = listOf("content://a"),
            clipUris = emptyList(),
            dataUri = null,
        )

        assertNull(SharePayloadParser.firstImage(payload))
    }

    @Test
    fun `empty payload returns null`() {
        assertNull(
            SharePayloadParser.firstImage(
                SharePayload(null, null, emptyList(), emptyList(), null)
            )
        )
    }
}

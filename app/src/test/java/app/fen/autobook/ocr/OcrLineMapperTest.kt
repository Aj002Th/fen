package app.fen.autobook.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLineMapperTest {
    @Test
    fun `maps raw lines in order`() {
        val result = OcrLineMapper.map(
            listOf(
                RawOcrLine("微信支付", 0, 0, 100, 20, 0.9f),
                RawOcrLine("¥32.50", 0, 30, 100, 60, null),
            )
        )

        assertEquals(2, result.lines.size)
        assertEquals("微信支付\n¥32.50", result.fullText)
        assertEquals(20, result.lines[0].box.height)
        assertEquals(0.9f, result.lines[0].confidence!!, 0.001f)
    }

    @Test
    fun `empty input maps to empty result`() {
        assertTrue(OcrLineMapper.map(emptyList()).lines.isEmpty())
    }
}

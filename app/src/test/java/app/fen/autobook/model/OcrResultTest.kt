package app.fen.autobook.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrResultTest {
    @Test
    fun `joins non-empty lines`() {
        val result = OcrResult(
            listOf(
                line("微信支付"),
                line(" "),
                line("¥32.50"),
            )
        )

        assertEquals("微信支付\n¥32.50", result.fullText)
    }

    private fun line(text: String) = OcrLine(text, BoundingBox(0, 0, 100, 20), null)
}

package app.fen.autobook.ocr

import android.graphics.Bitmap
import app.fen.autobook.model.OcrResult

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): OcrResult
}

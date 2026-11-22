package app.fen.autobook.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.fen.autobook.classify.Classifier
import app.fen.autobook.model.ParsedBill
import app.fen.autobook.ocr.OcrEngine
import app.fen.autobook.pipeline.RecognitionEngine
import app.fen.data.Category
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

sealed interface ImageImportState {
    data object Idle : ImageImportState
    data object Loading : ImageImportState
    data class Ready(val bill: ParsedBill, val preview: Bitmap?) : ImageImportState
    data class ReadyBatch(val items: List<BatchItem>) : ImageImportState
    data class Error(val message: String) : ImageImportState
}

class ImageImportController(
    private val context: Context,
    private val ocr: OcrEngine,
    private val engine: RecognitionEngine,
    private val classifier: Classifier,
    private val categories: () -> List<Category>,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<ImageImportState>(ImageImportState.Idle)
    val state: StateFlow<ImageImportState> = _state.asStateFlow()

    fun submit(uri: Uri) {
        scope.launch {
            _state.value = ImageImportState.Loading
            try {
                val bill = recognize(uri) ?: error("无法读取图片")
                _state.value = ImageImportState.Ready(bill, null)
            } catch (error: Exception) {
                _state.value = ImageImportState.Error(error.message ?: "识别失败")
            }
        }
    }

    fun submitAll(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            _state.value = ImageImportState.Loading
            val items = mutableListOf<BatchItem>()
            for (uri in uris.take(MAX_BATCH)) {
                val bill = runCatching { recognize(uri) }.getOrNull()
                if (bill != null && bill.amountMinor > 0) {
                    items += BatchItem(bill, null)
                } else {
                    items += BatchItem(null, "未识别到金额")
                }
            }
            _state.value = ImageImportState.ReadyBatch(items)
        }
    }

    fun consume() {
        _state.value = ImageImportState.Idle
    }

    private suspend fun recognize(uri: Uri): ParsedBill? {
        val bitmap = decode(uri) ?: return null
        return try {
            val ocrResult = ocr.recognize(bitmap)
            val bill = engine.recognize(
                lines = ocrResult.lines,
                nowMillis = System.currentTimeMillis(),
                zone = ZoneId.systemDefault(),
            )
            classifier.apply(bill, categories())
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        const val MAX_EDGE = 2048
        const val MAX_BATCH = 9
    }
}

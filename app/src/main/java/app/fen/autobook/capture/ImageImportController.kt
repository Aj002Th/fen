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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.ZoneId

sealed interface ImageImportState {
    data object Idle : ImageImportState
    data class Loading(val completed: Int = 0, val total: Int = 0) : ImageImportState
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
    private var job: Job? = null
    private val _state = MutableStateFlow<ImageImportState>(ImageImportState.Idle)
    val state: StateFlow<ImageImportState> = _state.asStateFlow()

    fun submit(uri: Uri) {
        job?.cancel()
        job = scope.launch {
            _state.value = ImageImportState.Loading()
            try {
                val result = withTimeout(OCR_TIMEOUT_MS) { recognize(uri, keepBitmap = true) }
                    ?: error("无法读取图片")
                _state.value = ImageImportState.Ready(result.bill, result.bitmap)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = ImageImportState.Error("识别失败，请重试")
            }
        }
    }

    fun submitAll(uris: List<Uri>) {
        if (uris.isEmpty()) return
        job?.cancel()
        job = scope.launch {
            val limited = uris.take(MAX_BATCH)
            _state.value = ImageImportState.Loading(0, limited.size)
            val items = mutableListOf<BatchItem>()
            for ((index, uri) in limited.withIndex()) {
                val result = try {
                    withTimeout(OCR_TIMEOUT_MS) { recognize(uri, keepBitmap = false) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    null
                }
                val bill = result?.bill
                if (bill != null && bill.amountMinor > 0) {
                    items += BatchItem(bill, null)
                } else {
                    items += BatchItem(null, "未识别到金额")
                }
                _state.value = ImageImportState.Loading(index + 1, limited.size)
            }
            _state.value = ImageImportState.ReadyBatch(items)
        }
    }

    fun consume() {
        job?.cancel()
        job = null
        _state.value = ImageImportState.Idle
    }

    private data class Recognized(val bill: ParsedBill, val bitmap: Bitmap?)

    private suspend fun recognize(uri: Uri, keepBitmap: Boolean): Recognized? {
        val bitmap = decode(uri) ?: return null
        return try {
            val ocrResult = ocr.recognize(bitmap)
            val bill = engine.recognize(
                lines = ocrResult.lines,
                nowMillis = System.currentTimeMillis(),
                zone = ZoneId.systemDefault(),
            )
            val classified = classifier.apply(bill, categories())
            Recognized(classified, if (keepBitmap) bitmap else null)
        } finally {
            if (!keepBitmap) bitmap.recycle()
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
        const val OCR_TIMEOUT_MS = 10_000L
    }
}

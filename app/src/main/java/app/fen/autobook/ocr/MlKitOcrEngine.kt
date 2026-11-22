package app.fen.autobook.ocr

import android.graphics.Bitmap
import app.fen.autobook.model.OcrResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrEngine : OcrEngine {
    private val client = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognize(bitmap: Bitmap): OcrResult =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            client.process(image)
                .addOnSuccessListener { text ->
                    val raw = text.textBlocks
                        .flatMap { block -> block.lines }
                        .mapNotNull { line ->
                            val box = line.boundingBox ?: return@mapNotNull null
                            RawOcrLine(
                                text = line.text,
                                left = box.left,
                                top = box.top,
                                right = box.right,
                                bottom = box.bottom,
                                confidence = null,
                            )
                        }
                    continuation.resume(OcrLineMapper.map(raw))
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }
}

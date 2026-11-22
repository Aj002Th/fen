package app.fen.autobook.capture

import android.content.Intent
import android.net.Uri
import android.os.Build

object IntentImageReader {
    fun firstImage(intent: Intent): String? = SharePayloadParser.firstImage(toPayload(intent))

    fun toPayload(intent: Intent): SharePayload = SharePayload(
        action = intent.action,
        type = intent.type,
        streamUris = streamUris(intent),
        clipUris = clipUris(intent),
        dataUri = intent.data?.toString(),
    )

    private fun streamUris(intent: Intent): List<String> {
        val list: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        if (!list.isNullOrEmpty()) return list.map { it.toString() }

        val single: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return listOfNotNull(single?.toString())
    }

    private fun clipUris(intent: Intent): List<String> {
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { index ->
            clip.getItemAt(index).uri?.toString()
        }
    }
}

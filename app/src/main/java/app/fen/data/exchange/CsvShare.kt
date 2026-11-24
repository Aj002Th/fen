package app.fen.data.exchange

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** 把 CSV 写到 cache，再拉起系统分享。没有可处理的应用时返回 false。 */
object CsvShare {

    fun share(context: Context, fileName: String, content: String): Boolean {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            return false
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, fileName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, fileName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }
}

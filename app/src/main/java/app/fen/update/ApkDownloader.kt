package app.fen.update

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 下载并算好 SHA-256 的安装包。 */
data class DownloadedApk(val file: File, val sha256: String)

/** 清空更新缓存目录里的文件；目录不存在时不做任何事。 */
fun cleanUpdatesDir(dir: File) {
    val files = dir.listFiles() ?: return
    files.forEach { if (it.isFile) it.delete() }
}

/**
 * 把 release 里的 APK 下到 `cacheDir/updates/`。
 * 每次下载前清空目录，失败或取消时删除半成品。
 */
class ApkDownloader(
    private val updatesDir: File,
    private val userAgent: String,
) {

    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): Result<DownloadedApk> = withContext(Dispatchers.IO) {
        cleanUpdatesDir(updatesDir)
        updatesDir.mkdirs()
        val target = File(updatesDir, fileName)
        try {
            val connection = openConnection(url)
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    target.delete()
                    return@withContext Result.failure(IOException("下载失败（HTTP $code）"))
                }
                val total = connection.contentLengthLong.takeIf { it > 0 }
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
                Result.success(DownloadedApk(target, digestHex(digest.digest())))
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            target.delete()
            throw cancelled
        } catch (error: IOException) {
            target.delete()
            Result.failure(IOException("下载失败，请检查网络", error))
        }
    }

    /** 取 `.sha256` 文件里的哈希；拿不到时返回可读失败。 */
    suspend fun fetchExpectedSha256(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val connection = openConnection(url)
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    return@withContext Result.failure(
                        IOException("无法获取安装包校验值，请稍后重试"),
                    )
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val hash = parseSha256Text(text)
                    ?: return@withContext Result.failure(
                        IOException("无法获取安装包校验值，请稍后重试"),
                    )
                Result.success(hash)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            Result.failure(IOException("无法获取安装包校验值，请稍后重试", error))
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", userAgent)
        }
}

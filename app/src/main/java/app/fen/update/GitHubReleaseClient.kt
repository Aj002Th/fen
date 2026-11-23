package app.fen.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/Aj002Th/fen/releases/latest"

/** HTTP 状态码转成给用户看的一句话。 */
fun updateHttpMessage(code: Int): String = when (code) {
    403, 429 -> "GitHub 暂时限制了请求，请稍后再试"
    else -> "GitHub 返回异常（HTTP $code）"
}

/**
 * 读 GitHub Release。匿名请求，带 [userAgent]；
 * [sourceOverride] 非空时改请求该地址（仅调试验收用）。
 */
class GitHubReleaseClient(
    private val userAgent: String,
    private val sourceOverride: () -> String?,
) : ReleaseSource {

    override suspend fun fetchLatest(): Result<UpdateRelease> = withContext(Dispatchers.IO) {
        val url = sourceOverride()?.takeIf { it.isNotBlank() } ?: LATEST_RELEASE_URL
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", userAgent)
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IOException(updateHttpMessage(code)))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = ReleaseJson.parse(body)
                ?: return@withContext Result.failure(IOException("这个版本没有可安装的安装包"))
            Result.success(release)
        } catch (error: IOException) {
            Result.failure(IOException("网络连接失败，请检查网络", error))
        } finally {
            connection?.disconnect()
        }
    }
}

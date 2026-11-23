package app.fen.update

import app.fen.data.AppPrefs
import org.json.JSONArray
import org.json.JSONObject

/** 检查结果。`stale` 表示这次是凭上次的缓存顶上的。 */
sealed interface UpdateCheck {
    data class Available(val release: UpdateRelease, val stale: Boolean) : UpdateCheck
    data class UpToDate(val stale: Boolean) : UpdateCheck
    data class Failed(val message: String, val cached: UpdateRelease?) : UpdateCheck
}

/** 从 GitHub（或调试源）取最新 release。 */
interface ReleaseSource {
    suspend fun fetchLatest(): Result<UpdateRelease>
}

/** 检查器所需的持久化能力。 */
interface UpdateStore {
    var lastCheckAt: Long
    var cachedReleaseJson: String
}

/** 把检查结果落到现有偏好设置里。 */
class AppPrefsUpdateStore(private val prefs: AppPrefs) : UpdateStore {
    override var lastCheckAt: Long
        get() = prefs.lastUpdateCheckAt
        set(value) {
            prefs.lastUpdateCheckAt = value
        }

    override var cachedReleaseJson: String
        get() = prefs.cachedReleaseJson
        set(value) {
            prefs.cachedReleaseJson = value
        }
}

/**
 * 比较本地版本与远端 release：最多每 [THROTTLE_MS] 联网一次，
 * 失败时退回缓存，手动检查（force）总是联网。
 */
class UpdateChecker(
    private val source: ReleaseSource,
    private val store: UpdateStore,
    private val localVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    companion object {
        const val THROTTLE_MS = 3_600_000L
    }

    fun cached(): UpdateRelease? = store.cachedReleaseJson
        .takeIf { it.isNotBlank() }
        ?.let { ReleaseJson.parse(it) }

    suspend fun check(force: Boolean): UpdateCheck {
        val now = clock()
        val cachedRelease = cached()
        val fresh = force ||
            store.lastCheckAt == 0L ||
            now < store.lastCheckAt ||
            now - store.lastCheckAt >= THROTTLE_MS
        if (!fresh) {
            return if (cachedRelease != null && UpdateVersion.isNewer(cachedRelease.version, localVersion)) {
                UpdateCheck.Available(cachedRelease, stale = false)
            } else {
                UpdateCheck.UpToDate(stale = false)
            }
        }

        val result = source.fetchLatest()
        store.lastCheckAt = now
        return result.fold(
            onSuccess = { release ->
                store.cachedReleaseJson = serialize(release)
                if (UpdateVersion.isNewer(release.version, localVersion)) {
                    UpdateCheck.Available(release, stale = false)
                } else {
                    UpdateCheck.UpToDate(stale = false)
                }
            },
            onFailure = { error ->
                val message = error.message?.takeIf { it.isNotBlank() } ?: "检查更新失败"
                if (cachedRelease != null && UpdateVersion.isNewer(cachedRelease.version, localVersion)) {
                    UpdateCheck.Available(cachedRelease, stale = true)
                } else {
                    UpdateCheck.Failed(message, cachedRelease)
                }
            },
        )
    }

    /**
     * 缓存的是原始 release JSON，但 [ReleaseSource] 只回传解析后的模型，
     * 所以这里保存一份等价的最小 JSON，够 [cached] 还原。
     */
    private fun serialize(release: UpdateRelease): String {
        val assets = JSONArray()
        assets.put(
            JSONObject().apply {
                put("name", release.apk.name)
                put("browser_download_url", release.apk.url)
                put("size", release.apk.size)
                release.apk.digestSha256?.let { put("digest", "sha256:$it") }
            },
        )
        release.sha256FileUrl?.let { url ->
            assets.put(
                JSONObject().apply {
                    put("name", "Fen-${release.version}.apk.sha256")
                    put("browser_download_url", url)
                },
            )
        }
        return JSONObject().apply {
            put("tag_name", release.tag)
            put("body", release.notes)
            put("html_url", release.htmlUrl)
            put("assets", assets)
        }.toString()
    }
}

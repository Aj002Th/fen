package app.fen.update

import org.json.JSONException
import org.json.JSONObject

/** 把 GitHub `/releases/latest` 的 JSON 转成 [UpdateRelease]。 */
object ReleaseJson {

    private const val DIGEST_PREFIX = "sha256:"

    fun parse(json: String): UpdateRelease? = try {
        val root = JSONObject(json)
        val tag = root.optString("tag_name").trim()
        if (tag.isEmpty()) {
            null
        } else {
            val version = UpdateVersion.normalize(tag)
            val assets = root.optJSONArray("assets").toAssetList()
            val apk = pickApk(assets, version)
            if (apk == null) {
                null
            } else {
                UpdateRelease(
                    tag = tag,
                    version = version,
                    notes = root.optString("body").trim(),
                    htmlUrl = root.optString("html_url").trim(),
                    apk = apk,
                    sha256FileUrl = pickSha256(assets, version)?.url,
                )
            }
        }
    } catch (_: JSONException) {
        null
    }

    private fun org.json.JSONArray?.toAssetList(): List<UpdateAsset> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val asset = optJSONObject(index) ?: return@mapNotNull null
            val name = asset.optString("name").trim()
            val url = asset.optString("browser_download_url").trim()
            if (name.isEmpty() || url.isEmpty()) return@mapNotNull null
            UpdateAsset(
                name = name,
                url = url,
                size = asset.optLong("size", 0L),
                digestSha256 = parseDigest(asset.optString("digest")),
            )
        }
    }

    private fun parseDigest(digest: String): String? {
        val trimmed = digest.trim()
        if (!trimmed.startsWith(DIGEST_PREFIX, ignoreCase = true)) return null
        return trimmed.substring(DIGEST_PREFIX.length)
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.lowercase()
    }

    private fun pickApk(assets: List<UpdateAsset>, version: String): UpdateAsset? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        return apkAssets.firstOrNull { it.name.equals("Fen-$version.apk", ignoreCase = true) }
            ?: apkAssets.firstOrNull()
    }

    private fun pickSha256(assets: List<UpdateAsset>, version: String): UpdateAsset? =
        assets.firstOrNull {
            it.name.equals("Fen-$version.apk.sha256", ignoreCase = true)
        }
}

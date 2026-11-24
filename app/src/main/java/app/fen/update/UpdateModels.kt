package app.fen.update

/** Release 里的一个文件资产。 */
data class UpdateAsset(
    val name: String,
    val url: String,
    val size: Long,
    val digestSha256: String?,
)

/** 一次检查得到的可安装版本；[apk] 一定非空。 */
data class UpdateRelease(
    val tag: String,
    val version: String,
    val notes: String,
    val htmlUrl: String,
    val apk: UpdateAsset,
    val sha256FileUrl: String?,
)

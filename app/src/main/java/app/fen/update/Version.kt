package app.fen.update

/**
 * 版本号比较：只认 `x.y.z`，忽略 `v` 前缀与 `-dev.xxx` 之类后缀。
 */
object UpdateVersion {

    /** 去掉前导 `v`/`V` 与 `-` 之后的后缀，并 trim。 */
    fun normalize(version: String): String =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .trim()

    /** 逐段转成整数，非数字段视为 0，至少补足 3 段。 */
    fun segments(version: String): List<Int> {
        val parts = normalize(version)
            .split('.')
            .map { it.trim().toIntOrNull() ?: 0 }
        return parts + List((3 - parts.size).coerceAtLeast(0)) { 0 }
    }

    /** 远端是否比本地新；相等或更旧都返回 false。 */
    fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = segments(remote)
        val localParts = segments(local)
        val size = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until size) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val localPart = localParts.getOrElse(index) { 0 }
            if (remotePart != localPart) return remotePart > localPart
        }
        return false
    }
}

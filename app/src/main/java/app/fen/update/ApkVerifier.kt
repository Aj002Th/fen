package app.fen.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** 从安装包解析出的、做覆盖安装判断所需的字段。 */
data class ApkInspection(
    val packageName: String,
    val versionCode: Long,
    val signerSha256: Set<String>,
)

/** 安装前的校验结果。 */
sealed interface ApkCheck {
    data object Ok : ApkCheck
    data object Unreadable : ApkCheck
    data object WrongPackage : ApkCheck
    data class TooOld(val archiveVersionCode: Long) : ApkCheck
    data object WrongSignature : ApkCheck
}

/** 纯函数形式的安装前校验：包名、版本、签名。 */
fun compareArchive(
    info: ApkInspection?,
    appPackage: String,
    currentVersionCode: Long,
    installedSigners: Set<String>,
): ApkCheck {
    if (info == null) return ApkCheck.Unreadable
    if (info.packageName != appPackage) return ApkCheck.WrongPackage
    if (info.versionCode < currentVersionCode) return ApkCheck.TooOld(info.versionCode)
    if (info.signerSha256.intersect(installedSigners).isEmpty()) return ApkCheck.WrongSignature
    return ApkCheck.Ok
}

private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")

/** 从 `sha256sum` 风格文本里取第一个 64 位 hex，小写返回。 */
fun parseSha256Text(text: String): String? =
    SHA256_PATTERN.find(text)?.value?.lowercase()

fun sha256Hex(bytes: ByteArray): String =
    digestHex(MessageDigest.getInstance("SHA-256").digest(bytes))

/** 把已经算好的摘要转成小写 hex（不再二次哈希）。 */
internal fun digestHex(bytes: ByteArray): String =
    bytes.joinToString("") { "%02x".format(it) }

/** 读安装包信息（API 26 与 28+ 的签名接口不同）。 */
object ApkArchive {

    @Suppress("DEPRECATION")
    fun inspect(context: Context, file: File): ApkInspection? {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = manager.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        val packageName = info.packageName ?: return null
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        val signers = signerFingerprints(info) ?: return null
        return ApkInspection(packageName, versionCode, signers)
    }

    @Suppress("DEPRECATION")
    fun installedSigners(context: Context): Set<String> {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return try {
            signerFingerprints(manager.getPackageInfo(context.packageName, flags)) ?: emptySet()
        } catch (_: PackageManager.NameNotFoundException) {
            emptySet()
        }
    }

    @Suppress("DEPRECATION")
    private fun signerFingerprints(info: PackageInfo): Set<String>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
                ?.map { sha256Hex(it.toByteArray()) }
                ?.toSet()
        } else {
            info.signatures
                ?.map { sha256Hex(it.toByteArray()) }
                ?.toSet()
        }
}

import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/* ------------------------------------------------------------------
 * 版本号统一放在仓库根目录的 version.properties：
 *   versionCode=17
 *   versionName=0.7.1
 * 发版 PR 只改这个文件；Release workflow 读取它并自动打 tag。
 * ------------------------------------------------------------------ */
val versionProps = Properties().apply {
    val file = rootProject.file("version.properties")
    if (!file.isFile) throw GradleException("缺少 version.properties")
    file.inputStream().use { load(it) }
}
val appVersionCode = versionProps.getProperty("versionCode")?.trim()?.toIntOrNull()
    ?: throw GradleException("version.properties 缺少有效的 versionCode")
val appVersionName = versionProps.getProperty("versionName")?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: throw GradleException("version.properties 缺少 versionName")

/* ------------------------------------------------------------------
 * 正式版签名：
 *   本地：keystore.properties（已 gitignore）
 *   CI ：FEN_KEYSTORE_PATH / FEN_KEYSTORE_PASSWORD / FEN_KEY_ALIAS / FEN_KEY_PASSWORD
 * 两者都没有时回退 debug 签名，方便本地自测；CI 会设置
 * FEN_REQUIRE_RELEASE_SIGNING=true，缺配置直接失败。
 * ------------------------------------------------------------------ */
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
fun secret(envName: String, key: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }

val releaseStorePath = secret("FEN_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = secret("FEN_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = secret("FEN_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = secret("FEN_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

if (System.getenv("FEN_REQUIRE_RELEASE_SIGNING") == "true" && !hasReleaseSigning) {
    throw GradleException(
        "缺少 release 签名配置。请设置 FEN_KEYSTORE_PATH / FEN_KEYSTORE_PASSWORD / " +
            "FEN_KEY_ALIAS / FEN_KEY_PASSWORD，或提供 keystore.properties。"
    )
}

/* ------------------------------------------------------------------
 * 开发版隔离：
 *   - 正式版 applicationId = app.fen
 *   - 开发版 applicationId = app.fen.dev.<分支名>，可以和正式版共存
 *   - 开发版图标底色根据分支名生成不同色相，桌面上一眼区分
 *   - 可用 FEN_DEV_ID 环境变量覆盖分支名
 * ------------------------------------------------------------------ */

fun git(vararg args: String): String? = try {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && output.isNotEmpty()) output else null
} catch (_: Exception) {
    null
}

val rawBranch = git("rev-parse", "--abbrev-ref", "HEAD") ?: "local"
val branchName = if (rawBranch == "HEAD") {
    "detached_" + (git("rev-parse", "--short", "HEAD") ?: "unknown")
} else {
    rawBranch
}

val devId = (System.getenv("FEN_DEV_ID") ?: branchName)
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')
    .ifBlank { "dev" }
    .take(24)

fun hslToHex(h: Float, s: Float, l: Float): String {
    val c = (1 - Math.abs(2 * l - 1)) * s
    val x = c * (1 - Math.abs((h / 60f) % 2 - 1))
    val m = l - c / 2
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(v: Float) = ((v + m) * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", channel(r), channel(g), channel(b))
}

val devIconColor = hslToHex(
    h = ((devId.hashCode() and 0x7fffffff) % 360).toFloat(),
    s = 0.55f,
    l = 0.34f,
)

val generatedDevResDir = layout.buildDirectory.dir("generated/fenDev").get().asFile

val generateDevIconColor = tasks.register("generateDevIconColor") {
    val outDir = generatedDevResDir
    val color = devIconColor
    outputs.dir(outDir)
    doLast {
        val values = File(outDir, "values").apply { mkdirs() }
        File(values, "ic_launcher_colors.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="ic_launcher_background">$color</color>
            </resources>
            """.trimIndent()
        )
    }
}

android {
    namespace = "app.fen"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.fen"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "FEN_VARIANT", "\"prod\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    sourceSets.getByName("debug") {
        res.srcDir(generatedDevResDir)
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev.$devId"
            versionNameSuffix = "-dev.$devId"
            resValue("string", "app_name", "Fen·$devId")
            buildConfigField("String", "FEN_VARIANT", "\"dev.$devId\"")
        }
        release {
            isMinifyEnabled = false
            // 已有正式 keystore 时使用正式签名；本地没配置时沿用 debug keystore 便于自测。
            // CI 发布必须提供正式签名（FEN_REQUIRE_RELEASE_SIGNING=true）。
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(generateDevIconColor)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}





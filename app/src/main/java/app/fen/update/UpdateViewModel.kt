package app.fen.update

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fen.BuildConfig
import app.fen.FenApp
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UpdatePhase {
    Idle,
    Checking,
    UpToDate,
    Available,
    Downloading,
    Verifying,
    Installing,
    NeedPermission,
    Error,
}

data class UpdateUiState(
    val sheetVisible: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.Idle,
    val currentVersion: String = "",
    val release: UpdateRelease? = null,
    val stale: Boolean = false,
    val progress: Float? = null,
    val error: String? = null,
)

/** 设置页「检查更新」那一行的副标题。 */
fun updateRowSubtitle(state: UpdateUiState): String = when (state.phase) {
    UpdatePhase.Checking -> "正在检查…"
    UpdatePhase.Error -> "检查失败，点此重试"
    UpdatePhase.Available ->
        state.release?.let { "发现新版本 ${it.version}" } ?: "当前版本 ${state.currentVersion}"
    else -> "当前版本 ${state.currentVersion}"
}

/** 设置页与关于页的红点：只在已发现可下载的新版本时亮。 */
fun hasAvailableUpdate(state: UpdateUiState): Boolean =
    state.phase == UpdatePhase.Available

/** 用户离开应用再回来后该做什么。 */
enum class ResumeAction {
    ResumeInstall,
    ResetInstalling,
    None,
}

fun resumeAction(phase: UpdatePhase, canInstall: Boolean): ResumeAction = when {
    phase == UpdatePhase.NeedPermission && canInstall -> ResumeAction.ResumeInstall
    phase == UpdatePhase.Installing -> ResumeAction.ResetInstalling
    else -> ResumeAction.None
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = (app as FenApp).prefs
    private val userAgent = "Fen/${BuildConfig.VERSION_NAME}"

    private val checker = UpdateChecker(
        source = GitHubReleaseClient(userAgent) {
            if (BuildConfig.DEBUG) prefs.updateSourceOverride else ""
        },
        store = AppPrefsUpdateStore(prefs),
        localVersion = BuildConfig.VERSION_NAME,
    )

    private val updatesDir = File(app.cacheDir, "updates")
    private val downloader = ApkDownloader(updatesDir, userAgent)

    private val _state = MutableStateFlow(
        UpdateUiState(currentVersion = UpdateVersion.normalize(BuildConfig.VERSION_NAME)),
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** 开发版与正式版签名、包名不同，装不上正式包。 */
    val isDevBuild: Boolean = BuildConfig.FEN_VARIANT != "prod"

    private var downloadJob: Job? = null
    private var pendingInstall: File? = null

    /** 启动时静默检查：失败不打扰用户。 */
    fun checkOnStart() {
        if (!canStartCheck()) return
        viewModelScope.launch {
            applyResult(checker.check(force = false), manual = false)
        }
    }

    /** 点击「检查更新」：先秒显缓存结果，再在后台刷新。 */
    fun openAndCheck() {
        val allowed = canStartCheck()
        val cached = checker.cachedNewer()
        _state.update { current ->
            when {
                !allowed -> current.copy(sheetVisible = true)
                cached != null -> current.copy(
                    sheetVisible = true,
                    phase = UpdatePhase.Available,
                    release = cached,
                    stale = false,
                    error = null,
                )
                else -> current.copy(
                    sheetVisible = true,
                    phase = UpdatePhase.Checking,
                    stale = false,
                    error = null,
                )
            }
        }
        if (!allowed) return
        viewModelScope.launch {
            applyResult(checker.check(force = true), manual = true)
        }
    }

    fun closeSheet() {
        _state.update { it.copy(sheetVisible = false) }
    }

    fun downloadAndInstall() {
        val release = _state.value.release ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        phase = UpdatePhase.Downloading,
                        progress = 0f,
                        error = null,
                        stale = false,
                    )
                }
                val downloaded = downloader.download(
                    url = release.apk.url,
                    fileName = "Fen-${release.version}.apk",
                ) { done, total ->
                    val fraction = total?.let { (done.toFloat() / it).coerceIn(0f, 1f) }
                    _state.update { it.copy(progress = fraction) }
                }.getOrElse { error ->
                    failDownload(error.message ?: "下载失败")
                    return@launch
                }

                _state.update { it.copy(phase = UpdatePhase.Verifying, progress = null) }
                val expected = when {
                    release.apk.digestSha256 != null -> release.apk.digestSha256
                    release.sha256FileUrl != null -> downloader.fetchExpectedSha256(release.sha256FileUrl)
                        .getOrElse {
                            failDownload("无法获取安装包校验值，请稍后重试")
                            return@launch
                        }
                    else -> null
                }
                if (expected != null && !expected.equals(downloaded.sha256, ignoreCase = true)) {
                    failDownload("安装包校验失败，请重试")
                    return@launch
                }

                val app = getApplication<Application>()
                val check = withContext(Dispatchers.IO) {
                    compareArchive(
                        info = ApkArchive.inspect(app, downloaded.file),
                        appPackage = app.packageName,
                        currentVersionCode = currentVersionCode(app),
                        installedSigners = ApkArchive.installedSigners(app),
                    )
                }
                val message = when (check) {
                    ApkCheck.Ok -> null
                    ApkCheck.Unreadable -> "安装包无法读取，请重试"
                    ApkCheck.WrongPackage -> "这个安装包属于其他应用，无法安装"
                    is ApkCheck.TooOld -> "安装包版本低于当前版本"
                    ApkCheck.WrongSignature -> "安装包签名与当前应用不一致"
                }
                if (message != null) {
                    failDownload(message)
                    return@launch
                }

                if (!UpdateInstaller.canInstall(app)) {
                    pendingInstall = downloaded.file
                    _state.update { it.copy(phase = UpdatePhase.NeedPermission, progress = null) }
                    return@launch
                }
                startInstall(downloaded.file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failDownload("更新失败，请重试")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        cleanUpdatesDir(updatesDir)
        pendingInstall = null
        _state.update { it.copy(phase = UpdatePhase.Available, progress = null, error = null) }
    }

    fun openInstallPermission() {
        val app = getApplication<Application>()
        if (!UpdateInstaller.openUnknownSourcesSettings(app)) {
            _state.update {
                it.copy(
                    phase = UpdatePhase.Error,
                    progress = null,
                    error = "无法打开安装权限设置，请到系统设置里允许 Fen 安装应用",
                )
            }
        }
    }

    /** 用户离开应用再回来后继续安装，或从「正在安装」复位。 */
    fun resumeAfterPermission() {
        val app = getApplication<Application>()
        when (resumeAction(_state.value.phase, UpdateInstaller.canInstall(app))) {
            ResumeAction.ResumeInstall -> pendingInstall?.let { startInstall(it) }
            ResumeAction.ResetInstalling -> {
                cleanUpdatesDir(updatesDir)
                _state.update {
                    it.copy(phase = UpdatePhase.Available, progress = null, error = null)
                }
            }
            ResumeAction.None -> Unit
        }
    }

    fun retry() {
        if (_state.value.release != null && _state.value.phase == UpdatePhase.Error) {
            downloadAndInstall()
        } else {
            openAndCheck()
        }
    }

    private fun canStartCheck(): Boolean = when (_state.value.phase) {
        UpdatePhase.Checking, UpdatePhase.Downloading, UpdatePhase.Verifying -> false
        else -> true
    }

    private fun applyResult(result: UpdateCheck, manual: Boolean) {
        when (result) {
            is UpdateCheck.Available -> _state.update {
                it.copy(
                    phase = UpdatePhase.Available,
                    release = result.release,
                    stale = result.stale,
                    progress = null,
                    error = null,
                )
            }
            is UpdateCheck.UpToDate -> _state.update {
                it.copy(
                    phase = UpdatePhase.UpToDate,
                    release = null,
                    stale = result.stale,
                    progress = null,
                    error = null,
                )
            }
            is UpdateCheck.Failed -> if (manual) {
                _state.update {
                    it.copy(phase = UpdatePhase.Error, progress = null, error = result.message)
                }
            }
        }
    }

    private fun startInstall(file: File) {
        val app = getApplication<Application>()
        pendingInstall = null
        if (UpdateInstaller.install(app, file)) {
            _state.update { it.copy(phase = UpdatePhase.Installing, progress = null, error = null) }
        } else {
            _state.update {
                it.copy(
                    phase = UpdatePhase.Error,
                    progress = null,
                    error = "没有找到可用的系统安装器",
                )
            }
        }
    }

    private fun failDownload(message: String) {
        cleanUpdatesDir(updatesDir)
        pendingInstall = null
        _state.update { it.copy(phase = UpdatePhase.Error, progress = null, error = message) }
    }

    private fun currentVersionCode(app: Application): Long {
        val info = try {
            app.packageManager.getPackageInfo(app.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return 0L
        }
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }
}

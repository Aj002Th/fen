package app.fen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fen.update.UpdatePhase
import app.fen.update.UpdateUiState
import app.fen.ui.theme.Fen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    state: UpdateUiState,
    isDevBuild: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenPermission: () -> Unit,
    onResumeAfterPermission: () -> Unit,
    onRecheck: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var leftApp = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> leftApp = true
                Lifecycle.Event.ON_RESUME -> if (leftApp) {
                    leftApp = false
                    onResumeAfterPermission()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Fen.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "检查更新",
                style = MaterialTheme.typography.titleLarge,
                color = Fen.colors.ink,
            )
            Spacer(Modifier.height(12.dp))

            when (state.phase) {
                UpdatePhase.Idle, UpdatePhase.Checking -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "正在检查…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fen.colors.inkSoft,
                    )
                }

                UpdatePhase.UpToDate -> {
                    Text(
                        text = "已是最新版本 ${state.currentVersion}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Fen.colors.ink,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onRecheck) { Text("重新检查") }
                }

                UpdatePhase.Available -> {
                    if (state.stale) {
                        Text(
                            text = "刷新失败，显示的是上次结果",
                            style = MaterialTheme.typography.bodySmall,
                            color = Fen.colors.inkSoft,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = "新版本 ${state.release?.version.orEmpty()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Fen.colors.ink,
                    )
                    state.release?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Fen.colors.inkSoft,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    if (isDevBuild) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "开发版无法安装正式版安装包（包名与签名不同）",
                            style = MaterialTheme.typography.bodySmall,
                            color = Fen.colors.inkFaint,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    SheetPrimaryButton("下载并安装", onDownload)
                }

                UpdatePhase.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "正在下载 ${percentOf(state.progress)}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Fen.colors.inkSoft,
                        )
                        TextButton(onClick = onCancel) {
                            Text("取消", color = Fen.colors.inkSoft)
                        }
                    }
                }

                UpdatePhase.Verifying -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "正在校验安装包…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fen.colors.inkSoft,
                    )
                }

                UpdatePhase.Installing -> {
                    Text(
                        text = "正在打开系统安装器…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fen.colors.inkSoft,
                    )
                }

                UpdatePhase.NeedPermission -> {
                    Text(
                        text = "需要允许 Fen 安装应用，授权后会自动继续",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fen.colors.inkSoft,
                    )
                    Spacer(Modifier.height(16.dp))
                    SheetPrimaryButton("去授权", onOpenPermission)
                }

                UpdatePhase.Error -> {
                    Text(
                        text = state.error ?: "检查更新失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fen.colors.expense,
                    )
                    Spacer(Modifier.height(16.dp))
                    SheetPrimaryButton("重试", onRetry)
                }
            }
        }
    }
}

private fun percentOf(progress: Float?): Int = ((progress ?: 0f) * 100f).toInt()

@Composable
private fun SheetPrimaryButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        color = Fen.colors.link,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

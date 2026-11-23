package app.fen.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.fen.R
import app.fen.ui.components.CircleAction
import app.fen.ui.components.DotBadge
import app.fen.ui.components.IconBadge
import app.fen.ui.components.ImageBadge
import app.fen.ui.components.RowDivider
import app.fen.ui.theme.Fen
import app.fen.update.UpdateUiState
import app.fen.update.UpdateVersion
import app.fen.update.aboutUpdateSubtitle
import app.fen.update.hasAvailableUpdate

private const val GITHUB_URL = "https://github.com/Aj002Th/fen"
internal const val DEV_UNLOCK_TAPS = 10

/** 连点图标第 10 次解锁开发选项。 */
internal fun shouldUnlockDev(taps: Int): Boolean = taps >= DEV_UNLOCK_TAPS

@Composable
fun AboutScreen(
    versionName: String,
    updateState: UpdateUiState,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onUnlockDev: () -> Unit,
) {
    val context = LocalContext.current
    val version = UpdateVersion.normalize(versionName)
    var showPrivacy by remember { mutableStateOf(false) }
    var iconTaps by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleAction(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    tint = Fen.colors.ink,
                )
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF15171B))
                        .clickable {
                            iconTaps += 1
                            if (shouldUnlockDev(iconTaps)) {
                                iconTaps = 0
                                onUnlockDev()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "Fen",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Fen",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Fen.colors.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version $version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fen.colors.inkSoft,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        item { Spacer(Modifier.height(26.dp)) }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    AboutRow(
                        title = "检查更新",
                        subtitle = aboutUpdateSubtitle(updateState),
                        onClick = onCheckUpdate,
                        showDot = hasAvailableUpdate(updateState),
                        leading = {
                            IconBadge(
                                icon = Icons.Outlined.SystemUpdate,
                                color = Color(0xFF2F6BFF),
                                size = 36.dp,
                                iconSize = 18.dp,
                            )
                        },
                    )
                    RowDivider(startInset = 64.dp)
                    AboutRow(
                        title = "隐私说明",
                        subtitle = "数据如何保存、何时联网",
                        onClick = { showPrivacy = true },
                        leading = {
                            IconBadge(
                                icon = Icons.Outlined.Lock,
                                color = Color(0xFF7C8794),
                                size = 36.dp,
                                iconSize = 18.dp,
                            )
                        },
                    )
                    RowDivider(startInset = 64.dp)
                    AboutRow(
                        title = "GitHub",
                        subtitle = "github.com/Aj002Th/fen",
                        onClick = { openGithub(context) },
                        leading = {
                            ImageBadge(
                                painter = painterResource(R.drawable.ic_github),
                                color = Color(0xFF24292F),
                                size = 36.dp,
                                iconSize = 18.dp,
                            )
                        },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Apache License 2.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = Fen.colors.inkFaint,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "数据只保存在本机，唯一的联网是检查更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Fen.colors.inkSoft,
                )
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私说明") },
            text = {
                Text(
                    "数据只保存在本机，没有账号。\n" +
                        "唯一的联网是检查更新：启动时最多每小时一次，" +
                        "或你点击检查更新时访问 GitHub 获取版本信息，不上传任何数据。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("好的") }
            },
        )
    }
}

@Composable
private fun AboutRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    showDot: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkSoft,
            )
        }
        if (showDot) {
            DotBadge()
            Spacer(Modifier.size(8.dp))
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Fen.colors.inkFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun openGithub(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // 没有浏览器时静默失败
    }
}

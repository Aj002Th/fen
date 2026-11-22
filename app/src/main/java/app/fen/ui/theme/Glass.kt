package app.fen.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 浮层（底栏、底部面板、对话框）的底色。
 * 玻璃材质下是半透明，纯色材质下就是普通卡片色。
 */
@Composable
fun glassContainer(): Color = when {
    !Fen.colors.glass -> Fen.colors.surface
    Fen.colors.isDark -> Color(0xE61A1C20)
    else -> Color(0xF0FFFFFF)
}

/** 玻璃的细高光边；纯色材质下返回透明。 */
@Composable
fun glassOutline(): Color = when {
    !Fen.colors.glass -> Color.Transparent
    Fen.colors.isDark -> Color(0x26FFFFFF)
    else -> Color(0xB3FFFFFF)
}

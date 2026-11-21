package app.fen.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// 现代极简：冷白底 + 纯白卡片 + 近黑文字 + 单一语义强调色
val Bg = Color(0xFFF4F5F7)
val Surface = Color(0xFFFFFFFF)
val SurfaceMuted = Color(0xFFEEF0F4)
val Ink = Color(0xFF15171B)
val InkSoft = Color(0xFF868C96)
val InkFaint = Color(0xFFB7BCC5)
val Divider = Color(0xFFEFF1F4)

val Expense = Color(0xFFF2583E)
val Income = Color(0xFF17A467)
val Link = Color(0xFF2F6BFF)

// 深色
val BgNight = Color(0xFF0F1013)
val SurfaceNight = Color(0xFF1A1C20)
val SurfaceMutedNight = Color(0xFF24272C)
val InkNight = Color(0xFFF3F4F6)
val InkSoftNight = Color(0xFF9BA1AB)
val DividerNight = Color(0xFF2A2D33)
val ExpenseNight = Color(0xFFFF6B52)
val IncomeNight = Color(0xFF35C98A)
val LinkNight = Color(0xFF6C9BFF)

// 分类底色（用于图标圆底，低饱和）
val CategoryPalette = listOf(
    Color(0xFFF2583E), // coral
    Color(0xFF3E8BF2), // blue
    Color(0xFF17A467), // green
    Color(0xFFF2A93E), // amber
    Color(0xFF8B6CF2), // violet
    Color(0xFFE05C9A), // pink
    Color(0xFF21B8C7), // cyan
    Color(0xFF7C8794), // slate
)

/** 存进数据库的 ARGB 值 */
val CategoryColorValues = CategoryPalette.map { it.toArgb().toLong() and 0xFFFFFFFFL }


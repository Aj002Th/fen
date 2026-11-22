package app.fen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class FenColors(
    val bg: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val divider: Color,
    val expense: Color,
    val income: Color,
    val link: Color,
    val isDark: Boolean,
    val glass: Boolean = false,
)

val LocalFenColors = staticCompositionLocalOf {
    FenColors(
        bg = Bg, surface = Surface, surfaceMuted = SurfaceMuted,
        ink = Ink, inkSoft = InkSoft, inkFaint = InkFaint, divider = Divider,
        expense = Expense, income = Income, link = Link, isDark = false,
    )
}

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = SurfaceMuted,
    onPrimaryContainer = Ink,
    secondary = Link,
    onSecondary = Color.White,
    background = Bg,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = InkSoft,
    outline = Divider,
    outlineVariant = Divider,
    error = Expense,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = InkNight,
    onPrimary = Color(0xFF15171B),
    primaryContainer = SurfaceMutedNight,
    onPrimaryContainer = InkNight,
    secondary = LinkNight,
    onSecondary = Color(0xFF0B0F18),
    background = BgNight,
    onBackground = InkNight,
    surface = SurfaceNight,
    onSurface = InkNight,
    surfaceVariant = SurfaceMutedNight,
    onSurfaceVariant = InkSoftNight,
    outline = DividerNight,
    outlineVariant = DividerNight,
    error = ExpenseNight,
    onError = Color(0xFF2A0B06),
)

private val FenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    glass: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) {
        FenColors(
            bg = BgNight, surface = SurfaceNight, surfaceMuted = SurfaceMutedNight,
            ink = InkNight, inkSoft = InkSoftNight, inkFaint = Color(0xFF6B717B), divider = DividerNight,
            expense = ExpenseNight, income = IncomeNight, link = LinkNight, isDark = true,
            glass = glass,
        )
    } else {
        FenColors(
            bg = Bg, surface = Surface, surfaceMuted = SurfaceMuted,
            ink = Ink, inkSoft = InkSoft, inkFaint = InkFaint, divider = Divider,
            expense = Expense, income = Income, link = Link, isDark = false,
            glass = glass,
        )
    }
    CompositionLocalProvider(LocalFenColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = FenTypography,
            shapes = FenShapes,
            content = content,
        )
    }
}

object Fen {
    val colors: FenColors
        @Composable get() = LocalFenColors.current
}

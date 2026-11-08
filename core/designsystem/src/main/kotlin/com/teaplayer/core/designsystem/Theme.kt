package com.teaplayer.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 唯一取色依据：docs/design/tokens.md。改这里之前先改文档。 */
object TpColor {
    val Bg = Color(0xFF0B0E13)
    val Surface = Color(0xFF161D27)
    val Surface2 = Color(0xFF1D2733)
    val Surface3 = Color(0xFF243040)
    val Line = Color(0xFF28313D)
    val Line2 = Color(0xFF39465A)
    val Tx = Color(0xFFE8EEF7)
    val Tx2 = Color(0xFF9AA8B8)
    val Tx3 = Color(0xFF6B7887)
    val Accent = Color(0xFF4C9AFF)
    val Accent2 = Color(0xFF2F7CE0)
    val AccentSoft = Color(0x244C9AFF)
    val Ok = Color(0xFF3FB950)
    val Warn = Color(0xFFD29922)
    val Err = Color(0xFFF85149)
    val Revoked = Color(0xFFC06BFF)
}

object TpSpace {
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp
    val s4 = 16.dp; val s5 = 20.dp; val s6 = 24.dp
}

object TpRadius {
    val r1 = 8.dp; val r2 = 12.dp; val r3 = 16.dp; val pill = 999.dp
}

object TpType {
    val caption = TextStyle(fontSize = 11.sp, lineHeight = 16.sp)
    val bodyS = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)
    val body = TextStyle(fontSize = 13.sp, lineHeight = 20.sp)
    val titleS = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    val title = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
    val titleL = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val display = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
}

private val TpScheme = darkColorScheme(
    primary = TpColor.Accent,
    onPrimary = Color(0xFF06121F),
    background = TpColor.Bg,
    onBackground = TpColor.Tx,
    surface = TpColor.Surface,
    onSurface = TpColor.Tx,
    surfaceVariant = TpColor.Surface2,
    onSurfaceVariant = TpColor.Tx2,
    outline = TpColor.Line,
    error = TpColor.Err,
)

/**
 * 深色是唯一主题；**不启用动态取色**（dynamicColor=false）：
 * 系统取色会破坏 ok/warn/err/revoked 的语义稳定性（tokens.md §5）。
 */
@Composable
fun TeaPlayerTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TpScheme,
        typography = Typography(
            bodyLarge = TpType.body,
            bodyMedium = TpType.body,
            titleMedium = TpType.titleS,
            titleLarge = TpType.title,
            headlineSmall = TpType.titleL,
        ),
    ) {
        // MaterialTheme 本身不绘制底色；不包 Surface 会露出系统窗口灰（实测 #303030）
        Surface(color = TpColor.Bg, contentColor = TpColor.Tx) { content() }
    }
}

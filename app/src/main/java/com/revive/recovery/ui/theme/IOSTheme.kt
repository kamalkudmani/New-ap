package com.revive.recovery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Apple Human Interface Guidelines system colors. */
@Immutable
data class IOSColors(
    val dark: Boolean,
    val background: Color,          // systemGroupedBackground
    val card: Color,                // secondarySystemGroupedBackground
    val elevated: Color,            // tertiarySystemGroupedBackground
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,                // tertiarySystemFill
    val blue: Color, val green: Color, val red: Color, val orange: Color,
    val purple: Color, val indigo: Color, val teal: Color, val pink: Color, val yellow: Color,
    val bar: Color,                 // translucent chrome
)

val LightIOS = IOSColors(
    dark = false,
    background = Color(0xFFF2F2F7), card = Color.White, elevated = Color(0xFFF2F2F7),
    label = Color.Black, secondaryLabel = Color(0x993C3C43), tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43), fill = Color(0x1F767680),
    blue = Color(0xFF007AFF), green = Color(0xFF34C759), red = Color(0xFFFF3B30), orange = Color(0xFFFF9500),
    purple = Color(0xFFAF52DE), indigo = Color(0xFF5856D6), teal = Color(0xFF30B0C7), pink = Color(0xFFFF2D55),
    yellow = Color(0xFFFFCC00), bar = Color(0xE6F9F9F9),
)

val DarkIOS = IOSColors(
    dark = true,
    background = Color.Black, card = Color(0xFF1C1C1E), elevated = Color(0xFF2C2C2E),
    label = Color.White, secondaryLabel = Color(0x99EBEBF5), tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458), fill = Color(0x3D767680),
    blue = Color(0xFF0A84FF), green = Color(0xFF30D158), red = Color(0xFFFF453A), orange = Color(0xFFFF9F0A),
    purple = Color(0xFFBF5AF2), indigo = Color(0xFF5E5CE6), teal = Color(0xFF40C8E0), pink = Color(0xFFFF375F),
    yellow = Color(0xFFFFD60A), bar = Color(0xE61C1C1E),
)

/** iOS Dynamic Type sizes (Large default). */
object IOSType {
    private val tight = (-0.02).em
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = tight, lineHeight = 41.sp)
    val title1 = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = tight)
    val title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = tight)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = tight)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    val body = TextStyle(fontSize = 17.sp, letterSpacing = (-0.01).em, lineHeight = 22.sp)
    val callout = TextStyle(fontSize = 16.sp, letterSpacing = (-0.01).em)
    val subhead = TextStyle(fontSize = 15.sp, letterSpacing = (-0.01).em)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp)
    val caption2 = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

val LocalIOS = staticCompositionLocalOf { LightIOS }

object IOS {
    val colors: IOSColors @Composable get() = LocalIOS.current
}

@Composable
fun IOSTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) DarkIOS else LightIOS
    val scheme = if (dark) darkColorScheme(primary = c.blue, background = c.background, surface = c.card)
    else lightColorScheme(primary = c.blue, background = c.background, surface = c.card)
    CompositionLocalProvider(LocalIOS provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

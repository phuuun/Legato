package com.phuuun.legato.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.phuuun.legato.R

/** Tsundoku's Bold palette, with Mint as the one accent (no Tomato or Pink): dark paper, ink, a few loud colours. Dark only. */
object Bold {
    val Paper = Color(0xFF0B0B0C)
    val Surface = Color(0xFF161618)
    val SurfaceHigh = Color(0xFF1F1F22)
    val Ink = Color(0xFFF4F4F5)
    val Muted = Color(0xFFA1A1AA)
    val Placeholder = Color(0xFF8B8B93)
    val QuietLine = Color(0xFF3F3F46)
    val DarkInk = Color(0xFF151515)
    val Yellow = Color(0xFFFFC93C)
    val Mint = Color(0xFF3DDC97)
    val Error = Color(0xFFFF6B6B)
}

internal val DarkScheme = darkColorScheme(
    primary = Bold.Mint, onPrimary = Bold.DarkInk,
    primaryContainer = Bold.Mint, onPrimaryContainer = Bold.DarkInk,
    secondary = Bold.Ink, onSecondary = Bold.Paper,
    secondaryContainer = Bold.SurfaceHigh, onSecondaryContainer = Bold.Ink,
    background = Bold.Paper, onBackground = Bold.Ink,
    surface = Bold.Paper, onSurface = Bold.Ink,
    surfaceVariant = Bold.Surface, onSurfaceVariant = Bold.Muted,
    outline = Bold.QuietLine, outlineVariant = Bold.QuietLine,
    error = Bold.Error, onError = Bold.DarkInk,
    // Sheets, menus, sliders and tonal buttons read these; unset they fall back to Material's lavender tint.
    surfaceContainerLowest = Bold.Paper, surfaceContainerLow = Bold.Surface,
    surfaceContainer = Bold.Surface, surfaceContainerHigh = Bold.SurfaceHigh,
    surfaceContainerHighest = Bold.SurfaceHigh,
    inverseSurface = Bold.Ink, inverseOnSurface = Bold.Paper, inversePrimary = Bold.Mint,
)

private val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque_regular, FontWeight.Normal),
    Font(R.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
)

// Every style Legato or a Material component reads is set, so nothing falls back to Roboto.
private val Type = Typography(
    // The big section title.
    headlineLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 50.sp, lineHeight = 50.sp, letterSpacing = (-2.5).sp),
    // Album, artist and playlist names.
    headlineMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1.2).sp),
    headlineSmall = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp),
    titleLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Bricolage, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Bricolage, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Bricolage, fontSize = 12.sp, lineHeight = 16.sp),
    // Track title and artist in a row.
    labelLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    // Small caps-style eyebrows and stickers: SONGS, NOW PLAYING, FINISHED.
    labelSmall = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.sp),
)

@Composable
fun LegatoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = Type, content = content)
}

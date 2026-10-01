package com.timestablequest.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mathematics-atlas palette: warm paper, muted teal/blue tiles, soft lavender practice controls. */
object Atlas {
    val Paper = Color(0xFFFBF5E9)
    val PaperDeep = Color(0xFFF3E9D4)
    val PaperCard = Color(0xFFFFFCF5)
    val GridLine = Color(0xFFE2D6BC)
    val Navy = Color(0xFF1B2A4A)
    val NavySoft = Color(0xFF46557A)
    val Teal = Color(0xFF2F7F80)
    val TealTile = Color(0xFFD6EBE8)
    val Blue = Color(0xFF3F6A9C)
    val BlueTile = Color(0xFFDCE6F2)
    val Lavender = Color(0xFFE6DDF4)
    val LavenderDeep = Color(0xFF5E4B8B)
    val Yellow = Color(0xFFF5C84C)
    val YellowSoft = Color(0xFFFCEFC4)
    val Text = Color(0xFF1B2333)
    val TextMuted = Color(0xFF4D5568)
    val Correct = Color(0xFF2B7A4B)
    val CorrectSoft = Color(0xFFDDF0E3)
    val Incorrect = Color(0xFFB0412E)
    val IncorrectSoft = Color(0xFFF7E0DA)
    val SegmentEmpty = Color(0xFFC9CFD9)

    /** Limited rotating palette distinguishing rows: (tile fill, accent). */
    private val rowPalette = listOf(
        TealTile to Teal,
        BlueTile to Blue,
        Color(0xFFE1EEE0) to Color(0xFF4F7A55),
        Color(0xFFE4E3F1) to Color(0xFF55578F),
    )

    fun rowFill(row: Int): Color = rowPalette[(row - 1).mod(rowPalette.size)].first
    fun rowAccent(row: Int): Color = rowPalette[(row - 1).mod(rowPalette.size)].second
}

private val ColorScheme = lightColorScheme(
    primary = Atlas.Navy,
    onPrimary = Color.White,
    primaryContainer = Atlas.BlueTile,
    onPrimaryContainer = Atlas.Navy,
    secondary = Atlas.Teal,
    onSecondary = Color.White,
    secondaryContainer = Atlas.TealTile,
    onSecondaryContainer = Color(0xFF0F3D3E),
    tertiary = Atlas.LavenderDeep,
    onTertiary = Color.White,
    tertiaryContainer = Atlas.Lavender,
    onTertiaryContainer = Color(0xFF2C2148),
    background = Atlas.Paper,
    onBackground = Atlas.Text,
    surface = Atlas.Paper,
    onSurface = Atlas.Text,
    surfaceVariant = Atlas.PaperDeep,
    onSurfaceVariant = Atlas.TextMuted,
    surfaceContainerLowest = Atlas.PaperCard,
    surfaceContainerLow = Color(0xFFFDF8EE),
    surfaceContainer = Color(0xFFF8F0E0),
    surfaceContainerHigh = Color(0xFFF4EAD7),
    surfaceContainerHighest = Atlas.PaperDeep,
    outline = Color(0xFF7A8295),
    outlineVariant = Atlas.GridLine,
    error = Atlas.Incorrect,
    onError = Color.White,
)

private val Family = FontFamily.SansSerif

private val AppTypography = Typography(
    displayMedium = TextStyle(fontFamily = Family, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = Family, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = Family, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Family, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Family, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Family, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Family, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Family, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Family, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

/** Whether decorative animation is reduced (user setting). */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun TimesTableQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = AppTypography, shapes = AppShapes, content = content)
}

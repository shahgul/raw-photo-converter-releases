package com.shahgul.rawphotoconverter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shahgul.rawphotoconverter.R

private val Geist = FontFamily(Font(R.font.geist_regular), Font(R.font.geist_medium, FontWeight.Medium), Font(R.font.geist_semibold, FontWeight.SemiBold))
val MeasurementFont = FontFamily(Font(R.font.geist_mono_regular))
private val BaseType = Typography()
private val AppType = Typography(
    headlineMedium = BaseType.headlineMedium.copy(fontFamily = Geist, fontWeight = FontWeight.Medium),
    headlineSmall = BaseType.headlineSmall.copy(fontFamily = Geist, fontWeight = FontWeight.Medium),
    titleLarge = BaseType.titleLarge.copy(fontFamily = Geist, fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontFamily = Geist, fontWeight = FontWeight.Medium),
    titleSmall = BaseType.titleSmall.copy(fontFamily = Geist),
    bodyLarge = BaseType.bodyLarge.copy(fontFamily = Geist),
    bodyMedium = BaseType.bodyMedium.copy(fontFamily = Geist),
    bodySmall = BaseType.bodySmall.copy(fontFamily = Geist),
    labelLarge = BaseType.labelLarge.copy(fontFamily = Geist, fontWeight = FontWeight.Medium),
    labelMedium = BaseType.labelMedium.copy(fontFamily = Geist),
    labelSmall = BaseType.labelSmall.copy(fontFamily = Geist),
)

/** Larger radii and softer elevation for the sleek mobile direction. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7145), onPrimary = Color(0xFF2A1A14),
    primaryContainer = Color(0xFF3F2C24), onPrimaryContainer = Color(0xFFF5C9B0),
    secondary = Color(0xFFCEC5BB), onSecondary = Color(0xFF292622),
    secondaryContainer = Color(0xFF2C2925), onSecondaryContainer = Color(0xFFE8E1D8),
    tertiary = Color(0xFFBFCAB5), onTertiary = Color(0xFF253021),
    tertiaryContainer = Color(0xFF30392B), onTertiaryContainer = Color(0xFFD9E4CF),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF3F0EA),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF3F0EA),
    surfaceVariant = Color(0xFF2A2824),
    onSurfaceVariant = Color(0xFFB8B3AA),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF222220),
    surfaceContainerHigh = Color(0xFF2C2B28),
    surfaceContainerHighest = Color(0xFF373530),
    outline = Color(0xFF8A847B), outlineVariant = Color(0xFF3C3A35),
    inverseSurface = Color(0xFFF3F0EA), inverseOnSurface = Color(0xFF292622),
    inversePrimary = Color(0xFF885039), surfaceTint = Color(0xFFF1BEA5),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB04727), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2DCCE), onPrimaryContainer = Color(0xFF3F271C),
    secondary = Color(0xFF625B52), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E2D9), onSecondaryContainer = Color(0xFF292622),
    tertiary = Color(0xFF526448), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7E5CA), onTertiaryContainer = Color(0xFF253021),
    background = Color(0xFFF4F2ED),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFF4F2ED),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFEAE6DF),
    onSurfaceVariant = Color(0xFF6C6861),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF8F4),
    surfaceContainer = Color(0xFFF0ECE5),
    surfaceContainerHigh = Color(0xFFE9E4DC),
    surfaceContainerHighest = Color(0xFFE1DBD2),
    outline = Color(0xFF81796D), outlineVariant = Color(0xFFD8D1C6),
    inverseSurface = Color(0xFF292622), inverseOnSurface = Color(0xFFF3F0EA),
    inversePrimary = Color(0xFFF1BEA5), surfaceTint = Color(0xFF885039),
)

@Composable
fun RawPhotoConverterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppType,
        shapes = AppShapes,
        content = content,
    )
}

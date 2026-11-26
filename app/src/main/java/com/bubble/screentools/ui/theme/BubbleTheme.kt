package com.bubble.screentools.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat

enum class BubblePalette(
    val key: String,
    val previewPrimary: Color,
    val previewTertiary: Color,
) {
    WALLPAPER("wallpaper", Color(0xFF1A73E8), Color(0xFF8B5CF6)),
    BUBBLE_BLUE("blue", Color(0xFF1A73E8), Color(0xFF00838F)),
    LAGOON("lagoon", Color(0xFF006B5E), Color(0xFF4361EE)),
    LAVENDER("lavender", Color(0xFF6750A4), Color(0xFFB5446E)),
    CORAL("coral", Color(0xFFC0483E), Color(0xFF7B5EA7));

    companion object {
        fun fromKey(key: String?): BubblePalette =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: WALLPAPER
    }
}

val supportsDynamicWallpaper: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val BubbleBlueLight = lightColorScheme(
    primary = Color(0xFF0B61C2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001C3B),
    secondary = Color(0xFF4F607A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E3FF),
    onSecondaryContainer = Color(0xFF0B1C33),
    tertiary = Color(0xFF006A67),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9DF2ED),
    onTertiaryContainer = Color(0xFF00201F),
    background = Color(0xFFF8F9FE),
    onBackground = Color(0xFF181C22),
    surface = Color(0xFFF8F9FE),
    onSurface = Color(0xFF181C22),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF434750),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4FB),
    surfaceContainer = Color(0xFFEBEEF7),
    surfaceContainerHigh = Color(0xFFE5E9F2),
    surfaceContainerHighest = Color(0xFFDFE3EC),
    outline = Color(0xFF747781),
    outlineVariant = Color(0xFFC4C6D0),
)

private val BubbleBlueDark = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00478D),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB7C7E5),
    onSecondary = Color(0xFF213149),
    secondaryContainer = Color(0xFF384862),
    onSecondaryContainer = Color(0xFFD5E3FF),
    tertiary = Color(0xFF80D5D1),
    onTertiary = Color(0xFF003735),
    tertiaryContainer = Color(0xFF00504D),
    onTertiaryContainer = Color(0xFF9DF2ED),
    background = Color(0xFF101319),
    onBackground = Color(0xFFE1E2EA),
    surface = Color(0xFF101319),
    onSurface = Color(0xFFE1E2EA),
    surfaceVariant = Color(0xFF434750),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFF0B0E14),
    surfaceContainerLow = Color(0xFF181C23),
    surfaceContainer = Color(0xFF1C2028),
    surfaceContainerHigh = Color(0xFF262A33),
    surfaceContainerHighest = Color(0xFF31353E),
    outline = Color(0xFF8E909A),
    outlineVariant = Color(0xFF434750),
)

private val LagoonLight = lightColorScheme(
    primary = Color(0xFF006B5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9DF2E1),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A635E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8E1),
    onSecondaryContainer = Color(0xFF06201B),
    tertiary = Color(0xFF3E5AA9),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDBE1FF),
    onTertiaryContainer = Color(0xFF00174B),
    background = Color(0xFFF5FBF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FBF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEC),
    surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDDE4E1),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
)

private val LagoonDark = darkColorScheme(
    primary = Color(0xFF81D5C5),
    onPrimary = Color(0xFF003830),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFF9DF2E1),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCDE8E1),
    tertiary = Color(0xFFB4C5FF),
    onTertiary = Color(0xFF062B78),
    tertiaryContainer = Color(0xFF234290),
    onTertiaryContainer = Color(0xFFDBE1FF),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF303634),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
)

private val LavenderLight = lightColorScheme(
    primary = Color(0xFF6550A6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DDFF),
    onPrimaryContainer = Color(0xFF21005E),
    secondary = Color(0xFF615B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF8A4A64),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E5),
    onTertiaryContainer = Color(0xFF390720),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE6E0EC),
    onSurfaceVariant = Color(0xFF48454E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2FA),
    surfaceContainer = Color(0xFFF0ECF4),
    surfaceContainerHigh = Color(0xFFEBE6EE),
    surfaceContainerHighest = Color(0xFFE5E1E9),
    outline = Color(0xFF79757F),
    outlineVariant = Color(0xFFCAC4CF),
)

private val LavenderDark = darkColorScheme(
    primary = Color(0xFFCEBDFF),
    onPrimary = Color(0xFF361E75),
    primaryContainer = Color(0xFF4D378C),
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondary = Color(0xFFCBC2DC),
    onSecondary = Color(0xFF322E41),
    secondaryContainer = Color(0xFF494458),
    onSecondaryContainer = Color(0xFFE7DEF8),
    tertiary = Color(0xFFFFB0CE),
    onTertiary = Color(0xFF531D35),
    tertiaryContainer = Color(0xFF6E334C),
    onTertiaryContainer = Color(0xFFFFD9E5),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF48454E),
    onSurfaceVariant = Color(0xFFCAC4CF),
    surfaceContainerLowest = Color(0xFF0E0D13),
    surfaceContainerLow = Color(0xFF1C1B20),
    surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343A),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF48454E),
)

private val CoralLight = lightColorScheme(
    primary = Color(0xFFB33E34),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF6A5C2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E0A6),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EE),
    surfaceContainer = Color(0xFFFCEAE7),
    surfaceContainerHigh = Color(0xFFF6E4E1),
    surfaceContainerHighest = Color(0xFFF1DEDC),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BF),
)

private val CoralDark = darkColorScheme(
    primary = Color(0xFFFFB4AA),
    onPrimary = Color(0xFF690F0B),
    primaryContainer = Color(0xFF8F271F),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442926),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFD6C48C),
    onTertiary = Color(0xFF392E05),
    tertiaryContainer = Color(0xFF514419),
    onTertiaryContainer = Color(0xFFF3E0A6),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF1DEDC),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DEDC),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    surfaceContainerLowest = Color(0xFF140C0B),
    surfaceContainerLow = Color(0xFF231918),
    surfaceContainer = Color(0xFF271D1C),
    surfaceContainerHigh = Color(0xFF322826),
    surfaceContainerHighest = Color(0xFF3D3231),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF534341),
)

private val BubbleTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

private val BubbleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BubbleExpressiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    palette: BubblePalette = BubblePalette.WALLPAPER,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val baseScheme: ColorScheme =
        when {
            palette == BubblePalette.WALLPAPER && supportsDynamicWallpaper ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            palette == BubblePalette.LAGOON -> if (darkTheme) LagoonDark else LagoonLight
            palette == BubblePalette.LAVENDER -> if (darkTheme) LavenderDark else LavenderLight
            palette == BubblePalette.CORAL -> if (darkTheme) CoralDark else CoralLight
            else -> if (darkTheme) BubbleBlueDark else BubbleBlueLight
        }

    val colorScheme =
        remember(baseScheme, pureBlack, darkTheme) {
            if (darkTheme && pureBlack) {
                baseScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF101216),
                )
            } else {
                baseScheme
            }
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowInsetsControllerCompat(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = BubbleTypography,
        shapes = BubbleShapes,
        content = content,
    )
}

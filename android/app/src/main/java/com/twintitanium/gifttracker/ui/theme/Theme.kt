package com.twintitanium.gifttracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.twintitanium.gifttracker.data.model.ColorMode
import com.twintitanium.gifttracker.data.model.ThemeSeed

private val TerracottaDark = darkColorScheme(
    primary = Color(0xFFFFB5A0),
    onPrimary = Color(0xFF561F0F),
    primaryContainer = Color(0xFF723523),
    onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFE7BDB2),
    onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFFD8C58D),
    onTertiary = Color(0xFF3B2F05),
    tertiaryContainer = Color(0xFF534619),
    onTertiaryContainer = Color(0xFFF5E1A7),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF1DFDA),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D322F)
)

private val TerracottaLight = lightColorScheme(
    primary = Color(0xFF8F4C38),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD1),
    onPrimaryContainer = Color(0xFF3A0B01),
    secondary = Color(0xFF77574E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF2C150F),
    tertiary = Color(0xFF6C5D2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E1A7),
    onTertiaryContainer = Color(0xFF231B00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED8),
    onSurfaceVariant = Color(0xFF53433F)
)

private val ForestDark = darkColorScheme(
    primary = Color(0xFF9CD49F),
    onPrimary = Color(0xFF003913),
    primaryContainer = Color(0xFF1D5128),
    onPrimaryContainer = Color(0xFFB7F1BA),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF253424),
    secondaryContainer = Color(0xFF3B4B39),
    onSecondaryContainer = Color(0xFFD5E8D0),
    tertiary = Color(0xFFA1CED5),
    onTertiary = Color(0xFF00363C),
    tertiaryContainer = Color(0xFF1F4D53),
    onTertiaryContainer = Color(0xFFBCEBF1),
    background = Color(0xFF101510),
    onBackground = Color(0xFFE0E4DC),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFE0E4DC)
)

private val ForestLight = lightColorScheme(
    primary = Color(0xFF35693E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F1BA),
    onPrimaryContainer = Color(0xFF002108),
    secondary = Color(0xFF526350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8D0),
    onSecondaryContainer = Color(0xFF101F10),
    background = Color(0xFFF7FBF2),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF7FBF2),
    onSurface = Color(0xFF181D18)
)

private val OchreDark = darkColorScheme(
    primary = Color(0xFFF6BE48),
    onPrimary = Color(0xFF412D00),
    primaryContainer = Color(0xFF5E4200),
    onPrimaryContainer = Color(0xFFFFDEA3),
    secondary = Color(0xFFDBC3A0),
    onSecondary = Color(0xFF3D2E16),
    secondaryContainer = Color(0xFF55442A),
    onSecondaryContainer = Color(0xFFF8DFBB),
    background = Color(0xFF17130B),
    onBackground = Color(0xFFECE1D4),
    surface = Color(0xFF17130B),
    onSurface = Color(0xFFECE1D4)
)

private val OchreLight = lightColorScheme(
    primary = Color(0xFF7B5800),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA3),
    onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF6E5C40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8DFBB),
    onSecondaryContainer = Color(0xFF261904),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF201B13),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF201B13)
)

private val OceanDark = darkColorScheme(
    primary = Color(0xFF83D2E4),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF004E5C),
    onPrimaryContainer = Color(0xFFA5EEFF),
    secondary = Color(0xFFB2CBD2),
    onSecondary = Color(0xFF1D343A),
    secondaryContainer = Color(0xFF334B51),
    onSecondaryContainer = Color(0xFFCDE7EE),
    background = Color(0xFF0E1416),
    onBackground = Color(0xFFDEE3E5),
    surface = Color(0xFF0E1416),
    onSurface = Color(0xFFDEE3E5)
)

private val OceanLight = lightColorScheme(
    primary = Color(0xFF006879),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA5EEFF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF4B6268),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE7EE),
    onSecondaryContainer = Color(0xFF051F24),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFF5FAFC),
    onSurface = Color(0xFF171D1E)
)

private val ExpressiveTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)

@Composable
fun GiftTrackerTheme(
    themeSeed: ThemeSeed,
    colorMode: ColorMode,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDark = when (colorMode) {
        ColorMode.DARK -> true
        ColorMode.LIGHT -> false
        ColorMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme: ColorScheme = when (themeSeed) {
        ThemeSeed.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (useDark) TerracottaDark else TerracottaLight
            }
        }
        ThemeSeed.TERRACOTTA -> if (useDark) TerracottaDark else TerracottaLight
        ThemeSeed.FOREST -> if (useDark) ForestDark else ForestLight
        ThemeSeed.OCHRE -> if (useDark) OchreDark else OchreLight
        ThemeSeed.OCEAN -> if (useDark) OceanDark else OceanLight
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = ExpressiveTypography,
        content = content
    )
}

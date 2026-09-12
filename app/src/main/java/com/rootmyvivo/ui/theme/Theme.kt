package com.rootmyvivo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rootmyvivo.data.AppThemeName
import com.rootmyvivo.data.ThemeMode

// ─────────── Брендовые акценты Monet Old ───────────

private val BrandViolet = Color(0xFF7C4DFF)
private val BrandVioletDim = Color(0xFF9F8CFF)
private val BrandTeal = Color(0xFF00BFA5)
private val BrandTealDim = Color(0xFF4DDAC8)

// ─────────── Monet: синий тональный набор в духе ReSukiSU ───────────

private val MonetBlue = Color(0xFF0B57D0)          // Google Blue (seed)
private val MonetBlueDark = Color(0xFFA8C8FA)

private fun monetLight() = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF705575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBD7FC),
    onTertiaryContainer = Color(0xFF28132D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B23),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B23),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceContainer = Color(0xFFEEEFF7),
    surfaceContainerLow = Color(0xFFF4F4FA),
    surfaceContainerHigh = Color(0xFFE8E9F1),
    surfaceBright = Color(0xFFFAF8FF),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
)

private fun monetDark() = darkColorScheme(
    primary = MonetBlueDark,
    onPrimary = Color(0xFF002C77),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDCBBE0),
    onTertiary = Color(0xFF3E2843),
    tertiaryContainer = Color(0xFF563E5B),
    onTertiaryContainer = Color(0xFFFBD7FC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainerHigh = Color(0xFF282A30),
    surfaceBright = Color(0xFF3A3B41),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
)

// ─────────── OriginOS: серая заготовка (позже доработаем) ───────────

private fun originGrayLight() = lightColorScheme(
    primary = Color(0xFF5C6470),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFE3EB),
    onPrimaryContainer = Color(0xFF191C21),
    secondary = Color(0xFF565E6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F1),
    onSecondaryContainer = Color(0xFF131B25),
    tertiary = Color(0xFF6D5677),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6D9FF),
    onTertiaryContainer = Color(0xFF261430),
    background = Color(0xFFF9F9FB),
    onBackground = Color(0xFF1A1C1F),
    surface = Color(0xFFF9F9FB),
    onSurface = Color(0xFF1A1C1F),
    surfaceVariant = Color(0xFFDFE3EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLow = Color(0xFFF3F3F6),
    surfaceContainer = Color(0xFFEDEEF1),
    surfaceContainerHigh = Color(0xFFE7E8EC),
    outlineVariant = Color(0xFFC3C6CF),
)

private fun originGrayDark() = darkColorScheme(
    primary = Color(0xFFC4CAD6),
    onPrimary = Color(0xFF2D313A),
    primaryContainer = Color(0xFF444953),
    onPrimaryContainer = Color(0xFFE0E2E9),
    secondary = Color(0xFFBEC6D5),
    onSecondary = Color(0xFF28303C),
    secondaryContainer = Color(0xFF3E4754),
    onSecondaryContainer = Color(0xFFDAE2F1),
    tertiary = Color(0xFFD7BDE3),
    onTertiary = Color(0xFF3B2946),
    tertiaryContainer = Color(0xFF533F5E),
    onTertiaryContainer = Color(0xFFF6D9FF),
    background = Color(0xFF111317),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF111317),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLow = Color(0xFF191B1F),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2E),
    outlineVariant = Color(0xFF43474E),
)

// ─────────── Стиль поведения UI для текущей темы ───────────

@Immutable
data class AppStyle(
    val theme: AppThemeName = AppThemeName.MONET,
    val predictiveBack: Boolean = true,
) {
    /** Старая тема: никаких поведенческих изменений. */
    val isLegacy get() = theme == AppThemeName.MONET_OLD
    val sideOverlays get() = !isLegacy
    val expressiveDialogs get() = !isLegacy
    val slowPager get() = !isLegacy
    val tintedSquareIcons get() = !isLegacy
}

val LocalAppStyle = staticCompositionLocalOf { AppStyle() }

/** Доступ к стилю из любого места композиции. */
val CurrentAppStyle: AppStyle
    @Composable get() = LocalAppStyle.current

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeoTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    dynamicColors: Boolean = true,
    appTheme: AppThemeName = AppThemeName.MONET,
    predictiveBack: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when (appTheme) {
        AppThemeName.MONET_OLD -> when {
            dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            dark -> darkColorScheme(
                primary = BrandVioletDim,
                onPrimary = Color(0xFF151022),
                secondary = BrandTealDim,
            )
            else -> expressiveLightColorScheme().copy(
                primary = BrandViolet,
                secondary = BrandTeal,
            )
        }
        // цвета у Monet те же, что у Monet Old — тема меняет только стиль
        AppThemeName.MONET -> when {
            dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            dark -> darkColorScheme(
                primary = BrandVioletDim,
                onPrimary = Color(0xFF151022),
                secondary = BrandTealDim,
            )
            else -> expressiveLightColorScheme().copy(
                primary = BrandViolet,
                secondary = BrandTeal,
            )
        }
        AppThemeName.ORIGIN_OS -> if (dark) originGrayDark() else originGrayLight()
    }

    val shapes = if (appTheme == AppThemeName.MONET_OLD) NeoShapes else NeoShapesExpressive

    CompositionLocalProvider(
        LocalAppStyle provides AppStyle(appTheme, predictiveBack),
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = shapes,
            content = content,
        )
    }
}

/** Прежняя геометрия Monet Old. */
val NeoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Новая тема: чуть крупнее и мягче. */
val NeoShapesExpressive = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

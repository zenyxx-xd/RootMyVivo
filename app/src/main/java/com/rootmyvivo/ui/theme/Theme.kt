package com.rootmyvivo.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rootmyvivo.data.AppThemeName
import com.rootmyvivo.data.ThemeMode

// ─────────── Брендовые акценты ───────────

private val BrandViolet = Color(0xFF7C4DFF)
private val BrandVioletDim = Color(0xFF9F8CFF)
private val BrandTeal = Color(0xFF00BFA5)
private val BrandTealDim = Color(0xFF4DDAC8)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeoTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    dynamicColors: Boolean = true,
    appTheme: AppThemeName = AppThemeName.MONET,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val targetScheme = when {
        dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
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

    // Смена темы/режима/динамики — плавный переход палитры, а не мгновенный скачок
    val colorScheme = animateScheme(targetScheme)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = NeoShapes,
        content = content,
    )
}

/**
 * Анимация переключения темы: ключевые роли палитры перетекают
 * друг в друга за 450 мс (в т.ч. акценты — «акцентность»).
 */
@Composable
private fun animateScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(450)

    @Composable
    fun c(get: (ColorScheme) -> Color): Color =
        animateColorAsState(get(target), spec, label = "schemeColor").value

    return target.copy(
        primary = c { it.primary },
        onPrimary = c { it.onPrimary },
        primaryContainer = c { it.primaryContainer },
        onPrimaryContainer = c { it.onPrimaryContainer },
        secondary = c { it.secondary },
        onSecondary = c { it.onSecondary },
        secondaryContainer = c { it.secondaryContainer },
        onSecondaryContainer = c { it.onSecondaryContainer },
        tertiary = c { it.tertiary },
        onTertiary = c { it.onTertiary },
        tertiaryContainer = c { it.tertiaryContainer },
        onTertiaryContainer = c { it.onTertiaryContainer },
        error = c { it.error },
        errorContainer = c { it.errorContainer },
        background = c { it.background },
        onBackground = c { it.onBackground },
        surface = c { it.surface },
        onSurface = c { it.onSurface },
        surfaceVariant = c { it.surfaceVariant },
        onSurfaceVariant = c { it.onSurfaceVariant },
        surfaceContainerLowest = c { it.surfaceContainerLowest },
        surfaceContainerLow = c { it.surfaceContainerLow },
        surfaceContainer = c { it.surfaceContainer },
        surfaceContainerHigh = c { it.surfaceContainerHigh },
        surfaceContainerHighest = c { it.surfaceContainerHighest },
        outline = c { it.outline },
        outlineVariant = c { it.outlineVariant },
    )
}

/** Expressive-геометрия: крупные мягкие углы. */
val NeoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

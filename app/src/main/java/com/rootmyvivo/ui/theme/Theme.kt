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
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rootmyvivo.data.ThemeMode

// ─────────── Брендовые акценты поверх expressive-палитры ───────────

private val BrandViolet = Color(0xFF7C4DFF)
private val BrandVioletDim = Color(0xFF9F8CFF)
private val BrandTeal = Color(0xFF00BFA5)
private val BrandTealDim = Color(0xFF4DDAC8)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeoTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    dynamicColors: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
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

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = NeoShapes,
        content = content,
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

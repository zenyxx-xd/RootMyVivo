package com.rootmyvivo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.graphics.drawable.AdaptiveIconDrawable
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

// Брендовые цвета (fallback если dynamic color недоступен)
val BrandPrimary = Color(0xFF7C4DFF)
val BrandOnPrimary = Color.White
val BrandPrimaryContainer = Color(0xFFE8DEFF)
val BrandOnPrimaryContainer = Color(0xFF21005D)
val BrandSecondary = Color(0xFF00BFA5)
val BrandSurface = Color(0xFFFEF7FF)
val BrandSurfaceDark = Color(0xFF141218)

@Composable
fun RootMyVivoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) 
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = BrandPrimary,
            onPrimary = BrandOnPrimary,
            primaryContainer = Color(0xFF4A148C),
            onPrimaryContainer = Color(0xFFE8DEFF),
            secondary = BrandSecondary,
            surface = BrandSurfaceDark,
        )
        else -> lightColorScheme(
            primary = BrandPrimary,
            onPrimary = BrandOnPrimary,
            primaryContainer = BrandPrimaryContainer,
            onPrimaryContainer = BrandOnPrimaryContainer,
            secondary = BrandSecondary,
            surface = BrandSurface,
        )
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RmvTypography(),
        shapes = RmvShapes(),
        content = content,
    )
}

/** Material You 3 Expressive типографика */
@Composable
fun RmvTypography() = Typography(
    displayLarge = Typography().displayLarge.copy(
        // Крупный заголовок для главного экрана
    ),
    headlineLarge = Typography().headlineLarge.copy(),
    titleLarge = Typography().titleLarge.copy(),
    bodyLarge = Typography().bodyLarge.copy(),
    labelSmall = Typography().labelSmall.copy(),
)

/** Material You 3 Expressive формы — крупные скругления */
@Composable
fun RmvShapes() = Shapes(
    extraSmall = MaterialTheme.shapes.extraSmall,
    small = MaterialTheme.shapes.small,
    medium = MaterialTheme.shapes.medium,
    large = MaterialTheme.shapes.large,       // 16dp — карточки
    extraLarge = MaterialTheme.shapes.extraLarge, // 28dp — кнопки, диалоги
)

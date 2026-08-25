package com.rootmyvivo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Spring

/**
 * Главная кнопка ROOT в стиле Material You 3 Expressive:
 * крупная, с пружинной анимацией при нажатии.
 */
@Composable
fun RootButton(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isRunning: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "button_scale"
    )
    
    Button(
        onClick = onClick,
        enabled = enabled && !isRunning,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp,
        ),
        interactionSource = interactionSource,
    ) {
        if (isRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
            )
            Spacer(Modifier.width(12.dp))
            Text("Выполняется...", style = MaterialTheme.typography.titleMedium)
        } else {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// Extension для scale
private fun Modifier.scale(scale: Float): Modifier =
    this.then(androidx.compose.ui.draw.scale(scale))

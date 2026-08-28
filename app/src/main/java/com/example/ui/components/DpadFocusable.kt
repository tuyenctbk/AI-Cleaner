package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.dpadFocusable(
    interactionSource: MutableInteractionSource,
    shape: Shape = RoundedCornerShape(12.dp),
    borderWidth: Dp = 3.dp,
    borderColor: Color? = null
): Modifier = composed {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val finalColor = borderColor ?: MaterialTheme.colorScheme.primary
    if (isFocused) {
        this.border(borderWidth, finalColor, shape)
    } else {
        this
    }
}

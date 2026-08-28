package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AiScanningAnimation(
    progress: Float,
    stepText: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AiScanTransition")

    // Rotation angle for the radar sweep beam
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarRotation"
    )

    // Pulse scale for expanding concentric rings
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Floating particle angle
    val particleOrbit by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticleOrbit"
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.04f))
            .testTag("ai_scanning_animation_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(outlineColor.copy(alpha = 0.6f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Main Graphics Canvas Animation
            Box(
                modifier = Modifier
                    .size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerPx = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.width / 2.4f

                    // 1. Outer static dashed ring
                    drawCircle(
                        color = outlineColor.copy(alpha = 0.4f),
                        radius = baseRadius,
                        center = centerPx,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // 2. Pulsing concentric glowing aura
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.12f * (1.2f - pulseScale)),
                        radius = baseRadius * pulseScale,
                        center = centerPx
                    )

                    // 3. Rotating Radar Sweep Beam
                    rotate(rotationAngle, pivot = centerPx) {
                        val sweepBrush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                primaryColor.copy(alpha = 0.05f),
                                primaryColor.copy(alpha = 0.25f),
                                primaryColor.copy(alpha = 0.6f)
                            ),
                            center = centerPx
                        )
                        drawArc(
                            brush = sweepBrush,
                            startAngle = 0f,
                            sweepAngle = 90f,
                            useCenter = true,
                            topLeft = Offset(centerPx.x - baseRadius, centerPx.y - baseRadius),
                            size = androidx.compose.ui.geometry.Size(baseRadius * 2, baseRadius * 2)
                        )
                    }

                    // 4. Progress Arc
                    val progressAngle = progress * 360f
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = progressAngle,
                        useCenter = false,
                        topLeft = Offset(centerPx.x - baseRadius, centerPx.y - baseRadius),
                        size = androidx.compose.ui.geometry.Size(baseRadius * 2, baseRadius * 2),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 5. Floating AI Particles in Orbit
                    val particleCount = 4
                    for (i in 0 until particleCount) {
                        val angleRad = Math.toRadians((particleOrbit + i * (360f / particleCount)).toDouble())
                        val px = centerPx.x + (baseRadius * 0.75f) * cos(angleRad).toFloat()
                        val py = centerPx.y + (baseRadius * 0.75f) * sin(angleRad).toFloat()
                        drawCircle(
                            color = if (i % 2 == 0) primaryColor else secondaryColor,
                            radius = (4 + i % 3).dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                // Inner AI Core Badge
                Surface(
                    shape = CircleShape,
                    color = primaryContainerColor,
                    tonalElevation = 6.dp,
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Scanner",
                            tint = primaryColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Scanning Status Details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "AI Storage Engine Scanning",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = onSurfaceColor
                )
                Text(
                    text = stepText.ifEmpty { "Analyzing cache, temp files & media..." },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = onSurfaceVariantColor
                )
            }

            // Progress Bar & Percentage Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceVariantColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Deep Junk Identification",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = onSurfaceColor
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primaryColor
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

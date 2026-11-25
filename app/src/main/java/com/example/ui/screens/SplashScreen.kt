package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEmblem by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showFooter by remember { mutableStateOf(false) }
    val progressAnim = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "SplashOrbital")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OuterRingRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "InnerRingRotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AmbientPulse"
    )

    LaunchedEffect(Unit) {
        showEmblem = true
        delay(180)
        showTitle = true
        delay(160)
        showFooter = true
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing)
        )
        delay(140)
        onSplashFinished()
    }

    val cyanAccent = Color(0xFF38BDF8)
    val violetAccent = Color(0xFF8B5CF6)
    val aquaAccent = Color(0xFF2DD4BF)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070D19),
                        Color(0xFF0A1529),
                        Color(0xFF141B3A)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("app_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Ambient radial aura behind emblem
        Box(
            modifier = Modifier
                .size(300.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            cyanAccent.copy(alpha = 0.22f),
                            violetAccent.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Emblem + Counter-rotating Luminous Orbital Rings
            AnimatedVisibility(
                visible = showEmblem,
                enter = fadeIn(tween(450)) + scaleIn(
                    initialScale = 0.72f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(176.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerR = size.minDimension * 0.48f
                        val innerR = size.minDimension * 0.41f

                        // Subtle base ring track
                        drawCircle(
                            color = Color.White.copy(alpha = 0.06f),
                            radius = outerR,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Outer sweeping luminous arc
                        rotate(outerRotation, pivot = center) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        cyanAccent.copy(alpha = 0.25f),
                                        cyanAccent,
                                        aquaAccent
                                    ),
                                    center = center
                                ),
                                startAngle = 0f,
                                sweepAngle = 210f,
                                useCenter = false,
                                topLeft = Offset(center.x - outerR, center.y - outerR),
                                size = Size(outerR * 2f, outerR * 2f),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Counter-rotating violet inner arc
                        rotate(innerRotation, pivot = center) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        violetAccent.copy(alpha = 0.35f),
                                        violetAccent
                                    ),
                                    center = center
                                ),
                                startAngle = 90f,
                                sweepAngle = 140f,
                                useCenter = false,
                                topLeft = Offset(center.x - innerR, center.y - innerR),
                                size = Size(innerR * 2f, innerR * 2f),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Brand App Emblem Card
                    Surface(
                        shape = RoundedCornerShape(34.dp),
                        color = Color(0xFF0A1529),
                        shadowElevation = 20.dp,
                        modifier = Modifier
                            .size(116.dp)
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(listOf(cyanAccent, violetAccent, aquaAccent)),
                                shape = RoundedCornerShape(34.dp)
                            )
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brand_emblem),
                            contentDescription = stringResource(R.string.app_name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(34.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Staggered Title + Tagline Entrance
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.splash_tagline),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Smooth Gradient Progress Sweep
            AnimatedVisibility(
                visible = showFooter,
                enter = fadeIn(tween(350))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(164.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressAnim.value.coerceIn(0.05f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(cyanAccent, violetAccent, aquaAccent)
                                    )
                                )
                        )
                    }

                    Text(
                        text = stringResource(R.string.splash_loading),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Bottom Privacy & Version Pill
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .alpha(if (showFooter) 1f else 0f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = aquaAccent,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${stringResource(R.string.header_private_secure)} • v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

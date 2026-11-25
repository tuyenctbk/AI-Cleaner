package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.formatFileSize
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CleanSuccessDialog(
    freedBytes: Long,
    title: String,
    onDismiss: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    val countAnim = remember { Animatable(0f) }
    val burstAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entered = true
        burstAnim.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(freedBytes) {
        countAnim.snapTo(0f)
        countAnim.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
    }

    val badgeScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.55f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SuccessBadgeScale"
    )

    val displayedBytes = (freedBytes * countAnim.value).toLong()
    val primaryColor = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("clean_success_dialog"),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Celebratory Clean Illustration with Sparkle Burst Ring
                Box(
                    modifier = Modifier.size(124.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxR = size.minDimension * 0.48f
                        val p = burstAnim.value
                        val particleColors = listOf(EmeraldGreen, primaryColor, AmberGolden, VividViolet)
                        for (i in 0 until 10) {
                            val angle = Math.toRadians(i * 36.0)
                            val dist = (maxR * 0.55f) + (maxR * 0.45f * p)
                            val px = center.x + dist * cos(angle).toFloat()
                            val py = center.y + dist * sin(angle).toFloat()
                            val alpha = (1f - p * 0.65f).coerceIn(0.15f, 1f)
                            drawCircle(
                                color = particleColors[i % particleColors.size].copy(alpha = alpha),
                                radius = (4.5f - p * 1.5f).dp.toPx(),
                                center = Offset(px, py)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .scale(badgeScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_cleaning_trash_1787907390869),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Text details with animated count-up
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title.ifBlank { stringResource(R.string.dialog_clean_success_title) },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 21.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.dialog_clean_success),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(displayedBytes),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Performance status pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EmeraldGreen.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.clean_success_tagline),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        )
                    }
                }

                // 100% Free & No Ads Guarantee note
                Text(
                    text = stringResource(R.string.clean_success_free_forever),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Done Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("dismiss_success_button"),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = stringResource(R.string.btn_done),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

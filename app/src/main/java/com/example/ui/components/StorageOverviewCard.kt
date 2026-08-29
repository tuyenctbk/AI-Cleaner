package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StorageStats
import com.example.data.model.formatFileSize
import com.example.ui.theme.*
import com.example.R
import androidx.compose.ui.res.stringResource

@Composable
fun StorageOverviewCard(
    stats: StorageStats,
    isScanning: Boolean = false,
    onSmartCleanClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Usage Summary + Circular Ring
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Usage description and Pill
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.storage_status_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(R.string.stat_used_of_total, stats.formattedUsed, stats.formattedTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Available storage badge pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "${formatFileSize(stats.freeBytes)} " + stringResource(R.string.storage_donut_free),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Right: Circular Progress Ring
                Box(
                    modifier = Modifier.size(86.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val progress = (stats.usedPercent / 100f).coerceIn(0f, 1f)
                    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                    val primaryColor = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.size(80.dp)) {
                        val strokeWidth = 8.dp.toPx()
                        // Background track
                        drawCircle(
                            color = surfaceVariantColor,
                            style = Stroke(width = strokeWidth)
                        )
                        // Progress arc
                        drawArc(
                            color = primaryColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${stats.usedPercent}%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.storage_donut_center_used),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Segmented Storage Progress Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val total = stats.totalBytes.toFloat().coerceAtLeast(1f)
                val photoRatio = (stats.photoBytes.toFloat() / total).coerceIn(0.05f, 0.5f)
                val videoRatio = (stats.videoBytes.toFloat() / total).coerceIn(0.05f, 0.4f)
                val junkRatio = (stats.junkBytes.toFloat() / total).coerceIn(0.03f, 0.2f)
                val otherRatio = (1f - (photoRatio + videoRatio + junkRatio)).coerceAtLeast(0.1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(photoRatio)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(videoRatio)
                            .background(ColorVideos)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(junkRatio)
                            .background(ColorJunk)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(otherRatio)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }

                // Storage Segment Legend Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StorageLegendItem(color = MaterialTheme.colorScheme.primary, label = stringResource(R.string.storage_donut_photos), size = stats.photoBytes)
                    StorageLegendItem(color = ColorVideos, label = stringResource(R.string.storage_donut_videos), size = stats.videoBytes)
                    StorageLegendItem(color = ColorJunk, label = stringResource(R.string.storage_donut_junk), size = stats.junkBytes)
                    StorageLegendItem(color = MaterialTheme.colorScheme.outline, label = stringResource(R.string.storage_donut_free), size = stats.freeBytes)
                }
            }

            // Summary text badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stat_media_files, stats.photoCount + stats.videoCount),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.stat_can_be_freed, stats.formattedJunk),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // AI Smart Clean Action Button (Solid Polish Primary with full rounded corners)
            Button(
                onClick = onSmartCleanClick,
                enabled = !isScanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("ai_smart_clean_button"),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isScanning) stringResource(R.string.smart_clean_scanning) else stringResource(R.string.btn_ai_smart_clean),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageLegendItem(
    color: Color,
    label: String,
    size: Long
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


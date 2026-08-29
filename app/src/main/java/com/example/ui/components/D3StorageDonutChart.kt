package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StorageStats
import com.example.data.model.formatFileSize
import com.example.ui.theme.*

import com.example.R
import androidx.compose.ui.res.stringResource

data class ChartSlice(
    val label: String,
    val bytes: Long,
    val color: Color
)

@Composable
fun D3StorageDonutChart(
    stats: StorageStats,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val photoBytes = stats.photoBytes
    val videoBytes = stats.videoBytes
    val appBytes = stats.appBytes
    val docBytes = stats.docBytes
    val systemBytes = stats.systemBytes + stats.junkBytes
    val freeBytes = stats.freeBytes

    val labelPhotos = stringResource(R.string.category_photos)
    val labelVideos = stringResource(R.string.category_videos)
    val labelApps = stringResource(R.string.storage_cat_apps)
    val labelSystem = stringResource(R.string.storage_cat_system)
    val labelDocs = stringResource(R.string.storage_cat_docs)
    val labelFree = stringResource(R.string.stat_free)

    val slices = remember(stats, labelPhotos, labelVideos, labelApps, labelSystem, labelDocs, labelFree) {
        listOf(
            ChartSlice(labelPhotos, photoBytes, ColorPhotos),
            ChartSlice(labelVideos, videoBytes, ColorVideos),
            ChartSlice(labelApps, appBytes, ColorApps),
            ChartSlice(labelSystem, systemBytes, ColorSystem),
            ChartSlice(labelDocs, docBytes, ColorDocs),
            ChartSlice(labelFree, freeBytes, EmeraldGreen)
        )
    }

    val totalBytes = remember(slices) {
        slices.sumOf { it.bytes }.coerceAtLeast(1L)
    }

    var selectedIndex by remember { mutableIntStateOf(-1) }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(stats) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val selectedSlice = if (selectedIndex in slices.indices) slices[selectedIndex] else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.03f))
            .testTag("d3_storage_donut_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.title_storage_breakdown),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selectedSlice != null) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedIndex = -1
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.btn_reset), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Donut Chart Canvas Container
            Box(
                modifier = Modifier
                    .size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 24.dp.toPx()
                    val diameter = size.minDimension - strokeWidth - 8.dp.toPx()
                    val arcSize = Size(diameter, diameter)
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

                    var startAngle = -90f
                    val sweepFactor = animProgress.value

                    slices.forEachIndexed { index, slice ->
                        val sweepAngle = (slice.bytes.toFloat() / totalBytes.toFloat()) * 360f * sweepFactor
                        val isSelected = (index == selectedIndex)
                        val currentStrokeWidth = if (isSelected) strokeWidth + 6.dp.toPx() else strokeWidth

                        if (sweepAngle > 0.5f) {
                            drawArc(
                                color = if (selectedIndex == -1 || isSelected) slice.color else slice.color.copy(alpha = 0.35f),
                                startAngle = startAngle + 1f,
                                sweepAngle = (sweepAngle - 2f).coerceAtLeast(0.5f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = currentStrokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        startAngle += sweepAngle
                    }
                }

                // Center Label
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = selectedSlice?.label ?: "Total Storage",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (selectedSlice != null) formatFileSize(selectedSlice.bytes) else stats.formattedTotal,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedSlice != null) {
                            "${((selectedSlice.bytes.toFloat() / totalBytes.toFloat()) * 100).toInt()}% of total"
                        } else {
                            "${((stats.usedBytes.toFloat() / stats.totalBytes.coerceAtLeast(1).toFloat()) * 100).toInt()}% Used"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = selectedSlice?.color ?: MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Interactive Legend Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pairs = slices.chunked(2)
                pairs.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { slice ->
                            val index = slices.indexOf(slice)
                            val isSelected = (index == selectedIndex)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedIndex = if (selectedIndex == index) -1 else index
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) slice.color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = SolidColor(slice.color)) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(slice.color)
                                        )
                                        Text(
                                            text = slice.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = formatFileSize(slice.bytes),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

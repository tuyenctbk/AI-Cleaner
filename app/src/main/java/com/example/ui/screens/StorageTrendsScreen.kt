package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StorageTrendPoint
import com.example.data.model.formatFileSize
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState

import com.example.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageTrendsScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = uiState.storageTrendSummary
    val haptic = LocalHapticFeedback.current

    var selectedTimeframeDays by remember { mutableIntStateOf(30) }

    val filteredPoints = remember(summary.trendPoints, selectedTimeframeDays) {
        summary.trendPoints.takeLast(selectedTimeframeDays)
    }

    val burstPoints = remember(filteredPoints) {
        filteredPoints.filter { it.isRapidAccumulationBurst }
    }
    val timeframeDeltaBytes = remember(filteredPoints) {
        filteredPoints.sumOf { it.deltaBytes }
    }
    val timeframePeakDay = remember(filteredPoints, summary.highestBurstDay) {
        val peak = filteredPoints.maxByOrNull { it.deltaBytes }
        if (peak != null && peak.deltaBytes > 0L) peak.dayLabel else summary.highestBurstDay
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_storage_trends),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Timeframe Filter Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeframes = listOf(7, 14, 30)
                    timeframes.forEach { days ->
                        val isSelected = selectedTimeframeDays == days
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTimeframeDays = days
                            },
                            label = { Text(stringResource(R.string.trends_days_fmt, days), fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue,
                                selectedLabelColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            // High Level Summary Metrics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(ElectricBlue, VividViolet))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = ElectricBlue)
                                Text(
                                    text = stringResource(R.string.trends_accumulation_delta_fmt, selectedTimeframeDays),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = if (timeframeDeltaBytes > 0L) stringResource(R.string.storage_delta_plus_fmt, formatFileSize(timeframeDeltaBytes)) else formatFileSize(0L),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (timeframeDeltaBytes > 0L) CoralRed else EmeraldGreen
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(stringResource(R.string.trends_top_growth_driver), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    summary.topGrowthCategory.ifBlank { stringResource(R.string.trends_normal_usage) },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.trends_peak_accumulation), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    timeframePeakDay.ifBlank { stringResource(R.string.trends_none) },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (timeframeDeltaBytes > 0L) CoralRed else EmeraldGreen
                                )
                            }
                        }
                    }
                }
            }

            // Native Smooth Canvas Trend Curve Chart
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("storage_trend_canvas_chart"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.trends_growth_trend_graph),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Smooth Trend Canvas Drawing
                        StorageTrendCanvasChart(
                            points = filteredPoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = filteredPoints.firstOrNull()?.dayLabel ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.trends_accumulation_delta_label),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElectricBlue
                            )
                            Text(
                                text = filteredPoints.lastOrNull()?.dayLabel ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Rapid Accumulation Burst Cards Header
            if (burstPoints.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = CoralRed)
                        Text(
                            text = stringResource(R.string.trends_bursts_detected),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(burstPoints) { burstPoint ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(CoralRed, VibrantOrange))
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(CoralRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, tint = CoralRed)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trends_burst_event_fmt, burstPoint.dayLabel),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = burstPoint.burstDescription ?: stringResource(R.string.trends_abnormal_accumulation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = burstPoint.formattedDelta,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = CoralRed
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StorageTrendCanvasChart(
    points: List<StorageTrendPoint>,
    modifier: Modifier = Modifier
) {
    val revealProgress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        revealProgress.snapTo(0f)
        revealProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val width = size.width
        val height = size.height

        val maxVal = points.maxOf { it.usedBytes }.toFloat()
        val minVal = points.minOf { it.usedBytes }.toFloat()
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val dx = width / (points.size - 1)

        val path = Path()
        val fillPath = Path()

        val strokeColor = ElectricBlue
        val burstColor = CoralRed

        points.forEachIndexed { i, pt ->
            val x = i * dx
            val normalizedY = 1f - ((pt.usedBytes - minVal) / range)
            val y = (normalizedY * (height - 40.dp.toPx())) + 20.dp.toPx()

            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (i - 1) * dx
                val prevPt = points[i - 1]
                val prevNormY = 1f - ((prevPt.usedBytes - minVal) / range)
                val prevY = (prevNormY * (height - 40.dp.toPx())) + 20.dp.toPx()

                val controlX1 = prevX + (dx / 2)
                val controlY1 = prevY
                val controlX2 = prevX + (dx / 2)
                val controlY2 = y

                path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
            }

            if (i == points.size - 1) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }

        clipRect(right = width * revealProgress.value) {
            // Draw area gradient fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(strokeColor.copy(alpha = 0.32f), strokeColor.copy(alpha = 0.0f))
                )
            )

            // Draw trend curve
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw burst indicators & data points
            points.forEachIndexed { i, pt ->
                val x = i * dx
                val normalizedY = 1f - ((pt.usedBytes - minVal) / range)
                val y = (normalizedY * (height - 40.dp.toPx())) + 20.dp.toPx()

                if (pt.isRapidAccumulationBurst) {
                    drawCircle(
                        color = burstColor,
                        radius = 7.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                } else if (i % 5 == 0 || i == points.size - 1) {
                    drawCircle(
                        color = strokeColor,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

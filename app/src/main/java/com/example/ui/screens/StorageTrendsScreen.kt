package com.example.ui.screens

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Storage Trends & Accumulation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PolishTextPrimary
                        )
                        Text(
                            text = "30-Day Delta & Rapid Accumulation Analysis",
                            style = MaterialTheme.typography.labelSmall,
                            color = PolishTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PolishTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PolishBackground)
            )
        },
        containerColor = PolishBackground,
        modifier = modifier
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
                    val timeframes = listOf(7 to "7 Days", 14 to "14 Days", 30 to "30 Days")
                    timeframes.forEach { (days, label) ->
                        val isSelected = selectedTimeframeDays == days
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTimeframeDays = days
                            },
                            label = { Text(label, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue,
                                selectedLabelColor = Color.White,
                                containerColor = PolishSurface,
                                labelColor = PolishTextPrimary
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
                    colors = CardDefaults.cardColors(containerColor = PolishSurface),
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
                                Icon(imageVector = Icons.Default.TrendingUp, contentDescription = null, tint = ElectricBlue)
                                Text(
                                    text = "Accumulation Delta ($selectedTimeframeDays Days)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PolishTextPrimary
                                )
                            }

                            Text(
                                text = "+${formatFileSize(filteredPoints.sumOf { it.deltaBytes })}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = CoralRed
                            )
                        }

                        Divider(color = PolishOutline.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Top Growth Driver", style = MaterialTheme.typography.labelSmall, color = PolishTextSecondary)
                                Text(summary.topGrowthCategory, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PolishTextPrimary)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Peak Accumulation", style = MaterialTheme.typography.labelSmall, color = PolishTextSecondary)
                                Text(summary.highestBurstDay, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = CoralRed)
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
                    colors = CardDefaults.cardColors(containerColor = PolishSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Daily Growth Trend Graph",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = PolishTextPrimary
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
                                color = PolishTextSecondary
                            )
                            Text(
                                text = "Daily Storage Accumulation Delta",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElectricBlue
                            )
                            Text(
                                text = filteredPoints.lastOrNull()?.dayLabel ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = PolishTextSecondary
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
                            text = "Rapid Accumulation Bursts Detected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PolishTextPrimary
                        )
                    }
                }

                items(burstPoints) { burstPoint ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PolishSurfaceVariant),
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
                                    text = burstPoint.dayLabel + " Burst Event",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PolishTextPrimary
                                )
                                Text(
                                    text = burstPoint.burstDescription ?: "Abnormal accumulation detected.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PolishTextSecondary
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

        val strokeColor = Color(0xFF00E5FF)
        val burstColor = Color(0xFFFF5252)

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

        // Draw area gradient fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(strokeColor.copy(alpha = 0.35f), strokeColor.copy(alpha = 0.0f))
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

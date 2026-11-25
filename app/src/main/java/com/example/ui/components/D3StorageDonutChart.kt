package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.PieChart
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.StorageStats
import com.example.data.model.formatFileSize
import com.example.ui.theme.*
import kotlin.math.roundToInt

data class ChartSlice(
    val label: String,
    val bytes: Long,
    val color: Color,
    val fileTypeCategory: StorageFileTypeCategory? = null
)

@Composable
fun D3StorageDonutChart(
    stats: StorageStats,
    modifier: Modifier = Modifier,
    initialVisualStyle: ChartVisualStyle = ChartVisualStyle.DONUT,
    initialScope: StorageChartScope = StorageChartScope.ALL_STORAGE,
    showModeControls: Boolean = true,
    selectedIndexOverride: Int? = null,
    onSliceSelected: ((Int) -> Unit)? = null,
    onScopeChanged: ((StorageChartScope) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    var visualStyle by remember(initialVisualStyle) { mutableStateOf(initialVisualStyle) }
    var chartScope by remember(initialScope) { mutableStateOf(initialScope) }

    val labelPhotos = stringResource(R.string.category_photos)
    val labelVideos = stringResource(R.string.category_videos)
    val labelApps = stringResource(R.string.storage_cat_apps)
    val labelSystem = stringResource(R.string.storage_cat_system)
    val labelDocs = stringResource(R.string.storage_cat_docs)
    val labelFree = stringResource(R.string.stat_free)

    val slices = remember(
        stats,
        chartScope,
        labelPhotos,
        labelVideos,
        labelApps,
        labelSystem,
        labelDocs,
        labelFree
    ) {
        val fileTypeSlices = listOf(
            ChartSlice(labelPhotos, stats.photoBytes.coerceAtLeast(0L), ColorPhotos, StorageFileTypeCategory.PHOTOS),
            ChartSlice(labelVideos, stats.videoBytes.coerceAtLeast(0L), ColorVideos, StorageFileTypeCategory.VIDEOS),
            ChartSlice(labelApps, stats.appBytes.coerceAtLeast(0L), ColorApps, StorageFileTypeCategory.APPS),
            ChartSlice(labelDocs, stats.docBytes.coerceAtLeast(0L), ColorDocs, StorageFileTypeCategory.DOCUMENTS)
        )
        if (chartScope == StorageChartScope.FILE_TYPES) {
            fileTypeSlices
        } else {
            listOf(
                ChartSlice(labelPhotos, stats.photoBytes.coerceAtLeast(0L), ColorPhotos, StorageFileTypeCategory.PHOTOS),
                ChartSlice(labelVideos, stats.videoBytes.coerceAtLeast(0L), ColorVideos, StorageFileTypeCategory.VIDEOS),
                ChartSlice(labelApps, stats.appBytes.coerceAtLeast(0L), ColorApps, StorageFileTypeCategory.APPS),
                ChartSlice(labelSystem, (stats.systemBytes + stats.junkBytes).coerceAtLeast(0L), ColorSystem),
                ChartSlice(labelDocs, stats.docBytes.coerceAtLeast(0L), ColorDocs, StorageFileTypeCategory.DOCUMENTS),
                ChartSlice(labelFree, stats.freeBytes.coerceAtLeast(0L), EmeraldGreen)
            )
        }
    }

    val rawTotalBytes = remember(slices) { slices.sumOf { it.bytes } }
    val totalBytes = remember(rawTotalBytes) { rawTotalBytes.coerceAtLeast(1L) }

    var internalSelectedIndex by remember { mutableIntStateOf(-1) }
    val selectedIndex = selectedIndexOverride ?: internalSelectedIndex

    fun updateSelection(nextIndex: Int) {
        val resolved = if (selectedIndex == nextIndex) -1 else nextIndex
        if (selectedIndexOverride == null) {
            internalSelectedIndex = resolved
        }
        onSliceSelected?.invoke(resolved)
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(stats, chartScope, visualStyle) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
    }

    val selectedSlice = if (selectedIndex in slices.indices) slices[selectedIndex] else null
    val sliceBytesList = remember(slices) { slices.map { it.bytes } }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val context = LocalContext.current

    val chartTitle = if (chartScope == StorageChartScope.FILE_TYPES) {
        stringResource(R.string.analyzer_by_file_type_title)
    } else {
        stringResource(R.string.title_storage_breakdown)
    }
    val chartContentDesc = chartTitle

    val popOutDp by animateDpAsState(
        targetValue = if (selectedIndex >= 0) 9.dp else 0.dp,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "PieSlicePopOut"
    )

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chartTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedSlice != null) {
                            val pct = if (rawTotalBytes > 0L) ((selectedSlice.bytes.toFloat() / rawTotalBytes.toFloat()) * 100f).roundToInt() else 0
                            "${selectedSlice.label} • ${formatFileSize(selectedSlice.bytes)} ($pct%)"
                        } else {
                            stringResource(R.string.analyzer_tap_slice_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = selectedSlice?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (selectedSlice != null) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            updateSelection(selectedIndex)
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("chart_reset_selection_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_reset),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Optional Mode Switchers (Pie / Donut & File Types / All Storage)
            if (showModeControls) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = visualStyle == ChartVisualStyle.PIE,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            visualStyle = ChartVisualStyle.PIE
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.analyzer_chart_style_pie),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("chart_mode_pie_chip")
                    )

                    FilterChip(
                        selected = visualStyle == ChartVisualStyle.DONUT,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            visualStyle = ChartVisualStyle.DONUT
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.analyzer_chart_style_donut),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DonutLarge,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("chart_mode_donut_chip")
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    FilterChip(
                        selected = chartScope == StorageChartScope.FILE_TYPES,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val nextScope = if (chartScope == StorageChartScope.FILE_TYPES) {
                                StorageChartScope.ALL_STORAGE
                            } else {
                                StorageChartScope.FILE_TYPES
                            }
                            chartScope = nextScope
                            if (selectedIndexOverride == null) internalSelectedIndex = -1
                            onSliceSelected?.invoke(-1)
                            onScopeChanged?.invoke(nextScope)
                        },
                        label = {
                            Text(
                                text = if (chartScope == StorageChartScope.FILE_TYPES) {
                                    stringResource(R.string.analyzer_scope_file_types)
                                } else {
                                    stringResource(R.string.analyzer_scope_all_storage)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("chart_scope_toggle_chip")
                    )
                }
            }

            // Interactive D3 Pie / Donut Chart Canvas Container
            Box(
                modifier = Modifier
                    .size(236.dp)
                    .semantics { contentDescription = chartContentDesc },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("d3_storage_chart_canvas")
                        .pointerInput(sliceBytesList, visualStyle, selectedIndex) {
                            detectTapGestures { offset ->
                                val outerRadius = if (visualStyle == ChartVisualStyle.PIE) {
                                    (size.width.coerceAtMost(size.height) / 2f) - 12.dp.toPx()
                                } else {
                                    (size.width.coerceAtMost(size.height) / 2f) - 4.dp.toPx()
                                }
                                val innerRadius = if (visualStyle == ChartVisualStyle.PIE) {
                                    0f
                                } else {
                                    (outerRadius - 42.dp.toPx()).coerceAtLeast(0f)
                                }
                                val tappedIndex = StorageAnalyzerCalculator.findSliceIndexAtTap(
                                    tapX = offset.x,
                                    tapY = offset.y,
                                    width = size.width.toFloat(),
                                    height = size.height.toFloat(),
                                    outerRadiusPx = outerRadius,
                                    innerRadiusPx = innerRadius,
                                    sliceBytes = sliceBytesList
                                )
                                if (tappedIndex >= 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    updateSelection(tappedIndex)
                                } else if (selectedIndex >= 0) {
                                    updateSelection(selectedIndex)
                                }
                            }
                        }
                ) {
                    val sweepFactor = animProgress.value
                    val spans = StorageAnalyzerCalculator.computeSliceAngles(sliceBytesList)

                    if (rawTotalBytes <= 0L) {
                        if (visualStyle == ChartVisualStyle.PIE) {
                            val r = (size.minDimension / 2f) - 14.dp.toPx()
                            drawCircle(
                                color = surfaceVariantColor,
                                radius = r,
                                center = Offset(size.width / 2f, size.height / 2f)
                            )
                        } else {
                            val strokeWidth = 26.dp.toPx()
                            val diameter = size.minDimension - strokeWidth - 16.dp.toPx()
                            drawArc(
                                color = surfaceVariantColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f),
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeWidth)
                            )
                        }
                    } else if (visualStyle == ChartVisualStyle.PIE) {
                        // D3-style Filled Pie Chart with crisp dividers, radial pop-out, and percentage callouts
                        val popMargin = 14.dp.toPx()
                        val pieDiameter = size.minDimension - (popMargin * 2f)
                        val pieSize = Size(pieDiameter, pieDiameter)
                        val baseTopLeft = Offset(
                            (size.width - pieDiameter) / 2f,
                            (size.height - pieDiameter) / 2f
                        )

                        spans.forEach { span ->
                            val slice = slices[span.index]
                            val animatedSweep = span.sweepAngleDeg * sweepFactor
                            if (animatedSweep > 0.4f) {
                                val isSelected = (span.index == selectedIndex)
                                val popOffsetPx = if (isSelected) popOutDp.toPx() else 0f
                                val (dx, dy) = StorageAnalyzerCalculator.computeSliceCenterOffset(
                                    startAngleDeg = span.startAngleDeg,
                                    sweepAngleDeg = animatedSweep,
                                    distancePx = popOffsetPx
                                )
                                val sliceTopLeft = Offset(baseTopLeft.x + dx, baseTopLeft.y + dy)
                                val sliceColor = if (selectedIndex == -1 || isSelected) {
                                    slice.color
                                } else {
                                    slice.color.copy(alpha = 0.42f)
                                }

                                drawArc(
                                    color = sliceColor,
                                    startAngle = span.startAngleDeg,
                                    sweepAngle = animatedSweep,
                                    useCenter = true,
                                    topLeft = sliceTopLeft,
                                    size = pieSize
                                )

                                // Crisp D3 wedge divider outline
                                drawArc(
                                    color = surfaceColor,
                                    startAngle = span.startAngleDeg,
                                    sweepAngle = animatedSweep,
                                    useCenter = true,
                                    topLeft = sliceTopLeft,
                                    size = pieSize,
                                    style = Stroke(width = if (isSelected) 3.dp.toPx() else 2.dp.toPx())
                                )

                                // D3 percentage badge inside sufficiently large wedges (>= 8%)
                                val pct = ((slice.bytes.toFloat() / rawTotalBytes.toFloat()) * 100f).roundToInt()
                                if (pct >= 8 && sweepFactor > 0.85f) {
                                    val labelRadius = (pieDiameter / 2f) * 0.62f + popOffsetPx
                                    val (lx, ly) = StorageAnalyzerCalculator.computeSliceCenterOffset(
                                        startAngleDeg = span.startAngleDeg,
                                        sweepAngleDeg = animatedSweep,
                                        distancePx = labelRadius
                                    )
                                    val labelText = context.getString(R.string.format_percentage, pct)
                                    val measured = textMeasurer.measure(
                                        text = labelText,
                                        style = TextStyle(
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    )
                                    val tx = (size.width / 2f + lx) - (measured.size.width / 2f)
                                    val ty = (size.height / 2f + ly) - (measured.size.height / 2f)
                                    drawText(
                                        textLayoutResult = measured,
                                        topLeft = Offset(tx, ty)
                                    )
                                }
                            }
                        }
                    } else {
                        // D3-style Donut Ring Chart with radial pop-out and center metric readout
                        val strokeWidth = 26.dp.toPx()
                        val diameter = size.minDimension - strokeWidth - 18.dp.toPx()
                        val arcSize = Size(diameter, diameter)
                        val baseTopLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

                        spans.forEach { span ->
                            val slice = slices[span.index]
                            val sweepAngle = span.sweepAngleDeg * sweepFactor
                            val isSelected = (span.index == selectedIndex)
                            val currentStrokeWidth = if (isSelected) strokeWidth + 6.dp.toPx() else strokeWidth
                            val popOffsetPx = if (isSelected) (popOutDp.toPx() * 0.55f) else 0f
                            val (dx, dy) = StorageAnalyzerCalculator.computeSliceCenterOffset(
                                startAngleDeg = span.startAngleDeg,
                                sweepAngleDeg = sweepAngle,
                                distancePx = popOffsetPx
                            )

                            if (sweepAngle > 0.5f) {
                                drawArc(
                                    color = if (selectedIndex == -1 || isSelected) slice.color else slice.color.copy(alpha = 0.35f),
                                    startAngle = span.startAngleDeg + 1f,
                                    sweepAngle = (sweepAngle - 2f).coerceAtLeast(0.5f),
                                    useCenter = false,
                                    topLeft = Offset(baseTopLeft.x + dx, baseTopLeft.y + dy),
                                    size = arcSize,
                                    style = Stroke(width = currentStrokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
                }

                // Center Label (Donut mode only, or empty-state message in Pie mode)
                if (visualStyle == ChartVisualStyle.DONUT || rawTotalBytes <= 0L) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        val centerLabel = when {
                            rawTotalBytes <= 0L -> stringResource(R.string.analyzer_categorized_total)
                            selectedSlice != null -> selectedSlice.label
                            chartScope == StorageChartScope.FILE_TYPES -> stringResource(R.string.analyzer_categorized_total)
                            else -> stringResource(R.string.donut_total_storage)
                        }
                        val centerValue = when {
                            rawTotalBytes <= 0L -> formatFileSize(0L)
                            selectedSlice != null -> formatFileSize(selectedSlice.bytes)
                            chartScope == StorageChartScope.FILE_TYPES -> formatFileSize(rawTotalBytes)
                            else -> stats.formattedTotal
                        }
                        val centerSub = when {
                            rawTotalBytes <= 0L -> stringResource(R.string.analyzer_empty_categorized)
                            selectedSlice != null -> stringResource(
                                R.string.donut_percent_of_total_fmt,
                                ((selectedSlice.bytes.toFloat() / totalBytes.toFloat()) * 100f).roundToInt()
                            )
                            chartScope == StorageChartScope.FILE_TYPES -> {
                                val pct = if (stats.totalBytes > 0L) {
                                    ((rawTotalBytes.toFloat() / stats.totalBytes.toFloat()) * 100f).roundToInt()
                                } else 0
                                stringResource(R.string.donut_percent_of_total_fmt, pct)
                            }
                            else -> stringResource(
                                R.string.stat_used_percent,
                                ((stats.usedBytes.toFloat() / stats.totalBytes.coerceAtLeast(1L).toFloat()) * 100f).roundToInt()
                            )
                        }

                        Text(
                            text = centerLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = centerValue,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = centerSub,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = selectedSlice?.color ?: MaterialTheme.colorScheme.primary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Interactive Legend Grid (minimum 48.dp touch target per item)
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
                            val slicePct = if (rawTotalBytes > 0L) {
                                ((slice.bytes.toFloat() / rawTotalBytes.toFloat()) * 100f).roundToInt()
                            } else 0
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        updateSelection(index)
                                    }
                                    .testTag("chart_legend_item_$index"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) slice.color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = SolidColor(slice.color)) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(slice.color)
                                        )
                                        Text(
                                            text = slice.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatFileSize(slice.bytes),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.format_percentage, slicePct),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = slice.color
                                        )
                                    }
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

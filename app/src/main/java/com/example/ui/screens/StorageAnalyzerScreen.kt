package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.StorageStats
import com.example.data.model.formatFileSize
import com.example.ui.components.*
import com.example.ui.theme.EmeraldGreen
import com.example.ui.viewmodel.CleanerUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenCompressor: () -> Unit,
    onOpenAppCache: () -> Unit,
    onOpenStorageExplorer: (initialExtensionFilter: String) -> Unit,
    onOpenQuickCleanup: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val stats = uiState.storageStats
    val haptic = LocalHapticFeedback.current

    val docCount = remember(uiState.allMedia) {
        StorageAnalyzerCalculator.countDocumentItems(uiState.allMedia)
    }

    val fileTypeEntries = remember(stats, docCount) {
        StorageAnalyzerCalculator.buildFileTypeBreakdown(
            stats = stats,
            docCount = if (docCount > 0) docCount else null
        )
    }

    var currentScope by remember { mutableStateOf(StorageChartScope.FILE_TYPES) }
    var selectedChartIndex by remember { mutableIntStateOf(-1) }

    // Map chart slice index to fileTypeEntries index cleanly across scopes:
    // FILE_TYPES slices order: [0]=Photos, [1]=Videos, [2]=Apps, [3]=Docs
    // ALL_STORAGE slices order: [0]=Photos, [1]=Videos, [2]=Apps, [3]=System, [4]=Docs, [5]=Free
    fun entryIndexForChartIndex(chartIdx: Int, scope: StorageChartScope): Int {
        if (chartIdx < 0) return -1
        return if (scope == StorageChartScope.FILE_TYPES) {
            if (chartIdx in fileTypeEntries.indices) chartIdx else -1
        } else {
            when (chartIdx) {
                0 -> 0 // Photos
                1 -> 1 // Videos
                2 -> 2 // Apps
                4 -> 3 // Documents
                else -> -1
            }
        }
    }

    fun chartIndexForEntryIndex(entryIdx: Int, scope: StorageChartScope): Int {
        if (entryIdx !in fileTypeEntries.indices) return -1
        return if (scope == StorageChartScope.FILE_TYPES) {
            entryIdx
        } else {
            when (entryIdx) {
                0 -> 0
                1 -> 1
                2 -> 2
                3 -> 4
                else -> -1
            }
        }
    }

    val selectedEntryIndex = entryIndexForChartIndex(selectedChartIndex, currentScope)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.title_storage_analyzer),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.analyzer_by_file_type_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("storage_analyzer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRefresh()
                        },
                        enabled = !uiState.isScanning,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("storage_analyzer_refresh_button")
                    ) {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.btn_refresh_scan),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 720.dp

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("storage_analyzer_chart_pane"),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            D3StorageDonutChart(
                                stats = stats,
                                initialVisualStyle = ChartVisualStyle.PIE,
                                initialScope = StorageChartScope.FILE_TYPES,
                                showModeControls = true,
                                selectedIndexOverride = selectedChartIndex,
                                onSliceSelected = { selectedChartIndex = it },
                                onScopeChanged = {
                                    currentScope = it
                                    selectedChartIndex = -1
                                }
                            )
                        }
                        item {
                            DeviceCapacityOverviewBanner(stats = stats)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .testTag("storage_analyzer_cards_pane"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            SectionHeaderRow(stats = stats)
                        }
                        itemsIndexed(fileTypeEntries, key = { _, e -> e.category.name }) { idx, entry ->
                            FileTypeBreakdownCard(
                                entry = entry,
                                isSelected = idx == selectedEntryIndex,
                                onToggleSelect = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val mapped = chartIndexForEntryIndex(idx, currentScope)
                                    selectedChartIndex = if (selectedChartIndex == mapped) -1 else mapped
                                },
                                onActionClick = {
                                    when (entry.category) {
                                        StorageFileTypeCategory.PHOTOS -> onOpenDuplicates()
                                        StorageFileTypeCategory.VIDEOS -> onOpenCompressor()
                                        StorageFileTypeCategory.APPS -> onOpenAppCache()
                                        StorageFileTypeCategory.DOCUMENTS -> onOpenStorageExplorer(".pdf")
                                    }
                                }
                            )
                        }
                        item {
                            if (onOpenQuickCleanup != null) {
                                QuickCleanupShortcutCard(onClick = onOpenQuickCleanup)
                            }
                        }
                        item {
                            ExploreLargeFilesCtaCard(
                                largeFileCount = stats.largeFileCount,
                                onClick = { onOpenStorageExplorer("All") }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("storage_analyzer_lazy_column"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        D3StorageDonutChart(
                            stats = stats,
                            initialVisualStyle = ChartVisualStyle.PIE,
                            initialScope = StorageChartScope.FILE_TYPES,
                            showModeControls = true,
                            selectedIndexOverride = selectedChartIndex,
                            onSliceSelected = { selectedChartIndex = it },
                            onScopeChanged = {
                                currentScope = it
                                selectedChartIndex = -1
                            }
                        )
                    }

                    item {
                        DeviceCapacityOverviewBanner(stats = stats)
                    }

                    item {
                        SectionHeaderRow(stats = stats)
                    }

                    itemsIndexed(fileTypeEntries, key = { _, e -> e.category.name }) { idx, entry ->
                        FileTypeBreakdownCard(
                            entry = entry,
                            isSelected = idx == selectedEntryIndex,
                            onToggleSelect = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val mapped = chartIndexForEntryIndex(idx, currentScope)
                                selectedChartIndex = if (selectedChartIndex == mapped) -1 else mapped
                            },
                            onActionClick = {
                                when (entry.category) {
                                    StorageFileTypeCategory.PHOTOS -> onOpenDuplicates()
                                    StorageFileTypeCategory.VIDEOS -> onOpenCompressor()
                                    StorageFileTypeCategory.APPS -> onOpenAppCache()
                                    StorageFileTypeCategory.DOCUMENTS -> onOpenStorageExplorer(".pdf")
                                }
                            }
                        )
                    }

                    if (onOpenQuickCleanup != null) {
                        item {
                            QuickCleanupShortcutCard(onClick = onOpenQuickCleanup)
                        }
                    }

                    item {
                        ExploreLargeFilesCtaCard(
                            largeFileCount = stats.largeFileCount,
                            onClick = { onOpenStorageExplorer("All") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(stats: StorageStats) {
    val categorizedTotal = (stats.photoBytes + stats.videoBytes + stats.appBytes + stats.docBytes).coerceAtLeast(0L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.analyzer_by_file_type_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = formatFileSize(categorizedTotal),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DeviceCapacityOverviewBanner(stats: StorageStats) {
    val usedPct = if (stats.totalBytes > 0L) {
        ((stats.usedBytes.toFloat() / stats.totalBytes.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analyzer_capacity_banner"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.storage_status_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.stat_used_of_total, stats.formattedUsed, stats.formattedTotal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldGreen.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = stringResource(R.string.battery_storage_free_fmt, stats.formattedFree),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (usedPct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun FileTypeBreakdownCard(
    entry: FileTypeBreakdownEntry,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onActionClick: () -> Unit
) {
    val categoryIcon: ImageVector = when (entry.category) {
        StorageFileTypeCategory.PHOTOS -> Icons.Default.Image
        StorageFileTypeCategory.VIDEOS -> Icons.Default.Videocam
        StorageFileTypeCategory.APPS -> Icons.Default.Apps
        StorageFileTypeCategory.DOCUMENTS -> Icons.Default.Description
    }

    val actionLabel: String = when (entry.category) {
        StorageFileTypeCategory.PHOTOS -> stringResource(R.string.analyzer_photos_action)
        StorageFileTypeCategory.VIDEOS -> stringResource(R.string.analyzer_videos_action)
        StorageFileTypeCategory.APPS -> stringResource(R.string.analyzer_apps_action)
        StorageFileTypeCategory.DOCUMENTS -> stringResource(R.string.analyzer_docs_action)
    }

    val subtitleText: String = when (entry.category) {
        StorageFileTypeCategory.PHOTOS -> stringResource(R.string.card_photos_count, entry.itemCount ?: 0)
        StorageFileTypeCategory.VIDEOS -> stringResource(R.string.card_videos_count, entry.itemCount ?: 0)
        StorageFileTypeCategory.APPS -> stringResource(R.string.analyzer_apps_footprint_desc)
        StorageFileTypeCategory.DOCUMENTS -> {
            val count = entry.itemCount
            if (count != null && count > 0) {
                stringResource(R.string.analyzer_docs_count_fmt, count)
            } else {
                stringResource(R.string.analyzer_docs_footprint_desc)
            }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (entry.percentageOfCategorized / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "FileTypeProgress_${entry.category.name}"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isSelected) 4.dp else 1.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onToggleSelect)
            .testTag("analyzer_card_${entry.category.name.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) entry.color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (isSelected) entry.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(entry.color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = stringResource(entry.labelRes),
                        tint = entry.color,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatFileSize(entry.bytes),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.analyzer_share_of_files_fmt,
                            entry.percentageOfCategorized.roundToInt()
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = entry.color
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = entry.color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.donut_percent_of_total_fmt,
                        entry.percentageOfDevice.roundToInt()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledTonalButton(
                    onClick = onActionClick,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("analyzer_action_${entry.category.name.lowercase()}"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = entry.color.copy(alpha = 0.14f),
                        contentColor = entry.color
                    )
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreLargeFilesCtaCard(
    largeFileCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("analyzer_open_explorer_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.analyzer_explore_large_files_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.analyzer_explore_large_files_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            if (largeFileCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "$largeFileCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.title_storage_explorer),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun QuickCleanupShortcutCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("analyzer_open_quick_cleanup_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.12f)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(EmeraldGreen.copy(alpha = 0.45f))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.title_quick_cleanup),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.quick_cleanup_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.title_quick_cleanup),
                tint = EmeraldGreen
            )
        }
    }
}

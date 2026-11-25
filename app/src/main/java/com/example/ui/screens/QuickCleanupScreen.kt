package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppCacheItem
import com.example.data.model.JunkCategory
import com.example.data.model.MediaItem
import com.example.data.model.formatFileSize
import com.example.data.scanner.QuickCleanupFileFilter
import com.example.data.scanner.QuickCleanupPlanner
import com.example.ui.components.CommonMediaThumbnail
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCleanupScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onExecuteQuickCleanup: (includeCachedData: Boolean, selectedLargeFiles: List<MediaItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val summary = remember(
        uiState.allMedia,
        uiState.appCacheItems,
        uiState.junkCategories,
        uiState.storageStats.junkBytes
    ) {
        QuickCleanupPlanner.buildSummary(
            allMedia = uiState.allMedia,
            appCacheItems = uiState.appCacheItems,
            junkCategories = uiState.junkCategories,
            fallbackJunkBytes = uiState.storageStats.junkBytes
        )
    }

    var includeCachedData by remember(summary.cachedBytes > 0L) {
        mutableStateOf(summary.cachedBytes > 0L)
    }
    var selectedLargeFileIds by remember(summary.largeFiles) {
        mutableStateOf(summary.largeFiles.map { it.id }.toSet())
    }
    var activeFilter by remember { mutableStateOf(QuickCleanupFileFilter.ALL) }
    var showLargeDeleteConfirm by remember { mutableStateOf(false) }

    val filteredLargeFiles = remember(summary.largeFiles, activeFilter) {
        QuickCleanupPlanner.filterLargeFiles(summary.largeFiles, activeFilter)
    }

    val selectedLargeFiles = remember(summary.largeFiles, selectedLargeFileIds) {
        summary.largeFiles.filter { it.id in selectedLargeFileIds }
    }

    val selectedReclaimableBytes = remember(summary, includeCachedData, selectedLargeFileIds) {
        QuickCleanupPlanner.computeSelectedReclaimableBytes(
            summary = summary,
            includeCachedData = includeCachedData,
            selectedLargeFileIds = selectedLargeFileIds
        )
    }

    val nothingSelected = (!includeCachedData || summary.cachedBytes <= 0L) && selectedLargeFiles.isEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.title_quick_cleanup),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.quick_cleanup_subtitle),
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
                            .testTag("quick_cleanup_back_button")
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
                        enabled = !uiState.isScanning && !uiState.isExecutingQuickCleanup,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("quick_cleanup_refresh_button")
                    ) {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
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
        bottomBar = {
            if (summary.totalReclaimableBytes > 0L || uiState.isExecutingQuickCleanup) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isExecutingQuickCleanup) {
                            Text(
                                text = uiState.quickCleanupStepText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { uiState.quickCleanupProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = EmeraldGreen,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (selectedLargeFiles.isNotEmpty()) {
                                        showLargeDeleteConfirm = true
                                    } else {
                                        onExecuteQuickCleanup(includeCachedData, emptyList())
                                    }
                                },
                                enabled = !nothingSelected && selectedReclaimableBytes > 0L,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("quick_cleanup_execute_button"),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(
                                        R.string.quick_cleanup_btn_one_tap_fmt,
                                        formatFileSize(selectedReclaimableBytes)
                                    ),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("quick_cleanup_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Reclaimable Summary Card
            item {
                QuickCleanupHeroCard(
                    totalReclaimableBytes = summary.totalReclaimableBytes,
                    cachedBytes = summary.cachedBytes,
                    largeFilesBytes = summary.largeFilesBytes,
                    largeFileCount = summary.largeFiles.size,
                    hasCache = summary.cachedBytes > 0L,
                    hasLargeFiles = summary.largeFiles.isNotEmpty(),
                    onSelectAll = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        includeCachedData = summary.cachedBytes > 0L
                        selectedLargeFileIds = summary.largeFiles.map { it.id }.toSet()
                    },
                    onSelectCacheOnly = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        includeCachedData = summary.cachedBytes > 0L
                        selectedLargeFileIds = emptySet()
                    },
                    onClearSelection = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        includeCachedData = false
                        selectedLargeFileIds = emptySet()
                    }
                )
            }

            if (summary.totalReclaimableBytes == 0L) {
                item {
                    QuickCleanupEmptyCard(onRescan = onRefresh)
                }
            } else {
                // Section 1: Cached Data & Temp Junk
                item {
                    CachedDataCleanupCard(
                        cachedBytes = summary.cachedBytes,
                        cachedBuckets = summary.cachedBuckets,
                        extraJunkCategories = summary.extraJunkCategories,
                        includeCachedData = includeCachedData,
                        onToggleIncludeCache = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            includeCachedData = it
                        }
                    )
                }

                // Section 2: Large Files Header & Filter Chips
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.quick_cleanup_large_files_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(
                                        R.string.quick_cleanup_large_files_sub_fmt,
                                        summary.largeFiles.size,
                                        formatFileSize(summary.largeFilesBytes)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (filteredLargeFiles.isNotEmpty()) {
                                val allVisibleSelected = filteredLargeFiles.all { it.id in selectedLargeFileIds }
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val visibleIds = filteredLargeFiles.map { it.id }.toSet()
                                        selectedLargeFileIds = if (allVisibleSelected) {
                                            selectedLargeFileIds - visibleIds
                                        } else {
                                            selectedLargeFileIds + visibleIds
                                        }
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("quick_cleanup_toggle_all_large_button")
                                ) {
                                    Text(
                                        text = if (allVisibleSelected) {
                                            stringResource(R.string.btn_deselect_all)
                                        } else {
                                            stringResource(R.string.storage_select_all_count_fmt, filteredLargeFiles.size)
                                        },
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        if (summary.largeFiles.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(QuickCleanupFileFilter.entries) { filter ->
                                    val labelRes = when (filter) {
                                        QuickCleanupFileFilter.ALL -> R.string.storage_filter_all
                                        QuickCleanupFileFilter.VIDEOS -> R.string.storage_filter_videos
                                        QuickCleanupFileFilter.PHOTOS -> R.string.category_photos
                                        QuickCleanupFileFilter.OTHER -> R.string.quick_cleanup_filter_other
                                    }
                                    FilterChip(
                                        selected = activeFilter == filter,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            activeFilter = filter
                                        },
                                        label = { Text(stringResource(labelRes)) },
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("quick_cleanup_filter_${filter.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }

                if (filteredLargeFiles.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldGreen
                                )
                                Text(
                                    text = stringResource(R.string.quick_cleanup_no_large_files),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredLargeFiles, key = { it.id }) { media ->
                        val isSelected = media.id in selectedLargeFileIds
                        QuickCleanupLargeFileRow(
                            media = media,
                            isSelected = isSelected,
                            onToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedLargeFileIds = if (isSelected) {
                                    selectedLargeFileIds - media.id
                                } else {
                                    selectedLargeFileIds + media.id
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showLargeDeleteConfirm && selectedLargeFiles.isNotEmpty()) {
            DeleteConfirmationDialog(
                selectedItems = selectedLargeFiles,
                onConfirmDelete = {
                    showLargeDeleteConfirm = false
                    onExecuteQuickCleanup(includeCachedData, selectedLargeFiles)
                },
                onDismiss = { showLargeDeleteConfirm = false }
            )
        }
    }
}

@Composable
private fun QuickCleanupHeroCard(
    totalReclaimableBytes: Long,
    cachedBytes: Long,
    largeFilesBytes: Long,
    largeFileCount: Int,
    hasCache: Boolean,
    hasLargeFiles: Boolean,
    onSelectAll: () -> Unit,
    onSelectCacheOnly: () -> Unit,
    onClearSelection: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(24.dp))
            .testTag("quick_cleanup_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(EmeraldGreen, ElectricBlue))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.quick_cleanup_reclaimable_label),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(totalReclaimableBytes),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = if (totalReclaimableBytes > 0L) EmeraldGreen else MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryBreakdownPill(
                    label = stringResource(R.string.quick_cleanup_cached_data_title),
                    value = formatFileSize(cachedBytes),
                    color = ElectricBlue,
                    modifier = Modifier.weight(1f)
                )
                SummaryBreakdownPill(
                    label = stringResource(R.string.quick_cleanup_large_files_count_fmt, largeFileCount),
                    value = formatFileSize(largeFilesBytes),
                    color = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            if (hasCache || hasLargeFiles) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSelectAll,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("quick_cleanup_preset_all"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_select_all),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (hasCache) {
                        FilledTonalButton(
                            onClick = onSelectCacheOnly,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("quick_cleanup_preset_cache"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.quick_cleanup_preset_cache_only),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onClearSelection,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("quick_cleanup_preset_clear"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_clear),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBreakdownPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CachedDataCleanupCard(
    cachedBytes: Long,
    cachedBuckets: List<AppCacheItem>,
    extraJunkCategories: List<JunkCategory>,
    includeCachedData: Boolean,
    onToggleIncludeCache: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_cleanup_cache_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (includeCachedData && cachedBytes > 0L) ElectricBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            )
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElectricBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cached,
                            contentDescription = null,
                            tint = ElectricBlue
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.quick_cleanup_cached_data_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (cachedBytes > 0L) {
                                formatFileSize(cachedBytes)
                            } else {
                                stringResource(R.string.quick_cleanup_cache_already_clean)
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (cachedBytes > 0L) ElectricBlue else EmeraldGreen
                        )
                    }
                }

                Switch(
                    checked = includeCachedData && cachedBytes > 0L,
                    enabled = cachedBytes > 0L,
                    onCheckedChange = onToggleIncludeCache,
                    modifier = Modifier.testTag("quick_cleanup_cache_switch")
                )
            }

            AnimatedVisibility(visible = cachedBuckets.isNotEmpty() || extraJunkCategories.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    cachedBuckets.forEach { bucket ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bucket.appName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatFileSize(bucket.cacheSizeBytes),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    extraJunkCategories.forEach { junk ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = junk.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatFileSize(junk.sizeBytes),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCleanupLargeFileRow(
    media: MediaItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .testTag("quick_cleanup_large_item_${media.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CoralRed.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (isSelected) CoralRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 68.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                CommonMediaThumbnail(
                    mediaItem = media,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = WarningAmber.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = media.formattedSize,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = WarningAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = media.mimeType.substringAfter('/').uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                colors = CheckboxDefaults.colors(checkedColor = CoralRed)
            )
        }
    }
}

@Composable
private fun QuickCleanupEmptyCard(onRescan: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_cleanup_empty_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = null,
                tint = EmeraldGreen,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.status_device_optimized),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.quick_cleanup_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_refresh_scan))
            }
        }
    }
}

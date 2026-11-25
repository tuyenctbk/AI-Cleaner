package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.data.model.formatFileSize
import com.example.ui.components.CommonMediaThumbnail
import com.example.ui.components.D3StorageDonutChart
import com.example.ui.components.DeleteConfirmationDialog
import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState

enum class StorageDateFilter(val stringRes: Int) {
    ALL_TIME(R.string.storage_filter_all_time),
    LAST_7_DAYS(R.string.storage_filter_last_7_days),
    LAST_30_DAYS(R.string.storage_filter_last_30_days),
    OLDER_6_MONTHS(R.string.storage_filter_older_6_months)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageBreakdownScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onDeleteLargeFiles: (List<MediaItem>) -> Unit,
    initialExtensionFilter: String = "All",
    onOpenStorageAnalyzer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val stats = uiState.storageStats
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedExtensionFilter by remember(initialExtensionFilter) { mutableStateOf(initialExtensionFilter) }
    var selectedDateFilter by remember { mutableStateOf(StorageDateFilter.ALL_TIME) }

    val rawLargeFiles = uiState.allMedia.filter { it.isLarge || it.sizeBytes > 10 * 1024 * 1024 }

    val filteredLargeFiles = remember(rawLargeFiles, searchQuery, selectedExtensionFilter, selectedDateFilter) {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        rawLargeFiles.filter { media ->
            // Search query filter
            val matchesSearch = searchQuery.isBlank() ||
                    media.title.contains(searchQuery, ignoreCase = true) ||
                    media.path.contains(searchQuery, ignoreCase = true)

            // Extension filter
            val matchesExtension = when (selectedExtensionFilter) {
                ".mp4" -> media.title.endsWith(".mp4", ignoreCase = true) || media.mimeType.contains("video", ignoreCase = true)
                ".jpg" -> media.title.endsWith(".jpg", ignoreCase = true) || media.title.endsWith(".png", ignoreCase = true) || media.mimeType.contains("image", ignoreCase = true)
                ".zip" -> media.title.endsWith(".zip", ignoreCase = true) || media.title.endsWith(".rar", ignoreCase = true)
                ".apk" -> media.title.endsWith(".apk", ignoreCase = true)
                ".pdf" -> media.title.endsWith(".pdf", ignoreCase = true) || media.title.endsWith(".doc", ignoreCase = true)
                else -> true
            }

            // Date created/modified filter
            val matchesDate = when (selectedDateFilter) {
                StorageDateFilter.LAST_7_DAYS -> (now - media.dateModified) <= (7 * dayMs)
                StorageDateFilter.LAST_30_DAYS -> (now - media.dateModified) <= (30 * dayMs)
                StorageDateFilter.OLDER_6_MONTHS -> (now - media.dateModified) >= (180 * dayMs)
                StorageDateFilter.ALL_TIME -> true
            }

            matchesSearch && matchesExtension && matchesDate
        }
    }

    var selectedItemIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val validLargeIds = remember(rawLargeFiles) { rawLargeFiles.map { it.id }.toSet() }
    val activeSelectedIds = remember(selectedItemIds, validLargeIds) { selectedItemIds.intersect(validLargeIds) }
    val selectedItemsList = remember(rawLargeFiles, activeSelectedIds) { rawLargeFiles.filter { it.id in activeSelectedIds } }
    val selectedBytes = selectedItemsList.sumOf { it.sizeBytes }
    val allFilteredSelected = filteredLargeFiles.isNotEmpty() && filteredLargeFiles.all { it.id in activeSelectedIds }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_storage_explorer),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (onOpenStorageAnalyzer != null) {
                        TextButton(
                            onClick = onOpenStorageAnalyzer,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("explorer_open_analyzer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.btn_open_storage_analyzer),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
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
            if (selectedItemsList.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Summary Banner for Search & Selection
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (searchQuery.isNotEmpty()) {
                                                stringResource(R.string.storage_selected_files_search_fmt, selectedItemsList.size)
                                            } else {
                                                stringResource(R.string.storage_selected_files_fmt, selectedItemsList.size)
                                            },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.storage_total_saved_fmt, formatFileSize(selectedBytes)),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = EmeraldGreen
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { selectedItemIds = emptySet() }
                                ) {
                                    Text(stringResource(R.string.btn_clear), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Action Buttons: Confirm Bulk Deletion
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("delete_selected_large_files_button"),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CoralRed,
                                    contentColor = Color.White
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
                                    Text(
                                        text = stringResource(R.string.btn_confirm_bulk_deletion_fmt, formatFileSize(selectedBytes)),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
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
                .testTag("storage_breakdown_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Interactive D3 Storage Donut Chart
            item {
                D3StorageDonutChart(stats = stats)
            }

            // Visual Folder Heatmap Component
            if (uiState.folderHeatmapItems.isNotEmpty()) {
                item {
                    FolderHeatmapSection(
                        folders = uiState.folderHeatmapItems,
                        onSelectFolder = { folderName ->
                            searchQuery = folderName
                        }
                    )
                }
            }

            // Storage Health & Total Usage Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.03f)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.storage_status_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.stat_used_of_total, stats.formattedUsed, stats.formattedTotal),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val usedPct = if (stats.totalBytes > 0L) ((stats.usedBytes.toFloat() / stats.totalBytes.toFloat()) * 100).toInt().coerceIn(0, 100) else 0
                            Text(
                                text = stringResource(R.string.format_percentage, usedPct),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        // Storage Category Breakdown Rows
                        CategoryProgressItem(stringResource(R.string.category_photos), stats.photoBytes, stats.totalBytes, ColorPhotos, Icons.Default.Image)
                        CategoryProgressItem(stringResource(R.string.category_videos), stats.videoBytes, stats.totalBytes, ColorVideos, Icons.Default.Videocam)
                        CategoryProgressItem(stringResource(R.string.storage_cat_apps), stats.appBytes, stats.totalBytes, ColorApps, Icons.Default.Apps)
                        CategoryProgressItem(stringResource(R.string.storage_cat_docs), stats.docBytes, stats.totalBytes, ColorDocs, Icons.Default.Description)
                        CategoryProgressItem(stringResource(R.string.storage_cat_system_cache), stats.systemBytes + stats.junkBytes, stats.totalBytes, ColorSystem, Icons.Default.Settings)
                    }
                }
            }

            // Search & Filter Bar Section Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.storage_large_files_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val visibleIds = filteredLargeFiles.map { it.id }.toSet()
                                selectedItemIds = if (allFilteredSelected) {
                                    activeSelectedIds - visibleIds
                                } else {
                                    activeSelectedIds + visibleIds
                                }
                            }
                        ) {
                            Text(
                                text = if (allFilteredSelected) {
                                    stringResource(R.string.btn_deselect_all)
                                } else {
                                    stringResource(R.string.storage_select_all_count_fmt, filteredLargeFiles.size)
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Search TextField
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("file_search_input"),
                        placeholder = { Text(stringResource(R.string.search_files_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.btn_clear), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    // Extension Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val extList = listOf("All", ".mp4", ".jpg", ".zip", ".apk", ".pdf")
                        items(extList) { ext ->
                            val isSelected = selectedExtensionFilter == ext
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedExtensionFilter = ext
                                },
                                label = { Text(if (ext == "All") stringResource(R.string.storage_filter_all) else ext) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    // Date Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(StorageDateFilter.entries) { dateFilter ->
                            val isSelected = selectedDateFilter == dateFilter
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedDateFilter = dateFilter
                                },
                                label = { Text(stringResource(dateFilter.stringRes)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    if (filteredLargeFiles.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FindInPage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.storage_no_matching_files),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.storage_no_matching_files_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Large File Items
            items(filteredLargeFiles, key = { it.id }) { media ->
                val isSelected = media.id in selectedItemIds
                LargeFileRowItem(
                    media = media,
                    isSelected = isSelected,
                    onToggleSelect = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedItemIds = if (isSelected) {
                            selectedItemIds - media.id
                        } else {
                            selectedItemIds + media.id
                        }
                    }
                )
            }
        }

        // Custom Material 3 Delete Confirmation Dialog
        if (showDeleteConfirmDialog && selectedItemsList.isNotEmpty()) {
            DeleteConfirmationDialog(
                selectedItems = selectedItemsList,
                onConfirmDelete = {
                    showDeleteConfirmDialog = false
                    onDeleteLargeFiles(selectedItemsList)
                    selectedItemIds = emptySet()
                },
                onDismiss = { showDeleteConfirmDialog = false }
            )
        }
    }
}

@Composable
private fun CategoryProgressItem(
    label: String,
    categoryBytes: Long,
    totalBytes: Long,
    color: Color,
    icon: ImageVector
) {
    val progress = if (totalBytes > 0) (categoryBytes.toFloat() / totalBytes.toFloat()) else 0f
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = formatFileSize(categoryBytes),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun LargeFileRowItem(
    media: MediaItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelect)
            .shadow(1.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.02f)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (isSelected) CoralRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                CommonMediaThumbnail(
                    mediaItem = media,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(
                        R.string.storage_media_subtitle_fmt,
                        media.formattedSize,
                        media.mimeType.substringAfter('/')
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = CoralRed)
            )
        }
    }
}

@Composable
private fun FolderHeatmapSection(
    folders: List<com.example.data.model.FolderStorageItem>,
    onSelectFolder: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val maxBytes = folders.maxOfOrNull { it.sizeBytes } ?: 1L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.03f))
            .testTag("folder_heatmap_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.storage_heatmap_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.storage_heatmap_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Intensity Scale Legend Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.storage_heatmap_intensity),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeatmapLegendChip(stringResource(R.string.storage_heatmap_legend_gt_15gb), Color(0xFFE53935))
                    HeatmapLegendChip(stringResource(R.string.storage_heatmap_legend_5_15gb), Color(0xFFFB8C00))
                    HeatmapLegendChip(stringResource(R.string.storage_heatmap_legend_1_5gb), Color(0xFFFFB300))
                    HeatmapLegendChip(stringResource(R.string.storage_heatmap_legend_lt_1gb), Color(0xFF00ACC1))
                }
            }

            // Heatmap Grid / Blocks
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                folders.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { folder ->
                            val intensity = (folder.sizeBytes.toFloat() / maxBytes.toFloat()).coerceIn(0.1f, 1.0f)
                            val tileColor = when {
                                folder.sizeBytes >= 15_000_000_000L -> Color(0xFFE53935)
                                folder.sizeBytes >= 5_000_000_000L -> Color(0xFFFB8C00)
                                folder.sizeBytes >= 1_000_000_000L -> Color(0xFFFFB300)
                                else -> Color(0xFF00ACC1)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(115.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(tileColor.copy(alpha = 0.12f))
                                    .border(1.5.dp, tileColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val folderQuery = folder.path
                                            .trimEnd('/')
                                            .substringAfterLast('/')
                                            .ifBlank { folder.name }
                                        onSelectFolder(folderQuery)
                                    }
                                    .padding(12.dp)
                                    .testTag("heatmap_tile_${folder.id}")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(tileColor)
                                        )
                                        Text(
                                            text = stringResource(R.string.storage_heatmap_intensity_fmt, (intensity * 100).toInt()),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            color = tileColor
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = stringResource(R.string.storage_heatmap_folder_summary_fmt, folder.formattedSize, folder.fileCount),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                            color = tileColor
                                        )
                                    }

                                    Text(
                                        text = folder.previewFileTypes,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
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

@Composable
private fun HeatmapLegendChip(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}



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
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageBreakdownScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onDeleteLargeFiles: (List<MediaItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = uiState.storageStats
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedExtensionFilter by remember { mutableStateOf("All") }
    var selectedDateFilter by remember { mutableStateOf("All Time") }

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
                "Last 7 Days" -> (now - media.dateModified) <= (7 * dayMs)
                "Last 30 Days" -> (now - media.dateModified) <= (30 * dayMs)
                "Older than 6 Months" -> (now - media.dateModified) >= (180 * dayMs)
                else -> true
            }

            matchesSearch && matchesExtension && matchesDate
        }
    }

    var selectedItemIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val selectedItemsList = rawLargeFiles.filter { it.id in selectedItemIds }
    val selectedBytes = selectedItemsList.sumOf { it.sizeBytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Deep Storage Breakdown",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        color = PolishTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PolishTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PolishBackground
                )
            )
        },
        bottomBar = {
            if (selectedItemIds.isNotEmpty()) {
                Surface(
                    color = PolishSurface,
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
                            colors = CardDefaults.cardColors(containerColor = PolishPrimary.copy(alpha = 0.1f)),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(PolishPrimary.copy(alpha = 0.4f)))
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
                                        tint = PolishPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Selected ${selectedItemsList.size} Files" + if (searchQuery.isNotEmpty()) " in Search" else "",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = PolishTextPrimary
                                        )
                                        Text(
                                            text = "Total Space to be Saved: ${formatFileSize(selectedBytes)}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = EmeraldGreen
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { selectedItemIds = emptySet() }
                                ) {
                                    Text("Clear", color = PolishPrimary, fontWeight = FontWeight.Bold)
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
                                colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
                                    Text(
                                        text = "Confirm Bulk Deletion (${formatFileSize(selectedBytes)})",
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
                    colors = CardDefaults.cardColors(containerColor = PolishSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(PolishOutline.copy(alpha = 0.6f))
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
                                    text = "Device Storage Status",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                    color = PolishTextPrimary
                                )
                                Text(
                                    text = "${stats.formattedUsed} used of ${stats.formattedTotal}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PolishTextSecondary
                                )
                            }
                            Text(
                                text = "${((stats.usedBytes.toFloat() / stats.totalBytes.toFloat()) * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = PolishPrimary
                                )
                            )
                        }

                        // Storage Category Breakdown Rows
                        CategoryProgressItem("Photos", stats.photoBytes, stats.totalBytes, ColorPhotos, Icons.Default.Image)
                        CategoryProgressItem("Videos", stats.videoBytes, stats.totalBytes, ColorVideos, Icons.Default.Videocam)
                        CategoryProgressItem("Apps & Data", stats.appBytes, stats.totalBytes, ColorApps, Icons.Default.Apps)
                        CategoryProgressItem("Documents", stats.docBytes, stats.totalBytes, ColorDocs, Icons.Default.Description)
                        CategoryProgressItem("System & Cache", stats.systemBytes + stats.junkBytes, stats.totalBytes, ColorSystem, Icons.Default.Settings)
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
                            text = "Large Files Management",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                            color = PolishTextPrimary
                        )
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedItemIds = if (selectedItemIds.size == filteredLargeFiles.size) {
                                    emptySet()
                                } else {
                                    filteredLargeFiles.map { it.id }.toSet()
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedItemIds.size == filteredLargeFiles.size) "Deselect All" else "Select All (${filteredLargeFiles.size})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PolishPrimary
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
                        placeholder = { Text("Search files by name or extension...", color = PolishTextSecondary) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = PolishTextSecondary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search", tint = PolishTextSecondary)
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = PolishSurface,
                            unfocusedContainerColor = PolishSurface,
                            focusedBorderColor = PolishPrimary,
                            unfocusedBorderColor = PolishOutline.copy(alpha = 0.5f)
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
                                label = { Text(ext) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PolishPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = PolishSurface,
                                    labelColor = PolishTextPrimary
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
                        val dateList = listOf("All Time", "Last 7 Days", "Last 30 Days", "Older than 6 Months")
                        items(dateList) { dateFilter ->
                            val isSelected = selectedDateFilter == dateFilter
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedDateFilter = dateFilter
                                },
                                label = { Text(dateFilter) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = PolishSurface,
                                    labelColor = PolishTextPrimary
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
                            colors = CardDefaults.cardColors(containerColor = PolishSurface)
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
                                    tint = PolishTextSecondary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No files match your search criteria",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PolishTextPrimary
                                )
                                Text(
                                    text = "Try clearing search keywords or selecting a different file extension filter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PolishTextSecondary
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
                    color = PolishTextPrimary
                )
            }
            Text(
                text = formatFileSize(categoryBytes),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = PolishTextPrimary
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = PolishSurfaceVariant
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
        colors = CardDefaults.cardColors(containerColor = PolishSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (isSelected) CoralRed else PolishOutline.copy(alpha = 0.5f))
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
                    color = PolishTextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "${media.formattedSize} • ${media.mimeType.substringAfter('/')}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = PolishTextSecondary
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
        colors = CardDefaults.cardColors(containerColor = PolishSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(PolishOutline.copy(alpha = 0.5f))
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
                        text = "Subdirectory Storage Heatmap",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        color = PolishTextPrimary
                    )
                    Text(
                        text = "Color-coded intensity scale by folder size footprint",
                        style = MaterialTheme.typography.bodySmall,
                        color = PolishTextSecondary
                    )
                }
            }

            // Intensity Scale Legend Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PolishSurfaceVariant)
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Intensity:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PolishTextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeatmapLegendChip(">15GB", Color(0xFFE53935))
                    HeatmapLegendChip("5-15GB", Color(0xFFFB8C00))
                    HeatmapLegendChip("1-5GB", Color(0xFFFFB300))
                    HeatmapLegendChip("<1GB", Color(0xFF00ACC1))
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
                                        onSelectFolder(folder.name)
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
                                            text = "${(intensity * 100).toInt()}% Intensity",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            color = tileColor
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                            color = PolishTextPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${folder.formattedSize} • ${folder.fileCount} files",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                            color = tileColor
                                        )
                                    }

                                    Text(
                                        text = folder.sampleFileTypes,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = PolishTextSecondary,
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
            color = PolishTextSecondary
        )
    }
}



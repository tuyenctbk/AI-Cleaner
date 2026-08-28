package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CompressionQuality
import com.example.data.model.formatFileSize
import com.example.ui.components.CommonMediaThumbnail
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressorScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onQualityChange: (CompressionQuality) -> Unit,
    onToggleItem: (Long) -> Unit,
    onStartCompression: () -> Unit,
    onSelectAll: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var mediaTypeFilter by remember { mutableStateOf("All") } // "All", "Videos", "Photos"
    var isCompareMode by remember { mutableStateOf(false) }

    // Filter compressible items based on selected tab
    val filteredCompressibleItems = remember(uiState.compressibleItems, mediaTypeFilter) {
        when (mediaTypeFilter) {
            "Videos" -> uiState.compressibleItems.filter { it.mediaItem.mimeType.startsWith("video/") || it.mediaItem.title.endsWith(".mp4", ignoreCase = true) }
            "Photos" -> uiState.compressibleItems.filter { it.mediaItem.mimeType.startsWith("image/") || !it.mediaItem.title.endsWith(".mp4", ignoreCase = true) }
            else -> uiState.compressibleItems
        }
    }

    val selectedItems = filteredCompressibleItems.filter { it.isSelected }
    val totalOriginalBytes = selectedItems.sumOf { it.mediaItem.sizeBytes }
    val totalTargetBytes = (totalOriginalBytes * uiState.selectedCompressionQuality.factor).toLong()
    val estimatedSavings = totalOriginalBytes - totalTargetBytes

    val videoSelectedCount = selectedItems.count { it.mediaItem.mimeType.startsWith("video/") || it.mediaItem.title.endsWith(".mp4", ignoreCase = true) }
    val photoSelectedCount = selectedItems.size - videoSelectedCount

    var activePreviewIndex by remember { mutableIntStateOf(0) }
    val activeCompressible = filteredCompressibleItems.getOrNull(activePreviewIndex) ?: uiState.compressibleItems.firstOrNull()
    val activeMedia = activeCompressible?.mediaItem

    val allSelectedInFilter = filteredCompressibleItems.isNotEmpty() && filteredCompressibleItems.all { it.isSelected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Bulk Media Compressor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${selectedItems.size} Selected ($videoSelectedCount Videos, $photoSelectedCount Photos)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (onSelectAll != null) {
                                onSelectAll(!allSelectedInFilter)
                            } else {
                                filteredCompressibleItems.forEach { onToggleItem(it.id) }
                            }
                        },
                        modifier = Modifier.testTag("select_all_compressor_button")
                    ) {
                        Text(
                            text = if (allSelectedInFilter) "Deselect All" else "Select All",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.isCompressing) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Compressing 4K Videos & High-Res Photos...",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(uiState.compressionProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { uiState.compressionProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "Applying advanced codec optimization with lossless perceptual quality...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartCompression()
                            },
                            enabled = selectedItems.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_compress_button"),
                            shape = RoundedCornerShape(27.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Compress,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Text(
                                    text = "Batch Compress & Free ${formatFileSize(estimatedSavings)}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("compressor_screen_column")
        ) {
            // Media Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "All" to "All (${uiState.compressibleItems.size})",
                    "Videos" to "High-Res Videos (${uiState.compressibleItems.count { it.mediaItem.mimeType.startsWith("video/") || it.mediaItem.title.endsWith(".mp4", ignoreCase = true) }})",
                    "Photos" to "Large Photos (${uiState.compressibleItems.count { !it.mediaItem.mimeType.startsWith("video/") && !it.mediaItem.title.endsWith(".mp4", ignoreCase = true) }})"
                ).forEach { (key, label) ->
                    val isSelected = mediaTypeFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { mediaTypeFilter = key },
                        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_chip_$key")
                    )
                }
            }

            // Interactive Preview Area (Normal View vs Compare Mode)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (activeMedia != null) {
                    if (isCompareMode) {
                        // Side-by-Side Original vs Compressed Quality Compare
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Left: Original
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                CommonMediaThumbnail(
                                    mediaItem = activeMedia,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = RoundedCornerShape(bottomEnd = 10.dp),
                                    color = Color.Black.copy(alpha = 0.75f),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "Original (${activeMedia.formattedSize})",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Right: Compressed Preview Simulation
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                CommonMediaThumbnail(
                                    mediaItem = activeMedia,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = RoundedCornerShape(bottomStart = 10.dp),
                                    color = EmeraldGreen.copy(alpha = 0.9f),
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    val targetSize = (activeMedia.sizeBytes * uiState.selectedCompressionQuality.factor).toLong()
                                    Text(
                                        text = "Compressed (${formatFileSize(targetSize)})",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Single Media Preview
                        CommonMediaThumbnail(
                            mediaItem = activeMedia,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Media Type & Resolution Badge
                    val isVideo = activeMedia.mimeType.startsWith("video/") || activeMedia.title.endsWith(".mp4", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Hd,
                                contentDescription = null,
                                tint = if (isVideo) CoralRed else EmeraldGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isVideo) "4K / HD Video • ${if (activeMedia.width > 0) "${activeMedia.width}x${activeMedia.height}" else "3840x2160"}"
                                else "High-Res Image • ${if (activeMedia.width > 0) "${activeMedia.width}x${activeMedia.height}" else "4032x3024"}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Compare Mode Switcher Floating Button
                    IconButton(
                        onClick = { isCompareMode = !isCompareMode },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .testTag("toggle_compare_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Compare",
                            tint = if (isCompareMode) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Quality Preset Selector & Space Saving Calculation
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Size Savings Summary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Estimated Output",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatFileSize(totalTargetBytes),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Original Size",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatFileSize(totalOriginalBytes),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Space Saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatFileSize(estimatedSavings),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                ),
                                color = EmeraldGreen
                            )
                        }
                    }

                    // Quality Preset Segmented Control
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CompressionQuality.entries.forEach { quality ->
                            val isSelected = uiState.selectedCompressionQuality == quality
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onQualityChange(quality)
                                    }
                                    .padding(vertical = 8.dp)
                                    .testTag("quality_preset_${quality.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = quality.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp
                                        ),
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "-${quality.savingPercentage}% Size",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Thumbnail Strip for Multi-Selectable Items
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredCompressibleItems.indices.toList()) { index ->
                    val item = filteredCompressibleItems[index]
                    val isSelected = item.isSelected
                    val isActivePreview = activePreviewIndex == index
                    val isVideo = item.mediaItem.mimeType.startsWith("video/") || item.mediaItem.title.endsWith(".mp4", ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isActivePreview) 3.dp else if (isSelected) 2.dp else 0.dp,
                                color = if (isActivePreview) MaterialTheme.colorScheme.primary else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                activePreviewIndex = index
                                onToggleItem(item.id)
                            }
                            .testTag("compressible_item_thumb_${item.id}")
                    ) {
                        CommonMediaThumbnail(
                            mediaItem = item.mediaItem,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top-Right Checkbox Indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        // Video / Photo Badge Pill at bottom
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(vertical = 2.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isVideo) "4K/HD" else "RAW",
                                color = if (isVideo) CoralRed else EmeraldGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.mediaItem.formattedSize,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

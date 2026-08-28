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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DuplicateGroup
import com.example.data.model.MediaItem
import com.example.data.model.formatFileSize
import com.example.ui.components.CommonMediaThumbnail
import com.example.ui.components.SimilarPhotosGroupCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onToggleItem: (String, Long) -> Unit,
    onToggleGroup: (String, Boolean) -> Unit,
    onSelectAllDuplicates: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var activeComparingGroupId by remember { mutableStateOf<String?>(null) }
    var showConfirmMassDeleteDialog by remember { mutableStateOf(false) }

    val totalSelectedCount = uiState.duplicateGroups.sumOf { it.selectedCount }
    val totalSelectedBytes = uiState.duplicateGroups.sumOf { it.selectedSizeBytes }
    val totalDuplicatesCount = uiState.duplicateGroups.sumOf { it.items.size }

    val selectedDuplicateItemsPreview = remember(uiState.duplicateGroups) {
        uiState.duplicateGroups.flatMap { group ->
            group.items.filter { it.isSelected }.map { "${it.title} (${it.formattedSize})" }
        }
    }

    if (showConfirmMassDeleteDialog) {
        com.example.ui.components.MassDeletionSummaryDialog(
            title = "Delete $totalSelectedCount Duplicate Photos?",
            subtitle = "Purge lower-quality duplicate candidate shots",
            totalFilesCount = totalSelectedCount,
            projectedSpaceBytes = totalSelectedBytes,
            itemsPreviewList = selectedDuplicateItemsPreview.take(10),
            onConfirmDelete = {
                onDeleteSelected()
            },
            onDismiss = { showConfirmMassDeleteDialog = false }
        )
    }

    val activeComparingGroup = remember(uiState.duplicateGroups, activeComparingGroupId) {
        uiState.duplicateGroups.firstOrNull { it.id == activeComparingGroupId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Similar & Duplicate Photos",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            color = PolishTextPrimary
                        )
                        Text(
                            text = "$totalDuplicatesCount Duplicates • $totalSelectedCount Selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = PolishTextSecondary
                        )
                    }
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
                actions = {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectAllDuplicates()
                        },
                        modifier = Modifier.testTag("select_all_duplicates_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PolishPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Keep Best",
                            fontWeight = FontWeight.Bold,
                            color = PolishPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PolishBackground
                )
            )
        },
        bottomBar = {
            if (totalSelectedCount > 0) {
                Surface(
                    color = PolishSurface,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = { showConfirmMassDeleteDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("delete_duplicates_button"),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PolishPrimary
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Text(
                                    text = "Delete $totalSelectedCount Photos (Free ${formatFileSize(totalSelectedBytes)})",
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
        modifier = modifier
    ) { innerPadding ->
        if (uiState.duplicateGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No Duplicates Found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PolishTextPrimary
                    )
                    Text(
                        text = "Your photo library is neatly organized!",
                        style = MaterialTheme.typography.bodySmall,
                        color = PolishTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("duplicates_lazy_column"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                items(uiState.duplicateGroups, key = { it.id }) { group ->
                    SimilarPhotosGroupCard(
                        group = group,
                        onToggleItem = { itemId -> onToggleItem(group.id, itemId) },
                        onToggleAllInGroup = { selectAll -> onToggleGroup(group.id, selectAll) },
                        onCompareGroup = { activeComparingGroupId = group.id }
                    )
                }
            }
        }

        // Side-by-side 'Compare' View Sheet
        activeComparingGroup?.let { group ->
            DuplicateCompareSheet(
                group = group,
                onDismiss = { activeComparingGroupId = null },
                onToggleItem = { itemId -> onToggleItem(group.id, itemId) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicateCompareSheet(
    group: DuplicateGroup,
    onDismiss: () -> Unit,
    onToggleItem: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bestItem = group.items.firstOrNull { it.isBestShot } ?: group.items.first()
    val duplicates = group.items.filter { it.id != bestItem.id }
    var selectedCandidateIndex by remember { mutableIntStateOf(0) }
    val candidateItem = duplicates.getOrNull(selectedCandidateIndex) ?: duplicates.firstOrNull() ?: bestItem

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .testTag("duplicate_compare_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Side-by-Side Photo Comparison",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                }
            }

            // Side-by-Side Images View
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Image: Best Shot
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .border(2.dp, Color(0xFFFFD700), RoundedCornerShape(20.dp))
                        .clickable { onToggleItem(bestItem.id) }
                ) {
                    CommonMediaThumbnail(
                        mediaItem = bestItem,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Best Shot Tag
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 12.dp),
                        color = Color(0xFFE65100),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                            Text("Best Shot", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // Keep Badge Checkbox
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (!bestItem.isSelected) EmeraldGreen else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (!bestItem.isSelected) "KEEP" else "DEL",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Right Image: Duplicate Candidate
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .border(2.dp, if (candidateItem.isSelected) CoralRed else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .clickable { onToggleItem(candidateItem.id) }
                ) {
                    CommonMediaThumbnail(
                        mediaItem = candidateItem,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Duplicate Tag
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 12.dp),
                        color = CoralRed.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "Duplicate Candidate",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Delete Checkbox
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (candidateItem.isSelected) CoralRed else Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (candidateItem.isSelected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text("KEEP", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (duplicates.size > 1) {
                // Selector Row for Multiple Duplicates
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Comparing Candidate ${selectedCandidateIndex + 1} of ${duplicates.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Side-by-Side Metadata Comparison Table
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Metadata & Quality Breakdown",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    MetadataRow("File Name", bestItem.title, candidateItem.title)
                    MetadataRow("File Size", bestItem.formattedSize, candidateItem.formattedSize, isBestHighlight = bestItem.sizeBytes > candidateItem.sizeBytes)
                    MetadataRow("Resolution", "${bestItem.width} x ${bestItem.height}", "${candidateItem.width} x ${candidateItem.height}")
                    MetadataRow(
                        "Clarity Rating",
                        if (bestItem.isBlurry) "82% (Slight Motion)" else "98% Crisp Focus",
                        if (candidateItem.isBlurry) "74% Blurry" else "88% Sharp",
                        isBestHighlight = !bestItem.isBlurry
                    )
                    MetadataRow("Storage Path", bestItem.path.takeLast(25), candidateItem.path.takeLast(25))
                }
            }

            // Quick Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirm Selection", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    bestVal: String,
    candVal: String,
    isBestHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(85.dp)
        )
        Text(
            text = bestVal,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isBestHighlight) FontWeight.Black else FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = if (isBestHighlight) Color(0xFFE65100) else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            text = candVal,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
    }
}

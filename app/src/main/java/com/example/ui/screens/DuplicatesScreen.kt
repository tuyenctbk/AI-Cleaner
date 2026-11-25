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
import androidx.compose.ui.res.stringResource
import com.example.R
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
    onToggleItem: (groupId: String, itemId: Long) -> Unit,
    onToggleGroup: (groupId: String, selectAll: Boolean) -> Unit,
    onSelectAllDuplicates: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRunBackgroundScan: () -> Unit = {},
    onMarkFlaggedReviewed: () -> Unit = {},
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
            title = stringResource(R.string.duplicates_dialog_title, totalSelectedCount),
            subtitle = stringResource(R.string.duplicates_dialog_subtitle),
            totalFilesCount = totalSelectedCount,
            projectedSpaceBytes = totalSelectedBytes,
            itemsPreviewList = selectedDuplicateItemsPreview.take(10),
            onConfirmDelete = {
                showConfirmMassDeleteDialog = false
                activeComparingGroupId = null
                onDeleteSelected()
            },
            onDismiss = { showConfirmMassDeleteDialog = false }
        )
    }

    val activeComparingGroup = remember(uiState.duplicateGroups, activeComparingGroupId) {
        uiState.duplicateGroups.firstOrNull { it.id == activeComparingGroupId }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.duplicates_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.duplicates_summary, totalDuplicatesCount, totalSelectedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = MaterialTheme.colorScheme.onBackground
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.btn_keep_best_shot),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (totalSelectedCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
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
                                containerColor = MaterialTheme.colorScheme.primary
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
                                    text = stringResource(R.string.duplicates_btn_delete_format, totalSelectedCount, formatFileSize(totalSelectedBytes)),
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
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        if (uiState.duplicateGroups.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BackgroundDuplicateScanCard(
                    uiState = uiState,
                    onRunBackgroundScan = onRunBackgroundScan,
                    onMarkFlaggedReviewed = onMarkFlaggedReviewed
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.duplicates_empty_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.duplicates_empty_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                item(key = "background_dhash_review_card") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        BackgroundDuplicateScanCard(
                            uiState = uiState,
                            onRunBackgroundScan = onRunBackgroundScan,
                            onMarkFlaggedReviewed = onMarkFlaggedReviewed
                        )
                    }
                }
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

@Composable
private fun BackgroundDuplicateScanCard(
    uiState: CleanerUiState,
    onRunBackgroundScan: () -> Unit,
    onMarkFlaggedReviewed: () -> Unit
) {
    val formattedLastScan = remember(uiState.lastDuplicateScanMillis) {
        if (uiState.lastDuplicateScanMillis > 0L) {
            java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT,
                java.text.DateFormat.SHORT,
                java.util.Locale.getDefault()
            ).format(java.util.Date(uiState.lastDuplicateScanMillis))
        } else {
            ""
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("background_duplicate_scan_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.flaggedDuplicatesCount > 0) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.duplicates_bg_scan_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val subtitleText = when {
                        uiState.isDuplicateWorkerRunning -> stringResource(R.string.duplicates_bg_scan_running)
                        uiState.flaggedDuplicatesCount > 0 -> stringResource(
                            R.string.duplicates_bg_scan_flagged_fmt,
                            uiState.flaggedDuplicatesCount,
                            uiState.flaggedDuplicatesGroupCount,
                            formatFileSize(uiState.flaggedDuplicatesReclaimableBytes)
                        )
                        formattedLastScan.isNotEmpty() -> stringResource(
                            R.string.duplicates_bg_scan_last_run_fmt,
                            formattedLastScan
                        )
                        else -> stringResource(R.string.duplicates_bg_scan_idle_desc)
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.isDuplicateWorkerRunning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onRunBackgroundScan,
                    enabled = !uiState.isDuplicateWorkerRunning,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("run_background_duplicate_scan_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.duplicates_btn_scan_gallery_dhash),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                if (uiState.flaggedDuplicatesCount > 0) {
                    TextButton(
                        onClick = onMarkFlaggedReviewed,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("mark_flagged_duplicates_reviewed_button")
                    ) {
                        Text(
                            text = stringResource(R.string.duplicates_btn_mark_reviewed),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
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
    var selectedCandidateIndex by remember(group.id) { mutableIntStateOf(0) }
    val safeIndex = selectedCandidateIndex.coerceIn(0, (duplicates.size - 1).coerceAtLeast(0))
    val candidateItem = duplicates.getOrNull(safeIndex) ?: duplicates.firstOrNull() ?: bestItem

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
                        text = stringResource(R.string.duplicates_compare_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.storage_media_subtitle_fmt, group.label, group.matchReason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.btn_cancel), tint = MaterialTheme.colorScheme.onBackground)
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
                            Text(stringResource(R.string.badge_best_shot), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
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
                            text = if (!bestItem.isSelected) stringResource(R.string.btn_keep_tag) else stringResource(R.string.btn_del_tag),
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
                            text = stringResource(R.string.badge_duplicate_candidate),
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
                            Text(stringResource(R.string.btn_keep_tag), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (duplicates.size > 1) {
                // Interactive Selector Row for Multiple Duplicates
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedCandidateIndex = if (safeIndex > 0) safeIndex - 1 else duplicates.lastIndex
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("prev_duplicate_candidate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.duplicates_comparing_candidate_fmt, safeIndex + 1, duplicates.size),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedCandidateIndex = if (safeIndex < duplicates.lastIndex) safeIndex + 1 else 0
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("next_duplicate_candidate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.btn_compare),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
                        text = stringResource(R.string.duplicates_metadata_breakdown),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    MetadataRow(stringResource(R.string.metadata_file_name), bestItem.title, candidateItem.title)
                    MetadataRow(stringResource(R.string.metadata_file_size), bestItem.formattedSize, candidateItem.formattedSize, isBestHighlight = bestItem.sizeBytes > candidateItem.sizeBytes)
                    MetadataRow(
                        stringResource(R.string.metadata_resolution),
                        stringResource(R.string.format_resolution, bestItem.width, bestItem.height),
                        stringResource(R.string.format_resolution, candidateItem.width, candidateItem.height)
                    )
                    val bestClarityLabel = when {
                        bestItem.sharpnessScore > 0f && !bestItem.isBlurry ->
                            stringResource(R.string.clarity_score_sharp_fmt, bestItem.sharpnessScore.toInt())
                        bestItem.sharpnessScore > 0f && bestItem.isBlurry ->
                            stringResource(R.string.clarity_score_blurry_fmt, bestItem.sharpnessScore.toInt())
                        bestItem.isBlurry -> stringResource(R.string.clarity_slight_motion)
                        else -> stringResource(R.string.clarity_crisp_focus)
                    }
                    val candClarityLabel = when {
                        candidateItem.sharpnessScore > 0f && !candidateItem.isBlurry ->
                            stringResource(R.string.clarity_score_sharp_fmt, candidateItem.sharpnessScore.toInt())
                        candidateItem.sharpnessScore > 0f && candidateItem.isBlurry ->
                            stringResource(R.string.clarity_score_blurry_fmt, candidateItem.sharpnessScore.toInt())
                        candidateItem.isBlurry -> stringResource(R.string.clarity_blurry)
                        else -> stringResource(R.string.clarity_sharp)
                    }
                    MetadataRow(
                        stringResource(R.string.metadata_clarity_rating),
                        bestClarityLabel,
                        candClarityLabel,
                        isBestHighlight = bestItem.sharpnessScore >= candidateItem.sharpnessScore && !bestItem.isBlurry
                    )
                    if (bestItem.faceCount > 0 || candidateItem.faceCount > 0) {
                        val bestFaceLabel = when {
                            bestItem.faceCount == 0 -> stringResource(R.string.face_not_detected)
                            bestItem.hasBlinkingFace -> stringResource(R.string.face_blink_detected)
                            else -> stringResource(R.string.face_eyes_open_fmt, bestItem.faceQualityScore.toInt().coerceIn(0, 100))
                        }
                        val candFaceLabel = when {
                            candidateItem.faceCount == 0 -> stringResource(R.string.face_not_detected)
                            candidateItem.hasBlinkingFace -> stringResource(R.string.face_blink_detected)
                            else -> stringResource(R.string.face_eyes_open_fmt, candidateItem.faceQualityScore.toInt().coerceIn(0, 100))
                        }
                        MetadataRow(
                            stringResource(R.string.metadata_face_eyes),
                            bestFaceLabel,
                            candFaceLabel,
                            isBestHighlight = bestItem.faceCount > 0 && !bestItem.hasBlinkingFace && bestItem.faceQualityScore >= candidateItem.faceQualityScore
                        )
                    }
                    MetadataRow(stringResource(R.string.metadata_storage_path), bestItem.path.takeLast(25), candidateItem.path.takeLast(25))
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
                    Text(stringResource(R.string.btn_confirm_selection), fontWeight = FontWeight.Bold)
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

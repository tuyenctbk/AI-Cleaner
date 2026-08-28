package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.data.model.formatFileSize
import com.example.ui.components.SwipeCardDeck
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState
import com.example.ui.viewmodel.SwipeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeCleanScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onFilterChange: (SwipeFilter) -> Unit,
    onSwipeLeftDelete: (MediaItem) -> Unit,
    onSwipeRightKeep: (MediaItem) -> Unit,
    onUndo: () -> Unit,
    onExecuteTrashClean: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trashedBytes = uiState.swipeTrashItems.sumOf { it.sizeBytes }
    val totalInitialItems = uiState.swipeQueue.size + uiState.swipeTrashItems.size + uiState.swipeKeepItems.size
    val reviewedCount = uiState.swipeTrashItems.size + uiState.swipeKeepItems.size
    val progressFraction = if (totalInitialItems > 0) reviewedCount.toFloat() / totalInitialItems.toFloat() else 0f
    var showConfirmTrashCleanDialog by remember { mutableStateOf(false) }

    if (showConfirmTrashCleanDialog) {
        com.example.ui.components.MassDeletionSummaryDialog(
            title = "Permanently Delete Trashed Photos?",
            subtitle = "Purge items marked for deletion during swipe review",
            totalFilesCount = uiState.swipeTrashItems.size,
            projectedSpaceBytes = trashedBytes,
            itemsPreviewList = uiState.swipeTrashItems.map { "${it.title} (${it.formattedSize})" },
            onConfirmDelete = {
                onExecuteTrashClean()
            },
            onDismiss = { showConfirmTrashCleanDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Swipe Gallery Cleaner",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Counter Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "$reviewedCount / $totalInitialItems Reviewed",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (uiState.swipeTrashItems.isNotEmpty()) {
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
                            onClick = { showConfirmTrashCleanDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("empty_trash_swipe_button"),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
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
                                    text = "Clean Trashed (${uiState.swipeTrashItems.size} items • ${formatFileSize(trashedBytes)})",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("swipe_screen_column"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress Bar & Counter Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gallery Progress Counter",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(progressFraction * 100).toInt()}% completed",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.swipeFilter == SwipeFilter.ALL,
                    onClick = { onFilterChange(SwipeFilter.ALL) },
                    label = { Text("All Photos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = uiState.swipeFilter == SwipeFilter.BLURRY,
                    onClick = { onFilterChange(SwipeFilter.BLURRY) },
                    label = { Text("Blurry (${uiState.allMedia.count { it.isBlurry }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = uiState.swipeFilter == SwipeFilter.SCREENSHOTS,
                    onClick = { onFilterChange(SwipeFilter.SCREENSHOTS) },
                    label = { Text("Screenshots (${uiState.allMedia.count { it.isScreenshot }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Swiping Deck
            SwipeCardDeck(
                items = uiState.swipeQueue,
                onSwipeLeftDelete = onSwipeLeftDelete,
                onSwipeRightKeep = onSwipeRightKeep,
                onUndo = onUndo,
                canUndo = uiState.swipeTrashItems.isNotEmpty() || uiState.swipeKeepItems.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )

            // Current Session Quick Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(CoralRed, RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "Marked for Delete: ${uiState.swipeTrashItems.size}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(EmeraldGreen, RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "Kept: ${uiState.swipeKeepItems.size}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


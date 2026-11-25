package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import com.example.data.ai.AiExecutionTier
import com.example.data.ai.RecommendationActionMode
import com.example.data.ai.RecommendationCategory
import com.example.data.ai.RecommendationPriority
import com.example.data.ai.SmartRecommendation
import com.example.data.model.MediaItem
import com.example.data.model.formatFileSize
import com.example.ui.components.ActiveAiEnginePill
import com.example.ui.components.AiQuotaRecoveryCard
import com.example.ui.components.AiSoftNudgeBanner
import com.example.ui.components.CommonMediaThumbnail
import com.example.ui.components.MassDeletionSummaryDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartRecommendationsScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onAnalyzeWithGemini: () -> Unit,
    onRetryEcoLite: () -> Unit,
    onRunLocalAnalysis: () -> Unit,
    onToggleEcoLite: (Boolean) -> Unit,
    onClaimGraceCredits: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onApplyRecommendation: (SmartRecommendation, Set<Long>) -> Unit,
    onDismissRecommendation: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var activeFilter by remember { mutableStateOf<RecommendationCategory?>(null) }
    var pendingDeleteConfirmation by remember { mutableStateOf<Pair<SmartRecommendation, List<MediaItem>>?>(null) }

    val mediaById = remember(uiState.allMedia) {
        uiState.allMedia.associateBy { it.id }
    }

    val filteredRecommendations = remember(uiState.smartRecommendations, activeFilter) {
        val list = uiState.smartRecommendations
        if (activeFilter == null) list else list.filter { it.category == activeFilter }
    }

    val totalReclaimableBytes = remember(uiState.smartRecommendations) {
        uiState.smartRecommendations.filter { !it.isApplied }.sumOf { it.reclaimableBytes }
    }
    val totalTargetsCount = remember(uiState.smartRecommendations) {
        uiState.smartRecommendations.filter { !it.isApplied }.sumOf { it.itemCount }
    }
    val blurryCount = remember(uiState.smartRecommendations) {
        uiState.smartRecommendations
            .filter { !it.isApplied && it.category == RecommendationCategory.BLURRY_PHOTOS }
            .sumOf { it.itemCount }
    }
    val lowResShotCount = remember(uiState.smartRecommendations) {
        uiState.smartRecommendations
            .filter { !it.isApplied && it.category == RecommendationCategory.LOW_QUALITY_SCREENSHOTS }
            .sumOf { it.itemCount }
    }

    pendingDeleteConfirmation?.let { (recommendation, targets) ->
        MassDeletionSummaryDialog(
            title = stringResource(R.string.smart_rec_dialog_title),
            subtitle = recommendation.title,
            totalFilesCount = targets.size,
            projectedSpaceBytes = targets.sumOf { it.sizeBytes },
            itemsPreviewList = targets.take(8).map { "${it.title} (${it.formattedSize})" },
            onConfirmDelete = {
                pendingDeleteConfirmation = null
                onApplyRecommendation(recommendation, targets.map { it.id }.toSet())
            },
            onDismiss = { pendingDeleteConfirmation = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = stringResource(R.string.title_smart_recommendations),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.smart_rec_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ActiveAiEnginePill(
                            quota = uiState.aiQuota.copy(activeTier = uiState.recommendationsLastTier),
                            fromCache = uiState.recommendationsFromCache
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("smart_rec_back_button")
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
                            onAnalyzeWithGemini()
                        },
                        enabled = !uiState.isAnalyzingRecommendations,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("smart_rec_refresh_button")
                    ) {
                        if (uiState.isAnalyzingRecommendations) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = stringResource(R.string.smart_rec_btn_analyze_gemini),
                                tint = VividViolet
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .testTag("smart_recommendations_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Summary & Gemini Analysis Trigger
            item {
                SmartRecommendationHeroCard(
                    totalReclaimableBytes = totalReclaimableBytes,
                    totalTargetsCount = totalTargetsCount,
                    blurryCount = blurryCount,
                    lowResShotCount = lowResShotCount,
                    isAnalyzing = uiState.isAnalyzingRecommendations,
                    onAnalyzeClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAnalyzeWithGemini()
                    }
                )
            }

            // Explicit error notice (if cloud Gemini call hit an error before local fallback)
            uiState.recommendationsError?.let { errorMsg ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart_rec_error_banner"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CoralRed.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = CoralRed
                            )
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onDismissError,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text(stringResource(R.string.smart_rec_btn_dismiss))
                            }
                        }
                    }
                }
            }

            if (uiState.aiQuota.shouldShowSoftNudge) {
                item {
                    AiSoftNudgeBanner(
                        quota = uiState.aiQuota,
                        onSwitchToEcoLite = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleEcoLite(true)
                        }
                    )
                }
            }

            if (uiState.aiQuota.shouldShowQuotaRecovery) {
                item {
                    AiQuotaRecoveryCard(
                        quota = uiState.aiQuota,
                        onClaimGraceCredits = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClaimGraceCredits()
                        },
                        onRetryEcoLite = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRetryEcoLite()
                        },
                        onRunLocalAi = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRunLocalAnalysis()
                        },
                        onOpenAiSettings = onOpenAiSettings
                    )
                }
            }

            // Category filter strip
            if (uiState.smartRecommendations.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = activeFilter == null,
                                onClick = { activeFilter = null },
                                label = { Text(stringResource(R.string.storage_filter_all)) },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("smart_rec_filter_all")
                            )
                        }
                        items(RecommendationCategory.entries) { category ->
                            val hasEntries = uiState.smartRecommendations.any { it.category == category }
                            if (hasEntries) {
                                FilterChip(
                                    selected = activeFilter == category,
                                    onClick = {
                                        activeFilter = if (activeFilter == category) null else category
                                    },
                                    label = { Text(categoryLabel(category)) },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("smart_rec_filter_${category.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            if (filteredRecommendations.isEmpty()) {
                item {
                    SmartRecommendationsEmptyCard(onReanalyze = onAnalyzeWithGemini)
                }
            } else {
                items(filteredRecommendations, key = { it.id }) { recommendation ->
                    val targetMedia = remember(recommendation.targetMediaIds, mediaById) {
                        recommendation.targetMediaIds.mapNotNull { id -> mediaById[id] }
                    }
                    SmartRecommendationCard(
                        recommendation = recommendation,
                        targetMedia = targetMedia,
                        onRequestApply = { selectedIds ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val selectedMedia = targetMedia.filter { it.id in selectedIds }
                            if (recommendation.actionMode == RecommendationActionMode.COMPRESS_VIDEOS ||
                                selectedMedia.isEmpty()
                            ) {
                                onApplyRecommendation(recommendation, selectedIds)
                            } else {
                                pendingDeleteConfirmation = Pair(recommendation, selectedMedia)
                            }
                        },
                        onDismiss = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismissRecommendation(recommendation.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartRecommendationHeroCard(
    totalReclaimableBytes: Long,
    totalTargetsCount: Int,
    blurryCount: Int,
    lowResShotCount: Int,
    isAnalyzing: Boolean,
    onAnalyzeClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(24.dp))
            .testTag("smart_rec_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(VividViolet, ElectricBlue))
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.smart_rec_hero_title),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = VividViolet
                    )
                    Text(
                        text = formatFileSize(totalReclaimableBytes),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.smart_rec_hero_desc_fmt,
                            totalTargetsCount,
                            formatFileSize(totalReclaimableBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(VividViolet.copy(alpha = 0.22f), ElectricBlue.copy(alpha = 0.22f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = VividViolet,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecommendationStatPill(
                    label = stringResource(R.string.smart_rec_filter_blurry),
                    count = blurryCount,
                    color = CoralRed,
                    modifier = Modifier.weight(1f)
                )
                RecommendationStatPill(
                    label = stringResource(R.string.smart_rec_filter_screenshots),
                    count = lowResShotCount,
                    color = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onAnalyzeClick,
                enabled = !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("smart_rec_analyze_gemini_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VividViolet,
                    contentColor = Color.White
                )
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.smart_rec_analyzing), fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.smart_rec_btn_analyze_gemini),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationStatPill(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.11f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = color
            )
        }
    }
}

@Composable
private fun SmartRecommendationCard(
    recommendation: SmartRecommendation,
    targetMedia: List<MediaItem>,
    onRequestApply: (Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = categoryAccentColor(recommendation.category)
    var selectedIds by remember(recommendation.id, recommendation.targetMediaIds) {
        mutableStateOf(recommendation.targetMediaIds.toSet())
    }

    val activeTargetMedia = remember(targetMedia, selectedIds) {
        targetMedia.filter { it.id in selectedIds }
    }
    val selectedBytes = remember(activeTargetMedia, recommendation.reclaimableBytes, targetMedia.size) {
        if (targetMedia.isNotEmpty()) activeTargetMedia.sumOf { it.sizeBytes } else recommendation.reclaimableBytes
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_rec_card_${recommendation.category.name.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (recommendation.isApplied) listOf(EmeraldGreen, EmeraldGreen)
                else listOf(accent.copy(alpha = 0.65f), ElectricBlue.copy(alpha = 0.55f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top badges row: Priority + Category + Tier/Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = categoryIcon(recommendation.category),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = categoryLabel(recommendation.category),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accent
                            )
                        }
                    }

                    PriorityBadge(priority = recommendation.priority)
                }

                Text(
                    text = stringResource(R.string.ai_confidence_fmt, recommendation.confidenceScore),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = EmeraldGreen
                )
            }

            // Title & Gemini rationale
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = recommendation.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = recommendation.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Source tier chip & selection count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tierLabel = when (recommendation.executionTier) {
                    AiExecutionTier.PRIMARY_CLOUD -> stringResource(R.string.smart_rec_badge_gemini)
                    AiExecutionTier.ECONOMY_LITE -> stringResource(R.string.smart_rec_badge_eco_lite)
                    else -> stringResource(R.string.smart_rec_badge_on_device)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = tierLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (targetMedia.isNotEmpty() && !recommendation.isApplied) {
                    Text(
                        text = stringResource(
                            R.string.smart_rec_selected_count_fmt,
                            activeTargetMedia.size,
                            targetMedia.size
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                }
            }

            // Interactive Thumbnail Strip of Real Detected Media Items
            AnimatedVisibility(visible = targetMedia.isNotEmpty() && !recommendation.isApplied) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(targetMedia.take(15), key = { it.id }) { media ->
                        val isSelected = media.id in selectedIds
                        RecommendationThumbnailTile(
                            media = media,
                            isSelected = isSelected,
                            accent = accent,
                            onToggle = {
                                selectedIds = if (isSelected) selectedIds - media.id else selectedIds + media.id
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Action footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (recommendation.isApplied) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldGreen
                        )
                        Text(
                            text = stringResource(R.string.smart_rec_applied_label),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldGreen
                        )
                    }
                } else {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("smart_rec_dismiss_${recommendation.category.name.lowercase()}")
                    ) {
                        Text(
                            text = stringResource(R.string.smart_rec_btn_dismiss),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onRequestApply(selectedIds) },
                        enabled = targetMedia.isEmpty() || activeTargetMedia.isNotEmpty(),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("smart_rec_apply_${recommendation.category.name.lowercase()}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (recommendation.actionMode == RecommendationActionMode.COMPRESS_VIDEOS) {
                                Icons.Default.Compress
                            } else {
                                Icons.Default.CleaningServices
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (recommendation.actionMode == RecommendationActionMode.COMPRESS_VIDEOS) {
                                recommendation.suggestedActionLabel
                            } else {
                                stringResource(R.string.smart_rec_btn_clean_fmt, formatFileSize(selectedBytes))
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationThumbnailTile(
    media: MediaItem,
    isSelected: Boolean,
    accent: Color,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onToggle)
            .padding(6.dp)
            .testTag("smart_rec_thumb_${media.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            CommonMediaThumbnail(
                mediaItem = media,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp),
                shape = CircleShape,
                color = if (isSelected) accent else Color.Black.copy(alpha = 0.55f)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(3.dp)
                )
            }
        }
        Text(
            text = media.formattedSize,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        val badgeText = when {
            media.isBlurry && media.sharpnessScore > 0f ->
                stringResource(R.string.smart_rec_sharpness_fmt, media.sharpnessScore.toInt())
            media.width > 0 && media.height > 0 ->
                stringResource(R.string.format_resolution, media.width, media.height)
            else -> media.title
        }
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PriorityBadge(priority: RecommendationPriority) {
    val (bg, fg, labelRes) = when (priority) {
        RecommendationPriority.HIGH -> Triple(CoralRed.copy(alpha = 0.14f), CoralRed, R.string.smart_rec_priority_high)
        RecommendationPriority.MEDIUM -> Triple(WarningAmber.copy(alpha = 0.16f), WarningAmber, R.string.smart_rec_priority_medium)
        RecommendationPriority.LOW -> Triple(ElectricBlue.copy(alpha = 0.14f), ElectricBlue, R.string.smart_rec_priority_low)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SmartRecommendationsEmptyCard(onReanalyze: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_rec_empty_card"),
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
                text = stringResource(R.string.smart_rec_empty_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.smart_rec_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onReanalyze,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.smart_rec_btn_analyze_gemini))
            }
        }
    }
}

@Composable
private fun categoryLabel(category: RecommendationCategory): String {
    return when (category) {
        RecommendationCategory.BLURRY_PHOTOS -> stringResource(R.string.smart_rec_filter_blurry)
        RecommendationCategory.LOW_QUALITY_SCREENSHOTS -> stringResource(R.string.smart_rec_filter_screenshots)
        RecommendationCategory.LARGE_VIDEOS -> stringResource(R.string.smart_rec_filter_videos)
        RecommendationCategory.OLD_DOWNLOADS -> stringResource(R.string.smart_rec_filter_downloads)
        RecommendationCategory.LOW_RES_PHOTOS -> stringResource(R.string.smart_rec_filter_low_res)
    }
}

private fun categoryAccentColor(category: RecommendationCategory): Color {
    return when (category) {
        RecommendationCategory.BLURRY_PHOTOS -> CoralRed
        RecommendationCategory.LOW_QUALITY_SCREENSHOTS -> WarningAmber
        RecommendationCategory.LARGE_VIDEOS -> ElectricBlue
        RecommendationCategory.OLD_DOWNLOADS -> VividViolet
        RecommendationCategory.LOW_RES_PHOTOS -> EmeraldGreen
    }
}

private fun categoryIcon(category: RecommendationCategory): ImageVector {
    return when (category) {
        RecommendationCategory.BLURRY_PHOTOS -> Icons.Default.BlurOn
        RecommendationCategory.LOW_QUALITY_SCREENSHOTS -> Icons.Default.Screenshot
        RecommendationCategory.LARGE_VIDEOS -> Icons.Default.Videocam
        RecommendationCategory.OLD_DOWNLOADS -> Icons.Default.FileDownload
        RecommendationCategory.LOW_RES_PHOTOS -> Icons.Default.PhotoSizeSelectSmall
    }
}

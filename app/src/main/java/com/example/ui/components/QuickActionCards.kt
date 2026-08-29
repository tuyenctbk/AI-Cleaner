package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.ui.components.dpadFocusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StorageStats
import com.example.data.model.formatFileSize
import com.example.ui.theme.*

import com.example.R
import androidx.compose.ui.res.stringResource

@Composable
fun QuickActionGrid(
    stats: StorageStats,
    onOpenDuplicates: () -> Unit,
    onOpenSwipeClean: () -> Unit,
    onOpenCompressor: () -> Unit,
    onOpenStorageExplorer: () -> Unit,
    onOpenAppCache: () -> Unit,
    onOpenSmartScheduler: () -> Unit,
    onOpenBatteryPerformance: () -> Unit,
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.section_manual_tools),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.label_all_tools_free),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 3x2 Grid of Main Feature Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: App Cache Cleaner
            ToolFeatureCard(
                title = stringResource(R.string.card_app_cache),
                subtitle = stringResource(R.string.card_app_cache_sub),
                badgeText = stringResource(R.string.btn_clear_cache),
                badgeColor = ElectricBlue,
                icon = Icons.Default.Apps,
                iconTint = ElectricBlue,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                onClick = onOpenAppCache,
                modifier = Modifier.weight(1f).testTag("app_cache_feature_card")
            )

            // Card 2: Vault Folder
            ToolFeatureCard(
                title = stringResource(R.string.card_vault),
                subtitle = stringResource(R.string.card_vault_sub),
                badgeText = stringResource(R.string.badge_secured),
                badgeColor = VividViolet,
                icon = Icons.Default.Lock,
                iconTint = VividViolet,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                onClick = onOpenVault,
                modifier = Modifier.weight(1f).testTag("vault_feature_card")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 3: Similar / Duplicate Photos (Featured Accent Card)
            ToolFeatureCard(
                title = stringResource(R.string.card_similar_photos),
                subtitle = stringResource(R.string.badge_duplicates_count, stats.duplicateCount),
                badgeText = stringResource(R.string.btn_keep_best_shot),
                badgeColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.BurstMode,
                iconTint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                onClick = onOpenDuplicates,
                modifier = Modifier.weight(1f).testTag("duplicates_feature_card")
            )

            // Card 4: Swipe Clean Gallery
            ToolFeatureCard(
                title = stringResource(R.string.card_swipe_clean),
                subtitle = stringResource(R.string.card_swipe_clean_sub),
                badgeText = stringResource(R.string.badge_fast_fun),
                badgeColor = EmeraldGreen,
                icon = Icons.Default.Swipe,
                iconTint = EmeraldGreen,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                onClick = onOpenSwipeClean,
                modifier = Modifier.weight(1f).testTag("swipe_feature_card")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 5: Photo & Video Compressor
            ToolFeatureCard(
                title = stringResource(R.string.card_compressor),
                subtitle = stringResource(R.string.card_compressor_sub),
                badgeText = stringResource(R.string.badge_lossless_ai),
                badgeColor = WarningAmber,
                icon = Icons.Default.Compress,
                iconTint = WarningAmber,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                onClick = onOpenCompressor,
                modifier = Modifier.weight(1f).testTag("compressor_feature_card")
            )

            // Card 6: Deep Storage Breakdown
            ToolFeatureCard(
                title = stringResource(R.string.card_deep_storage),
                subtitle = stringResource(R.string.card_deep_storage_sub),
                badgeText = stringResource(R.string.badge_large_files_count, stats.largeFileCount),
                badgeColor = MaterialTheme.colorScheme.secondary,
                icon = Icons.Default.PieChart,
                iconTint = MaterialTheme.colorScheme.secondary,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                onClick = onOpenStorageExplorer,
                modifier = Modifier.weight(1f).testTag("storage_explorer_card")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 7: Battery & Performance Tips
            ToolFeatureCard(
                title = stringResource(R.string.card_battery),
                subtitle = stringResource(R.string.card_battery_sub),
                badgeText = stringResource(R.string.badge_health_format, 88),
                badgeColor = EmeraldGreen,
                icon = Icons.Default.BatteryChargingFull,
                iconTint = EmeraldGreen,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                onClick = onOpenBatteryPerformance,
                modifier = Modifier.weight(1f).testTag("battery_performance_card")
            )

            // Card 8: Smart Scheduler Quick Link
            ToolFeatureCard(
                title = stringResource(R.string.card_auto_scan),
                subtitle = stringResource(R.string.card_auto_scan_sub),
                badgeText = stringResource(R.string.badge_active),
                badgeColor = ElectricBlue,
                icon = Icons.Default.Schedule,
                iconTint = ElectricBlue,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                onClick = onOpenSmartScheduler,
                modifier = Modifier.weight(1f).testTag("auto_scan_card")
            )
        }

        // Banner: Media Categories Quick Bar
        MediaCategoriesQuickBar(
            photoCount = stats.photoCount,
            photoSize = formatFileSize(stats.photoBytes),
            videoCount = stats.videoCount,
            videoSize = formatFileSize(stats.videoBytes),
            onPhotosClick = onOpenDuplicates,
            onVideosClick = onOpenStorageExplorer
        )
    }
}

@Composable
fun ToolFeatureCard(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .height(144.dp)
            .shadow(2.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.03f))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .dpadFocusable(interactionSource, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(borderColor)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // White rounded-xl icon container with soft shadow
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(2.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.05f))
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Badge Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MediaCategoriesQuickBar(
    photoCount: Int,
    photoSize: String,
    videoCount: Int,
    videoSize: String,
    onPhotosClick: () -> Unit,
    onVideosClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photoInteraction = remember { MutableInteractionSource() }
    val videoInteraction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Photos Category Card
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = photoInteraction,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onPhotosClick
                )
                .dpadFocusable(photoInteraction, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.storage_donut_photos),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = photoSize,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Text(
                        text = stringResource(R.string.card_photos_count, photoCount),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Videos Category Card
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = videoInteraction,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onVideosClick
                )
                .dpadFocusable(videoInteraction, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ColorVideos.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = ColorVideos,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.storage_donut_videos),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = videoSize,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = ColorVideos
                            )
                        )
                    }
                    Text(
                        text = stringResource(R.string.card_videos_count, videoCount),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


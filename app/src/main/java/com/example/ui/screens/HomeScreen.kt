package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.formatFileSize
import com.example.ui.components.MascotHeader
import com.example.ui.components.QuickActionGrid
import com.example.ui.components.StorageOverviewCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState
import com.example.ui.viewmodel.MainScreenTab

import com.example.ui.components.CleanupTipsSection

import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle

@Composable
fun HomeScreen(
    uiState: CleanerUiState,
    onNavigate: (MainScreenTab) -> Unit,
    onSmartClean: () -> Unit,
    onGlobalQuickClean: () -> Unit,
    onDismissTip: (String) -> Unit,
    onTestLowStorageNotification: () -> Unit,
    onTogglePauseCleaning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("home_screen_lazy_column"),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mascot and App Identity Header
        item {
            MascotHeader(onOpenSettings = { onNavigate(MainScreenTab.SETTINGS) })
        }

        // Pause Cleaning Control Card
        item {
            PauseCleaningCard(
                isPaused = uiState.isCleaningPaused,
                onTogglePause = onTogglePauseCleaning
            )
        }

        // Main Storage Overview Card with AI Smart Clean Button
        item {
            StorageOverviewCard(
                stats = uiState.storageStats,
                isScanning = uiState.isScanning,
                onSmartCleanClick = onSmartClean
            )
        }

        // Global Quick Action Card (Pre-defined Safe Quick Clean routine)
        item {
            GlobalQuickActionCard(
                isExecuting = uiState.isExecutingGlobalQuickClean,
                progress = uiState.globalQuickCleanProgress,
                stepText = uiState.globalQuickCleanStepText,
                junkBytes = uiState.storageStats.junkBytes,
                onQuickCleanClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onGlobalQuickClean()
                }
            )
        }

        // AI & Cloud Intelligence Hub
        item {
            AiCloudIntelligenceHub(
                cloudPhotosCount = uiState.cloudSyncedPhotos.size,
                aiRoutinesCount = uiState.aiRoutines.size,
                onOpenCloudCleaner = { onNavigate(MainScreenTab.CLOUD_SYNCED_PHOTOS) },
                onOpenStorageTrends = { onNavigate(MainScreenTab.STORAGE_TRENDS) },
                onOpenAiInsights = { onNavigate(MainScreenTab.AI_INSIGHTS) }
            )
        }

        // Cleanup Tips Section (Personalized habits analysis)
        if (uiState.cleanupTips.isNotEmpty()) {
            item {
                CleanupTipsSection(
                    tips = uiState.cleanupTips,
                    onActionClick = { tab ->
                        if (tab != null) onNavigate(tab)
                    },
                    onDismissTip = onDismissTip
                )
            }
        }

        // Quick Feature Grid (App Cache, Vault, Duplicates, Swipe, Compressor, Deep Storage, Battery)
        item {
            QuickActionGrid(
                stats = uiState.storageStats,
                onOpenDuplicates = { onNavigate(MainScreenTab.DUPLICATES) },
                onOpenSwipeClean = { onNavigate(MainScreenTab.SWIPE_CLEAN) },
                onOpenCompressor = { onNavigate(MainScreenTab.COMPRESSOR) },
                onOpenStorageExplorer = { onNavigate(MainScreenTab.STORAGE_EXPLORER) },
                onOpenAppCache = { onNavigate(MainScreenTab.APP_CACHE) },
                onOpenSmartScheduler = { onNavigate(MainScreenTab.SMART_SCHEDULER) },
                onOpenBatteryPerformance = { onNavigate(MainScreenTab.BATTERY_PERFORMANCE) },
                onOpenVault = { onNavigate(MainScreenTab.VAULT) }
            )
        }

        // Home Screen Widget & Observer Test Card
        item {
            HomeScreenWidgetCard(
                stats = uiState.storageStats,
                onOneTapScan = onSmartClean,
                onTestLowStorageNotification = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTestLowStorageNotification()
                }
            )
        }

        // Clean History & Lifetime Saved Banner
        if (uiState.cleanHistory.isNotEmpty()) {
            item {
                CleanHistoryBanner(
                    totalCleaned = formatFileSize(uiState.cleanHistory.sumOf { it.freedBytes }),
                    recordsCount = uiState.cleanHistory.size,
                    onClick = { onNavigate(MainScreenTab.CLEANUP_HISTORY) }
                )
            }
        }
    }
}

@Composable
private fun CleanHistoryBanner(
    totalCleaned: String,
    recordsCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
            .testTag("home_clean_history_banner"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.stat_lifetime_space_freed),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.stat_operations_format, recordsCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = totalCleaned,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = EmeraldGreen
                )
            )
        }
    }
}

@Composable
private fun HomeScreenWidgetCard(
    stats: com.example.data.model.StorageStats,
    onOneTapScan: () -> Unit,
    onTestLowStorageNotification: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("home_screen_widget_preview_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Widgets, contentDescription = null, tint = ElectricBlue)
                    Text(
                        text = stringResource(R.string.widget_preview_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ElectricBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(R.string.badge_active),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Live Widget Simulation Card Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ElectricBlue.copy(alpha = 0.3f))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.widget_ai_title),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.stat_used_percent, stats.usedPercent),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ElectricBlue
                        )
                    }

                    LinearProgressIndicator(
                        progress = { stats.usedPercentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ElectricBlue,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.stat_free_of_total, stats.formattedFree, stats.formattedTotal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = onOneTapScan,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_scan), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Low Storage Observer Notification Test Trigger
            OutlinedButton(
                onClick = onTestLowStorageNotification,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_low_storage_notification_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed)
            ) {
                Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, tint = CoralRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_test_low_storage), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun GlobalQuickActionCard(
    isExecuting: Boolean,
    progress: Float,
    stepText: String,
    junkBytes: Long,
    onQuickCleanClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("global_quick_action_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(EmeraldGreen, ElectricBlue))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.btn_safe_quick_clean),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.card_quick_clean_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Freed ~${formatFileSize(junkBytes.coerceAtLeast(1_850_000_000L))}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (isExecuting) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stepText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricBlue
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = EmeraldGreen,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            } else {
                Button(
                    onClick = onQuickCleanClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_quick_action_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_quick_clean), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AiCloudIntelligenceHub(
    cloudPhotosCount: Int,
    aiRoutinesCount: Int,
    onOpenCloudCleaner: () -> Unit,
    onOpenStorageTrends: () -> Unit,
    onOpenAiInsights: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.section_smart_storage_features),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cloud Photos Cleaner
            IntelligenceCard(
                title = stringResource(R.string.card_cloud_photos),
                subtitle = stringResource(R.string.badge_backed_up_count, cloudPhotosCount),
                icon = Icons.Default.CloudDone,
                color = EmeraldGreen,
                onClick = onOpenCloudCleaner,
                modifier = Modifier.weight(1f)
            )

            // Storage Trends
            IntelligenceCard(
                title = stringResource(R.string.card_storage_trends),
                subtitle = stringResource(R.string.card_storage_history),
                icon = Icons.Default.ShowChart,
                color = ElectricBlue,
                onClick = onOpenStorageTrends,
                modifier = Modifier.weight(1f)
            )

            // Gemini AI Insights
            IntelligenceCard(
                title = stringResource(R.string.card_ai_insights),
                subtitle = stringResource(R.string.badge_routines_count, aiRoutinesCount),
                icon = Icons.Default.AutoAwesome,
                color = VividViolet,
                onClick = onOpenAiInsights,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun IntelligenceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("intelligence_card_${title.lowercase().replace(' ', '_')}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.5f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PauseCleaningCard(
    isPaused: Boolean,
    onTogglePause: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .testTag("pause_cleaning_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPaused) CoralRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isPaused) CoralRed.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                        .background(if (isPaused) CoralRed.copy(alpha = 0.2f) else EmeraldGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = if (isPaused) CoralRed else EmeraldGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = if (isPaused) stringResource(R.string.label_cleaning_paused) else stringResource(R.string.label_background_active),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isPaused) stringResource(R.string.settings_pause_cleaning_on)
                        else stringResource(R.string.settings_pause_cleaning_off),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = isPaused,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePause()
                },
                modifier = Modifier.testTag("pause_cleaning_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CoralRed
                )
            )
        }
    }
}


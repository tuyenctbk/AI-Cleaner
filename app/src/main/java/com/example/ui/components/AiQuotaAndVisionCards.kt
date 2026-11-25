package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ai.AiExecutionTier
import com.example.data.ai.AiModelConfig
import com.example.data.ai.AiQuotaSnapshot
import com.example.data.model.ScreenshotClassificationSummary
import com.example.data.model.formatFileSize
import com.example.ui.theme.*

@Composable
fun ActiveAiEnginePill(
    quota: AiQuotaSnapshot,
    fromCache: Boolean,
    modifier: Modifier = Modifier
) {
    val (labelRes, pillColor) = when {
        fromCache -> R.string.ai_engine_cached to EmeraldGreen
        quota.hasUserByokKey && quota.activeTier != AiExecutionTier.ON_DEVICE_LOCAL ->
            R.string.ai_engine_byok to ElectricBlue
        quota.activeTier == AiExecutionTier.ECONOMY_LITE ->
            R.string.ai_engine_eco_lite to AmberGolden
        quota.activeTier == AiExecutionTier.PRIMARY_CLOUD ->
            R.string.ai_engine_cloud to VividViolet
        else -> R.string.ai_engine_on_device to EmeraldGreen
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = pillColor.copy(alpha = 0.16f),
        modifier = modifier.testTag("ai_active_engine_pill")
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = pillColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DailyAiValueReceiptCard(
    quota: AiQuotaSnapshot,
    cascadedToLite: Boolean,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_value_receipt_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.ai_value_receipt_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onOpenAiSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("ai_open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.title_settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 4-metric Value Receipt strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ValueMetricTile(
                    value = quota.facesScoredCount.toString(),
                    label = stringResource(R.string.ai_metric_faces_scored),
                    accent = VividViolet,
                    modifier = Modifier.weight(1f)
                )
                ValueMetricTile(
                    value = quota.screenshotsClassifiedCount.toString(),
                    label = stringResource(R.string.ai_metric_screenshots_ocr),
                    accent = ElectricBlue,
                    modifier = Modifier.weight(1f)
                )
                ValueMetricTile(
                    value = quota.routinesGeneratedCount.toString(),
                    label = stringResource(R.string.ai_metric_routines),
                    accent = EmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
                ValueMetricTile(
                    value = quota.cacheHitsSavedCount.toString(),
                    label = stringResource(R.string.ai_metric_cache_saved),
                    accent = AmberGolden,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quota Status & Countdown Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = when {
                    quota.hasUserByokKey -> stringResource(R.string.ai_value_receipt_byok_badge)
                    quota.isCloudKeyConfigured -> stringResource(
                        R.string.ai_value_receipt_quota_fmt,
                        quota.dailyUsed,
                        quota.effectiveLimit
                    )
                    else -> stringResource(R.string.ai_value_receipt_free_badge)
                }
                val statusColor = when {
                    quota.hasUserByokKey -> ElectricBlue
                    !quota.isCloudKeyConfigured -> EmeraldGreen
                    quota.usageRatio >= 0.8f -> AmberGolden
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = statusColor
                )
                Text(
                    text = stringResource(
                        R.string.ai_value_receipt_resets_fmt,
                        quota.resetHours,
                        quota.resetMinutes
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (quota.isCloudKeyConfigured && !quota.hasUserByokKey) {
                LinearProgressIndicator(
                    progress = { quota.usageRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        quota.remainingCredits <= 0 -> CoralRed
                        quota.usageRatio >= 0.8f -> AmberGolden
                        else -> VividViolet
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (cascadedToLite) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AmberGolden.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = AmberGolden,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.ai_cascaded_to_lite_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueMetricTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AiSoftNudgeBanner(
    quota: AiQuotaSnapshot,
    onSwitchToEcoLite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_soft_nudge_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AmberGolden.copy(alpha = 0.12f)),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(AmberGolden.copy(alpha = 0.6f)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BatterySaver,
                    contentDescription = null,
                    tint = AmberGolden,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.ai_nudge_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(
                    R.string.ai_nudge_body_fmt,
                    quota.usagePercent,
                    quota.remainingCredits,
                    quota.resetHours,
                    quota.resetMinutes
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onSwitchToEcoLite,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberGolden, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_switch_eco_lite_button")
            ) {
                Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ai_btn_switch_eco_lite),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AiQuotaRecoveryCard(
    quota: AiQuotaSnapshot,
    onClaimGraceCredits: () -> Unit,
    onRetryEcoLite: () -> Unit,
    onRunLocalAi: () -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_quota_recovery_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CoralRed.copy(alpha = 0.08f)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(CoralRed, AmberGolden))
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = CoralRed,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(R.string.ai_recovery_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(
                    R.string.ai_recovery_subtitle_fmt,
                    quota.resetHours,
                    quota.resetMinutes
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. One-time daily grace credits
            if (!quota.graceClaimedToday) {
                Button(
                    onClick = onClaimGraceCredits,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_claim_grace_credits_button")
                ) {
                    Icon(imageVector = Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ai_btn_claim_grace_fmt, AiModelConfig.GRACE_CREDITS_BONUS),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.ai_grace_claimed_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. Retry with Eco Lite + 3. Run Zero-Quota On-Device AI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRetryEcoLite,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_retry_eco_lite_button")
                ) {
                    Text(
                        text = stringResource(R.string.ai_btn_retry_eco_lite),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onRunLocalAi,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VividViolet,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_run_local_fallback_button")
                ) {
                    Text(
                        text = stringResource(R.string.ai_btn_run_local_ai),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 4. Connect Your Own Key (BYOK)
            TextButton(
                onClick = onOpenAiSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_connect_byok_button")
            ) {
                Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ai_btn_connect_byok),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SmartScreenshotOrganizerCard(
    summary: ScreenshotClassificationSummary,
    onRescanScreenshots: () -> Unit,
    onRequestDeleteSafe: () -> Unit,
    onProtectReceiptsToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_screenshot_organizer_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(EmeraldGreen.copy(alpha = 0.6f), ElectricBlue.copy(alpha = 0.6f)))
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.ai_ocr_card_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ai_ocr_card_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldGreen.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = stringResource(R.string.ai_ocr_card_badge),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (summary.isAnalyzing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = EmeraldGreen
                    )
                    Text(
                        text = stringResource(R.string.ai_ocr_analyzing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScreenshotBucketChip(
                        icon = Icons.Default.Pin,
                        count = summary.otpItems.size,
                        label = stringResource(R.string.ai_ocr_cat_otp),
                        accent = CoralRed,
                        modifier = Modifier.weight(1f)
                    )
                    ScreenshotBucketChip(
                        icon = Icons.Default.Forum,
                        count = summary.chatMemeItems.size,
                        label = stringResource(R.string.ai_ocr_cat_memes),
                        accent = AmberGolden,
                        modifier = Modifier.weight(1f)
                    )
                    ScreenshotBucketChip(
                        icon = Icons.Default.VerifiedUser,
                        count = summary.receiptDocItems.size,
                        label = stringResource(R.string.ai_ocr_cat_receipts),
                        accent = EmeraldGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (summary.totalClassified == 0) {
                    Text(
                        text = stringResource(R.string.ai_ocr_empty_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (summary.safeCleanableItems.isNotEmpty()) {
                    Button(
                        onClick = onRequestDeleteSafe,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clean_safe_screenshots_button")
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                R.string.ai_ocr_btn_clean_safe_fmt,
                                summary.safeCleanableItems.size,
                                formatFileSize(summary.safeCleanableBytes)
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (summary.receiptDocItems.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onProtectReceiptsToVault,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("protect_receipts_vault_button")
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.ai_ocr_btn_vault_receipts_fmt, summary.receiptDocItems.size),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onRescanScreenshots,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rescan_screenshots_ocr_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.ai_ocr_btn_rescan),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotBucketChip(
    icon: ImageVector,
    count: Int,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.11f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

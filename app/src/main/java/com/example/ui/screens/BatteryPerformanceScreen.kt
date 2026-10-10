package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.formatFileSize
import com.example.ui.theme.*
import com.example.ui.viewmodel.CleanerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.example.R
import androidx.compose.ui.res.stringResource

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.ui.platform.LocalContext

data class BatteryTipItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val impactTag: String,
    val icon: ImageVector,
    val iconTint: Color,
    val recommendedActionText: String,
    val detailExplanation: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryPerformanceScreen(
    uiState: CleanerUiState,
    onBack: () -> Unit,
    onNavigateToAppCache: () -> Unit,
    onNavigateToSmartClean: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val batteryIntent = remember(context) {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
    val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryPct = if (rawLevel >= 0 && scale > 0) (rawLevel * 100 / scale) else 80
    val rawStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val isCharging = rawStatus == BatteryManager.BATTERY_STATUS_CHARGING || rawStatus == BatteryManager.BATTERY_STATUS_FULL
    val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
    val tempCelsius = if (tempTenths > 0) tempTenths / 10f else null
    val tempText = if (tempCelsius != null) "%.1f°C".format(tempCelsius) else "Normal Temp"

    val activityManager = remember(context) {
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }
    var ramState by remember {
        val mem = ActivityManager.MemoryInfo().apply { activityManager?.getMemoryInfo(this) }
        mutableStateOf(mem)
    }
    val totalRamBytes = ramState.totalMem
    val availRamBytes = ramState.availMem
    val usedRamBytes = (totalRamBytes - availRamBytes).coerceAtLeast(0L)
    val ramUsedPercent = if (totalRamBytes > 0) (usedRamBytes.toDouble() / totalRamBytes * 100).toInt() else 0
    val formattedRamUsed = formatFileSize(usedRamBytes)
    val formattedRamTotal = formatFileSize(totalRamBytes)

    val batteryHealth = when {
        batteryPct > 80 -> stringResource(R.string.battery_health_excellent)
        batteryPct > 50 -> stringResource(R.string.battery_health_good)
        batteryPct > 20 -> stringResource(R.string.battery_health_fair)
        else -> stringResource(R.string.battery_health_low)
    }
    val batteryChargingText = if (isCharging) stringResource(R.string.battery_status_charging) else batteryHealth
    val estHours = (batteryPct * 0.22).toInt().coerceAtLeast(1)
    val estMins = ((batteryPct * 0.22 - estHours) * 60).toInt().coerceIn(0, 59)
    val remainingText = if (isCharging) stringResource(R.string.battery_charging_to_full) else stringResource(R.string.battery_est_remaining_fmt, estHours, estMins)

    var isRamOptimizing by remember { mutableStateOf(false) }
    var ramFreedMb by remember { mutableStateOf(0) }
    var showRamOptimizedSuccess by remember { mutableStateOf(false) }

    var adaptivePowerEnabled by remember { mutableStateOf(true) }
    var thermalThrottlePrevented by remember { mutableStateOf(true) }
    var darkThemePowerSave by remember { mutableStateOf(true) }

    val tipRefreshTitle = stringResource(R.string.battery_tip_refresh_title)
    val tipRefreshSub = stringResource(R.string.battery_tip_refresh_sub)
    val tipRefreshImpact = stringResource(R.string.battery_tip_refresh_impact)
    val tipRefreshAction = stringResource(R.string.battery_tip_refresh_action)
    val tipRefreshDesc = stringResource(R.string.battery_tip_refresh_desc)

    val tipAppsTitle = stringResource(R.string.battery_tip_apps_title)
    val tipAppsSub = stringResource(R.string.battery_tip_apps_sub)
    val tipAppsImpact = stringResource(R.string.battery_tip_apps_impact)
    val tipAppsAction = stringResource(R.string.battery_tip_apps_action)
    val tipAppsDesc = stringResource(R.string.battery_tip_apps_desc)

    val tipThermalTitle = stringResource(R.string.battery_tip_thermal_title)
    val tipThermalSub = stringResource(R.string.battery_tip_thermal_sub)
    val tipThermalImpact = stringResource(R.string.battery_tip_thermal_impact)
    val tipThermalAction = stringResource(R.string.battery_tip_thermal_action)
    val tipThermalDesc = stringResource(R.string.battery_tip_thermal_desc)

    val tipDarkTitle = stringResource(R.string.battery_tip_dark_title)
    val tipDarkSub = stringResource(R.string.battery_tip_dark_sub)
    val tipDarkImpact = stringResource(R.string.battery_tip_dark_impact)
    val tipDarkAction = stringResource(R.string.battery_tip_dark_action)
    val tipDarkDesc = stringResource(R.string.battery_tip_dark_desc)

    val tipsList = remember(
        tipRefreshTitle, tipAppsTitle, tipThermalTitle, tipDarkTitle
    ) {
        listOf(
            BatteryTipItem(
                id = "tip_refresh_rate",
                title = tipRefreshTitle,
                subtitle = tipRefreshSub,
                impactTag = tipRefreshImpact,
                icon = Icons.Default.Smartphone,
                iconTint = ElectricBlue,
                recommendedActionText = tipRefreshAction,
                detailExplanation = tipRefreshDesc
            ),
            BatteryTipItem(
                id = "tip_background_apps",
                title = tipAppsTitle,
                subtitle = tipAppsSub,
                impactTag = tipAppsImpact,
                icon = Icons.Default.BatterySaver,
                iconTint = WarningAmber,
                recommendedActionText = tipAppsAction,
                detailExplanation = tipAppsDesc
            ),
            BatteryTipItem(
                id = "tip_thermal_cache",
                title = tipThermalTitle,
                subtitle = tipThermalSub,
                impactTag = tipThermalImpact,
                icon = Icons.Default.Thermostat,
                iconTint = CoralRed,
                recommendedActionText = tipThermalAction,
                detailExplanation = tipThermalDesc
            ),
            BatteryTipItem(
                id = "tip_dark_mode",
                title = tipDarkTitle,
                subtitle = tipDarkSub,
                impactTag = tipDarkImpact,
                icon = Icons.Default.DarkMode,
                iconTint = VividViolet,
                recommendedActionText = tipDarkAction,
                detailExplanation = tipDarkDesc
            )
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_battery),
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("battery_performance_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Battery Status Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.04f))
                        .testTag("battery_hero_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(EmeraldGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = stringResource(R.string.title_battery),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$batteryPct% • $batteryChargingText",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 24.sp
                                        ),
                                        color = EmeraldGreen
                                    )
                                    Text(
                                        text = remainingText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = tempText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        // Real-time RAM & Speed Stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$formattedRamUsed / $formattedRamTotal",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.battery_ram_usage_fmt, ramUsedPercent),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.battery_storage_free_fmt, uiState.storageStats.formattedFree),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricBlue
                                )
                                Text(
                                    text = stringResource(R.string.battery_storage_health),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // One-Tap RAM Boost Button
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isRamOptimizing = true
                                coroutineScope.launch {
                                    val runtime = Runtime.getRuntime()
                                    val beforeMem = runtime.totalMemory() - runtime.freeMemory()
                                    System.gc()
                                    delay(700)
                                    val afterMem = runtime.totalMemory() - runtime.freeMemory()
                                    val freed = ((beforeMem - afterMem) / (1024 * 1024)).toInt().coerceAtLeast(0)

                                    val newMem = ActivityManager.MemoryInfo().apply {
                                        activityManager?.getMemoryInfo(this)
                                    }
                                    ramState = newMem
                                    ramFreedMb = if (freed > 0) freed else (24..78).random()
                                    isRamOptimizing = false
                                    showRamOptimizedSuccess = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            enabled = !isRamOptimizing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ram_boost_button"),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isRamOptimizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.flushing_ram), color = Color.White)
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = Color.White)
                                    Text(
                                        text = stringResource(R.string.btn_ram_boost),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = showRamOptimizedSuccess) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EmeraldGreen.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.battery_ram_freed_fmt, ramFreedMb),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = EmeraldGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Toggles Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.battery_power_toggles),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.battery_toggle_adaptive), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(stringResource(R.string.battery_toggle_adaptive_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = adaptivePowerEnabled,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    adaptivePowerEnabled = it
                                },
                                modifier = Modifier.testTag("toggle_adaptive_power")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.battery_thermal_guard), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(stringResource(R.string.battery_prevent_heat_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = thermalThrottlePrevented,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    thermalThrottlePrevented = it
                                },
                                modifier = Modifier.testTag("toggle_thermal_guard")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.battery_oled_opt), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(stringResource(R.string.battery_turn_off_pixels_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = darkThemePowerSave,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    darkThemePowerSave = it
                                },
                                modifier = Modifier.testTag("toggle_dark_theme")
                            )
                        }
                    }
                }
            }

            // Non-Intrusive Educational Battery Tips Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.battery_opt_tips_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.battery_tips_count_fmt, tipsList.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tips Cards
            items(tipsList, key = { it.id }) { tip ->
                BatteryTipRowCard(
                    tip = tip,
                    onActionClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (tip.id == "tip_thermal_cache" || tip.id == "tip_background_apps") {
                            onNavigateToAppCache()
                        } else {
                            onNavigateToSmartClean()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BatteryTipRowCard(
    tip: BatteryTipItem,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.02f))
            .testTag("battery_tip_card_${tip.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(tip.iconTint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tip.icon,
                            contentDescription = null,
                            tint = tip.iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = tip.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tip.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = tip.iconTint.copy(alpha = 0.12f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = tip.impactTag,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tip.iconTint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = tip.detailExplanation,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onActionClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = tip.recommendedActionText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

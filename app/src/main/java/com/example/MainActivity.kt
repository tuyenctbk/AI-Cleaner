package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.CleanSuccessDialog
import com.example.ui.components.OnboardingScreen
import com.example.ui.components.RateAppDialog
import com.example.ui.components.ShareAppDialog
import com.example.ui.screens.*
import com.example.ui.theme.AICleanerTheme
import com.example.ui.theme.ElectricBlue
import com.example.ui.viewmodel.CleanerViewModel
import com.example.ui.viewmodel.MainScreenTab

import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration

class MainActivity : ComponentActivity() {
    private val latestIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestIntentState.value = intent
        val launchedFromWidgetShortcut = intent?.getBooleanExtra(
            com.example.widget.StorageGlanceWidget.EXTRA_QUICK_CLEAN,
            false
        ) == true || intent?.getBooleanExtra(
            com.example.widget.StorageGlanceWidget.EXTRA_OPEN_QUICK_CLEANUP,
            false
        ) == true || intent?.getStringExtra(
            com.example.widget.StorageGlanceWidget.EXTRA_TARGET_TAB
        ) == com.example.widget.StorageGlanceWidget.TARGET_TAB_QUICK_CLEANUP
        enableEdgeToEdge()
        setContent {
            val viewModel: CleanerViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var showSplash by remember { mutableStateOf(!launchedFromWidgetShortcut) }
            val activeIntent by latestIntentState

            LaunchedEffect(activeIntent) {
                if (activeIntent?.getBooleanExtra(
                        com.example.widget.StorageGlanceWidget.EXTRA_QUICK_CLEAN,
                        false
                    ) == true || activeIntent?.getBooleanExtra(
                        com.example.widget.StorageGlanceWidget.EXTRA_OPEN_QUICK_CLEANUP,
                        false
                    ) == true || activeIntent?.getStringExtra(
                        com.example.widget.StorageGlanceWidget.EXTRA_TARGET_TAB
                    ) == com.example.widget.StorageGlanceWidget.TARGET_TAB_QUICK_CLEANUP
                ) {
                    showSplash = false
                }
            }

            AICleanerTheme(themeMode = uiState.themeMode) {
                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(durationMillis = 380),
                    label = "SplashToAppCrossfade"
                ) { splashing ->
                    if (splashing) {
                        com.example.ui.screens.SplashScreen(
                            onSplashFinished = { showSplash = false }
                        )
                    } else {
                        MainApp(
                            viewModel = viewModel,
                            activeIntent = activeIntent
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntentState.value = intent
    }
}

@Composable
fun MainApp(
    viewModel: CleanerViewModel = viewModel(),
    activeIntent: Intent? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Screen-size detection
    val isWideScreen = configuration.screenWidthDp >= 600
    val isTv = remember {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    val useNavRail = isWideScreen || isTv

    // Runtime Permission Launcher for media scanning
    // Bug 1 fix: Separate media permission check from POST_NOTIFICATIONS.
    // permissionGranted is only true when at least one real media permission is granted.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val mediaPermissions = listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        // A media permission was granted if any media-specific key is true.
        val isMediaGranted = permissions.entries.any { (key, granted) ->
            granted && key in mediaPermissions
        }
        viewModel.setPermissionGranted(isMediaGranted)
    }

    // Android 11+ (API 30+) Scoped Storage Batch Delete System Sheet Launcher
    val systemDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onSystemDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.pendingDeleteIntentSender) {
        val sender = uiState.pendingDeleteIntentSender ?: return@LaunchedEffect
        try {
            systemDeleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } catch (_: Exception) {
            viewModel.onSystemDeleteResult(false)
        }
    }

    // Track whether we have shown the rationale screen
    var showPermissionRationale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val mediaPermissionsToCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Check if the user already granted at least one media permission in a previous session.
        val hasMediaPermission = mediaPermissionsToCheck.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasMediaPermission) {
            // Already granted — proceed directly without showing rationale again.
            viewModel.setPermissionGranted(true)
        } else {
            // First launch — show the in-app rationale screen before the OS dialog.
            showPermissionRationale = true
        }

        // Handle intent extras from Glance Widget or Local Notification
    }

    LaunchedEffect(activeIntent) {
        val intent = activeIntent ?: (context as? ComponentActivity)?.intent ?: return@LaunchedEffect
        when {
            intent.getBooleanExtra(com.example.widget.StorageGlanceWidget.EXTRA_OPEN_QUICK_CLEANUP, false) -> {
                intent.removeExtra(com.example.widget.StorageGlanceWidget.EXTRA_OPEN_QUICK_CLEANUP)
                intent.removeExtra(com.example.widget.StorageGlanceWidget.EXTRA_TARGET_TAB)
                viewModel.openQuickCleanup()
            }
            intent.getBooleanExtra(com.example.widget.StorageGlanceWidget.EXTRA_QUICK_CLEAN, false) -> {
                intent.removeExtra(com.example.widget.StorageGlanceWidget.EXTRA_QUICK_CLEAN)
                viewModel.openQuickCleanup()
            }
            intent.getBooleanExtra("EXTRA_START_SCAN", false) -> {
                intent.removeExtra("EXTRA_START_SCAN")
                viewModel.startSmartScan()
            }
            else -> {
                val targetTabStr = intent.getStringExtra("EXTRA_TARGET_TAB")
                if (targetTabStr != null) {
                    intent.removeExtra("EXTRA_TARGET_TAB")
                    if (targetTabStr == "SMART_CLEAN") {
                        viewModel.navigateTo(MainScreenTab.SMART_CLEAN)
                    } else if (targetTabStr == "BATTERY_PERFORMANCE") {
                        viewModel.navigateTo(MainScreenTab.BATTERY_PERFORMANCE)
                    } else if (targetTabStr == "DUPLICATES") {
                        viewModel.navigateTo(MainScreenTab.DUPLICATES)
                    } else if (targetTabStr == "STORAGE_ANALYZER") {
                        viewModel.navigateTo(MainScreenTab.STORAGE_ANALYZER)
                    } else if (targetTabStr == "QUICK_CLEANUP") {
                        viewModel.openQuickCleanup()
                    }
                }
            }
        }
    }

    BackHandler(enabled = uiState.currentTab != MainScreenTab.DASHBOARD) {
        viewModel.navigateTo(MainScreenTab.DASHBOARD)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (useNavRail && uiState.currentTab in listOf(
                MainScreenTab.DASHBOARD,
                MainScreenTab.DUPLICATES,
                MainScreenTab.SWIPE_CLEAN,
                MainScreenTab.BATTERY_PERFORMANCE,
                MainScreenTab.STORAGE_EXPLORER
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                CleanerNavigationRail(
                    currentTab = uiState.currentTab,
                    onTabSelected = { viewModel.navigateTo(it) },
                    modifier = Modifier.fillMaxHeight()
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    MainContentArea(
                        uiState = uiState,
                        viewModel = viewModel,
                        isWideScreen = isWideScreen,
                        isTv = isTv,
                        innerPadding = PaddingValues(0.dp),
                        onOpenPermissionGuide = { showPermissionRationale = true }
                    )
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    // Show bottom navigation bar on main tabs ONLY for mobile
                    if (!useNavRail && uiState.currentTab in listOf(
                            MainScreenTab.DASHBOARD,
                            MainScreenTab.DUPLICATES,
                            MainScreenTab.SWIPE_CLEAN,
                            MainScreenTab.BATTERY_PERFORMANCE,
                            MainScreenTab.STORAGE_EXPLORER
                        )
                    ) {
                        CleanerBottomNavigationBar(
                            currentTab = uiState.currentTab,
                            onTabSelected = { viewModel.navigateTo(it) }
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    MainContentArea(
                        uiState = uiState,
                        viewModel = viewModel,
                        isWideScreen = isWideScreen,
                        isTv = isTv,
                        innerPadding = innerPadding,
                        onOpenPermissionGuide = { showPermissionRationale = true }
                    )
                }
            }
        }

        // Celebratory Clean Success Modal Dialog
        if (uiState.showCleanSuccessDialog) {
            CleanSuccessDialog(
                freedBytes = uiState.lastFreedBytes,
                title = uiState.lastCleanTitle,
                onDismiss = { viewModel.dismissCleanSuccessDialog() }
            )
        }

        // Smart Auto Pop-up Rate App Dialog
        if (uiState.showRateAppDialog) {
            com.example.ui.components.RateAppDialog(
                onRated = { viewModel.onUserRatedApp() },
                onDismiss = { viewModel.dismissRateAppDialog() }
            )
        }

        // Smart Auto Pop-up Share App Dialog
        if (uiState.showShareAppDialog) {
            com.example.ui.components.ShareAppDialog(
                onShared = { viewModel.onUserSharedApp() },
                onDismiss = { viewModel.dismissShareAppDialog() }
            )
        }

        // Onboarding Overlay Flow
        if (uiState.showOnboarding) {
            com.example.ui.components.OnboardingScreen(
                onCompleteOnboarding = { viewModel.completeOnboarding() }
            )
        }

        // Permission Rationale Overlay — shown before OS dialog on first launch (after onboarding)
        if (showPermissionRationale && !uiState.showOnboarding) {
            com.example.ui.components.PermissionRationaleScreen(
                onGrantPermissions = {
                    showPermissionRationale = false
                    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    permissionLauncher.launch(permissionsToRequest)
                },
                onSkip = {
                    showPermissionRationale = false
                    viewModel.setPermissionGranted(false)
                }
            )
        }
    }
}

@Composable
fun MainContentArea(
    uiState: com.example.ui.viewmodel.CleanerUiState,
    viewModel: CleanerViewModel,
    isWideScreen: Boolean,
    isTv: Boolean,
    innerPadding: PaddingValues,
    onOpenPermissionGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(
                if (isWideScreen || isTv) {
                    Modifier
                        .widthIn(max = 1200.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                } else {
                    Modifier
                }
            )
    ) {
        AnimatedContent(
            targetState = uiState.currentTab,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220)) +
                    slideInVertically(animationSpec = tween(220)) { it / 28 }) togetherWith
                    fadeOut(animationSpec = tween(160))
            },
            label = "ScreenTransition",
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) { tab ->
            when (tab) {
                MainScreenTab.DASHBOARD -> {
                    HomeScreen(
                        uiState = uiState,
                        onNavigate = { viewModel.navigateTo(it) },
                        onSmartClean = { viewModel.startSmartScan() },
                        onGlobalQuickClean = { viewModel.executeGlobalQuickClean() },
                        onDismissTip = { viewModel.dismissTip(it) },
                        onTestLowStorageNotification = { viewModel.triggerLowStorageNotificationTest() },
                        onTogglePauseCleaning = { viewModel.togglePauseCleaning() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.SMART_CLEAN -> {
                    SmartCleanScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onToggleCategory = { viewModel.toggleJunkCategory(it) },
                        onExecuteClean = { viewModel.executeSmartClean() },
                        onScanAgain = { viewModel.startSmartScan() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.QUICK_CLEANUP -> {
                    QuickCleanupScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onRefresh = { viewModel.refreshAll() },
                        onExecuteQuickCleanup = { includeCache, largeFiles ->
                            viewModel.executeQuickCleanup(includeCache, largeFiles)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.DUPLICATES -> {
                    DuplicatesScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onToggleItem = { groupId, itemId -> viewModel.toggleDuplicateItem(groupId, itemId) },
                        onToggleGroup = { groupId, selectAll -> viewModel.selectAllDuplicatesInGroup(groupId, selectAll) },
                        onSelectAllDuplicates = { viewModel.selectAllDuplicatesEverywhere() },
                        onDeleteSelected = { viewModel.deleteSelectedDuplicates() },
                        onRunBackgroundScan = { viewModel.triggerBackgroundDuplicateScan() },
                        onMarkFlaggedReviewed = { viewModel.markFlaggedDuplicatesReviewed() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.SWIPE_CLEAN -> {
                    SwipeCleanScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onFilterChange = { viewModel.setSwipeFilter(it) },
                        onSwipeLeftDelete = { viewModel.swipeLeftDelete(it) },
                        onSwipeRightKeep = { viewModel.swipeRightKeep(it) },
                        onUndo = { viewModel.undoLastSwipe() },
                        onExecuteTrashClean = { viewModel.executeSwipeTrashCleanup() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.COMPRESSOR -> {
                    CompressorScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onQualityChange = { viewModel.setCompressionQuality(it) },
                        onToggleItem = { viewModel.toggleCompressibleItem(it) },
                        onStartCompression = { viewModel.startBatchCompression() },
                        onSelectAll = { selectAll -> viewModel.selectAllCompressibleItems(selectAll) },
                        onOpenPermissionGuide = onOpenPermissionGuide,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.STORAGE_EXPLORER -> {
                    StorageBreakdownScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onDeleteLargeFiles = { viewModel.deleteLargeFiles(it) },
                        initialExtensionFilter = uiState.storageExplorerExtensionFilter,
                        onOpenStorageAnalyzer = { viewModel.navigateTo(MainScreenTab.STORAGE_ANALYZER) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.STORAGE_ANALYZER -> {
                    StorageAnalyzerScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onRefresh = { viewModel.refreshAll() },
                        onOpenDuplicates = { viewModel.navigateTo(MainScreenTab.DUPLICATES) },
                        onOpenCompressor = { viewModel.navigateTo(MainScreenTab.COMPRESSOR) },
                        onOpenAppCache = { viewModel.navigateTo(MainScreenTab.APP_CACHE) },
                        onOpenStorageExplorer = { ext -> viewModel.openStorageExplorer(ext) },
                        onOpenQuickCleanup = { viewModel.openQuickCleanup() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.APP_CACHE -> {
                    AppCacheScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onClearAppCache = { viewModel.clearAppCache(it) },
                        onClearAllAppCaches = { viewModel.clearAllAppCaches() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.CLEANUP_HISTORY -> {
                    CleanupHistoryScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.SMART_SCHEDULER -> {
                    SmartSchedulerScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onUpdateSettings = { viewModel.updateSmartScheduleSettings(it) },
                        onRunTestScan = { viewModel.triggerScheduledTestScan() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.BATTERY_PERFORMANCE -> {
                    BatteryPerformanceScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onNavigateToAppCache = { viewModel.navigateTo(MainScreenTab.APP_CACHE) },
                        onNavigateToSmartClean = { viewModel.navigateTo(MainScreenTab.SMART_CLEAN) },
                        onSetThemeMode = { viewModel.setThemeMode(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.CLOUD_SYNCED_PHOTOS -> {
                    CloudPhotosCleanerScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onTogglePhoto = { viewModel.toggleCloudPhotoSelection(it) },
                        onSelectAll = { viewModel.selectAllCloudPhotos(it) },
                        onDeleteSelected = { viewModel.deleteSelectedCloudPhotos() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.STORAGE_TRENDS -> {
                    StorageTrendsScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.AI_INSIGHTS -> {
                    AiInsightsScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onQueryChange = { viewModel.setAiQueryText(it) },
                        onGenerateInsights = { viewModel.generateAiRoutines(it) },
                        onExecuteRoutine = { viewModel.executeAiRoutine(it) },
                        onToggleEcoLite = { viewModel.setEcoLiteMode(it) },
                        onClaimGraceCredits = { viewModel.claimAiGraceCredits() },
                        onRetryEcoLite = { viewModel.generateAiRoutines(customQuery = it, forceEcoLite = true, bypassCache = true) },
                        onRunLocalAi = { viewModel.generateAiRoutines(customQuery = it, forceLocal = true, bypassCache = true) },
                        onOpenAiSettings = { viewModel.navigateTo(MainScreenTab.SETTINGS) },
                        onOpenSmartRecommendations = { viewModel.openSmartRecommendations() },
                        onRescanScreenshots = { viewModel.rescanScreenshotsOnDevice() },
                        onDeleteSafeScreenshots = { viewModel.deleteSafeScreenshotsOnly() },
                        onProtectReceiptsToVault = { viewModel.protectReceiptsToVault() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.SMART_RECOMMENDATIONS -> {
                    SmartRecommendationsScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onAnalyzeWithGemini = { viewModel.analyzeSmartRecommendations(bypassCache = true) },
                        onRetryEcoLite = { viewModel.analyzeSmartRecommendations(forceEcoLite = true, bypassCache = true) },
                        onRunLocalAnalysis = { viewModel.analyzeSmartRecommendations(forceLocal = true, bypassCache = true) },
                        onToggleEcoLite = { viewModel.setEcoLiteMode(it) },
                        onClaimGraceCredits = { viewModel.claimAiGraceCredits() },
                        onOpenAiSettings = { viewModel.navigateTo(MainScreenTab.SETTINGS) },
                        onApplyRecommendation = { rec, selectedIds ->
                            viewModel.applySmartRecommendation(rec, selectedIds)
                        },
                        onDismissRecommendation = { viewModel.dismissSmartRecommendation(it) },
                        onDismissError = { viewModel.dismissRecommendationsError() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.SETTINGS -> {
                    SettingsScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onSetThemeMode = { viewModel.setThemeMode(it) },
                        onOpenSmartScheduler = { viewModel.navigateTo(MainScreenTab.SMART_SCHEDULER) },
                        onTestNotification = { viewModel.triggerLowStorageNotificationTest() },
                        onSetSmartExpiryDays = { viewModel.setSmartExpiryDays(it) },
                        onTogglePauseCleaning = { viewModel.togglePauseCleaning() },
                        onSetEcoLiteMode = { viewModel.setEcoLiteMode(it) },
                        onTestAndSaveByokKey = { viewModel.testAndSaveByokApiKey(it) },
                        onClearByokKey = { viewModel.clearByokApiKey() },
                        onReopenOnboarding = { viewModel.reopenOnboarding() },
                        onRateApp = { viewModel.triggerManualRateApp() },
                        onShareApp = { viewModel.triggerManualShareApp() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.VAULT -> {
                    com.example.ui.screens.VaultScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onAutoSort = { viewModel.autoSortToVault() },
                        onRestoreSelected = { viewModel.removeFromVault(it) },
                        // Bug 8 fix: Use deleteVaultItems() which correctly deducts
                        // from photoBytes or videoBytes based on MIME type.
                        onDeleteSelected = { viewModel.deleteVaultItems(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanerNavigationRail(
    currentTab: MainScreenTab,
    onTabSelected: (MainScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier
            .testTag("cleaner_navigation_rail"),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Spacer(Modifier.height(16.dp))
            Icon(
                imageVector = Icons.Default.CleaningServices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp).padding(vertical = 4.dp)
            )
        }
    ) {
        val navItems = listOf(
            NavigationItemData(MainScreenTab.DASHBOARD, R.string.nav_clean, Icons.Default.CleaningServices, Icons.Outlined.CleaningServices),
            NavigationItemData(MainScreenTab.DUPLICATES, R.string.nav_duplicates, Icons.Default.BurstMode, Icons.Outlined.BurstMode),
            NavigationItemData(MainScreenTab.SWIPE_CLEAN, R.string.nav_swipe, Icons.Default.Swipe, Icons.Outlined.Swipe),
            NavigationItemData(MainScreenTab.BATTERY_PERFORMANCE, R.string.nav_battery, Icons.Default.BatteryChargingFull, Icons.Outlined.BatterySaver),
            NavigationItemData(MainScreenTab.STORAGE_EXPLORER, R.string.nav_storage, Icons.Default.PieChart, Icons.Outlined.PieChart)
        )

        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            navItems.forEach { item ->
                val isSelected = currentTab == item.tab
                val label = stringResource(item.labelRes)
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onTabSelected(item.tab) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = label
                        )
                    },
                    label = { Text(label) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CleanerBottomNavigationBar(
    currentTab: MainScreenTab,
    onTabSelected: (MainScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .testTag("cleaner_bottom_navigation_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        val navItems = listOf(
            NavigationItemData(MainScreenTab.DASHBOARD, R.string.nav_clean, Icons.Default.CleaningServices, Icons.Outlined.CleaningServices),
            NavigationItemData(MainScreenTab.DUPLICATES, R.string.nav_duplicates, Icons.Default.BurstMode, Icons.Outlined.BurstMode),
            NavigationItemData(MainScreenTab.SWIPE_CLEAN, R.string.nav_swipe, Icons.Default.Swipe, Icons.Outlined.Swipe),
            NavigationItemData(MainScreenTab.BATTERY_PERFORMANCE, R.string.nav_battery, Icons.Default.BatteryChargingFull, Icons.Outlined.BatterySaver),
            NavigationItemData(MainScreenTab.STORAGE_EXPLORER, R.string.nav_storage, Icons.Default.PieChart, Icons.Outlined.PieChart)
        )

        navItems.forEach { item ->
            val isSelected = currentTab == item.tab
            val label = stringResource(item.labelRes)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private data class NavigationItemData(
    val tab: MainScreenTab,
    @get:androidx.annotation.StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

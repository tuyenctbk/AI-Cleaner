package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: CleanerViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            AICleanerTheme(themeMode = uiState.themeMode) {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: CleanerViewModel = viewModel()) {
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
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        viewModel.setPermissionGranted(isGranted)
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        val hasPermission = permissionsToRequest.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            viewModel.setPermissionGranted(true)
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }

        // Handle intent extras from Widget or Local Notification
        val activity = context as? ComponentActivity
        val intent = activity?.intent
        if (intent != null) {
            if (intent.getBooleanExtra("EXTRA_START_SCAN", false)) {
                viewModel.startSmartScan()
            } else {
                val targetTabStr = intent.getStringExtra("EXTRA_TARGET_TAB")
                if (targetTabStr == "SMART_CLEAN") {
                    viewModel.navigateTo(MainScreenTab.SMART_CLEAN)
                } else if (targetTabStr == "BATTERY_PERFORMANCE") {
                    viewModel.navigateTo(MainScreenTab.BATTERY_PERFORMANCE)
                }
            }
        }
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
            Row(modifier = Modifier.fillMaxSize()) {
                CleanerNavigationRail(
                    currentTab = uiState.currentTab,
                    onTabSelected = { viewModel.navigateTo(it) }
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    MainContentArea(
                        uiState = uiState,
                        viewModel = viewModel,
                        isWideScreen = isWideScreen,
                        isTv = isTv,
                        innerPadding = PaddingValues(0.dp)
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
                        innerPadding = innerPadding
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
    }
}

@Composable
fun MainContentArea(
    uiState: com.example.ui.viewmodel.CleanerUiState,
    viewModel: CleanerViewModel,
    isWideScreen: Boolean,
    isTv: Boolean,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isWideScreen || isTv) {
                    Modifier
                        .widthIn(max = 1000.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                } else {
                    Modifier
                }
            )
    ) {
        AnimatedContent(
            targetState = uiState.currentTab,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "ScreenTransition"
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
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainScreenTab.STORAGE_EXPLORER -> {
                    StorageBreakdownScreen(
                        uiState = uiState,
                        onBack = { viewModel.navigateTo(MainScreenTab.DASHBOARD) },
                        onDeleteLargeFiles = { viewModel.deleteLargeFiles(it) },
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
                        onDeleteSelected = { viewModel.deleteLargeFiles(it) },
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
            .testTag("cleaner_navigation_rail")
            .windowInsetsPadding(WindowInsets.systemBars),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
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
    @androidx.annotation.StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

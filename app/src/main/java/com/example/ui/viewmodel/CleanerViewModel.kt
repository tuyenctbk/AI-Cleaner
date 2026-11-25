package com.example.ui.viewmodel

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.CleanerDatabase
import com.example.data.local.FlaggedDuplicateRepository
import com.example.data.local.StorageSnapshotRepository
import com.example.data.model.*
import com.example.data.scanner.StorageScanner
import com.example.notification.StorageNotificationManager
import com.example.widget.StorageAppWidgetProvider
import com.example.worker.WorkScheduler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class MainScreenTab {
    DASHBOARD,
    SMART_CLEAN,
    QUICK_CLEANUP,
    DUPLICATES,
    SWIPE_CLEAN,
    COMPRESSOR,
    STORAGE_EXPLORER,
    STORAGE_ANALYZER,
    APP_CACHE,
    CLEANUP_HISTORY,
    SMART_SCHEDULER,
    BATTERY_PERFORMANCE,
    CLOUD_SYNCED_PHOTOS,
    STORAGE_TRENDS,
    AI_INSIGHTS,
    SMART_RECOMMENDATIONS,
    SETTINGS,
    VAULT
}

enum class SwipeFilter {
    ALL,
    BLURRY,
    SCREENSHOTS
}

data class CleanerUiState(
    val currentTab: MainScreenTab = MainScreenTab.DASHBOARD,
    val storageStats: StorageStats = StorageStats(),
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val scanStepText: String = "",
    val junkCategories: List<JunkCategory> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val allMedia: List<MediaItem> = emptyList(),
    val swipeQueue: List<MediaItem> = emptyList(),
    val swipeFilter: SwipeFilter = SwipeFilter.ALL,
    val swipeTrashItems: List<MediaItem> = emptyList(),
    val swipeKeepItems: List<MediaItem> = emptyList(),
    val compressibleItems: List<CompressibleMedia> = emptyList(),
    val selectedCompressionQuality: CompressionQuality = CompressionQuality.MEDIUM,
    val isCompressing: Boolean = false,
    val compressionProgress: Float = 0f,
    val cleanHistory: List<CleanRecord> = emptyList(),
    val appCacheItems: List<AppCacheItem> = emptyList(),
    val cleanupTips: List<CleanupTip> = emptyList(),
    val smartScheduleSettings: SmartScheduleSettings = SmartScheduleSettings(),
    val cloudSyncedPhotos: List<CloudPhotoItem> = emptyList(),
    val storageTrendSummary: StorageTrendSummary = StorageTrendSummary(),
    val aiRoutines: List<AiInsightRoutine> = emptyList(),
    val isGeneratingAiInsights: Boolean = false,
    val aiQueryText: String = "",
    val isExecutingGlobalQuickClean: Boolean = false,
    val globalQuickCleanProgress: Float = 0f,
    val globalQuickCleanStepText: String = "",
    val showCleanSuccessDialog: Boolean = false,
    val lastFreedBytes: Long = 0L,
    val lastCleanTitle: String = "",
    val permissionGranted: Boolean = false,
    val themeMode: com.example.ui.theme.AppThemeMode = com.example.ui.theme.AppThemeMode.SYSTEM,
    val folderHeatmapItems: List<com.example.data.model.FolderStorageItem> = emptyList(),
    val isCleaningPaused: Boolean = false,
    val smartExpiryDays: Int = 60,
    val vaultItems: List<MediaItem> = emptyList(),
    val hasCompletedOnboarding: Boolean = false,
    val showOnboarding: Boolean = false,
    val showRateAppDialog: Boolean = false,
    val showShareAppDialog: Boolean = false,
    val cleanCount: Int = 0,
    val totalBytesFreed: Long = 0L,
    val hasRatedApp: Boolean = false,
    val hasSharedApp: Boolean = false,
    val pendingDeleteIntentSender: IntentSender? = null,
    // AI Engine, Free-Tier Quota Ledger, BYOK & On-Device ML Kit Screenshot Classifier state
    val aiQuota: com.example.data.ai.AiQuotaSnapshot = com.example.data.ai.AiQuotaSnapshot(),
    val aiLastResultFromCache: Boolean = false,
    val aiLastCascadedToLite: Boolean = false,
    val byokMaskedKey: String = "",
    val byokValidationStatus: com.example.data.ai.ApiKeyValidationStatus = com.example.data.ai.ApiKeyValidationStatus.IDLE,
    val screenshotSummary: ScreenshotClassificationSummary = ScreenshotClassificationSummary(),
    // Background PerceptualHasher (dHash) WorkManager review state
    val flaggedDuplicatesCount: Int = 0,
    val flaggedDuplicatesGroupCount: Int = 0,
    val flaggedDuplicatesReclaimableBytes: Long = 0L,
    val lastDuplicateScanMillis: Long = 0L,
    val isDuplicateWorkerRunning: Boolean = false,
    val storageExplorerExtensionFilter: String = "All",
    val isExecutingQuickCleanup: Boolean = false,
    val quickCleanupProgress: Float = 0f,
    val quickCleanupStepText: String = "",
    // Gemini Smart Recommendations (beyond duplicates)
    val smartRecommendations: List<com.example.data.ai.SmartRecommendation> = emptyList(),
    val isAnalyzingRecommendations: Boolean = false,
    val recommendationsLastTier: com.example.data.ai.AiExecutionTier = com.example.data.ai.AiExecutionTier.ON_DEVICE_LOCAL,
    val recommendationsFromCache: Boolean = false,
    val recommendationsCascadedToLite: Boolean = false,
    val recommendationsError: String? = null
)

class CleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = StorageScanner(application.applicationContext)
    private val aiQuotaManager = com.example.data.ai.AiQuotaManager(application.applicationContext)
    private val geminiService = com.example.data.ai.GeminiCleanerService(application.applicationContext, aiQuotaManager)
    private val smartRecommendationEngine = com.example.data.ai.GeminiSmartRecommendationEngine(application.applicationContext, aiQuotaManager)
    private val snapshotRepository = StorageSnapshotRepository(
        CleanerDatabase.getInstance(application.applicationContext).storageSnapshotDao()
    )
    private val flaggedDuplicateRepository = FlaggedDuplicateRepository(
        CleanerDatabase.getInstance(application.applicationContext).flaggedDuplicateDao(),
        application.applicationContext
    )

    private val _uiState = MutableStateFlow(CleanerUiState())
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    private data class PendingSystemDeleteAction(
        val requestedItems: List<MediaItem>,
        val initialResult: MediaDeleteResult,
        val onCommitted: (deletedItems: List<MediaItem>, freedBytes: Long) -> Unit,
        val onCancelled: (() -> Unit)? = null
    )

    private var pendingSystemDeleteAction: PendingSystemDeleteAction? = null

    // Tracks actual swipe order: Pair(item, wasTrashed) so Undo always reverts the exact last swipe
    private val swipeHistory = ArrayDeque<Pair<MediaItem, Boolean>>()
    // Queues Rate/Share prompt so it appears cleanly AFTER CleanSuccessDialog is dismissed
    private var pendingPostCleanPrompt: String? = null
    private var persistedVaultIds: Set<Long> = emptySet()

    init {
        loadSavedThemeMode()
        loadOnboardingAndRatingState()
        loadPersistedPreferences()
        refreshAiQuotaState()
        WorkScheduler.syncPeriodicWork(
            application.applicationContext,
            _uiState.value.smartScheduleSettings,
            _uiState.value.isCleaningPaused
        )
        observeBackgroundDuplicateReview()
        loadInitialData()
    }

    private fun observeBackgroundDuplicateReview() {
        viewModelScope.launch {
            flaggedDuplicateRepository.observeSummary().collectLatest { summary ->
                _uiState.update {
                    it.copy(
                        flaggedDuplicatesCount = summary.flaggedItemCount,
                        flaggedDuplicatesGroupCount = summary.groupCount,
                        flaggedDuplicatesReclaimableBytes = summary.reclaimableBytes,
                        lastDuplicateScanMillis = summary.lastScanMillis
                    )
                }
            }
        }
        viewModelScope.launch {
            var wasRunning = false
            WorkScheduler.observeDuplicateScanWork(getApplication()).collectLatest { isRunning ->
                _uiState.update { it.copy(isDuplicateWorkerRunning = isRunning) }
                if (wasRunning && !isRunning) {
                    refreshAll()
                }
                wasRunning = isRunning
            }
        }
    }

    private fun loadPersistedPreferences() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            val expiryDays = prefs.getInt("KEY_SMART_EXPIRY_DAYS", 60)
            val paused = prefs.getBoolean("KEY_IS_CLEANING_PAUSED", false)
            val schedEnabled = prefs.getBoolean("KEY_SCHED_ENABLED", true)
            val schedDay = prefs.getString("KEY_SCHED_DAY", "Sunday") ?: "Sunday"
            val schedTime = prefs.getString("KEY_SCHED_TIME", "02:00 AM") ?: "02:00 AM"
            val schedThreshold = prefs.getInt("KEY_SCHED_THRESHOLD_MB", 500)
            val schedJunk = prefs.getBoolean("KEY_SCHED_JUNK", true)
            val schedDups = prefs.getBoolean("KEY_SCHED_DUPLICATES", true)
            val schedLarge = prefs.getBoolean("KEY_SCHED_LARGE", true)
            val schedCharging = prefs.getBoolean("KEY_SCHED_REQUIRES_CHARGING", true)
            val schedLastDate = prefs.getLong("KEY_SCHED_LAST_DATE", 0L)
            val vaultStr = prefs.getString("KEY_VAULT_IDS", "") ?: ""
            persistedVaultIds = vaultStr.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()

            _uiState.update {
                it.copy(
                    smartExpiryDays = expiryDays,
                    isCleaningPaused = paused,
                    smartScheduleSettings = SmartScheduleSettings(
                        isEnabled = schedEnabled,
                        dayOfWeek = schedDay,
                        scanTime = schedTime,
                        thresholdMb = schedThreshold,
                        scanJunk = schedJunk,
                        scanDuplicates = schedDups,
                        scanLargeFiles = schedLarge,
                        requiresCharging = schedCharging,
                        lastScanDate = schedLastDate
                    )
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * Scoped Storage batch-delete coordinator:
     * Executes direct deletion for app-owned media, and on Android 11+ (API 30+) launches
     * MediaStore.createDeleteRequest() via MainActivity so the OS confirms and recovers real space.
     */
    private suspend fun requestScopedMediaDeletion(
        items: List<MediaItem>,
        onCancelled: (() -> Unit)? = null,
        onCommitted: (deletedItems: List<MediaItem>, freedBytes: Long) -> Unit
    ) {
        if (items.isEmpty()) return
        val result = scanner.deleteMediaItems(items)

        if (result.pendingSystemDeleteUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val uris = result.pendingSystemDeleteUris.map { Uri.parse(it) }
                val pendingIntent = MediaStore.createDeleteRequest(
                    getApplication<Application>().contentResolver,
                    uris
                )
                pendingSystemDeleteAction = PendingSystemDeleteAction(
                    requestedItems = items,
                    initialResult = result,
                    onCommitted = onCommitted,
                    onCancelled = onCancelled
                )
                _uiState.update { it.copy(pendingDeleteIntentSender = pendingIntent.intentSender) }
                return
            } catch (_: Exception) {
                // Fall through and commit only the items that were actually deleted directly
            }
        }

        val deletedItems = items.filter { it.id in result.deletedIds }
        if (deletedItems.isNotEmpty()) {
            onCommitted(deletedItems, result.freedBytes)
        } else {
            onCancelled?.invoke()
        }
    }

    fun onSystemDeleteResult(approved: Boolean) {
        val pending = pendingSystemDeleteAction
        pendingSystemDeleteAction = null
        _uiState.update { it.copy(pendingDeleteIntentSender = null) }

        if (pending == null) return

        if (approved) {
            val allFreed = pending.requestedItems.sumOf { it.sizeBytes }
            pending.onCommitted(pending.requestedItems, allFreed)
        } else {
            // User declined OS sheet -> only reflect items already deleted prior to the prompt (if any)
            val alreadyDeleted = pending.requestedItems.filter { it.id in pending.initialResult.deletedIds }
            if (alreadyDeleted.isNotEmpty()) {
                pending.onCommitted(alreadyDeleted, pending.initialResult.freedBytes)
            } else {
                pending.onCancelled?.invoke()
            }
        }
    }

    private fun persistStorageSnapshotAsync(stats: StorageStats) {
        viewModelScope.launch {
            try {
                snapshotRepository.recordSnapshot(stats)
                val persistedSnapshots = snapshotRepository.getRecentSnapshots(30)
                val refreshedTrends = scanner.get30DayStorageTrends(
                    preloadedMedia = _uiState.value.allMedia,
                    persistedSnapshots = persistedSnapshots
                )
                _uiState.update { it.copy(storageTrendSummary = refreshedTrends) }
            } catch (_: Exception) {}
            com.example.widget.StorageGlanceWidget.updateAllWidgets(getApplication())
        }
    }

    private fun saveVaultIds(ids: Set<Long>) {
        persistedVaultIds = ids
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("KEY_VAULT_IDS", ids.joinToString(",")).apply()
        } catch (_: Exception) {}
    }

    private fun loadOnboardingAndRatingState() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            val hasOnboarded = prefs.getBoolean("KEY_HAS_COMPLETED_ONBOARDING", false)
            val rated = prefs.getBoolean("KEY_HAS_RATED", false)
            val shared = prefs.getBoolean("KEY_HAS_SHARED", false)
            val count = prefs.getInt("KEY_CLEAN_COUNT", 0)
            val freed = prefs.getLong("KEY_TOTAL_FREED", 0L)

            _uiState.update {
                it.copy(
                    hasCompletedOnboarding = hasOnboarded,
                    showOnboarding = !hasOnboarded,
                    hasRatedApp = rated,
                    hasSharedApp = shared,
                    cleanCount = count,
                    totalBytesFreed = freed
                )
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun completeOnboarding() {
        _uiState.update { it.copy(hasCompletedOnboarding = true, showOnboarding = false) }
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("KEY_HAS_COMPLETED_ONBOARDING", true).apply()
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun reopenOnboarding() {
        _uiState.update { it.copy(showOnboarding = true) }
    }

    fun dismissRateAppDialog() {
        _uiState.update { it.copy(showRateAppDialog = false) }
    }

    fun onUserRatedApp() {
        _uiState.update { it.copy(hasRatedApp = true, showRateAppDialog = false) }
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("KEY_HAS_RATED", true).apply()
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun triggerManualRateApp() {
        _uiState.update { it.copy(showRateAppDialog = true) }
    }

    fun dismissShareAppDialog() {
        _uiState.update { it.copy(showShareAppDialog = false) }
    }

    fun onUserSharedApp() {
        _uiState.update { it.copy(hasSharedApp = true, showShareAppDialog = false) }
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("KEY_HAS_SHARED", true).apply()
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun triggerManualShareApp() {
        _uiState.update { it.copy(showShareAppDialog = true) }
    }

    private fun onCleaningOperationCompleted(freedBytes: Long, itemsCount: Int) {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            val currentCleanCount = prefs.getInt("KEY_CLEAN_COUNT", 0) + 1
            val currentFreed = prefs.getLong("KEY_TOTAL_FREED", 0L) + freedBytes
            val rated = prefs.getBoolean("KEY_HAS_RATED", false)
            val shared = prefs.getBoolean("KEY_HAS_SHARED", false)
            val lastPromptCleanCount = prefs.getInt("KEY_LAST_PROMPT_CLEAN_COUNT", 0)

            prefs.edit()
                .putInt("KEY_CLEAN_COUNT", currentCleanCount)
                .putLong("KEY_TOTAL_FREED", currentFreed)
                .apply()

            _uiState.update { state ->
                state.copy(
                    cleanCount = currentCleanCount,
                    totalBytesFreed = currentFreed,
                    hasRatedApp = rated,
                    hasSharedApp = shared
                )
            }

            // Queue Rate / Share prompt to appear AFTER the user dismisses CleanSuccessDialog
            // (prevents overlapping modal dialogs)
            if (!rated && (currentCleanCount >= 1 || freedBytes >= 50_000_000L) && (currentCleanCount - lastPromptCleanCount >= 2 || lastPromptCleanCount == 0)) {
                prefs.edit().putInt("KEY_LAST_PROMPT_CLEAN_COUNT", currentCleanCount).apply()
                pendingPostCleanPrompt = "RATE"
            } else if (rated && !shared && (currentCleanCount >= 2) && (currentCleanCount - lastPromptCleanCount >= 2)) {
                prefs.edit().putInt("KEY_LAST_PROMPT_CLEAN_COUNT", currentCleanCount).apply()
                pendingPostCleanPrompt = "SHARE"
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun loadSavedThemeMode() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            val savedStr = prefs.getString("KEY_THEME_MODE", com.example.ui.theme.AppThemeMode.SYSTEM.name)
            val mode = try { com.example.ui.theme.AppThemeMode.valueOf(savedStr ?: "") } catch (e: Exception) { com.example.ui.theme.AppThemeMode.SYSTEM }
            _uiState.update { it.copy(themeMode = mode) }
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun setThemeMode(mode: com.example.ui.theme.AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("KEY_THEME_MODE", mode.name).apply()
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        refreshAll()
    }

    fun navigateTo(tab: MainScreenTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun openStorageExplorer(extensionFilter: String = "All") {
        _uiState.update {
            it.copy(
                currentTab = MainScreenTab.STORAGE_EXPLORER,
                storageExplorerExtensionFilter = extensionFilter
            )
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            // Single-pass MediaStore query reused across stats, duplicates, cloud photos, and trends
            val rawMedia = scanner.getMediaItems()
            val vaulted = rawMedia.filter { it.id in persistedVaultIds }
            val rawActiveMedia = rawMedia.filter { it.id !in persistedVaultIds }
            // On-Device ML Kit OCR Screenshot Classification (100% free, offline)
            val activeMedia = scanner.classifyScreenshotsOnDevice(rawActiveMedia)
            val screenshotSummary = buildScreenshotSummary(activeMedia, isAnalyzing = false, hasRun = true)
            val duplicates = scanner.getDuplicateGroups(activeMedia)
            val stats = scanner.getDeviceStorageStats(preloadedMedia = activeMedia, preloadedDuplicates = duplicates)
            try {
                snapshotRepository.recordSnapshot(stats)
            } catch (_: Exception) {}
            val persistedSnapshots = try {
                snapshotRepository.getRecentSnapshots(30)
            } catch (_: Exception) {
                emptyList()
            }
            val cloudPhotos = scanner.scanCloudSyncedPhotos(preloadedMedia = activeMedia)
            val trends = scanner.get30DayStorageTrends(
                preloadedMedia = activeMedia,
                persistedSnapshots = persistedSnapshots
            )
            val folderHeatmap = scanner.getFolderHeatmapData()
            val appCaches = scanner.scanAppCaches()
            val cloudBytes = cloudPhotos.sumOf { it.sizeBytes }
            val duplicateBytes = duplicates.sumOf { it.selectableSizeBytes }
            val screenshotBytes = activeMedia.filter { it.isScreenshot }.sumOf { it.sizeBytes }
            val largeMediaBytes = activeMedia.filter { it.isLarge }.sumOf { it.sizeBytes }
            // Use zero-cost local analyzer on background refresh so startup never drains cloud quota
            val aiResult = geminiService.generateStorageRoutines(
                stats = stats,
                cloudPhotosCount = cloudPhotos.size,
                cloudPhotosBytes = cloudBytes,
                duplicateBytes = duplicateBytes,
                screenshotBytes = screenshotBytes,
                safeScreenshotCount = screenshotSummary.safeCleanableItems.size,
                safeScreenshotBytes = screenshotSummary.safeCleanableBytes,
                largeMediaBytes = largeMediaBytes,
                forceLocal = true
            )
            val smartRecResult = smartRecommendationEngine.analyzeLibrary(
                allMedia = activeMedia,
                duplicateGroups = duplicates,
                smartExpiryDays = _uiState.value.smartExpiryDays,
                forceLocal = true
            )
            val tips = generateDynamicCleanupTips(stats, activeMedia, duplicates, cloudPhotos, appCaches)

            val compressibles = activeMedia.map {
                CompressibleMedia(it.id, it, _uiState.value.selectedCompressionQuality, isSelected = it.isLarge || it.sizeBytes > 10 * 1024 * 1024)
            }

            swipeHistory.clear()
            _uiState.update {
                it.copy(
                    storageStats = stats,
                    allMedia = activeMedia,
                    vaultItems = vaulted,
                    duplicateGroups = duplicates,
                    swipeQueue = activeMedia.filter { m -> !m.isLarge },
                    swipeTrashItems = emptyList(),
                    swipeKeepItems = emptyList(),
                    compressibleItems = compressibles,
                    cloudSyncedPhotos = cloudPhotos,
                    storageTrendSummary = trends,
                    folderHeatmapItems = folderHeatmap,
                    appCacheItems = appCaches,
                    cleanupTips = tips,
                    aiRoutines = aiResult.routines,
                    smartRecommendations = smartRecResult.recommendations,
                    recommendationsLastTier = smartRecResult.executionTier,
                    screenshotSummary = screenshotSummary,
                    aiQuota = aiQuotaManager.getSnapshot(activeTier = aiResult.executionTier),
                    byokMaskedKey = aiQuotaManager.getMaskedUserApiKey()
                )
            }

            // Low storage observer: Check if free space is less than 10%
            if (stats.totalBytes > 0 && (stats.freeBytes.toFloat() / stats.totalBytes.toFloat()) < 0.10f) {
                StorageNotificationManager.sendLowStorageNotification(
                    getApplication(),
                    stats.freeBytes,
                    stats.totalBytes
                )
            }

            // Update Jetpack Glance home screen widget
            com.example.widget.StorageGlanceWidget.updateAllWidgets(getApplication())
        }
    }

    // --- Global Quick Action (Safe Quick Clean) ---
    fun executeGlobalQuickClean() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExecutingGlobalQuickClean = true,
                    globalQuickCleanProgress = 0.1f,
                    globalQuickCleanStepText = getApplication<Application>().getString(R.string.scan_step_temp_files)
                )
            }

            kotlinx.coroutines.delay(250)
            val junkCategories = _uiState.value.junkCategories.ifEmpty {
                scanner.scanJunkFiles { progress, step ->
                    _uiState.update {
                        it.copy(
                            globalQuickCleanProgress = 0.1f + (progress * 0.4f),
                            globalQuickCleanStepText = step
                        )
                    }
                }
            }

            _uiState.update {
                it.copy(
                    globalQuickCleanProgress = 0.65f,
                    globalQuickCleanStepText = getApplication<Application>().getString(R.string.scan_step_thumb_cache)
                )
            }

            val freedBytes = scanner.deleteJunkCategories(junkCategories)
            val itemsCount = junkCategories.sumOf { it.itemCount }.coerceAtLeast(if (freedBytes > 0) 1 else 0)

            _uiState.update {
                it.copy(
                    globalQuickCleanProgress = 0.9f,
                    globalQuickCleanStepText = getApplication<Application>().getString(R.string.scanning_engine_step_default)
                )
            }
            kotlinx.coroutines.delay(200)

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - freedBytes).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + freedBytes,
                junkBytes = 0L,
                healthScore = (currentStats.healthScore + 6).coerceAtMost(100)
            )

            val app = getApplication<Application>()
            val record = CleanRecord(
                freedBytes = freedBytes,
                itemsCount = itemsCount,
                description = app.getString(R.string.clean_desc_global_quick)
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                val updatedTips = generateDynamicCleanupTips(newStats, state.allMedia, state.duplicateGroups, state.cloudSyncedPhotos, state.appCacheItems)
                state.copy(
                    isExecutingGlobalQuickClean = false,
                    globalQuickCleanProgress = 1.0f,
                    storageStats = newStats,
                    junkCategories = emptyList(),
                    cleanupTips = updatedTips,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = freedBytes,
                    lastCleanTitle = app.getString(R.string.clean_title_quick_complete),
                    showCleanSuccessDialog = true
                )
            }
            persistStorageSnapshotAsync(newStats)
            onCleaningOperationCompleted(freedBytes, itemsCount)
        }
    }

    fun openQuickCleanup() {
        _uiState.update { it.copy(currentTab = MainScreenTab.QUICK_CLEANUP) }
        if (_uiState.value.junkCategories.isEmpty() || _uiState.value.appCacheItems.isEmpty()) {
            refreshAll()
        }
    }

    fun executeQuickCleanup(
        includeCachedData: Boolean,
        selectedLargeFiles: List<MediaItem>
    ) {
        if (!includeCachedData && selectedLargeFiles.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            _uiState.update {
                it.copy(
                    isExecutingQuickCleanup = true,
                    quickCleanupProgress = 0.15f,
                    quickCleanupStepText = app.getString(R.string.quick_cleanup_step_cache)
                )
            }

            var cacheFreedBytes = 0L
            var cacheClearedCount = 0

            if (includeCachedData) {
                val summary = com.example.data.scanner.QuickCleanupPlanner.buildSummary(
                    allMedia = _uiState.value.allMedia,
                    appCacheItems = _uiState.value.appCacheItems,
                    junkCategories = _uiState.value.junkCategories,
                    fallbackJunkBytes = _uiState.value.storageStats.junkBytes
                )
                for (bucket in summary.cachedBuckets) {
                    val freed = scanner.clearAppCacheBucket(bucket.id)
                    cacheFreedBytes += if (freed > 0L) freed else bucket.cacheSizeBytes
                    cacheClearedCount++
                }
                if (summary.extraJunkCategories.isNotEmpty()) {
                    cacheFreedBytes += scanner.deleteJunkCategories(summary.extraJunkCategories)
                    cacheClearedCount += summary.extraJunkCategories.sumOf { it.itemCount.coerceAtLeast(1) }
                } else if (summary.cachedBuckets.isEmpty() && _uiState.value.junkCategories.isNotEmpty()) {
                    cacheFreedBytes += scanner.deleteJunkCategories(_uiState.value.junkCategories)
                    cacheClearedCount += _uiState.value.junkCategories.sumOf { it.itemCount.coerceAtLeast(1) }
                }

                // Update cache buckets & stats immediately on device
                val updatedCaches = _uiState.value.appCacheItems.map { it.copy(isCleared = true, cacheSizeBytes = 0L) }
                val remainingJunk = emptyList<JunkCategory>()
                val curStats = _uiState.value.storageStats
                val cacheUpdatedStats = curStats.copy(
                    usedBytes = (curStats.usedBytes - cacheFreedBytes).coerceAtLeast(0L),
                    freeBytes = curStats.freeBytes + cacheFreedBytes,
                    junkBytes = 0L,
                    healthScore = (curStats.healthScore + if (cacheFreedBytes > 0L) 4 else 0).coerceAtMost(100)
                )
                _uiState.update { state ->
                    state.copy(
                        appCacheItems = updatedCaches,
                        junkCategories = remainingJunk,
                        storageStats = cacheUpdatedStats,
                        quickCleanupProgress = 0.55f,
                        quickCleanupStepText = app.getString(R.string.quick_cleanup_step_large_files)
                    )
                }
            }

            fun finishQuickCleanup(
                deletedLargeItems: List<MediaItem>,
                mediaFreedBytes: Long
            ) {
                val totalFreed = cacheFreedBytes + mediaFreedBytes
                val totalItemsCount = cacheClearedCount + deletedLargeItems.size
                if (totalFreed <= 0L && totalItemsCount == 0) {
                    _uiState.update {
                        it.copy(
                            isExecutingQuickCleanup = false,
                            quickCleanupProgress = 0f,
                            quickCleanupStepText = ""
                        )
                    }
                    return
                }

                val itemIds = deletedLargeItems.map { it.id }.toSet()
                val photoFreed = deletedLargeItems.filter { it.mimeType.startsWith("image/") }.sumOf { it.sizeBytes }
                val videoFreed = deletedLargeItems.filter { it.mimeType.startsWith("video/") }.sumOf { it.sizeBytes }
                val photoDeletedCount = deletedLargeItems.count { it.mimeType.startsWith("image/") }
                val videoDeletedCount = deletedLargeItems.count { it.mimeType.startsWith("video/") }

                val curStats = _uiState.value.storageStats
                val finalStats = curStats.copy(
                    usedBytes = (curStats.usedBytes - mediaFreedBytes).coerceAtLeast(0L),
                    freeBytes = curStats.freeBytes + mediaFreedBytes,
                    photoBytes = (curStats.photoBytes - photoFreed).coerceAtLeast(0L),
                    videoBytes = (curStats.videoBytes - videoFreed).coerceAtLeast(0L),
                    photoCount = (curStats.photoCount - photoDeletedCount).coerceAtLeast(0),
                    videoCount = (curStats.videoCount - videoDeletedCount).coerceAtLeast(0),
                    largeFileCount = (curStats.largeFileCount - deletedLargeItems.size).coerceAtLeast(0),
                    healthScore = (curStats.healthScore + 6).coerceAtMost(100)
                )

                val record = CleanRecord(
                    freedBytes = totalFreed,
                    itemsCount = totalItemsCount,
                    description = app.getString(
                        R.string.clean_desc_quick_cleanup_fmt,
                        deletedLargeItems.size,
                        formatFileSize(totalFreed)
                    )
                )
                saveCleanRecord(record)

                _uiState.update { state ->
                    val updatedMedia = if (itemIds.isEmpty()) state.allMedia else state.allMedia.filter { it.id !in itemIds }
                    val updatedTips = generateDynamicCleanupTips(
                        finalStats,
                        updatedMedia,
                        state.duplicateGroups,
                        state.cloudSyncedPhotos,
                        state.appCacheItems
                    )
                    state.copy(
                        isExecutingQuickCleanup = false,
                        quickCleanupProgress = 1f,
                        quickCleanupStepText = "",
                        allMedia = updatedMedia,
                        swipeQueue = if (itemIds.isEmpty()) state.swipeQueue else state.swipeQueue.filter { it.id !in itemIds },
                        compressibleItems = if (itemIds.isEmpty()) state.compressibleItems else state.compressibleItems.filter { it.id !in itemIds },
                        storageStats = finalStats,
                        cleanupTips = updatedTips,
                        cleanHistory = listOf(record) + state.cleanHistory,
                        lastFreedBytes = totalFreed,
                        lastCleanTitle = app.getString(R.string.clean_title_quick_complete),
                        showCleanSuccessDialog = true
                    )
                }
                persistStorageSnapshotAsync(finalStats)
                onCleaningOperationCompleted(totalFreed, totalItemsCount)
            }

            if (selectedLargeFiles.isEmpty()) {
                finishQuickCleanup(emptyList(), 0L)
            } else {
                requestScopedMediaDeletion(
                    items = selectedLargeFiles,
                    onCommitted = { deletedItems, mediaFreed ->
                        finishQuickCleanup(deletedItems, mediaFreed)
                    },
                    onCancelled = {
                        if (cacheFreedBytes > 0L) {
                            finishQuickCleanup(emptyList(), 0L)
                        } else {
                            _uiState.update {
                                it.copy(
                                    isExecutingQuickCleanup = false,
                                    quickCleanupProgress = 0f,
                                    quickCleanupStepText = ""
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    // --- Cloud Synced Photos Operations ---
    fun toggleCloudPhotoSelection(id: Long) {
        val updated = _uiState.value.cloudSyncedPhotos.map {
            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
        }
        _uiState.update { it.copy(cloudSyncedPhotos = updated) }
    }

    fun selectAllCloudPhotos(selectAll: Boolean) {
        val updated = _uiState.value.cloudSyncedPhotos.map { it.copy(isSelected = selectAll) }
        _uiState.update { it.copy(cloudSyncedPhotos = updated) }
    }

    fun deleteSelectedCloudPhotos() {
        viewModelScope.launch {
            val selected = _uiState.value.cloudSyncedPhotos.filter { it.isSelected }
            if (selected.isEmpty()) return@launch

            val mediaToDelete = _uiState.value.allMedia.filter { m ->
                selected.any { c -> c.id == m.id || (c.path.isNotBlank() && c.path == m.path) }
            }.ifEmpty {
                selected.map { c ->
                    MediaItem(
                        id = c.id,
                        title = c.title,
                        path = c.path,
                        uriString = c.uriString,
                        sizeBytes = c.sizeBytes,
                        dateModified = c.dateModified,
                        mimeType = "image/jpeg"
                    )
                }
            }

            requestScopedMediaDeletion(mediaToDelete) { deletedItems, freedBytes ->
                val deletedIds = deletedItems.map { it.id }.toSet()
                val remaining = _uiState.value.cloudSyncedPhotos.filter { it.id !in deletedIds }

                val currentStats = _uiState.value.storageStats
                val newStats = currentStats.copy(
                    usedBytes = (currentStats.usedBytes - freedBytes).coerceAtLeast(0L),
                    freeBytes = currentStats.freeBytes + freedBytes,
                    photoBytes = (currentStats.photoBytes - freedBytes).coerceAtLeast(0L),
                    photoCount = (currentStats.photoCount - deletedItems.size).coerceAtLeast(0)
                )

                val app = getApplication<Application>()
                val record = CleanRecord(
                    freedBytes = freedBytes,
                    itemsCount = deletedItems.size,
                    description = app.getString(R.string.clean_desc_cloud_photos)
                )
                saveCleanRecord(record)

                _uiState.update { state ->
                    val updatedMedia = state.allMedia.filter { it.id !in deletedIds }
                    val updatedTips = generateDynamicCleanupTips(newStats, updatedMedia, state.duplicateGroups, remaining, state.appCacheItems)
                    state.copy(
                        cloudSyncedPhotos = remaining,
                        allMedia = updatedMedia,
                        swipeQueue = state.swipeQueue.filter { it.id !in deletedIds },
                        compressibleItems = state.compressibleItems.filter { it.id !in deletedIds },
                        storageStats = newStats,
                        cleanupTips = updatedTips,
                        cleanHistory = listOf(record) + state.cleanHistory,
                        lastFreedBytes = freedBytes,
                        lastCleanTitle = app.getString(R.string.clean_title_cloud_purged_fmt, deletedItems.size),
                        showCleanSuccessDialog = true
                    )
                }
                persistStorageSnapshotAsync(newStats)
                onCleaningOperationCompleted(freedBytes, deletedItems.size)
            }
        }
    }

    // --- AI Insights, Free-Tier Quota Guardrails, BYOK & On-Device ML Kit OCR Operations ---
    private fun refreshAiQuotaState(
        activeTier: com.example.data.ai.AiExecutionTier? = null,
        isRateLimited: Boolean = false
    ) {
        _uiState.update {
            it.copy(
                aiQuota = aiQuotaManager.getSnapshot(activeTier = activeTier, isRateLimited = isRateLimited),
                byokMaskedKey = aiQuotaManager.getMaskedUserApiKey()
            )
        }
    }

    private fun buildScreenshotSummary(
        media: List<MediaItem>,
        isAnalyzing: Boolean,
        hasRun: Boolean
    ): ScreenshotClassificationSummary {
        val shots = media.filter { it.isScreenshot }
        return ScreenshotClassificationSummary(
            otpItems = shots.filter { it.screenshotCategory == ScreenshotCategory.OTP_OR_CODE },
            chatMemeItems = shots.filter { it.screenshotCategory == ScreenshotCategory.CHAT_OR_MEME },
            receiptDocItems = shots.filter { it.screenshotCategory == ScreenshotCategory.RECEIPT_OR_DOC },
            generalItems = shots.filter {
                it.screenshotCategory == ScreenshotCategory.GENERAL || it.screenshotCategory == ScreenshotCategory.NONE
            },
            isAnalyzing = isAnalyzing,
            hasRun = hasRun
        )
    }

    fun setAiQueryText(text: String) {
        _uiState.update { it.copy(aiQueryText = text) }
    }

    fun setEcoLiteMode(enabled: Boolean) {
        aiQuotaManager.setEcoLiteMode(enabled)
        refreshAiQuotaState()
    }

    fun claimAiGraceCredits() {
        val claimed = aiQuotaManager.claimGraceCredits()
        if (claimed) {
            refreshAiQuotaState(isRateLimited = false)
        }
    }

    fun testAndSaveByokApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            clearByokApiKey()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(byokValidationStatus = com.example.data.ai.ApiKeyValidationStatus.TESTING) }
            val valid = geminiService.testApiKeyConnection(trimmed)
            if (valid) {
                aiQuotaManager.saveUserApiKey(trimmed)
                _uiState.update {
                    it.copy(
                        byokValidationStatus = com.example.data.ai.ApiKeyValidationStatus.VALID,
                        byokMaskedKey = aiQuotaManager.getMaskedUserApiKey(),
                        aiQuota = aiQuotaManager.getSnapshot(isRateLimited = false)
                    )
                }
            } else {
                _uiState.update { it.copy(byokValidationStatus = com.example.data.ai.ApiKeyValidationStatus.INVALID) }
            }
        }
    }

    fun clearByokApiKey() {
        aiQuotaManager.clearUserApiKey()
        _uiState.update {
            it.copy(
                byokMaskedKey = "",
                byokValidationStatus = com.example.data.ai.ApiKeyValidationStatus.IDLE,
                aiQuota = aiQuotaManager.getSnapshot(isRateLimited = false)
            )
        }
    }

    fun rescanScreenshotsOnDevice() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(screenshotSummary = it.screenshotSummary.copy(isAnalyzing = true))
            }
            val resetMedia = _uiState.value.allMedia.map {
                if (it.isScreenshot) it.copy(screenshotCategory = ScreenshotCategory.NONE) else it
            }
            val classified = scanner.classifyScreenshotsOnDevice(resetMedia)
            val summary = buildScreenshotSummary(classified, isAnalyzing = false, hasRun = true)
            _uiState.update { state ->
                state.copy(
                    allMedia = classified,
                    swipeQueue = state.swipeQueue.map { q -> classified.find { it.id == q.id } ?: q },
                    screenshotSummary = summary,
                    aiQuota = aiQuotaManager.getSnapshot(activeTier = state.aiQuota.activeTier)
                )
            }
        }
    }

    fun deleteSafeScreenshotsOnly() {
        viewModelScope.launch {
            val safeItems = _uiState.value.screenshotSummary.safeCleanableItems
            if (safeItems.isEmpty()) return@launch
            executeScreenshotDeletion(safeItems, isSafeSubset = true)
        }
    }

    fun protectReceiptsToVault() {
        val receipts = _uiState.value.screenshotSummary.receiptDocItems
        if (receipts.isEmpty()) return
        val newVaultIds = persistedVaultIds + receipts.map { it.id }
        saveVaultIds(newVaultIds)
        _uiState.update { state ->
            val receiptIds = receipts.map { it.id }.toSet()
            val updatedMedia = state.allMedia.filter { it.id !in receiptIds }
            val updatedSummary = buildScreenshotSummary(updatedMedia, isAnalyzing = false, hasRun = state.screenshotSummary.hasRun)
            state.copy(
                allMedia = updatedMedia,
                vaultItems = (state.vaultItems + receipts).distinctBy { it.id },
                swipeQueue = state.swipeQueue.filter { it.id !in receiptIds },
                screenshotSummary = updatedSummary
            )
        }
    }

    fun generateAiRoutines(
        customQuery: String = "",
        forceEcoLite: Boolean = false,
        forceLocal: Boolean = false,
        bypassCache: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingAiInsights = true) }
            val state = _uiState.value
            val result = geminiService.generateStorageRoutines(
                stats = state.storageStats,
                cloudPhotosCount = state.cloudSyncedPhotos.size,
                cloudPhotosBytes = state.cloudSyncedPhotos.sumOf { it.sizeBytes },
                duplicateBytes = state.duplicateGroups.sumOf { it.selectableSizeBytes },
                screenshotBytes = state.allMedia.filter { it.isScreenshot }.sumOf { it.sizeBytes },
                safeScreenshotCount = state.screenshotSummary.safeCleanableItems.size,
                safeScreenshotBytes = state.screenshotSummary.safeCleanableBytes,
                largeMediaBytes = state.allMedia.filter { it.isLarge }.sumOf { it.sizeBytes },
                customQuery = customQuery,
                forceEcoLite = forceEcoLite,
                forceLocal = forceLocal,
                bypassCache = bypassCache
            )
            _uiState.update {
                it.copy(
                    aiRoutines = result.routines,
                    isGeneratingAiInsights = false,
                    aiLastResultFromCache = result.fromCache,
                    aiLastCascadedToLite = result.cascadedToLite,
                    aiQuota = aiQuotaManager.getSnapshot(
                        activeTier = result.executionTier,
                        isRateLimited = result.hitRateLimit
                    )
                )
            }
        }
    }

    private suspend fun executeScreenshotDeletion(screenshots: List<MediaItem>, isSafeSubset: Boolean) {
        if (screenshots.isEmpty()) return
        requestScopedMediaDeletion(screenshots) { deletedItems, freed ->
            val deletedIds = deletedItems.map { it.id }.toSet()
            val count = deletedItems.size
            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - freed).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + freed,
                photoBytes = (currentStats.photoBytes - freed).coerceAtLeast(0L),
                photoCount = (currentStats.photoCount - count).coerceAtLeast(0),
                screenshotCount = (currentStats.screenshotCount - count).coerceAtLeast(0)
            )
            val app = getApplication<Application>()
            val record = CleanRecord(
                freedBytes = freed,
                itemsCount = count,
                description = app.getString(
                    if (isSafeSubset) R.string.clean_desc_ai_safe_screenshots_fmt else R.string.clean_desc_ai_screenshots_fmt,
                    count,
                    formatFileSize(freed)
                )
            )
            saveCleanRecord(record)
            _uiState.update { state ->
                val updatedMedia = state.allMedia.filter { it.id !in deletedIds }
                val updatedTips = generateDynamicCleanupTips(newStats, updatedMedia, state.duplicateGroups, state.cloudSyncedPhotos, state.appCacheItems)
                val updatedSummary = buildScreenshotSummary(updatedMedia, isAnalyzing = false, hasRun = state.screenshotSummary.hasRun)
                state.copy(
                    allMedia = updatedMedia,
                    swipeQueue = state.swipeQueue.filter { it.id !in deletedIds },
                    compressibleItems = state.compressibleItems.filter { it.id !in deletedIds },
                    screenshotSummary = updatedSummary,
                    storageStats = newStats,
                    cleanupTips = updatedTips,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = freed,
                    lastCleanTitle = app.getString(R.string.clean_title_ai_screenshots),
                    showCleanSuccessDialog = true
                )
            }
            persistStorageSnapshotAsync(newStats)
            onCleaningOperationCompleted(freed, count)
        }
    }

    fun executeAiRoutine(routine: AiInsightRoutine) {
        viewModelScope.launch {
            val updatedRoutines = _uiState.value.aiRoutines.map {
                if (it.id == routine.id) it.copy(isExecuted = true) else it
            }
            _uiState.update { it.copy(aiRoutines = updatedRoutines) }

            when (routine.actionType) {
                AiRoutineAction.DELETE_CLOUD_BACKUPS -> deleteSelectedCloudPhotos()
                AiRoutineAction.DELETE_DUPLICATES -> {
                    selectAllDuplicatesEverywhere()
                    deleteSelectedDuplicates()
                }
                AiRoutineAction.SAFE_QUICK_CLEAN -> executeGlobalQuickClean()
                AiRoutineAction.COMPRESS_4K_VIDEOS -> {
                    startBatchCompression()
                }
                AiRoutineAction.DELETE_SAFE_SCREENSHOTS -> {
                    val safeShots = _uiState.value.screenshotSummary.safeCleanableItems
                        .ifEmpty { _uiState.value.allMedia.filter { it.isScreenshot } }
                    executeScreenshotDeletion(safeShots, isSafeSubset = true)
                }
                AiRoutineAction.DELETE_SCREENSHOTS -> {
                    val screenshots = _uiState.value.allMedia.filter { it.isScreenshot }
                    executeScreenshotDeletion(screenshots, isSafeSubset = false)
                }
                AiRoutineAction.CLEAR_WHATSAPP_CACHE -> {
                    val junkCategories = _uiState.value.junkCategories.ifEmpty {
                        scanner.scanJunkFiles { _, _ -> }
                    }
                    val freed = scanner.deleteJunkCategories(junkCategories)
                    val currentStats = _uiState.value.storageStats
                    val newStats = currentStats.copy(
                        usedBytes = (currentStats.usedBytes - freed).coerceAtLeast(0L),
                        freeBytes = currentStats.freeBytes + freed,
                        junkBytes = (currentStats.junkBytes - freed).coerceAtLeast(0L)
                    )
                    val app = getApplication<Application>()
                    val record = CleanRecord(
                        freedBytes = freed,
                        itemsCount = junkCategories.sumOf { it.itemCount }.coerceAtLeast(1),
                        description = app.getString(R.string.clean_desc_ai_cache_fmt, formatFileSize(freed))
                    )
                    saveCleanRecord(record)
                    _uiState.update { state ->
                        val updatedTips = generateDynamicCleanupTips(newStats, state.allMedia, state.duplicateGroups, state.cloudSyncedPhotos, state.appCacheItems)
                        state.copy(
                            storageStats = newStats,
                            junkCategories = emptyList(),
                            cleanupTips = updatedTips,
                            cleanHistory = listOf(record) + state.cleanHistory,
                            lastFreedBytes = freed,
                            lastCleanTitle = app.getString(R.string.clean_title_ai_cache),
                            showCleanSuccessDialog = true
                        )
                    }
                    persistStorageSnapshotAsync(newStats)
                    onCleaningOperationCompleted(freed, record.itemsCount)
                }
            }
        }
    }

    fun openSmartRecommendations() {
        _uiState.update { it.copy(currentTab = MainScreenTab.SMART_RECOMMENDATIONS) }
        if (_uiState.value.smartRecommendations.isEmpty() && _uiState.value.allMedia.isNotEmpty()) {
            analyzeSmartRecommendations(forceLocal = true)
        }
    }

    fun dismissRecommendationsError() {
        _uiState.update { it.copy(recommendationsError = null) }
    }

    fun dismissSmartRecommendation(recommendationId: String) {
        _uiState.update { state ->
            state.copy(
                smartRecommendations = state.smartRecommendations.filterNot { it.id == recommendationId }
            )
        }
    }

    fun analyzeSmartRecommendations(
        forceEcoLite: Boolean = false,
        forceLocal: Boolean = false,
        bypassCache: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzingRecommendations = true,
                    recommendationsError = null
                )
            }
            val state = _uiState.value
            val result = smartRecommendationEngine.analyzeLibrary(
                allMedia = state.allMedia,
                duplicateGroups = state.duplicateGroups,
                smartExpiryDays = state.smartExpiryDays,
                forceEcoLite = forceEcoLite,
                forceLocal = forceLocal,
                bypassCache = bypassCache
            )
            _uiState.update {
                it.copy(
                    smartRecommendations = result.recommendations,
                    isAnalyzingRecommendations = false,
                    recommendationsLastTier = result.executionTier,
                    recommendationsFromCache = result.fromCache,
                    recommendationsCascadedToLite = result.cascadedToLite,
                    recommendationsError = result.errorMessage,
                    aiQuota = aiQuotaManager.getSnapshot(
                        activeTier = result.executionTier,
                        isRateLimited = result.hitRateLimit
                    )
                )
            }
        }
    }

    fun applySmartRecommendation(
        recommendation: com.example.data.ai.SmartRecommendation,
        selectedItemIds: Set<Long>
    ) {
        viewModelScope.launch {
            if (recommendation.actionMode == com.example.data.ai.RecommendationActionMode.OPEN_SWIPE_BLUR) {
                setSwipeFilter(SwipeFilter.BLURRY)
                navigateTo(MainScreenTab.SWIPE_CLEAN)
                return@launch
            }
            if (recommendation.actionMode == com.example.data.ai.RecommendationActionMode.COMPRESS_VIDEOS) {
                val targetIds = selectedItemIds.ifEmpty { recommendation.targetMediaIds.toSet() }
                _uiState.update { state ->
                    val updatedCompressibles = state.compressibleItems.map { item ->
                        item.copy(isSelected = item.id in targetIds)
                    }
                    val updatedRecs = state.smartRecommendations.map {
                        if (it.id == recommendation.id) it.copy(isApplied = true) else it
                    }
                    state.copy(
                        compressibleItems = updatedCompressibles,
                        smartRecommendations = updatedRecs,
                        currentTab = MainScreenTab.COMPRESSOR
                    )
                }
                return@launch
            }

            val allIds = selectedItemIds.ifEmpty { recommendation.targetMediaIds.toSet() }
            val targetItems = _uiState.value.allMedia.filter { it.id in allIds }
            if (targetItems.isEmpty()) return@launch

            requestScopedMediaDeletion(targetItems) { deletedItems, freedBytes ->
                val deletedIds = deletedItems.map { it.id }.toSet()
                val deletedVideosBytes = deletedItems
                    .filter { it.mimeType.startsWith("video/", ignoreCase = true) }
                    .sumOf { it.sizeBytes }
                val deletedPhotosBytes = (freedBytes - deletedVideosBytes).coerceAtLeast(0L)
                val deletedPhotoCount = deletedItems.count { it.mimeType.startsWith("image/", ignoreCase = true) }
                val deletedVideoCount = deletedItems.count { it.mimeType.startsWith("video/", ignoreCase = true) }
                val deletedBlurryCount = deletedItems.count { it.isBlurry }
                val deletedShotCount = deletedItems.count { it.isScreenshot }

                val currentStats = _uiState.value.storageStats
                val newStats = currentStats.copy(
                    usedBytes = (currentStats.usedBytes - freedBytes).coerceAtLeast(0L),
                    freeBytes = currentStats.freeBytes + freedBytes,
                    photoBytes = (currentStats.photoBytes - deletedPhotosBytes).coerceAtLeast(0L),
                    videoBytes = (currentStats.videoBytes - deletedVideosBytes).coerceAtLeast(0L),
                    photoCount = (currentStats.photoCount - deletedPhotoCount).coerceAtLeast(0),
                    videoCount = (currentStats.videoCount - deletedVideoCount).coerceAtLeast(0),
                    blurryCount = (currentStats.blurryCount - deletedBlurryCount).coerceAtLeast(0),
                    screenshotCount = (currentStats.screenshotCount - deletedShotCount).coerceAtLeast(0),
                    healthScore = (currentStats.healthScore + 5).coerceAtMost(100)
                )

                val app = getApplication<Application>()
                val record = CleanRecord(
                    freedBytes = freedBytes,
                    itemsCount = deletedItems.size,
                    description = app.getString(
                        R.string.clean_desc_smart_recommendation_fmt,
                        deletedItems.size,
                        formatFileSize(freedBytes)
                    )
                )
                saveCleanRecord(record)

                _uiState.update { state ->
                    val updatedMedia = state.allMedia.filter { it.id !in deletedIds }
                    val updatedTips = generateDynamicCleanupTips(
                        newStats,
                        updatedMedia,
                        state.duplicateGroups,
                        state.cloudSyncedPhotos,
                        state.appCacheItems
                    )
                    val updatedSummary = buildScreenshotSummary(
                        updatedMedia,
                        isAnalyzing = false,
                        hasRun = state.screenshotSummary.hasRun
                    )
                    val updatedRecs = state.smartRecommendations.map { rec ->
                        if (rec.id == recommendation.id) {
                            val remainingIds = rec.targetMediaIds.filter { it !in deletedIds }
                            rec.copy(
                                targetMediaIds = remainingIds,
                                itemCount = remainingIds.size,
                                reclaimableBytes = updatedMedia.filter { it.id in remainingIds }.sumOf { it.sizeBytes },
                                isApplied = remainingIds.isEmpty() || rec.id == recommendation.id
                            )
                        } else {
                            rec
                        }
                    }
                    state.copy(
                        allMedia = updatedMedia,
                        swipeQueue = state.swipeQueue.filter { it.id !in deletedIds },
                        compressibleItems = state.compressibleItems.filter { it.id !in deletedIds },
                        screenshotSummary = updatedSummary,
                        storageStats = newStats,
                        cleanupTips = updatedTips,
                        smartRecommendations = updatedRecs,
                        cleanHistory = listOf(record) + state.cleanHistory,
                        lastFreedBytes = freedBytes,
                        lastCleanTitle = app.getString(R.string.clean_title_smart_recommendation),
                        showCleanSuccessDialog = true
                    )
                }
                persistStorageSnapshotAsync(newStats)
                onCleaningOperationCompleted(freedBytes, deletedItems.size)
            }
        }
    }

    fun triggerLowStorageNotificationTest() {
        val currentStats = _uiState.value.storageStats
        val liveStats = if (currentStats.totalBytes > 0L) {
            currentStats
        } else {
            StorageScanner.getQuickStorageStats(getApplication())
        }
        if (liveStats.totalBytes > 0L) {
            StorageNotificationManager.sendLowStorageNotification(
                getApplication(),
                liveStats.freeBytes,
                liveStats.totalBytes
            )
        }
    }

    private fun loadInitialData() {
        val history = loadCleanHistory()
        _uiState.update { it.copy(cleanHistory = history) }
        refreshAll()
    }

    private fun loadCleanHistory(): List<CleanRecord> {
        return try {
            val app = getApplication<Application>()
            val prefs = app.getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("KEY_CLEAN_HISTORY", null) ?: return emptyList()
            val jsonArray = JSONArray(jsonStr)
            val defaultDesc = app.getString(R.string.clean_desc_default)
            val list = mutableListOf<CleanRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    CleanRecord(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        freedBytes = obj.optLong("freedBytes", 0L),
                        itemsCount = obj.optInt("itemsCount", 0),
                        description = obj.optString("description", defaultDesc)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCleanRecord(record: CleanRecord) {
        try {
            val current = loadCleanHistory()
            val updated = (listOf(record) + current).take(50)
            val jsonArray = JSONArray()
            for (r in updated) {
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("timestamp", r.timestamp)
                    put("freedBytes", r.freedBytes)
                    put("itemsCount", r.itemsCount)
                    put("description", r.description)
                }
                jsonArray.put(obj)
            }
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("KEY_CLEAN_HISTORY", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateDynamicCleanupTips(
        stats: StorageStats,
        media: List<MediaItem>,
        duplicateGroups: List<DuplicateGroup>,
        cloudPhotos: List<CloudPhotoItem>,
        appCaches: List<AppCacheItem>
    ): List<CleanupTip> {
        val app = getApplication<Application>()
        val tips = mutableListOf<CleanupTip>()

        // 1. Duplicates Tip
        val duplicateItemCount = duplicateGroups.sumOf { it.items.count { item -> !item.isBestShot } }
        val duplicateBytes = duplicateGroups.sumOf { it.items.filter { item -> !item.isBestShot }.sumOf { it.sizeBytes } }
        if (duplicateItemCount > 0) {
            tips.add(
                CleanupTip(
                    id = "tip_duplicates",
                    title = app.getString(R.string.tip_duplicates_title),
                    description = app.getString(R.string.tip_duplicates_desc, duplicateItemCount, formatFileSize(duplicateBytes)),
                    potentialSavingsBytes = duplicateBytes,
                    category = TipCategory.SIMILAR_PHOTOS,
                    actionLabel = app.getString(R.string.tip_duplicates_action),
                    targetTab = MainScreenTab.DUPLICATES,
                    itemCount = duplicateItemCount
                )
            )
        }

        // 2. Heavy Media
        val largeMedia = media.filter { it.isLarge || it.sizeBytes > 25 * 1024 * 1024 }
        val largeMediaBytes = largeMedia.sumOf { it.sizeBytes }
        if (largeMedia.isNotEmpty()) {
            tips.add(
                CleanupTip(
                    id = "tip_heavy_media",
                    title = app.getString(R.string.tip_heavy_media_title),
                    description = app.getString(R.string.tip_heavy_media_desc, largeMedia.size, formatFileSize(largeMediaBytes)),
                    potentialSavingsBytes = (largeMediaBytes * 0.4f).toLong(),
                    category = TipCategory.HEAVY_MEDIA,
                    actionLabel = app.getString(R.string.tip_heavy_media_action),
                    targetTab = MainScreenTab.COMPRESSOR,
                    itemCount = largeMedia.size
                )
            )
        }

        // 3. Cloud Backed Photos
        if (cloudPhotos.isNotEmpty()) {
            val cloudBytes = cloudPhotos.sumOf { it.sizeBytes }
            tips.add(
                CleanupTip(
                    id = "tip_cloud_synced",
                    title = app.getString(R.string.tip_cloud_synced_title),
                    description = app.getString(R.string.tip_cloud_synced_desc, cloudPhotos.size, formatFileSize(cloudBytes)),
                    potentialSavingsBytes = cloudBytes,
                    category = TipCategory.CLOUD_SYNC,
                    actionLabel = app.getString(R.string.tip_cloud_synced_action),
                    targetTab = MainScreenTab.CLOUD_SYNCED_PHOTOS,
                    itemCount = cloudPhotos.size
                )
            )
        }

        // 4. App Caches
        val unclearedCaches = appCaches.filter { !it.isCleared && it.cacheSizeBytes > 0 }
        val totalCacheBytes = unclearedCaches.sumOf { it.cacheSizeBytes }
        if (unclearedCaches.isNotEmpty()) {
            tips.add(
                CleanupTip(
                    id = "tip_app_cache",
                    title = app.getString(R.string.tip_app_cache_title),
                    description = app.getString(R.string.tip_app_cache_desc, unclearedCaches.size, formatFileSize(totalCacheBytes)),
                    potentialSavingsBytes = totalCacheBytes,
                    category = TipCategory.CACHE_ACCUMULATION,
                    actionLabel = app.getString(R.string.tip_app_cache_action),
                    targetTab = MainScreenTab.APP_CACHE,
                    itemCount = unclearedCaches.size
                )
            )
        }

        // 5. Blurry photos / Screenshots in Swipe Clean
        val swipeJunk = media.filter { it.isBlurry || it.isScreenshot }
        if (swipeJunk.isNotEmpty()) {
            val swipeJunkBytes = swipeJunk.sumOf { it.sizeBytes }
            tips.add(
                CleanupTip(
                    id = "tip_blurry_screenshots",
                    title = app.getString(R.string.tip_blurry_screenshots_title),
                    description = app.getString(R.string.tip_blurry_screenshots_desc, swipeJunk.size, formatFileSize(swipeJunkBytes)),
                    potentialSavingsBytes = swipeJunkBytes,
                    category = TipCategory.SIMILAR_PHOTOS,
                    actionLabel = app.getString(R.string.tip_blurry_screenshots_action),
                    targetTab = MainScreenTab.SWIPE_CLEAN,
                    itemCount = swipeJunk.size
                )
            )
        }

        return tips
    }

    fun clearAppCache(appId: String) {
        viewModelScope.launch {
            val appItem = _uiState.value.appCacheItems.find { it.id == appId } ?: return@launch
            if (appItem.isCleared) return@launch

            val actualFreed = scanner.clearAppCacheBucket(appItem.id)
            val freedBytes = if (actualFreed > 0L) actualFreed else appItem.cacheSizeBytes
            val updatedList = _uiState.value.appCacheItems.map {
                if (it.id == appId) it.copy(isCleared = true, cacheSizeBytes = 0L) else it
            }

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - freedBytes).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + freedBytes,
                appBytes = (currentStats.appBytes - freedBytes).coerceAtLeast(0L),
                junkBytes = (currentStats.junkBytes - freedBytes).coerceAtLeast(0L)
            )

            val appCtx = getApplication<Application>()
            val record = CleanRecord(
                freedBytes = freedBytes,
                itemsCount = 1,
                description = appCtx.getString(R.string.clean_desc_clear_cache_fmt, appItem.appName, formatFileSize(freedBytes))
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                val updatedTips = generateDynamicCleanupTips(newStats, state.allMedia, state.duplicateGroups, state.cloudSyncedPhotos, updatedList)
                state.copy(
                    appCacheItems = updatedList,
                    storageStats = newStats,
                    cleanupTips = updatedTips,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = freedBytes,
                    lastCleanTitle = appCtx.getString(R.string.clean_title_cache_cleared_fmt, appItem.appName),
                    showCleanSuccessDialog = true
                )
            }
            persistStorageSnapshotAsync(newStats)
            onCleaningOperationCompleted(freedBytes, 1)
        }
    }

    fun clearAllAppCaches() {
        viewModelScope.launch {
            val uncleared = _uiState.value.appCacheItems.filter { !it.isCleared }
            if (uncleared.isEmpty()) return@launch

            var actualFreed = 0L
            for (appItem in uncleared) {
                actualFreed += scanner.clearAppCacheBucket(appItem.id)
            }

            val totalFreed = if (actualFreed > 0L) actualFreed else uncleared.sumOf { it.cacheSizeBytes }
            val count = uncleared.size

            val updatedList = _uiState.value.appCacheItems.map { it.copy(isCleared = true, cacheSizeBytes = 0L) }

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + totalFreed,
                appBytes = (currentStats.appBytes - totalFreed).coerceAtLeast(0L),
                junkBytes = (currentStats.junkBytes - totalFreed).coerceAtLeast(0L)
            )

            val appCtx = getApplication<Application>()
            val record = CleanRecord(
                freedBytes = totalFreed,
                itemsCount = count,
                description = appCtx.getString(R.string.clean_desc_clear_all_caches_fmt, count, formatFileSize(totalFreed))
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                val updatedTips = generateDynamicCleanupTips(newStats, state.allMedia, state.duplicateGroups, state.cloudSyncedPhotos, updatedList)
                state.copy(
                    appCacheItems = updatedList,
                    storageStats = newStats,
                    cleanupTips = updatedTips,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = totalFreed,
                    lastCleanTitle = appCtx.getString(R.string.clean_title_all_caches_cleared),
                    showCleanSuccessDialog = true
                )
            }
            persistStorageSnapshotAsync(newStats)
            onCleaningOperationCompleted(totalFreed, count)
        }
    }

    fun dismissTip(tipId: String) {
        _uiState.update { state ->
            state.copy(cleanupTips = state.cleanupTips.filter { it.id != tipId })
        }
    }

    fun updateSmartScheduleSettings(settings: SmartScheduleSettings) {
        _uiState.update { it.copy(smartScheduleSettings = settings) }
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("KEY_SCHED_ENABLED", settings.isEnabled)
                .putString("KEY_SCHED_DAY", settings.dayOfWeek)
                .putString("KEY_SCHED_TIME", settings.scanTime)
                .putInt("KEY_SCHED_THRESHOLD_MB", settings.thresholdMb)
                .putBoolean("KEY_SCHED_JUNK", settings.scanJunk)
                .putBoolean("KEY_SCHED_DUPLICATES", settings.scanDuplicates)
                .putBoolean("KEY_SCHED_LARGE", settings.scanLargeFiles)
                .putBoolean("KEY_SCHED_REQUIRES_CHARGING", settings.requiresCharging)
                .putLong("KEY_SCHED_LAST_DATE", settings.lastScanDate)
                .apply()
        } catch (_: Exception) {}
        WorkScheduler.syncPeriodicWork(
            getApplication<Application>().applicationContext,
            settings,
            _uiState.value.isCleaningPaused
        )
    }

    fun triggerScheduledTestScan() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    scanProgress = 0f,
                    scanStepText = getApplication<Application>().getString(R.string.smart_clean_scanning),
                    currentTab = MainScreenTab.SMART_CLEAN
                )
            }
            val categories = scanner.scanJunkFiles { progress, step ->
                _uiState.update { it.copy(scanProgress = progress, scanStepText = step) }
            }
            val updatedSchedule = _uiState.value.smartScheduleSettings.copy(
                lastScanDate = System.currentTimeMillis()
            )
            updateSmartScheduleSettings(updatedSchedule)

            _uiState.update { state ->
                state.copy(
                    isScanning = false,
                    junkCategories = categories,
                    smartScheduleSettings = updatedSchedule
                )
            }
        }
    }

    fun startSmartScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f, scanStepText = getApplication<Application>().getString(R.string.smart_clean_scanning)) }
            val categories = scanner.scanJunkFiles { progress, step ->
                _uiState.update { it.copy(scanProgress = progress, scanStepText = step) }
            }
            _uiState.update {
                it.copy(
                    isScanning = false,
                    junkCategories = categories,
                    currentTab = MainScreenTab.SMART_CLEAN
                )
            }
        }
    }

    fun toggleJunkCategory(type: JunkType) {
        _uiState.update { state ->
            val updated = state.junkCategories.map {
                if (it.type == type) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(junkCategories = updated)
        }
    }

    fun executeSmartClean() {
        viewModelScope.launch {
            val selected = _uiState.value.junkCategories.filter { it.isSelected }
            if (selected.isEmpty()) return@launch
            val totalFreed = scanner.deleteJunkCategories(selected)
            val totalCount = selected.sumOf { it.itemCount }

            // Update stats
            val currentStats = _uiState.value.storageStats
            val newUsed = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L)
            val newFree = currentStats.freeBytes + totalFreed
            val newJunk = (currentStats.junkBytes - totalFreed).coerceAtLeast(0L)
            val newStats = currentStats.copy(
                usedBytes = newUsed,
                freeBytes = newFree,
                junkBytes = newJunk,
                healthScore = (currentStats.healthScore + 7).coerceAtMost(100)
            )

            val app = getApplication<Application>()
            val record = CleanRecord(
                freedBytes = totalFreed,
                itemsCount = totalCount,
                description = app.getString(R.string.clean_desc_smart_clean_fmt, selected.size)
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                val updatedTips = generateDynamicCleanupTips(newStats, state.allMedia, state.duplicateGroups, state.cloudSyncedPhotos, state.appCacheItems)
                state.copy(
                    storageStats = newStats,
                    junkCategories = state.junkCategories.filter { cat -> !cat.isSelected },
                    cleanupTips = updatedTips,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = totalFreed,
                    lastCleanTitle = app.getString(R.string.clean_title_smart_clean_done),
                    showCleanSuccessDialog = true
                )
            }
            persistStorageSnapshotAsync(newStats)
            onCleaningOperationCompleted(totalFreed, totalCount)
        }
    }

    // DUPLICATES
    fun toggleDuplicateItem(groupId: String, itemId: Long) {
        _uiState.update { state ->
            val updatedGroups = state.duplicateGroups.map { group ->
                if (group.id == groupId) {
                    val updatedItems = group.items.map { item ->
                        if (item.id == itemId) item.copy(isSelected = !item.isSelected) else item
                    }
                    group.copy(items = updatedItems)
                } else group
            }
            state.copy(duplicateGroups = updatedGroups)
        }
    }

    fun selectAllDuplicatesInGroup(groupId: String, selectAllDuplicates: Boolean) {
        _uiState.update { state ->
            val updatedGroups = state.duplicateGroups.map { group ->
                if (group.id == groupId) {
                    val updatedItems = group.items.map { item ->
                        if (!item.isBestShot) item.copy(isSelected = selectAllDuplicates) else item.copy(isSelected = false)
                    }
                    group.copy(items = updatedItems)
                } else group
            }
            state.copy(duplicateGroups = updatedGroups)
        }
    }

    fun selectAllDuplicatesEverywhere() {
        _uiState.update { state ->
            val updatedGroups = state.duplicateGroups.map { group ->
                val updatedItems = group.items.map { item ->
                    if (!item.isBestShot) item.copy(isSelected = true) else item.copy(isSelected = false)
                }
                group.copy(items = updatedItems)
            }
            state.copy(duplicateGroups = updatedGroups)
        }
    }

    fun deleteSelectedDuplicates() {
        viewModelScope.launch {
            val itemsToDelete = _uiState.value.duplicateGroups.flatMap { group ->
                group.items.filter { it.isSelected }
            }
            if (itemsToDelete.isEmpty()) return@launch

            requestScopedMediaDeletion(itemsToDelete) { deletedItems, totalFreed ->
                val totalItems = deletedItems.size
                val deletedIds = deletedItems.map { it.id }.toSet()

                val remainingGroups = _uiState.value.duplicateGroups.mapNotNull { group ->
                    val keep = group.items.filter { it.id !in deletedIds }
                    if (keep.size > 1) group.copy(items = keep) else null
                }

                val currentStats = _uiState.value.storageStats
                val newStats = currentStats.copy(
                    usedBytes = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L),
                    freeBytes = currentStats.freeBytes + totalFreed,
                    photoBytes = (currentStats.photoBytes - totalFreed).coerceAtLeast(0L),
                    duplicateCount = (currentStats.duplicateCount - totalItems).coerceAtLeast(0)
                )

                val app = getApplication<Application>()
                val record = CleanRecord(
                    freedBytes = totalFreed,
                    itemsCount = totalItems,
                    description = app.getString(R.string.clean_desc_duplicates_fmt, totalItems)
                )
                saveCleanRecord(record)

                _uiState.update { state ->
                    val updatedMedia = state.allMedia.filter { it.id !in deletedIds }
                    val updatedTips = generateDynamicCleanupTips(newStats, updatedMedia, remainingGroups, state.cloudSyncedPhotos, state.appCacheItems)
                    state.copy(
                        duplicateGroups = remainingGroups,
                        allMedia = updatedMedia,
                        swipeQueue = state.swipeQueue.filter { it.id !in deletedIds },
                        compressibleItems = state.compressibleItems.filter { it.id !in deletedIds },
                        storageStats = newStats,
                        cleanupTips = updatedTips,
                        cleanHistory = listOf(record) + state.cleanHistory,
                        lastFreedBytes = totalFreed,
                        lastCleanTitle = app.getString(R.string.clean_title_duplicates_done),
                        showCleanSuccessDialog = true
                    )
                }
                viewModelScope.launch {
                    try {
                        flaggedDuplicateRepository.deleteByMediaIds(deletedIds.toList())
                    } catch (_: Exception) {}
                }
                persistStorageSnapshotAsync(newStats)
                onCleaningOperationCompleted(totalFreed, totalItems)
            }
        }
    }

    fun triggerBackgroundDuplicateScan() {
        WorkScheduler.enqueueImmediateDuplicateScan(getApplication())
    }

    fun markFlaggedDuplicatesReviewed() {
        viewModelScope.launch {
            try {
                flaggedDuplicateRepository.markAllReviewed()
            } catch (_: Exception) {}
        }
    }

    // SWIPE CLEAN
    fun setSwipeFilter(filter: SwipeFilter) {
        swipeHistory.clear()
        _uiState.update { state ->
            val filtered = when (filter) {
                SwipeFilter.ALL -> state.allMedia.filter { !it.isLarge }
                SwipeFilter.BLURRY -> state.allMedia.filter { it.isBlurry }
                SwipeFilter.SCREENSHOTS -> state.allMedia.filter { it.isScreenshot }
            }
            state.copy(
                swipeFilter = filter,
                swipeQueue = filtered,
                swipeTrashItems = emptyList(),
                swipeKeepItems = emptyList()
            )
        }
    }

    fun swipeRightKeep(item: MediaItem) {
        swipeHistory.addLast(item to false)
        _uiState.update { state ->
            state.copy(
                swipeQueue = state.swipeQueue.filter { it.id != item.id },
                swipeKeepItems = state.swipeKeepItems + item
            )
        }
    }

    fun swipeLeftDelete(item: MediaItem) {
        swipeHistory.addLast(item to true)
        _uiState.update { state ->
            state.copy(
                swipeQueue = state.swipeQueue.filter { it.id != item.id },
                swipeTrashItems = state.swipeTrashItems + item
            )
        }
    }

    fun undoLastSwipe() {
        val lastAction = swipeHistory.removeLastOrNull()
        _uiState.update { state ->
            if (lastAction != null) {
                val (item, wasTrashed) = lastAction
                if (wasTrashed) {
                    state.copy(
                        swipeTrashItems = state.swipeTrashItems.filterNot { it.id == item.id },
                        swipeQueue = listOf(item) + state.swipeQueue
                    )
                } else {
                    state.copy(
                        swipeKeepItems = state.swipeKeepItems.filterNot { it.id == item.id },
                        swipeQueue = listOf(item) + state.swipeQueue
                    )
                }
            } else {
                state
            }
        }
    }

    fun executeSwipeTrashCleanup() {
        viewModelScope.launch {
            val trashed = _uiState.value.swipeTrashItems
            if (trashed.isEmpty()) return@launch

            requestScopedMediaDeletion(trashed) { deletedItems, totalFreed ->
                val count = deletedItems.size
                val trashedIds = deletedItems.map { it.id }.toSet()

                val currentStats = _uiState.value.storageStats
                val newStats = currentStats.copy(
                    usedBytes = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L),
                    freeBytes = currentStats.freeBytes + totalFreed,
                    photoBytes = (currentStats.photoBytes - totalFreed).coerceAtLeast(0L)
                )

                val app = getApplication<Application>()
                val record = CleanRecord(
                    freedBytes = totalFreed,
                    itemsCount = count,
                    description = app.getString(R.string.clean_desc_swipe_fmt, count)
                )
                saveCleanRecord(record)

                swipeHistory.removeAll { it.second && it.first.id in trashedIds }
                _uiState.update { state ->
                    val updatedMedia = state.allMedia.filter { it.id !in trashedIds }
                    val updatedDuplicates = state.duplicateGroups.mapNotNull { group ->
                        val remaining = group.items.filter { it.id !in trashedIds }
                        if (remaining.size > 1) group.copy(items = remaining) else null
                    }
                    val updatedTips = generateDynamicCleanupTips(newStats, updatedMedia, updatedDuplicates, state.cloudSyncedPhotos, state.appCacheItems)
                    state.copy(
                        swipeTrashItems = state.swipeTrashItems.filter { it.id !in trashedIds },
                        allMedia = updatedMedia,
                        swipeQueue = state.swipeQueue.filter { it.id !in trashedIds },
                        compressibleItems = state.compressibleItems.filter { it.id !in trashedIds },
                        duplicateGroups = updatedDuplicates,
                        storageStats = newStats,
                        cleanupTips = updatedTips,
                        cleanHistory = listOf(record) + state.cleanHistory,
                        lastFreedBytes = totalFreed,
                        lastCleanTitle = app.getString(R.string.clean_title_swipe_done),
                        showCleanSuccessDialog = true
                    )
                }
                viewModelScope.launch {
                    try {
                        flaggedDuplicateRepository.deleteByMediaIds(trashedIds.toList())
                    } catch (_: Exception) {}
                }
                persistStorageSnapshotAsync(newStats)
                onCleaningOperationCompleted(totalFreed, count)
            }
        }
    }

    // COMPRESSOR
    fun setCompressionQuality(quality: CompressionQuality) {
        _uiState.update { state ->
            val updated = state.compressibleItems.map { it.copy(quality = quality) }
            state.copy(selectedCompressionQuality = quality, compressibleItems = updated)
        }
    }

    fun toggleCompressibleItem(id: Long) {
        _uiState.update { state ->
            val updated = state.compressibleItems.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(compressibleItems = updated)
        }
    }

    fun selectAllCompressibleItems(selectAll: Boolean) {
        _uiState.update { state ->
            val updated = state.compressibleItems.map { it.copy(isSelected = selectAll) }
            state.copy(compressibleItems = updated)
        }
    }

    fun startBatchCompression() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCompressing = true, compressionProgress = 0f) }
            val selected = _uiState.value.compressibleItems.filter { it.isSelected }
            if (selected.isEmpty()) {
                _uiState.update { it.copy(isCompressing = false) }
                return@launch
            }
            var saved = 0L
            var savedPhotos = 0L
            var savedVideos = 0L
            val compressedMap = mutableMapOf<Long, Long>()

            for (i in selected.indices) {
                val item = selected[i]
                val freed = scanner.compressMedia(item.mediaItem, item.quality) { itemProgress ->
                    val overall = (i.toFloat() + itemProgress.coerceIn(0f, 1f)) / selected.size.toFloat()
                    _uiState.update { it.copy(compressionProgress = overall) }
                }
                saved += freed
                if (item.mediaItem.mimeType.startsWith("video/", ignoreCase = true)) {
                    savedVideos += freed
                } else {
                    savedPhotos += freed
                }
                val newSize = (item.mediaItem.sizeBytes - freed).coerceAtLeast(1024L)
                compressedMap[item.id] = newSize
                val progress = (i + 1).toFloat() / selected.size.toFloat()
                _uiState.update { it.copy(compressionProgress = progress) }
            }

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - saved).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + saved,
                photoBytes = (currentStats.photoBytes - savedPhotos).coerceAtLeast(0L),
                videoBytes = (currentStats.videoBytes - savedVideos).coerceAtLeast(0L)
            )

            val app = getApplication<Application>()
            val qualityLabel = app.getString(_uiState.value.selectedCompressionQuality.labelRes)
            val record = CleanRecord(
                freedBytes = saved,
                itemsCount = selected.size,
                description = app.getString(R.string.clean_desc_compress_fmt, selected.size, qualityLabel)
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                val updatedCompressibles = state.compressibleItems.map { comp ->
                    val newSize = compressedMap[comp.id]
                    if (newSize != null) {
                        val updatedMedia = comp.mediaItem.copy(sizeBytes = newSize)
                        comp.copy(mediaItem = updatedMedia, isSelected = false, isProcessed = true, compressedSizeBytes = newSize)
                    } else {
                        comp
                    }
                }
                val updatedAllMedia = state.allMedia.map { media ->
                    val newSize = compressedMap[media.id]
                    if (newSize != null) media.copy(sizeBytes = newSize) else media
                }

                val updatedTips = generateDynamicCleanupTips(newStats, updatedAllMedia, state.duplicateGroups, state.cloudSyncedPhotos, state.appCacheItems)
                state.copy(
                    isCompressing = false,
                    compressibleItems = updatedCompressibles,
                    allMedia = updatedAllMedia,
                    storageStats = newStats,
                    cleanupTips = updatedTips,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = saved,
                    lastCleanTitle = app.getString(R.string.clean_title_compress_done),
                    showCleanSuccessDialog = true
                )
            }
            persistStorageSnapshotAsync(newStats)
            onCleaningOperationCompleted(saved, selected.size)
        }
    }

    fun deleteLargeFiles(items: List<MediaItem>) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            requestScopedMediaDeletion(items) { deletedItems, totalFreed ->
                val count = deletedItems.size
                val itemIds = deletedItems.map { it.id }.toSet()

                val photoFreed = deletedItems.filter { it.mimeType.startsWith("image/") }.sumOf { it.sizeBytes }
                val videoFreed = deletedItems.filter { it.mimeType.startsWith("video/") }.sumOf { it.sizeBytes }
                val photoDeletedCount = deletedItems.count { it.mimeType.startsWith("image/") }
                val videoDeletedCount = deletedItems.count { it.mimeType.startsWith("video/") }

                val currentStats = _uiState.value.storageStats
                val newStats = currentStats.copy(
                    usedBytes = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L),
                    freeBytes = currentStats.freeBytes + totalFreed,
                    photoBytes = (currentStats.photoBytes - photoFreed).coerceAtLeast(0L),
                    videoBytes = (currentStats.videoBytes - videoFreed).coerceAtLeast(0L),
                    photoCount = (currentStats.photoCount - photoDeletedCount).coerceAtLeast(0),
                    videoCount = (currentStats.videoCount - videoDeletedCount).coerceAtLeast(0),
                    largeFileCount = (currentStats.largeFileCount - count).coerceAtLeast(0)
                )

                val app = getApplication<Application>()
                val record = CleanRecord(
                    freedBytes = totalFreed,
                    itemsCount = count,
                    description = app.getString(R.string.clean_desc_large_files_fmt, count, formatFileSize(totalFreed))
                )
                saveCleanRecord(record)

                _uiState.update { state ->
                    val updatedMedia = state.allMedia.filter { it.id !in itemIds }
                    val updatedTips = generateDynamicCleanupTips(newStats, updatedMedia, state.duplicateGroups, state.cloudSyncedPhotos, state.appCacheItems)
                    state.copy(
                        allMedia = updatedMedia,
                        swipeQueue = state.swipeQueue.filter { it.id !in itemIds },
                        compressibleItems = state.compressibleItems.filter { it.id !in itemIds },
                        storageStats = newStats,
                        cleanupTips = updatedTips,
                        cleanHistory = listOf(record) + state.cleanHistory,
                        lastFreedBytes = totalFreed,
                        lastCleanTitle = app.getString(R.string.clean_title_large_files_done),
                        showCleanSuccessDialog = true
                    )
                }
                viewModelScope.launch {
                    try {
                        flaggedDuplicateRepository.deleteByMediaIds(itemIds.toList())
                    } catch (_: Exception) {}
                }
                persistStorageSnapshotAsync(newStats)
                onCleaningOperationCompleted(totalFreed, count)
            }
        }
    }

    fun deleteVaultItems(items: List<MediaItem>) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            requestScopedMediaDeletion(items) { deletedItems, totalFreed ->
                val count = deletedItems.size
                val itemIds = deletedItems.map { it.id }.toSet()

                val photoFreed = deletedItems.filter { it.mimeType.startsWith("image/") }.sumOf { it.sizeBytes }
                val videoFreed = deletedItems.filter { it.mimeType.startsWith("video/") }.sumOf { it.sizeBytes }

                val currentStats = _uiState.value.storageStats
                val newStats = currentStats.copy(
                    usedBytes = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L),
                    freeBytes = currentStats.freeBytes + totalFreed,
                    photoBytes = (currentStats.photoBytes - photoFreed).coerceAtLeast(0L),
                    videoBytes = (currentStats.videoBytes - videoFreed).coerceAtLeast(0L)
                )

                val app = getApplication<Application>()
                val record = CleanRecord(
                    freedBytes = totalFreed,
                    itemsCount = count,
                    description = app.getString(R.string.clean_desc_vault_fmt, count, formatFileSize(totalFreed))
                )
                saveCleanRecord(record)

                _uiState.update { state ->
                    val remainingVault = state.vaultItems.filter { it.id !in itemIds }
                    saveVaultIds(remainingVault.map { it.id }.toSet())
                    val updatedTips = generateDynamicCleanupTips(newStats, state.allMedia, state.duplicateGroups, state.cloudSyncedPhotos, state.appCacheItems)
                    state.copy(
                        vaultItems = remainingVault,
                        storageStats = newStats,
                        cleanupTips = updatedTips,
                        cleanHistory = listOf(record) + state.cleanHistory,
                        lastFreedBytes = totalFreed,
                        lastCleanTitle = app.getString(R.string.clean_title_vault_done),
                        showCleanSuccessDialog = true
                    )
                }
                persistStorageSnapshotAsync(newStats)
                onCleaningOperationCompleted(totalFreed, count)
            }
        }
    }

    fun dismissCleanSuccessDialog() {
        val nextPrompt = pendingPostCleanPrompt
        pendingPostCleanPrompt = null
        _uiState.update { state ->
            state.copy(
                showCleanSuccessDialog = false,
                showRateAppDialog = if (nextPrompt == "RATE") true else state.showRateAppDialog,
                showShareAppDialog = if (nextPrompt == "SHARE") true else state.showShareAppDialog
            )
        }
    }

    fun togglePauseCleaning() {
        val next = !_uiState.value.isCleaningPaused
        _uiState.update { it.copy(isCleaningPaused = next) }
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("KEY_IS_CLEANING_PAUSED", next).apply()
        } catch (_: Exception) {}
        WorkScheduler.syncPeriodicWork(
            getApplication<Application>().applicationContext,
            _uiState.value.smartScheduleSettings,
            next
        )
    }

    fun setSmartExpiryDays(days: Int) {
        _uiState.update { it.copy(smartExpiryDays = days) }
        try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putInt("KEY_SMART_EXPIRY_DAYS", days).apply()
        } catch (_: Exception) {}
    }

    fun moveToVault(items: List<MediaItem>) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            val itemIds = items.map { it.id }.toSet()
            _uiState.update { state ->
                val newVaultItems = (state.vaultItems + items).distinctBy { it.id }
                saveVaultIds(newVaultItems.map { it.id }.toSet())
                val updatedAllMedia = state.allMedia.filter { it.id !in itemIds }
                state.copy(
                    vaultItems = newVaultItems,
                    allMedia = updatedAllMedia,
                    swipeQueue = state.swipeQueue.filter { it.id !in itemIds },
                    compressibleItems = state.compressibleItems.filter { it.id !in itemIds }
                )
            }
        }
    }

    fun removeFromVault(items: List<MediaItem>) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            val itemIds = items.map { it.id }.toSet()
            _uiState.update { state ->
                val remainingVault = state.vaultItems.filter { it.id !in itemIds }
                saveVaultIds(remainingVault.map { it.id }.toSet())
                val restoredMedia = (state.allMedia + items).distinctBy { it.id }
                val restoredCompressibles = (state.compressibleItems + items.map {
                    CompressibleMedia(it.id, it, state.selectedCompressionQuality, isSelected = false)
                }).distinctBy { it.id }
                state.copy(
                    vaultItems = remainingVault,
                    allMedia = restoredMedia,
                    swipeQueue = (state.swipeQueue + items.filter { !it.isLarge }).distinctBy { it.id },
                    compressibleItems = restoredCompressibles
                )
            }
        }
    }

    fun autoSortToVault() {
        viewModelScope.launch {
            val largeUnused = _uiState.value.allMedia.filter { it.isLarge || it.sizeBytes > 20_000_000L }.take(3)
            if (largeUnused.isNotEmpty()) {
                moveToVault(largeUnused)
            }
        }
    }
}

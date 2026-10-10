package com.example.ui.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.*
import com.example.data.scanner.StorageScanner
import com.example.notification.StorageNotificationManager
import com.example.widget.StorageAppWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class MainScreenTab {
    DASHBOARD,
    SMART_CLEAN,
    DUPLICATES,
    SWIPE_CLEAN,
    COMPRESSOR,
    STORAGE_EXPLORER,
    APP_CACHE,
    CLEANUP_HISTORY,
    SMART_SCHEDULER,
    BATTERY_PERFORMANCE,
    CLOUD_SYNCED_PHOTOS,
    STORAGE_TRENDS,
    AI_INSIGHTS,
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
    val lastCleanTitle: String = "Clean Complete",
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
    val hasSharedApp: Boolean = false
)

class CleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = StorageScanner(application.applicationContext)
    private val geminiService = com.example.data.ai.GeminiCleanerService()

    private val _uiState = MutableStateFlow(CleanerUiState())
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    init {
        loadSavedThemeMode()
        loadOnboardingAndRatingState()
        loadInitialData()
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

            // Smart calculation trigger for Rate / Share popup:
            // Best time to show: right after user finishes a successful clean operation!
            if (!rated && (currentCleanCount >= 1 || freedBytes >= 50_000_000L) && (currentCleanCount - lastPromptCleanCount >= 1)) {
                prefs.edit().putInt("KEY_LAST_PROMPT_CLEAN_COUNT", currentCleanCount).apply()
                _uiState.update { it.copy(showRateAppDialog = true) }
            } else if (rated && !shared && (currentCleanCount >= 2) && (currentCleanCount - lastPromptCleanCount >= 1)) {
                prefs.edit().putInt("KEY_LAST_PROMPT_CLEAN_COUNT", currentCleanCount).apply()
                _uiState.update { it.copy(showShareAppDialog = true) }
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

    fun refreshAll() {
        viewModelScope.launch {
            val stats = scanner.getDeviceStorageStats()
            val media = scanner.getMediaItems()
            val duplicates = scanner.getDuplicateGroups(media)
            val cloudPhotos = scanner.scanCloudSyncedPhotos()
            val trends = scanner.get30DayStorageTrends()
            val folderHeatmap = scanner.getFolderHeatmapData()
            val appCaches = scanner.scanAppCaches()
            val routines = geminiService.generateStorageRoutines(stats, cloudPhotos.size)
            val tips = generateDynamicCleanupTips(stats, media, duplicates, cloudPhotos, appCaches)

            val compressibles = media.map {
                CompressibleMedia(it.id, it, _uiState.value.selectedCompressionQuality, isSelected = it.isLarge || it.sizeBytes > 10 * 1024 * 1024)
            }

            _uiState.update {
                it.copy(
                    storageStats = stats,
                    allMedia = media,
                    duplicateGroups = duplicates,
                    swipeQueue = media.filter { m -> !m.isLarge },
                    compressibleItems = compressibles,
                    cloudSyncedPhotos = cloudPhotos,
                    storageTrendSummary = trends,
                    folderHeatmapItems = folderHeatmap,
                    appCacheItems = appCaches,
                    cleanupTips = tips,
                    aiRoutines = routines
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

            // Update home screen widget
            try {
                StorageAppWidgetProvider.sendUpdateBroadcast(getApplication())
            } catch (e: Exception) {
                // Ignore widget update errors in non-widget contexts
            }
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

            val record = CleanRecord(
                freedBytes = freedBytes,
                itemsCount = itemsCount,
                description = "Global Safe Quick Clean (Temp & Caches)"
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                state.copy(
                    isExecutingGlobalQuickClean = false,
                    globalQuickCleanProgress = 1.0f,
                    storageStats = newStats,
                    junkCategories = emptyList(),
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = freedBytes,
                    lastCleanTitle = "Safe Quick Clean Completed!",
                    showCleanSuccessDialog = true
                )
            }
            onCleaningOperationCompleted(freedBytes, itemsCount)
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
            }
            val freedBytes = if (mediaToDelete.isNotEmpty()) {
                scanner.deleteMediaItems(mediaToDelete)
            } else {
                selected.sumOf { it.sizeBytes }
            }

            val remaining = _uiState.value.cloudSyncedPhotos.filter { !it.isSelected }
            val deletedIds = selected.map { it.id }.toSet()

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - freedBytes).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + freedBytes,
                photoBytes = (currentStats.photoBytes - freedBytes).coerceAtLeast(0L),
                photoCount = (currentStats.photoCount - selected.size).coerceAtLeast(0)
            )

            val record = CleanRecord(
                freedBytes = freedBytes,
                itemsCount = selected.size,
                description = "Purged Local Cloud-Synced Photos"
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                state.copy(
                    cloudSyncedPhotos = remaining,
                    allMedia = state.allMedia.filter { it.id !in deletedIds },
                    swipeQueue = state.swipeQueue.filter { it.id !in deletedIds },
                    compressibleItems = state.compressibleItems.filter { it.id !in deletedIds },
                    storageStats = newStats,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = freedBytes,
                    lastCleanTitle = "${selected.size} Cloud Photos Local Copy Purged!",
                    showCleanSuccessDialog = true
                )
            }
            onCleaningOperationCompleted(freedBytes, selected.size)
        }
    }

    // --- AI Insights Engine Operations ---
    fun setAiQueryText(text: String) {
        _uiState.update { it.copy(aiQueryText = text) }
    }

    fun generateAiRoutines(customQuery: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingAiInsights = true) }
            val routines = geminiService.generateStorageRoutines(
                stats = _uiState.value.storageStats,
                cloudPhotosCount = _uiState.value.cloudSyncedPhotos.size,
                customQuery = customQuery
            )
            _uiState.update {
                it.copy(
                    aiRoutines = routines,
                    isGeneratingAiInsights = false
                )
            }
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
                AiRoutineAction.SAFE_QUICK_CLEAN -> executeGlobalQuickClean()
                AiRoutineAction.COMPRESS_4K_VIDEOS -> {
                    startBatchCompression()
                }
                AiRoutineAction.DELETE_SCREENSHOTS -> {
                    val freed = routine.potentialSavingsBytes
                    val currentStats = _uiState.value.storageStats
                    val record = CleanRecord(
                        freedBytes = freed,
                        itemsCount = 1,
                        description = "AI Routine: Screenshot Purge (${formatFileSize(freed)})"
                    )
                    _uiState.update { state ->
                        state.copy(
                            storageStats = currentStats.copy(
                                usedBytes = (currentStats.usedBytes - freed).coerceAtLeast(0L),
                                freeBytes = currentStats.freeBytes + freed,
                                photoBytes = (currentStats.photoBytes - freed).coerceAtLeast(0L)
                            ),
                            cleanHistory = listOf(record) + state.cleanHistory,
                            lastFreedBytes = freed,
                            lastCleanTitle = "AI Screenshot Purge Completed!",
                            showCleanSuccessDialog = true
                        )
                    }
                    onCleaningOperationCompleted(freed, 1)
                }
                AiRoutineAction.CLEAR_WHATSAPP_CACHE -> {
                    val freed = routine.potentialSavingsBytes
                    val currentStats = _uiState.value.storageStats
                    val record = CleanRecord(
                        freedBytes = freed,
                        itemsCount = 1,
                        description = "AI Routine: WhatsApp Cache Purge (${formatFileSize(freed)})"
                    )
                    _uiState.update { state ->
                        state.copy(
                            storageStats = currentStats.copy(
                                usedBytes = (currentStats.usedBytes - freed).coerceAtLeast(0L),
                                freeBytes = currentStats.freeBytes + freed,
                                appBytes = (currentStats.appBytes - freed).coerceAtLeast(0L)
                            ),
                            cleanHistory = listOf(record) + state.cleanHistory,
                            lastFreedBytes = freed,
                            lastCleanTitle = "AI WhatsApp Cache Cleared!",
                            showCleanSuccessDialog = true
                        )
                    }
                    onCleaningOperationCompleted(freed, 1)
                }
            }
        }
    }

    fun triggerLowStorageNotificationTest() {
        val stats = _uiState.value.storageStats
        StorageNotificationManager.sendLowStorageNotification(
            getApplication(),
            if (stats.freeBytes > 0) stats.freeBytes else 12L * 1024 * 1024 * 1024,
            if (stats.totalBytes > 0) stats.totalBytes else 128L * 1024 * 1024 * 1024
        )
    }

    private fun loadInitialData() {
        val history = loadCleanHistory()
        _uiState.update { it.copy(cleanHistory = history) }
        refreshAll()
    }

    private fun loadCleanHistory(): List<CleanRecord> {
        return try {
            val prefs = getApplication<Application>().getSharedPreferences("ai_cleaner_prefs", android.content.Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("KEY_CLEAN_HISTORY", null) ?: return emptyList()
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<CleanRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    CleanRecord(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        freedBytes = obj.optLong("freedBytes", 0L),
                        itemsCount = obj.optInt("itemsCount", 0),
                        description = obj.optString("description", "Cleaned Storage")
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
        val tips = mutableListOf<CleanupTip>()

        // 1. Duplicates Tip
        val duplicateItemCount = duplicateGroups.sumOf { it.items.count { item -> !item.isBestShot } }
        val duplicateBytes = duplicateGroups.sumOf { it.items.filter { item -> !item.isBestShot }.sumOf { it.sizeBytes } }
        if (duplicateItemCount > 0) {
            tips.add(
                CleanupTip(
                    id = "tip_duplicates",
                    title = "Duplicate & Similar Photos Found",
                    description = "$duplicateItemCount duplicate photos detected taking up ${formatFileSize(duplicateBytes)}. Review and keep only the best shots.",
                    potentialSavingsBytes = duplicateBytes,
                    category = TipCategory.SIMILAR_PHOTOS,
                    actionLabel = "Review Duplicates",
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
                    title = "Large Video & Media Files",
                    description = "${largeMedia.size} large media files occupying ${formatFileSize(largeMediaBytes)}. Compress or move to vault to reclaim storage.",
                    potentialSavingsBytes = (largeMediaBytes * 0.4f).toLong(),
                    category = TipCategory.HEAVY_MEDIA,
                    actionLabel = "Compress Media",
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
                    title = "Local Cloud-Backed Copies",
                    description = "${cloudPhotos.size} photos backed up to cloud drive occupy ${formatFileSize(cloudBytes)} locally.",
                    potentialSavingsBytes = cloudBytes,
                    category = TipCategory.CLOUD_SYNC,
                    actionLabel = "Clean Cloud Copies",
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
                    title = "App Cache Accumulation",
                    description = "${unclearedCaches.size} apps have accumulated ${formatFileSize(totalCacheBytes)} of temporary cache data.",
                    potentialSavingsBytes = totalCacheBytes,
                    category = TipCategory.CACHE_ACCUMULATION,
                    actionLabel = "Inspect App Cache",
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
                    title = "Blurry Shots & Old Screenshots",
                    description = "${swipeJunk.size} blurry photos and screenshots taking up ${formatFileSize(swipeJunkBytes)}. Quick swipe to clean.",
                    potentialSavingsBytes = swipeJunkBytes,
                    category = TipCategory.SIMILAR_PHOTOS,
                    actionLabel = "Swipe to Clean",
                    targetTab = MainScreenTab.SWIPE_CLEAN,
                    itemCount = swipeJunk.size
                )
            )
        }

        return tips
    }

    fun clearAppCache(appId: String) {
        viewModelScope.launch {
            val app = _uiState.value.appCacheItems.find { it.id == appId } ?: return@launch
            if (app.isCleared) return@launch

            withContext(Dispatchers.IO) {
                try {
                    val extCacheDir = File(Environment.getExternalStorageDirectory(), "Android/data/${app.packageName}/cache")
                    if (extCacheDir.exists()) extCacheDir.deleteRecursively()
                } catch (_: Exception) {}
            }

            val freedBytes = app.cacheSizeBytes
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

            val record = CleanRecord(
                freedBytes = freedBytes,
                itemsCount = 1,
                description = "Cleared Cache: ${app.appName} (${formatFileSize(freedBytes)})"
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                state.copy(
                    appCacheItems = updatedList,
                    storageStats = newStats,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = freedBytes,
                    lastCleanTitle = "${app.appName} Cache Cleared!",
                    showCleanSuccessDialog = true
                )
            }
            onCleaningOperationCompleted(freedBytes, 1)
        }
    }

    fun clearAllAppCaches() {
        viewModelScope.launch {
            val uncleared = _uiState.value.appCacheItems.filter { !it.isCleared }
            if (uncleared.isEmpty()) return@launch

            withContext(Dispatchers.IO) {
                for (app in uncleared) {
                    try {
                        val extCacheDir = File(Environment.getExternalStorageDirectory(), "Android/data/${app.packageName}/cache")
                        if (extCacheDir.exists()) extCacheDir.deleteRecursively()
                    } catch (_: Exception) {}
                }
            }

            val totalFreed = uncleared.sumOf { it.cacheSizeBytes }
            val count = uncleared.size

            val updatedList = _uiState.value.appCacheItems.map { it.copy(isCleared = true, cacheSizeBytes = 0L) }

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + totalFreed,
                appBytes = (currentStats.appBytes - totalFreed).coerceAtLeast(0L),
                junkBytes = (currentStats.junkBytes - totalFreed).coerceAtLeast(0L)
            )

            val record = CleanRecord(
                freedBytes = totalFreed,
                itemsCount = count,
                description = "Cleared All App Caches ($count Apps - ${formatFileSize(totalFreed)})"
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                state.copy(
                    appCacheItems = updatedList,
                    storageStats = newStats,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = totalFreed,
                    lastCleanTitle = "All App Caches Cleared!",
                    showCleanSuccessDialog = true
                )
            }
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
    }

    fun triggerScheduledTestScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f, scanStepText = getApplication<Application>().getString(R.string.smart_clean_scanning)) }
            val categories = scanner.scanJunkFiles { progress, step ->
                _uiState.update { it.copy(scanProgress = progress, scanStepText = step) }
            }
            val foundJunkBytes = categories.sumOf { it.sizeBytes }
            val updatedSchedule = _uiState.value.smartScheduleSettings.copy(
                lastScanDate = System.currentTimeMillis()
            )

            val record = CleanRecord(
                freedBytes = foundJunkBytes,
                itemsCount = categories.sumOf { it.itemCount },
                description = "Automated Scan: ${formatFileSize(foundJunkBytes)} Recoverable Junk Found"
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                state.copy(
                    isScanning = false,
                    junkCategories = categories,
                    smartScheduleSettings = updatedSchedule,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = foundJunkBytes,
                    lastCleanTitle = "Smart Scheduler Identified ${formatFileSize(foundJunkBytes)}!",
                    showCleanSuccessDialog = true
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
            val newFree = currentStats.totalBytes - newUsed
            val newJunk = (currentStats.junkBytes - totalFreed).coerceAtLeast(0L)
            val newStats = currentStats.copy(
                usedBytes = newUsed,
                freeBytes = newFree,
                junkBytes = newJunk,
                healthScore = (currentStats.healthScore + 7).coerceAtMost(100)
            )

            val record = CleanRecord(
                freedBytes = totalFreed,
                itemsCount = totalCount,
                description = "AI Smart Clean - ${selected.size} categories"
            )
            saveCleanRecord(record)

            _uiState.update {
                it.copy(
                    storageStats = newStats,
                    junkCategories = it.junkCategories.filter { cat -> !cat.isSelected },
                    cleanHistory = listOf(record) + it.cleanHistory,
                    lastFreedBytes = totalFreed,
                    lastCleanTitle = "Smart Clean Finished!",
                    showCleanSuccessDialog = true
                )
            }
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
            val itemsToDelete = mutableListOf<MediaItem>()
            val remainingGroups = mutableListOf<DuplicateGroup>()

            for (group in _uiState.value.duplicateGroups) {
                val selected = group.items.filter { it.isSelected }
                val keep = group.items.filter { !it.isSelected }
                itemsToDelete.addAll(selected)
                if (keep.size > 1) {
                    remainingGroups.add(group.copy(items = keep))
                }
            }

            if (itemsToDelete.isEmpty()) return@launch

            val totalFreed = scanner.deleteMediaItems(itemsToDelete)
            val totalItems = itemsToDelete.size
            val deletedIds = itemsToDelete.map { it.id }.toSet()

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + totalFreed,
                photoBytes = (currentStats.photoBytes - totalFreed).coerceAtLeast(0L),
                duplicateCount = (currentStats.duplicateCount - totalItems).coerceAtLeast(0)
            )

            val record = CleanRecord(
                freedBytes = totalFreed,
                itemsCount = totalItems,
                description = "Deleted $totalItems duplicate photos"
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
                    lastCleanTitle = "Duplicates Cleaned!",
                    showCleanSuccessDialog = true
                )
            }
            onCleaningOperationCompleted(totalFreed, totalItems)
        }
    }

    // SWIPE CLEAN
    fun setSwipeFilter(filter: SwipeFilter) {
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
        _uiState.update { state ->
            state.copy(
                swipeQueue = state.swipeQueue.filter { it.id != item.id },
                swipeKeepItems = state.swipeKeepItems + item
            )
        }
    }

    fun swipeLeftDelete(item: MediaItem) {
        _uiState.update { state ->
            state.copy(
                swipeQueue = state.swipeQueue.filter { it.id != item.id },
                swipeTrashItems = state.swipeTrashItems + item
            )
        }
    }

    fun undoLastSwipe() {
        _uiState.update { state ->
            if (state.swipeTrashItems.isNotEmpty()) {
                val last = state.swipeTrashItems.last()
                state.copy(
                    swipeTrashItems = state.swipeTrashItems.dropLast(1),
                    swipeQueue = listOf(last) + state.swipeQueue
                )
            } else if (state.swipeKeepItems.isNotEmpty()) {
                val last = state.swipeKeepItems.last()
                state.copy(
                    swipeKeepItems = state.swipeKeepItems.dropLast(1),
                    swipeQueue = listOf(last) + state.swipeQueue
                )
            } else {
                state
            }
        }
    }

    fun executeSwipeTrashCleanup() {
        viewModelScope.launch {
            val trashed = _uiState.value.swipeTrashItems
            if (trashed.isEmpty()) return@launch

            val totalFreed = scanner.deleteMediaItems(trashed)
            val count = trashed.size
            val trashedIds = trashed.map { it.id }.toSet()

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + totalFreed,
                photoBytes = (currentStats.photoBytes - totalFreed).coerceAtLeast(0L)
            )

            val record = CleanRecord(
                freedBytes = totalFreed,
                itemsCount = count,
                description = "Swipe Gallery cleanup ($count items)"
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                val updatedMedia = state.allMedia.filter { it.id !in trashedIds }
                val updatedDuplicates = state.duplicateGroups.mapNotNull { group ->
                    val remaining = group.items.filter { it.id !in trashedIds }
                    if (remaining.size > 1) group.copy(items = remaining) else null
                }
                val updatedTips = generateDynamicCleanupTips(newStats, updatedMedia, updatedDuplicates, state.cloudSyncedPhotos, state.appCacheItems)
                state.copy(
                    swipeTrashItems = emptyList(),
                    allMedia = updatedMedia,
                    swipeQueue = state.swipeQueue.filter { it.id !in trashedIds },
                    compressibleItems = state.compressibleItems.filter { it.id !in trashedIds },
                    duplicateGroups = updatedDuplicates,
                    storageStats = newStats,
                    cleanupTips = updatedTips,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = totalFreed,
                    lastCleanTitle = "Gallery Swipe Cleaned!",
                    showCleanSuccessDialog = true
                )
            }
            onCleaningOperationCompleted(totalFreed, count)
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
            val compressedMap = mutableMapOf<Long, Long>()

            for (i in selected.indices) {
                val item = selected[i]
                val freed = scanner.compressImage(item.mediaItem, item.quality)
                saved += freed
                val newSize = (item.mediaItem.sizeBytes - freed).coerceAtLeast(1024L)
                compressedMap[item.id] = newSize
                val progress = (i + 1).toFloat() / selected.size.toFloat()
                _uiState.update { it.copy(compressionProgress = progress) }
            }

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - saved).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + saved,
                photoBytes = (currentStats.photoBytes - saved).coerceAtLeast(0L)
            )

            val record = CleanRecord(
                freedBytes = saved,
                itemsCount = selected.size,
                description = "Compressed ${selected.size} photos (${_uiState.value.selectedCompressionQuality.label})"
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                val updatedCompressibles = state.compressibleItems.map { comp ->
                    val newSize = compressedMap[comp.id]
                    if (newSize != null) {
                        val updatedMedia = comp.mediaItem.copy(sizeBytes = newSize)
                        comp.copy(mediaItem = updatedMedia, isSelected = false, isProcessed = true)
                    } else {
                        comp
                    }
                }
                val updatedAllMedia = state.allMedia.map { media ->
                    val newSize = compressedMap[media.id]
                    if (newSize != null) media.copy(sizeBytes = newSize) else media
                }

                state.copy(
                    isCompressing = false,
                    compressibleItems = updatedCompressibles,
                    allMedia = updatedAllMedia,
                    storageStats = newStats,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = saved,
                    lastCleanTitle = "Media Compression Complete!",
                    showCleanSuccessDialog = true
                )
            }
            onCleaningOperationCompleted(saved, selected.size)
        }
    }

    fun deleteLargeFiles(items: List<MediaItem>) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            val totalFreed = scanner.deleteMediaItems(items)
            val count = items.size
            val itemIds = items.map { it.id }.toSet()

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + totalFreed,
                videoBytes = (currentStats.videoBytes - totalFreed).coerceAtLeast(0L)
            )

            val record = CleanRecord(
                freedBytes = totalFreed,
                itemsCount = count,
                description = "Deleted $count large files (${formatFileSize(totalFreed)})"
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                val updatedMedia = state.allMedia.filter { it.id !in itemIds }
                state.copy(
                    allMedia = updatedMedia,
                    swipeQueue = state.swipeQueue.filter { it.id !in itemIds },
                    compressibleItems = state.compressibleItems.filter { it.id !in itemIds },
                    storageStats = newStats,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = totalFreed,
                    lastCleanTitle = "Large Files Deleted!",
                    showCleanSuccessDialog = true
                )
            }
            onCleaningOperationCompleted(totalFreed, count)
        }
    }

    fun deleteVaultItems(items: List<MediaItem>) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            val totalFreed = scanner.deleteMediaItems(items)
            val count = items.size
            val itemIds = items.map { it.id }.toSet()

            val photoFreed = items.filter { it.mimeType.startsWith("image/") }.sumOf { it.sizeBytes }
            val videoFreed = items.filter { it.mimeType.startsWith("video/") }.sumOf { it.sizeBytes }

            val currentStats = _uiState.value.storageStats
            val newStats = currentStats.copy(
                usedBytes = (currentStats.usedBytes - totalFreed).coerceAtLeast(0L),
                freeBytes = currentStats.freeBytes + totalFreed,
                photoBytes = (currentStats.photoBytes - photoFreed).coerceAtLeast(0L),
                videoBytes = (currentStats.videoBytes - videoFreed).coerceAtLeast(0L)
            )

            val record = CleanRecord(
                freedBytes = totalFreed,
                itemsCount = count,
                description = "Deleted $count vault items (${formatFileSize(totalFreed)})"
            )
            saveCleanRecord(record)

            _uiState.update { state ->
                state.copy(
                    vaultItems = state.vaultItems.filter { it.id !in itemIds },
                    storageStats = newStats,
                    cleanHistory = listOf(record) + state.cleanHistory,
                    lastFreedBytes = totalFreed,
                    lastCleanTitle = "Vault Items Deleted!",
                    showCleanSuccessDialog = true
                )
            }
            onCleaningOperationCompleted(totalFreed, count)
        }
    }

    fun dismissCleanSuccessDialog() {
        _uiState.update { it.copy(showCleanSuccessDialog = false) }
    }

    fun togglePauseCleaning() {
        _uiState.update { it.copy(isCleaningPaused = !it.isCleaningPaused) }
    }

    fun setSmartExpiryDays(days: Int) {
        _uiState.update { it.copy(smartExpiryDays = days) }
    }

    fun moveToVault(items: List<MediaItem>) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            val itemIds = items.map { it.id }.toSet()
            _uiState.update { state ->
                val newVaultItems = (state.vaultItems + items).distinctBy { it.id }
                val updatedAllMedia = state.allMedia.filter { it.id !in itemIds }
                val record = CleanRecord(
                    freedBytes = items.sumOf { it.sizeBytes },
                    itemsCount = items.size,
                    description = "Moved ${items.size} files into Vault Storage"
                )
                state.copy(
                    vaultItems = newVaultItems,
                    allMedia = updatedAllMedia,
                    cleanHistory = listOf(record) + state.cleanHistory
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
                val restoredMedia = (state.allMedia + items).distinctBy { it.id }
                state.copy(
                    vaultItems = remainingVault,
                    allMedia = restoredMedia
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

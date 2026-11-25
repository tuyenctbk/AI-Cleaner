package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.model.StorageStats
import com.example.widget.StorageGlanceWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageGlanceWidgetTest {

    @Test
    fun storageStats_calculatesUsagePercentageCorrectly() {
        val total = 128L * 1024L * 1024L * 1024L
        val used = (total * 0.68).toLong()
        val free = total - used

        val stats = StorageStats(
            totalBytes = total,
            usedBytes = used,
            freeBytes = free
        )

        assertEquals(68, stats.usedPercent)
        assertEquals(0.68f, stats.usedPercentage, 0.01f)
    }

    @Test
    fun storageStats_handlesZeroTotalSafely() {
        val stats = StorageStats(
            totalBytes = 0L,
            usedBytes = 0L,
            freeBytes = 0L
        )

        assertEquals(0, stats.usedPercent)
        assertEquals(0f, stats.usedPercentage, 0.001f)
    }

    @Test
    fun buildQuickCleanupShortcutIntent_targetsMainActivityAndQuickCleanupScreen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = StorageGlanceWidget.buildQuickCleanupShortcutIntent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(StorageGlanceWidget.ACTION_OPEN_QUICK_CLEANUP, intent.action)
        assertTrue(intent.getBooleanExtra(StorageGlanceWidget.EXTRA_OPEN_QUICK_CLEANUP, false))
        assertTrue(intent.getBooleanExtra(StorageGlanceWidget.EXTRA_QUICK_CLEAN, false))
        assertEquals(
            StorageGlanceWidget.TARGET_TAB_QUICK_CLEANUP,
            intent.getStringExtra(StorageGlanceWidget.EXTRA_TARGET_TAB)
        )
    }

    @Test
    fun buildOpenAppIntent_targetsMainActivityWithoutQuickCleanupFlag() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = StorageGlanceWidget.buildOpenAppIntent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(
            false,
            intent.getBooleanExtra(StorageGlanceWidget.EXTRA_OPEN_QUICK_CLEANUP, false)
        )
    }
}

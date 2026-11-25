package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.data.model.StorageStats
import com.example.data.scanner.StorageScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Jetpack Glance Home Screen Widget displaying real-time free device storage
 * and a 1-tap "Quick Clean" action that launches AI Cleaner's primary optimization flow.
 */
class StorageGlanceWidget : GlanceAppWidget() {

    companion object {
        const val EXTRA_QUICK_CLEAN = "EXTRA_QUICK_CLEAN"
        const val EXTRA_OPEN_QUICK_CLEANUP = "EXTRA_OPEN_QUICK_CLEANUP"
        const val EXTRA_TARGET_TAB = "EXTRA_TARGET_TAB"
        const val TARGET_TAB_QUICK_CLEANUP = "QUICK_CLEANUP"
        const val ACTION_QUICK_CLEAN = "com.soloprono.aicleaner.ACTION_QUICK_CLEAN"
        const val ACTION_OPEN_QUICK_CLEANUP = "com.soloprono.aicleaner.ACTION_OPEN_QUICK_CLEANUP"

        private val COMPACT_SIZE = DpSize(180.dp, 100.dp)
        private val MEDIUM_SIZE = DpSize(250.dp, 110.dp)
        private val EXPANDED_SIZE = DpSize(300.dp, 160.dp)

        fun buildOpenAppIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }

        fun buildQuickCleanupShortcutIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_QUICK_CLEANUP
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_QUICK_CLEANUP, true)
                putExtra(EXTRA_QUICK_CLEAN, true)
                putExtra(EXTRA_TARGET_TAB, TARGET_TAB_QUICK_CLEANUP)
            }
        }

        suspend fun updateAllWidgets(context: Context) {
            try {
                StorageGlanceWidget().updateAll(context.applicationContext)
            } catch (_: Exception) {
                // Safely ignore when no widget instances are pinned
            }
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, MEDIUM_SIZE, EXPANDED_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stats = withContext(Dispatchers.IO) {
            StorageScanner.getQuickStorageStats(context)
        }

        provideContent {
            GlanceTheme {
                StorageGlanceContent(stats = stats)
            }
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        val fallbackViews = RemoteViews(context.packageName, R.layout.widget_storage_layout)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallbackViews)
    }
}

@Composable
private fun StorageGlanceContent(stats: StorageStats) {
    val context = LocalContext.current
    val size = LocalSize.current
    val isCompact = size.width < 220.dp
    val isTall = size.height >= 145.dp

    val openAppIntent = StorageGlanceWidget.buildOpenAppIntent(context)
    val quickCleanupIntent = StorageGlanceWidget.buildQuickCleanupShortcutIntent(context)

    val usedPercentClamped = stats.usedPercent.coerceIn(0, 100)
    val progressAccent = when {
        usedPercentClamped >= 90 -> ColorProvider(Color(0xFFEF4444))
        usedPercentClamped >= 75 -> ColorProvider(Color(0xFFF59E0B))
        else -> GlanceTheme.colors.primary
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity(openAppIntent))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Header Row: Brand icon + App name + Free space pill
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.mipmap.ic_launcher_round),
                contentDescription = context.getString(R.string.app_name),
                modifier = GlanceModifier.size(22.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${stats.formattedFree} • ${context.getString(R.string.widget_free_space_label)}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(if (isTall) 10.dp else 6.dp))

        // Main Storage Usage Percentage Metric + Direct Quick Cleanup Shortcut
        if (isCompact) {
            Text(
                text = context.getString(R.string.stat_used_percent, usedPercentClamped),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = context.getString(
                    R.string.stat_used_of_total,
                    stats.formattedUsed,
                    stats.formattedTotal
                ),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = stats.usedPercentage.coerceIn(0f, 1f),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = progressAccent,
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Button(
                text = context.getString(R.string.title_quick_cleanup),
                onClick = actionStartActivity(quickCleanupIntent),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = GlanceTheme.colors.primary,
                    contentColor = GlanceTheme.colors.onPrimary
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = context.getString(R.string.stat_used_percent, usedPercentClamped),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = if (isTall) 26.sp else 22.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = context.getString(
                            R.string.stat_used_of_total,
                            stats.formattedUsed,
                            stats.formattedTotal
                        ),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                Button(
                    text = context.getString(R.string.title_quick_cleanup),
                    onClick = actionStartActivity(quickCleanupIntent),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = GlanceTheme.colors.primary,
                        contentColor = GlanceTheme.colors.onPrimary
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(if (isTall) 12.dp else 8.dp))

            LinearProgressIndicator(
                progress = stats.usedPercentage.coerceIn(0f, 1f),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = progressAccent,
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )
        }
    }
}

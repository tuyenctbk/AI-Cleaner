package com.example.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * GlanceAppWidgetReceiver binding [StorageGlanceWidget] to the Android home screen widget entry.
 * Preserves the manifest receiver class name so existing pinned widgets upgrade seamlessly.
 */
class StorageAppWidgetProvider : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = StorageGlanceWidget()

    companion object {
        fun sendUpdateBroadcast(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                StorageGlanceWidget.updateAllWidgets(context.applicationContext)
            }
        }
    }
}

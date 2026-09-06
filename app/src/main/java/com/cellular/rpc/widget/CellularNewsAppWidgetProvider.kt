package com.cellular.rpc.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.engine.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CellularNewsAppWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, CellularNewsAppWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isNotEmpty()) {
                val intent = Intent(context, CellularNewsAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val cachedNews = db.widgetCacheDao().getWidgetByType("news_digest")
            val parsedNews = cachedNews?.let { WidgetData.parse(it.jsonPayload) as? WidgetData.NewsDigest }
                ?: WidgetData.NewsDigest(
                    id = "N1",
                    headline = "Cellular RPC Protocol Deployed Over Low-Band SMS",
                    summary = "Zero-data operating layer delivers live home-screen updates in dead zones.",
                    source = "PALLY NET"
                )

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_news)

                // 1. Transparency & Background Styling
                val bgColor = WidgetPreferences.getBackgroundColorArgb(context, appWidgetId)
                views.setInt(R.id.widget_news_root, "setBackgroundColor", bgColor)

                // 2. Topic Category & Content
                val configuredTopic = WidgetPreferences.getNewsTopic(context, appWidgetId)
                views.setTextViewText(R.id.widget_news_category, configuredTopic.uppercase())
                views.setTextViewText(R.id.widget_news_headline, parsedNews.headline)
                views.setTextViewText(R.id.widget_news_summary, parsedNews.summary)

                // 3. Status
                val statusText = if (cachedNews?.lastStatus == "304_NOT_MODIFIED") {
                    "Pally SMS • 304 Cache Hit (0B)"
                } else {
                    "Pally SMS • ${parsedNews.source}"
                }
                views.setTextViewText(R.id.widget_news_status, statusText)
                views.setTextViewText(R.id.widget_news_updated, timeStr)

                // 4. Settings Intent
                val settingsIntent = Intent(context, WidgetConfigurationActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(WidgetConfigurationActivity.EXTRA_CONFIG_TYPE, "news")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val settingsPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 3,
                    settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_news_btn_settings, settingsPendingIntent)

                // 5. Refresh Intent
                val refreshIntent = Intent(context, PullBroadcastReceiver::class.java).apply {
                    action = PullBroadcastReceiver.ACTION_WIDGET_MANUAL_REFRESH
                    putExtra(PullBroadcastReceiver.EXTRA_WIDGET_TYPE, "news")
                    putExtra(PullBroadcastReceiver.EXTRA_APP_WIDGET_ID, appWidgetId)
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 4,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_news_btn_refresh, refreshPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}

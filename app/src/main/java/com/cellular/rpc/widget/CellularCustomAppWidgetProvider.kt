package com.cellular.rpc.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.cellular.rpc.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Universal Custom AI Home Screen Widget Provider.
 * Safely renders customized metrics and state from dynamic micro-apps/tools without altering the native Weather & News widgets.
 */
class CellularCustomAppWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val PREFS_CUSTOM_FEATURE_ID = "custom_feature_id_"
        private const val PREFS_CUSTOM_METRIC_KEY = "custom_metric_key_"
        private const val PREFS_CUSTOM_TITLE = "custom_title_"

        fun setCustomWidgetConfig(context: Context, appWidgetId: Int, featureId: String, title: String, metricKey: String) {
            val prefs = context.getSharedPreferences("cellular_widget_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREFS_CUSTOM_FEATURE_ID + appWidgetId, featureId)
                .putString(PREFS_CUSTOM_TITLE + appWidgetId, title)
                .putString(PREFS_CUSTOM_METRIC_KEY + appWidgetId, metricKey)
                .apply()
        }

        fun getCustomFeatureId(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences("cellular_widget_prefs", Context.MODE_PRIVATE)
            return prefs.getString(PREFS_CUSTOM_FEATURE_ID + appWidgetId, "solar_estimator") ?: "solar_estimator"
        }

        fun getCustomTitle(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences("cellular_widget_prefs", Context.MODE_PRIVATE)
            return prefs.getString(PREFS_CUSTOM_TITLE + appWidgetId, "Solar Array Estimator") ?: "Solar Array Estimator"
        }

        fun getCustomMetricKey(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences("cellular_widget_prefs", Context.MODE_PRIVATE)
            return prefs.getString(PREFS_CUSTOM_METRIC_KEY + appWidgetId, "result") ?: "result"
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, CellularCustomAppWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isNotEmpty()) {
                val intent = Intent(context, CellularCustomAppWidgetProvider::class.java).apply {
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
            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_custom_ai)

                // 1. Transparency & Background Styling
                val bgColor = WidgetPreferences.getBackgroundColorArgb(context, appWidgetId)
                views.setInt(R.id.widget_custom_root, "setBackgroundColor", bgColor)

                // 2. Custom Binding
                val featureId = getCustomFeatureId(context, appWidgetId)
                val configuredTitle = getCustomTitle(context, appWidgetId)
                val metricKey = getCustomMetricKey(context, appWidgetId)

                val feature = db.dynamicFeatureDao().getFeatureById(featureId)
                val title = feature?.title ?: configuredTitle
                val stateJson = feature?.currentStateJson ?: "{}"

                var metricDisplay = "Ready"
                var bodyText = feature?.description ?: "Offline micro-app telemetry"

                try {
                    val jsonObj = JSONObject(stateJson)
                    if (jsonObj.has(metricKey)) {
                        metricDisplay = jsonObj.get(metricKey).toString()
                    } else if (jsonObj.length() > 0) {
                        val firstKey = jsonObj.keys().next()
                        metricDisplay = "$firstKey: ${jsonObj.get(firstKey)}"
                    }
                } catch (e: Exception) {
                    metricDisplay = "Active"
                }

                views.setTextViewText(R.id.widget_custom_title, title)
                views.setTextViewText(R.id.widget_custom_primary_metric, metricDisplay)
                views.setTextViewText(R.id.widget_custom_body, bodyText)
                views.setTextViewText(R.id.widget_custom_feature_id, featureId.uppercase(Locale.getDefault()))
                views.setTextViewText(R.id.widget_custom_updated_time, timeStr)

                // 3. Settings Click
                val settingsIntent = Intent(context, WidgetConfigurationActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(WidgetConfigurationActivity.EXTRA_WIDGET_TYPE, "custom")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val settingsPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId + 5000,
                    settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_custom_btn_settings, settingsPendingIntent)

                // 4. Refresh Button
                val refreshIntent = Intent(context, PullBroadcastReceiver::class.java).apply {
                    action = PullBroadcastReceiver.ACTION_WIDGET_MANUAL_REFRESH
                    putExtra(PullBroadcastReceiver.EXTRA_WIDGET_TYPE, "custom")
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId + 6000,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_custom_btn_refresh, refreshPendingIntent)

                // 5. Open App on root click
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val mainPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId + 7000,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_custom_root, mainPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}

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

class CellularWeatherAppWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, CellularWeatherAppWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isNotEmpty()) {
                val intent = Intent(context, CellularWeatherAppWidgetProvider::class.java).apply {
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
            val cachedWeather = db.widgetCacheDao().getWidgetByType("weather")
            val parsedWeather = cachedWeather?.let { WidgetData.parse(it.jsonPayload) as? WidgetData.Weather }
                ?: WidgetData.Weather(temp = 72, city = "San Francisco", cond = "Sunny", high = 76, low = 58)

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_weather)

                // 1. Transparency & Background Styling
                val bgColor = WidgetPreferences.getBackgroundColorArgb(context, appWidgetId)
                views.setInt(R.id.widget_weather_root, "setBackgroundColor", bgColor)

                // 2. City & Temperature Units
                val configuredCity = WidgetPreferences.getWeatherCity(context, appWidgetId)
                val unit = WidgetPreferences.getWeatherUnit(context, appWidgetId)
                val displayTemp = if (unit == "C") {
                    ((parsedWeather.temp - 32) * 5 / 9)
                } else {
                    parsedWeather.temp
                }

                views.setTextViewText(R.id.widget_weather_city, configuredCity)
                views.setTextViewText(R.id.widget_weather_temp, "$displayTemp°")
                views.setTextViewText(R.id.widget_weather_condition, parsedWeather.cond)
                views.setTextViewText(R.id.widget_weather_high_low, "H: ${parsedWeather.high}°  L: ${parsedWeather.low}°")

                // 3. Status
                val statusText = if (cachedWeather?.lastStatus == "304_NOT_MODIFIED") {
                    "Pally SMS • 304 Not Modified (0B)"
                } else {
                    "Pally SMS • Port 8901 Sync"
                }
                views.setTextViewText(R.id.widget_weather_status, statusText)
                views.setTextViewText(R.id.widget_weather_updated, timeStr)

                // 4. Settings Button Intent
                val settingsIntent = Intent(context, WidgetConfigurationActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(WidgetConfigurationActivity.EXTRA_CONFIG_TYPE, "weather")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val settingsPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 1,
                    settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_weather_btn_settings, settingsPendingIntent)

                // 5. Refresh Button Intent (Pull via SMS)
                val refreshIntent = Intent(context, PullBroadcastReceiver::class.java).apply {
                    action = PullBroadcastReceiver.ACTION_WIDGET_MANUAL_REFRESH
                    putExtra(PullBroadcastReceiver.EXTRA_WIDGET_TYPE, "weather")
                    putExtra(PullBroadcastReceiver.EXTRA_APP_WIDGET_ID, appWidgetId)
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 2,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_weather_btn_refresh, refreshPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}

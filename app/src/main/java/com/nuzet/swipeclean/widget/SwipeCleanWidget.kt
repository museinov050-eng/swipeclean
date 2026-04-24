package com.nuzet.swipeclean.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.nuzet.swipeclean.MainActivity
import com.nuzet.swipeclean.PhotoRepository
import com.nuzet.swipeclean.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwipeCleanWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.nuzet.swipeclean.ACTION_REFRESH_WIDGET"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SwipeCleanWidget::class.java)
            )
            ids.forEach { id ->
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_swipe_clean)

            // Tap opens app
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
            val launchPi = PendingIntent.getActivity(context, 0, launchIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_root, launchPi)

            // Refresh button
            val refreshIntent = Intent(context, SwipeCleanWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPi = PendingIntent.getBroadcast(context, 0, refreshIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)

            // Load bitmap async (widget update must push once bitmap ready)
            CoroutineScope(Dispatchers.IO).launch {
                val uri: Uri? = PhotoRepository.getRandomPhotoUri(context)
                val bitmap: Bitmap? = uri?.let { loadBitmap(context, it) }
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_image, bitmap)
                        views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
                    } else {
                        views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                    }
                    manager.updateAppWidget(widgetId, views)
                }
            }

            // Initial push (loading state)
            manager.updateAppWidget(widgetId, views)
        }

        private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
            return try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(input, null, opts)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

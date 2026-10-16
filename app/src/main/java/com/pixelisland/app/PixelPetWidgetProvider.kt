package com.pixelisland.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews

/**
 * Pixel Pet Widget Provider для главного экрана Android (аналог Pixel Pals iOS Widgets).
 * Отображает живого 3D-питомца прямо на рабочем столе с кнопками кормления и ласки!
 */
class PixelPetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_FEED -> {
                IslandStateBus.feedPet()
                FirebaseCoupleManager.sendPartnerAction("FEED")
                updateAllWidgets(context)
            }
            ACTION_LOVE -> {
                IslandStateBus.playWithPet()
                FirebaseCoupleManager.sendPartnerAction("HEART")
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_FEED = "com.pixelisland.app.ACTION_WIDGET_FEED"
        const val ACTION_LOVE = "com.pixelisland.app.ACTION_WIDGET_LOVE"

        fun updateAllWidgets(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val component = ComponentName(context, PixelPetWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids != null && ids.isNotEmpty()) {
                    for (id in ids) {
                        updateWidget(context, manager, id)
                    }
                }
            } catch (ignored: Exception) {
            }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val state = IslandStateBus.state.value
            val views = RemoteViews(context.packageName, R.layout.pixel_pet_widget)

            val emoji = when (state.species) {
                PetSpecies.CAT -> "🐱"
                PetSpecies.DOG -> "🐶"
                PetSpecies.FOX -> "🦊"
                PetSpecies.DUCK -> "🦆"
                PetSpecies.PANDA -> "🐼"
            }

            views.setTextViewText(R.id.widget_pet_name, "$emoji ${state.petName}")

            val heartsCount = ((state.mood / 20).coerceIn(1, 5))
            val heartsStr = "❤️".repeat(heartsCount)
            views.setTextViewText(R.id.widget_pet_level, "Lv.${state.petLevel} $heartsStr")

            val statsStr = "🍖 ${state.hunger}% • ⚡ ${state.energy}% • 💖 ${state.mood}%"
            views.setTextViewText(R.id.widget_pet_stats, statsStr)

            // Генерируем красивый Bitmap зверька для виджета
            val bitmap = createPetBitmap(context, state)
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_pet_image, bitmap)
            }

            // Тап по питомцу -> открыть приложение
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pMain = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            views.setOnClickPendingIntent(R.id.widget_pet_image, pMain)
            views.setOnClickPendingIntent(R.id.widget_root, pMain)

            // Кнопка [ 🍎 Корм ]
            val feedIntent = Intent(context, PixelPetWidgetProvider::class.java).apply {
                action = ACTION_FEED
            }
            val pFeed = PendingIntent.getBroadcast(
                context,
                1,
                feedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            views.setOnClickPendingIntent(R.id.widget_btn_feed, pFeed)

            // Кнопка [ ❤️ Гладь ]
            val loveIntent = Intent(context, PixelPetWidgetProvider::class.java).apply {
                action = ACTION_LOVE
            }
            val pLove = PendingIntent.getBroadcast(
                context,
                2,
                loveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            views.setOnClickPendingIntent(R.id.widget_btn_love, pLove)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createPetBitmap(context: Context, state: IslandState): Bitmap? {
            return try {
                val w = 220
                val h = 180
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.TRANSPARENT)

                val renderer = PixelPetRenderer(context)
                val rect = RectF(16f, 16f, (w - 16).toFloat(), (h - 16).toFloat())
                renderer.drawPet(
                    canvas = canvas,
                    dstRect = rect,
                    mode = state.petMode,
                    frameIndex = 0,
                    isWalking = false,
                    isEmittingHearts = false
                )
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }
}

package com.pixelisland.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

object RewardedAdManager {

    private const val PREFS_NAME = "pixel_island_prefs"
    private const val KEY_ADS_WATCHED = "ads_watched_count"
    private const val KEY_CUSTOM_PET_EXPIRE = "custom_pet_expire_ms"
    private const val TWELVE_HOURS_MS = 12L * 60L * 60L * 1000L

    fun initFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val adsCount = prefs.getInt(KEY_ADS_WATCHED, 0)
        val expireMs = prefs.getLong(KEY_CUSTOM_PET_EXPIRE, 0L)
        IslandStateBus.updateCustomPetAuth(adsCount, expireMs)
    }

    fun isCustomPetActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expireMs = prefs.getLong(KEY_CUSTOM_PET_EXPIRE, 0L)
        return System.currentTimeMillis() < expireMs
    }

    fun getRemainingTimeFormatted(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expireMs = prefs.getLong(KEY_CUSTOM_PET_EXPIRE, 0L)
        val diff = expireMs - System.currentTimeMillis()
        if (diff <= 0L) {
            return "Не активно (нужно 2 рекламы)"
        }
        val hours = diff / (1000L * 60L * 60L)
        val minutes = (diff % (1000L * 60L * 60L)) / (1000L * 60L)
        return "Активно: ${hours}ч ${minutes}м"
    }

    fun showRewardedAd(activity: Activity, onStateUpdated: () -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(32), dp(24), dp(32))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1E1E"))
                cornerRadius = dp(16).toFloat()
            }
        }

        val titleView = TextView(activity).apply {
            text = "🎬 Рекламная пауза"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        container.addView(titleView)

        val progressBar = ProgressBar(activity).apply {
            isIndeterminate = true
            setPadding(0, 0, 0, dp(16))
        }
        container.addView(progressBar)

        val countdownView = TextView(activity).apply {
            text = "Просмотр рекламы... Осталось 5 сек"
            textSize = 15f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        }
        container.addView(countdownView)

        val dialog = AlertDialog.Builder(activity)
            .setView(container)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val handler = Handler(Looper.getMainLooper())
        var remainingSeconds = 5

        val countdownRunnable = object : Runnable {
            override fun run() {
                remainingSeconds--
                if (remainingSeconds > 0) {
                    countdownView.text = "Просмотр рекламы... Осталось $remainingSeconds сек"
                    handler.postDelayed(this, 1000L)
                } else {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        try {
                            dialog.dismiss()
                        } catch (ignored: Exception) {
                        }
                    }

                    val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val currentCount = prefs.getInt(KEY_ADS_WATCHED, 0) + 1

                    if (currentCount >= 2) {
                        val newExpire = System.currentTimeMillis() + TWELVE_HOURS_MS
                        prefs.edit()
                            .putInt(KEY_ADS_WATCHED, 0)
                            .putLong(KEY_CUSTOM_PET_EXPIRE, newExpire)
                            .apply()
                        IslandStateBus.updateCustomPetAuth(0, newExpire)
                        Toast.makeText(
                            activity,
                            "Свой питомец разблокирован на 12 часов!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val expireMs = prefs.getLong(KEY_CUSTOM_PET_EXPIRE, 0L)
                        prefs.edit()
                            .putInt(KEY_ADS_WATCHED, 1)
                            .apply()
                        IslandStateBus.updateCustomPetAuth(1, expireMs)
                        Toast.makeText(
                            activity,
                            "Реклама 1 из 2 просмотрена! Посмотрите еще 1 для активации на 12 часов",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    onStateUpdated()
                }
            }
        }

        handler.postDelayed(countdownRunnable, 1000L)
    }
}

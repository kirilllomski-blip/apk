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
    private const val KEY_PRO_LIFETIME = "is_pro_lifetime"
    private const val KEY_SHORT_DEVICE_ID = "short_device_id"
    private const val TWELVE_HOURS_MS = 12L * 60L * 60L * 1000L

    fun initFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isPro = prefs.getBoolean(KEY_PRO_LIFETIME, false)
        if (isPro) {
            IslandStateBus.setProLifetime(true)
        }
        val adsCount = prefs.getInt(KEY_ADS_WATCHED, 0)
        val expireMs = prefs.getLong(KEY_CUSTOM_PET_EXPIRE, 0L)
        IslandStateBus.updateCustomPetAuth(adsCount, expireMs)
    }

    fun isProUnlocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PRO_LIFETIME, false)
    }

    fun isCustomPetActive(context: Context): Boolean {
        if (isProUnlocked(context)) {
            return true
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expireMs = prefs.getLong(KEY_CUSTOM_PET_EXPIRE, 0L)
        return System.currentTimeMillis() < expireMs
    }

    fun getRemainingTimeFormatted(context: Context): String {
        if (isProUnlocked(context)) {
            return "👑 PRO АКТИВЕН НАВСЕГДА"
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expireMs = prefs.getLong(KEY_CUSTOM_PET_EXPIRE, 0L)
        val diff = expireMs - System.currentTimeMillis()
        if (diff <= 0L) {
            return "Не активно (нужно 2 рекламы или PRO-код)"
        }
        val hours = diff / (1000L * 60L * 60L)
        val minutes = (diff % (1000L * 60L * 60L)) / (1000L * 60L)
        return "Активно: ${hours}ч ${minutes}м"
    }

    fun getShortDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var devId = prefs.getString(KEY_SHORT_DEVICE_ID, null)
        if (devId.isNullOrBlank()) {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            devId = (1..4).map { chars.random() }.joinToString("")
            prefs.edit().putString(KEY_SHORT_DEVICE_ID, devId).apply()
        }
        return devId
    }

    fun generateProKeyForDevice(deviceId: String): String {
        val cleanId = deviceId.trim().uppercase()
        val secret = ((cleanId.hashCode() and 0x7FFFFFFF) % 8999) + 1000
        return "PRO-$cleanId-$secret"
    }

    fun redeemKey(context: Context, inputKey: String): Pair<Boolean, String> {
        val key = inputKey.trim().uppercase()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Бесплатный промокод из Telegram-канала на 12 часов
        if (key == "TG12H") {
            val newExpire = System.currentTimeMillis() + TWELVE_HOURS_MS
            prefs.edit()
                .putLong(KEY_CUSTOM_PET_EXPIRE, newExpire)
                .putInt(KEY_ADS_WATCHED, 0)
                .apply()
            IslandStateBus.updateCustomPetAuth(0, newExpire)
            return true to "⚡️ Код из Telegram принят! Свой скин активен на 12 часов"
        }

        // 2. Вечный PRO-ключ (мастер-ключ или персональный для этого устройства)
        val myDeviceId = getShortDeviceId(context)
        val expectedKey = generateProKeyForDevice(myDeviceId)
        if (key == "CYBER-ADMIN-999" || key == expectedKey) {
            prefs.edit()
                .putBoolean(KEY_PRO_LIFETIME, true)
                .apply()
            IslandStateBus.setProLifetime(true)
            return true to "👑 Вечный PRO-доступ активирован навсегда!"
        }

        return false to "❌ Неверный ключ или он от другого телефона"
    }

    fun showAdForCustomPet(activity: Activity, onDone: (Boolean, String) -> Unit) {
        showRewardedAd(activity) {
            if (isCustomPetActive(activity)) {
                onDone(true, "Свой питомец разблокирован на 12 часов!")
            } else {
                onDone(false, "Реклама 1 из 2 просмотрена! Посмотрите еще 1 для активации.")
            }
        }
    }

    fun showRewardedAd(activity: Activity, onStateUpdated: () -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(32), dp(24), dp(32))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181D2A"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), Color.parseColor("#2A344B"))
            }
        }

        val titleView = TextView(activity).apply {
            text = "🎬 Рекламная пауза"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        }
        container.addView(titleView)

        val progressBar = ProgressBar(activity).apply {
            isIndeterminate = true
            setPadding(0, 0, 0, dp(14))
        }
        container.addView(progressBar)

        val countdownView = TextView(activity).apply {
            text = "Просмотр рекламы... Осталось 5 сек"
            textSize = 13.5f
            setTextColor(Color.parseColor("#00B8D4"))
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

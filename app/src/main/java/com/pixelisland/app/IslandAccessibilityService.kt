package com.pixelisland.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Сервис доступности Pixel Island:
 * 1. Отображает интерактивный оверлей островка поверх всех окон.
 * 2. Считывает наклон телефона через акселерометр для физики скольжения питомца.
 * 3. Авто-детектор Анти-Рилсов: непрерывно следит за текущим открытым приложением
 *    (TikTok, Instagram, YouTube Shorts, VK Клипы) и при превышении лимита времени
 *    вызывает предупреждение на островке с тройной вибрацией.
 */
class IslandAccessibilityService : AccessibilityService(), SensorEventListener {

    private var windowManager: WindowManager? = null
    private var overlayView: IslandOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var systemBroadcastReceiver: BroadcastReceiver? = null

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isSensorRegistered = false

    // ==================== АВТО-ДЕТЕКТОР АНТИ-РИЛСОВ ====================
    private var currentForegroundPackage: String = ""
    private var reelsWatchSeconds: Int = 0
    private val doomscrollHandler = Handler(Looper.getMainLooper())

    private val shortVideoKeywords = listOf(
        "musically",
        "tiktok",
        "instagram",
        "youtube",
        "vkontakte",
        "clips"
    )

    private val doomscrollTicker = object : Runnable {
        override fun run() {
            val state = IslandStateBus.state.value

            if (state.isScreenOn && isShortVideoPackage(currentForegroundPackage)) {
                reelsWatchSeconds += 5

                // Порог: "Тест 10 сек" (если doomscrollLimitMinutes <= 0) -> 10 сек, иначе minutes * 60
                val thresholdSeconds = if (state.doomscrollLimitMinutes <= 0) {
                    10
                } else {
                    state.doomscrollLimitMinutes * 60
                }

                if (reelsWatchSeconds >= thresholdSeconds) {
                    IslandStateBus.triggerDoomscrollWarning("🚨 Ты листаешь ленту уже слишком долго! Отложи телефон!")
                    performTripleVibration()
                    reelsWatchSeconds = 0
                }
            } else if (!isShortVideoPackage(currentForegroundPackage)) {
                // Если пользователь вышел из приложений с клипами/рилсами, сбрасываем счетчик
                reelsWatchSeconds = 0
            }

            doomscrollHandler.postDelayed(this, 5000L)
        }
    }

    private fun isShortVideoPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lower = pkg.lowercase()
        return shortVideoKeywords.any { lower.contains(it) }
    }

    private fun performTripleVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Тройной импульс: 200мс вибро, 120мс пауза, 200мс вибро, 120мс пауза, 300мс вибро
                    val timings = longArrayOf(0, 200, 120, 200, 120, 300)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 200, 120, 200, 120, 300), -1)
                }
            }
        } catch (ignored: Exception) {
        }
    }

    companion object {
        fun triggerTestDoomscroll() {
            IslandStateBus.triggerDoomscrollWarning("🚨 Ты листаешь ленту уже слишком долго! Отложи телефон!")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        RewardedAdManager.initFromPrefs(this)

        // Подключаем отслеживание смены окон и скролла для соцсетей
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = info.eventTypes or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
        serviceInfo = info

        // Запуск 5-секундного таймера Анти-Рилсов
        doomscrollHandler.removeCallbacks(doomscrollTicker)
        doomscrollHandler.postDelayed(doomscrollTicker, 5000L)

        // Загрузка сохраненных настроек калибровки и комнаты
        val prefs = getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
        val offsetX = prefs.getInt("offset_x", 0)
        val offsetY = prefs.getInt("offset_y", 0)
        val scale = prefs.getFloat("island_scale", 1.0f)
        val dbUrl = prefs.getString("database_url", "") ?: ""
        val roomCode = prefs.getString("room_code", "") ?: ""

        IslandStateBus.updateCalibration(offsetX, offsetY, scale)
        if (roomCode.isNotBlank()) {
            IslandStateBus.setRoomCode(roomCode)
        }

        if (dbUrl.isNotBlank() && roomCode.isNotBlank()) {
            FirebaseCoupleManager.startPairing(this, dbUrl, roomCode)
        }

        // Создание плавающего оверлея
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = IslandOverlayView(this)

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = offsetX
            y = offsetY
        }
        layoutParams = params

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Подписка на обновление позиции и масштаба
        scope.launch {
            IslandStateBus.state.collect { state ->
                layoutParams?.let { lp ->
                    if (lp.x != state.offsetX || lp.y != state.offsetY) {
                        lp.x = state.offsetX
                        lp.y = state.offsetY
                        try {
                            windowManager?.updateViewLayout(overlayView, lp)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                overlayView?.invalidate()
            }
        }

        // Датчик акселерометра для наклона
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenInteractive = powerManager?.isInteractive ?: true
        IslandStateBus.setScreenOn(isScreenInteractive)

        if (isScreenInteractive) {
            registerAccelerometer()
        }

        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, batteryFilter)
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        IslandStateBus.setCharging(isCharging)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        systemBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        IslandStateBus.setScreenOn(true)
                        registerAccelerometer()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        IslandStateBus.setScreenOn(false)
                        unregisterAccelerometer()
                        reelsWatchSeconds = 0
                    }
                    Intent.ACTION_POWER_CONNECTED -> IslandStateBus.setCharging(true)
                    Intent.ACTION_POWER_DISCONNECTED -> IslandStateBus.setCharging(false)
                }
            }
        }

        registerReceiver(systemBroadcastReceiver, filter)
    }

    private fun registerAccelerometer() {
        if (!isSensorRegistered && accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            isSensorRegistered = true
        }
    }

    private fun unregisterAccelerometer() {
        if (isSensorRegistered) {
            sensorManager?.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val gx = event.values[0]
        val shiftPx = (-gx * 8f).coerceIn(-35f, 35f)
        val hanging = abs(gx) > 4.5f
        IslandStateBus.updateTilt(shiftPx, hanging)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не требуется
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            // Игнорируем собственное приложение и системную шторку
            if (pkg != packageName && !pkg.contains("systemui", ignoreCase = true)) {
                currentForegroundPackage = pkg
            }
        }
    }

    override fun onInterrupt() {
        // Не требуется
    }

    override fun onDestroy() {
        super.onDestroy()
        doomscrollHandler.removeCallbacks(doomscrollTicker)
        unregisterAccelerometer()

        systemBroadcastReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (ignored: Exception) {
            }
        }
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (ignored: Exception) {
            }
        }
        scope.cancel()
    }
}

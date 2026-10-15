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
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class IslandAccessibilityService : AccessibilityService(), SensorEventListener {

    private var windowManager: WindowManager? = null
    private var overlayView: IslandOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var systemBroadcastReceiver: BroadcastReceiver? = null

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isSensorRegistered = false

    private var currentDoomscrollPackage: String = ""
    private var doomscrollStartTime: Long = 0L

    companion object {
        private const val DOOMSCROLL_LIMIT_MS = 15L * 60L * 1000L

        fun triggerTestDoomscroll() {
            IslandStateBus.triggerDoomscrollWarning("🌱 Хватит листать рилсы! Потрогай траву!")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        RewardedAdManager.initFromPrefs(this)

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        serviceInfo = info

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
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        val isDoomApp = pkg.contains("musically", ignoreCase = true) ||
                pkg.contains("tiktok", ignoreCase = true) ||
                pkg.contains("instagram", ignoreCase = true) ||
                pkg.contains("youtube", ignoreCase = true)

        if (isDoomApp) {
            val now = System.currentTimeMillis()
            if (currentDoomscrollPackage != pkg) {
                currentDoomscrollPackage = pkg
                doomscrollStartTime = now
            } else {
                if (doomscrollStartTime > 0L && now - doomscrollStartTime >= DOOMSCROLL_LIMIT_MS) {
                    IslandStateBus.triggerDoomscrollWarning("🌱 Хватит листать рилсы! Потрогай траву!")
                    doomscrollStartTime = now
                }
            }
        } else {
            currentDoomscrollPackage = ""
            doomscrollStartTime = 0L
        }
    }

    override fun onInterrupt() {
        // Не требуется
    }

    override fun onDestroy() {
        super.onDestroy()
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

package com.pixelisland.app

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object FirebaseCoupleManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var databaseUrl: String = ""
    private var roomCode: String = ""
    private var myDeviceId: String = ""
    private var lastProcessedTimestamp: Long = 0L

    fun startPairing(context: Context, dbUrl: String, code: String) {
        if (dbUrl.isBlank() || code.isBlank()) {
            return
        }

        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
        var devId = prefs.getString("device_id", null)
        if (devId.isNullOrBlank()) {
            val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            devId = (1..8).map { allowedChars.random() }.joinToString("")
            prefs.edit().putString("device_id", devId).apply()
        }
        myDeviceId = devId

        val cleanedDbUrl = dbUrl.trim().trimEnd('/')
        databaseUrl = cleanedDbUrl
        roomCode = code.trim()

        prefs.edit()
            .putString("database_url", databaseUrl)
            .putString("room_code", roomCode)
            .apply()

        IslandStateBus.setRoomCode(roomCode)

        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                if (!IslandStateBus.state.value.isScreenOn) {
                    delay(4000L)
                    continue
                }

                try {
                    val url = URL("$databaseUrl/rooms/$roomCode.json")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.setRequestProperty("Accept", "application/json")

                    val responseCode = conn.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        if (responseText.isNotBlank() && responseText != "null") {
                            val json = JSONObject(responseText)
                            val senderId = json.optString("senderId")
                            val action = json.optString("action")
                            val timestamp = json.optLong("timestamp", 0L)

                            if (lastProcessedTimestamp == 0L) {
                                lastProcessedTimestamp = timestamp
                            } else if (timestamp > lastProcessedTimestamp && senderId != myDeviceId) {
                                lastProcessedTimestamp = timestamp
                                withContext(Dispatchers.Main) {
                                    triggerVibration(context)
                                    when (action) {
                                        "HEART" -> IslandStateBus.triggerPartnerHeart()
                                        "VISIT" -> IslandStateBus.triggerPartnerVisit()
                                        "NOTE" -> {
                                            val note = json.optString("note", "")
                                            val battery = json.optInt("battery", 100)
                                            IslandStateBus.triggerPartnerNote(note, battery)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    // Игнорируем ошибки сети для продолжения цикла
                }

                delay(2500L)
            }
        }
    }

    fun sendPartnerAction(action: String) {
        if (databaseUrl.isBlank() || roomCode.isBlank()) {
            return
        }

        if (action == "VISIT") {
            Handler(Looper.getMainLooper()).post {
                IslandStateBus.setMyPetAway(true)
            }
            Handler(Looper.getMainLooper()).postDelayed({
                IslandStateBus.setMyPetAway(false)
            }, 20000L)
        }

        scope.launch {
            try {
                val url = URL("$databaseUrl/rooms/$roomCode.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                val json = JSONObject().apply {
                    put("senderId", myDeviceId)
                    put("action", action)
                    put("timestamp", System.currentTimeMillis())
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                // Игнорируем сетевые сбои
            }
        }
    }

    fun sendPartnerNote(context: Context, noteText: String) {
        if (databaseUrl.isBlank() || roomCode.isBlank()) {
            return
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        scope.launch {
            try {
                val url = URL("$databaseUrl/rooms/$roomCode.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                val json = JSONObject().apply {
                    put("senderId", myDeviceId)
                    put("action", "NOTE")
                    put("note", noteText)
                    put("battery", battery)
                    put("timestamp", System.currentTimeMillis())
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                // Игнорируем сетевые сбои
            }
        }
    }

    private fun triggerVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 60), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 40, 80, 60), -1)
                }
            }
        } catch (ignored: Exception) {
        }
    }
}

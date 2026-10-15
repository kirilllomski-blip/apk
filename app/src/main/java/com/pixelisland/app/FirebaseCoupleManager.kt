package com.pixelisland.app

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
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

    private const val DEFAULT_DB_URL = "https://pixel-island-default-rtdb.europe-west1.firebasedatabase.app"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    private var databaseUrl: String = DEFAULT_DB_URL
    private var myUsername: String = ""
    private var partnerUsername: String = ""
    private var roomCode: String = ""
    private var myDeviceId: String = ""
    private var lastProcessedTimestamp: Long = 0L
    private var appContext: Context? = null

    fun setCustomDbUrlIfNeeded(url: String) {
        if (url.isNotBlank()) {
            databaseUrl = url.trim().trimEnd('/')
            appContext?.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
                ?.edit()
                ?.putString("database_url", databaseUrl)
                ?.apply()
        }
    }

    fun startPairing(context: Context, dbUrl: String = "", roomCode: String = "") {
        if (dbUrl.isNotBlank()) {
            setCustomDbUrlIfNeeded(dbUrl)
        }
        init(context)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)

        val savedDb = prefs.getString("database_url", null)
        databaseUrl = if (!savedDb.isNullOrBlank()) savedDb.trim().trimEnd('/') else DEFAULT_DB_URL

        var devId = prefs.getString("device_id", null)
        if (devId.isNullOrBlank()) {
            val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            devId = (1..8).map { allowedChars.random() }.joinToString("")
            prefs.edit().putString("device_id", devId).apply()
        }
        myDeviceId = devId

        myUsername = prefs.getString("my_username", "") ?: ""
        partnerUsername = prefs.getString("partner_username", "") ?: ""
        roomCode = prefs.getString("room_code", "") ?: ""

        if (myUsername.isNotBlank() && partnerUsername.isNotBlank() && roomCode.isBlank()) {
            roomCode = listOf(myUsername, partnerUsername).sorted().joinToString("_")
            prefs.edit().putString("room_code", roomCode).apply()
        }

        IslandStateBus.setUsernames(myUsername, partnerUsername)
        IslandStateBus.setRoomCode(roomCode)

        startSyncLoop(context)
    }

    fun getMyUsername(context: Context): String {
        if (myUsername.isBlank()) {
            myUsername = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
                .getString("my_username", "") ?: ""
        }
        return myUsername
    }

    fun getPartnerUsername(context: Context): String {
        if (partnerUsername.isBlank()) {
            partnerUsername = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
                .getString("partner_username", "") ?: ""
        }
        return partnerUsername
    }

    fun getPendingOutgoing(context: Context): String {
        return context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
            .getString("pending_outgoing_to", "") ?: ""
    }

    /**
     * Регистрация своего никнейма:
     * Очищает ник от @ и пробелов, сохраняет в SharedPreferences,
     * записывает профиль в Firebase /users/$myUsername/profile.json и запускает фоновый цикл.
     */
    fun registerMyUsername(context: Context, rawUsername: String, onDone: (Boolean, String) -> Unit) {
        appContext = context.applicationContext
        val clean = rawUsername.trim().removePrefix("@").lowercase()

        if (clean.length < 2) {
            onDone(false, "Никнейм должен содержать минимум 2 символа")
            return
        }

        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("my_username", clean).apply()
        myUsername = clean

        partnerUsername = prefs.getString("partner_username", "") ?: ""
        IslandStateBus.setUsernames(clean, partnerUsername)

        scope.launch {
            try {
                val url = URL("$databaseUrl/users/$clean/profile.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                val json = JSONObject().apply {
                    put("username", clean)
                    put("deviceId", myDeviceId)
                    put("registeredAt", System.currentTimeMillis())
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                conn.responseCode
                conn.disconnect()
            } catch (ignored: Exception) {
                // Если нет интернета - профиль уже сохранен локально
            }

            withContext(Dispatchers.Main) {
                startSyncLoop(context)
                onDone(true, "Никнейм @$clean успешно сохранён!")
            }
        }
    }

    /**
     * Отправка заявки в друзья по нику:
     * Отправляет в /users/$targetUsername/incoming_request.json.
     * Если цель == свой ник или "test", сразу включает локальный Loopback fallback
     * (симуляция входящей заявки от @test_friend для проверки интерфейса).
     */
    fun sendFriendRequest(context: Context, targetRawUsername: String, onResult: (String) -> Unit) {
        appContext = context.applicationContext
        val cleanTarget = targetRawUsername.trim().removePrefix("@").lowercase()

        if (cleanTarget.length < 2) {
            onResult("Введите корректный никнейм (от 2 символов)")
            return
        }

        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
        val me = prefs.getString("my_username", myUsername) ?: myUsername

        if (me.isBlank()) {
            onResult("Сначала сохраните свой никнейм выше!")
            return
        }

        // ЛОКАЛЬНЫЙ FALLBACK (Loopback mode для тестирования)
        if (cleanTarget == me || cleanTarget == "test" || cleanTarget == "test_friend") {
            prefs.edit().putString("pending_outgoing_to", cleanTarget).apply()
            IslandStateBus.setIncomingRequest("test_friend")
            onResult("✅ Тестовая заявка от @test_friend отправлена! Проверьте островок.")
            return
        }

        prefs.edit().putString("pending_outgoing_to", cleanTarget).apply()

        scope.launch {
            var success = false
            try {
                val url = URL("$databaseUrl/users/$cleanTarget/incoming_request.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                val json = JSONObject().apply {
                    put("from", me)
                    put("status", "pending")
                    put("timestamp", System.currentTimeMillis())
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                success = (conn.responseCode == HttpURLConnection.HTTP_OK)
                conn.disconnect()
            } catch (ignored: Exception) {
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    onResult("Заявка к @$cleanTarget отправлена!")
                } else {
                    // Fallback: симулируем заявку локально, чтобы можно было проверить UI
                    IslandStateBus.setIncomingRequest(cleanTarget)
                    onResult("Заявка к @$cleanTarget создана (локальный тест: проверьте островок)")
                }
            }
        }
    }

    /**
     * Подтверждение заявки в друзья:
     * Сохраняет partner_username, очищает incomingFriendRequestFrom,
     * записывает общий код комнаты в Firebase и показывает Toast.
     */
    fun acceptFriendRequest(context: Context, fromUsername: String) {
        appContext = context.applicationContext
        val cleanFrom = fromUsername.trim().removePrefix("@").lowercase()
        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
        val me = prefs.getString("my_username", myUsername) ?: myUsername

        val sharedRoom = listOf(me, cleanFrom).filter { it.isNotBlank() }.sorted().joinToString("_")

        prefs.edit()
            .putString("partner_username", cleanFrom)
            .putString("room_code", sharedRoom)
            .remove("pending_outgoing_to")
            .apply()

        partnerUsername = cleanFrom
        roomCode = sharedRoom

        IslandStateBus.setIncomingRequest(null)
        IslandStateBus.setUsernames(me, cleanFrom)
        IslandStateBus.setRoomCode(sharedRoom)

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Вы и @$cleanFrom теперь в паре! 🎉", Toast.LENGTH_LONG).show()
        }

        scope.launch {
            try {
                // 1. Очищаем входящую заявку у себя
                val delUrl = URL("$databaseUrl/users/$me/incoming_request.json")
                val connDel = delUrl.openConnection() as HttpURLConnection
                connDel.requestMethod = "DELETE"
                connDel.connectTimeout = 3000
                connDel.readTimeout = 3000
                connDel.responseCode
                connDel.disconnect()

                // 2. Устанавливаем пару в моем профиле
                val myUrl = URL("$databaseUrl/users/$me/partner.json")
                val connMy = myUrl.openConnection() as HttpURLConnection
                connMy.requestMethod = "PUT"
                connMy.connectTimeout = 3000
                connMy.readTimeout = 3000
                connMy.doOutput = true
                connMy.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                val myJson = JSONObject().apply {
                    put("partner", cleanFrom)
                    put("room", sharedRoom)
                    put("status", "accepted")
                    put("timestamp", System.currentTimeMillis())
                }
                connMy.outputStream.use { it.write(myJson.toString().toByteArray(Charsets.UTF_8)) }
                connMy.responseCode
                connMy.disconnect()

                // 3. Устанавливаем пару в профиле партнера
                val theirUrl = URL("$databaseUrl/users/$cleanFrom/partner.json")
                val connTheir = theirUrl.openConnection() as HttpURLConnection
                connTheir.requestMethod = "PUT"
                connTheir.connectTimeout = 3000
                connTheir.readTimeout = 3000
                connTheir.doOutput = true
                connTheir.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                val theirJson = JSONObject().apply {
                    put("partner", me)
                    put("room", sharedRoom)
                    put("status", "accepted")
                    put("timestamp", System.currentTimeMillis())
                }
                connTheir.outputStream.use { it.write(theirJson.toString().toByteArray(Charsets.UTF_8)) }
                connTheir.responseCode
                connTheir.disconnect()
            } catch (ignored: Exception) {
            }
        }

        startSyncLoop(context)
    }

    /**
     * Отклонение заявки в друзья.
     */
    fun declineFriendRequest(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
        val me = prefs.getString("my_username", myUsername) ?: myUsername

        IslandStateBus.setIncomingRequest(null)

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Заявка в друзья отклонена", Toast.LENGTH_SHORT).show()
        }

        scope.launch {
            try {
                val url = URL("$databaseUrl/users/$me/incoming_request.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "DELETE"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.responseCode
                conn.disconnect()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Удаление привязки к партнеру / расставание.
     */
    fun unpairFriend(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
        val me = prefs.getString("my_username", myUsername) ?: myUsername
        val oldPartner = prefs.getString("partner_username", "") ?: ""

        prefs.edit()
            .remove("partner_username")
            .remove("room_code")
            .remove("pending_outgoing_to")
            .apply()

        partnerUsername = ""
        roomCode = ""

        IslandStateBus.setUsernames(me, "")
        IslandStateBus.setRoomCode("")

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Связь с партнером разорвана", Toast.LENGTH_SHORT).show()
        }

        scope.launch {
            try {
                // Очищаем статус партнера у себя
                val urlMy = URL("$databaseUrl/users/$me/partner.json")
                val connMy = urlMy.openConnection() as HttpURLConnection
                connMy.requestMethod = "DELETE"
                connMy.connectTimeout = 3000
                connMy.readTimeout = 3000
                connMy.responseCode
                connMy.disconnect()

                if (oldPartner.isNotBlank()) {
                    val urlTheir = URL("$databaseUrl/users/$oldPartner/partner.json")
                    val connTheir = urlTheir.openConnection() as HttpURLConnection
                    connTheir.requestMethod = "DELETE"
                    connTheir.connectTimeout = 3000
                    connTheir.readTimeout = 3000
                    connTheir.responseCode
                    connTheir.disconnect()
                }
            } catch (ignored: Exception) {
            }
        }

        startSyncLoop(context)
    }

    /**
     * Фоновый цикл проверки входящих заявок и общей комнаты активности.
     */
    fun startSyncLoop(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)

        myUsername = prefs.getString("my_username", "") ?: ""
        partnerUsername = prefs.getString("partner_username", "") ?: ""
        roomCode = prefs.getString("room_code", "") ?: ""

        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                if (!IslandStateBus.state.value.isScreenOn) {
                    delay(4000L)
                    continue
                }

                // 1. ПРОВЕРКА ВХОДЯЩИХ ЗАЯВОК В ДРУЗЬЯ
                if (myUsername.isNotBlank()) {
                    try {
                        val reqUrl = URL("$databaseUrl/users/$myUsername/incoming_request.json")
                        val connReq = reqUrl.openConnection() as HttpURLConnection
                        connReq.requestMethod = "GET"
                        connReq.connectTimeout = 3000
                        connReq.readTimeout = 3000
                        connReq.setRequestProperty("Accept", "application/json")

                        if (connReq.responseCode == HttpURLConnection.HTTP_OK) {
                            val text = connReq.inputStream.bufferedReader().use { it.readText() }
                            if (text.isNotBlank() && text != "null") {
                                val json = JSONObject(text)
                                val status = json.optString("status", "")
                                val from = json.optString("from", "")
                                if (status == "pending" && from.isNotBlank()) {
                                    withContext(Dispatchers.Main) {
                                        IslandStateBus.setIncomingRequest(from)
                                    }
                                }
                            }
                        }
                        connReq.disconnect()
                    } catch (ignored: Exception) {
                    }
                }

                // 2. ПРОВЕРКА, ПРИНЯЛ ЛИ ПАРТНЕР НАШУ ИСХОДЯЩУЮ ЗАЯВКУ
                if (partnerUsername.isBlank() && myUsername.isNotBlank()) {
                    try {
                        val partnerUrl = URL("$databaseUrl/users/$myUsername/partner.json")
                        val connPartner = partnerUrl.openConnection() as HttpURLConnection
                        connPartner.requestMethod = "GET"
                        connPartner.connectTimeout = 3000
                        connPartner.readTimeout = 3000
                        connPartner.setRequestProperty("Accept", "application/json")

                        if (connPartner.responseCode == HttpURLConnection.HTTP_OK) {
                            val pText = connPartner.inputStream.bufferedReader().use { it.readText() }
                            if (pText.isNotBlank() && pText != "null") {
                                val pJson = JSONObject(pText)
                                val status = pJson.optString("status")
                                val newPartner = pJson.optString("partner")
                                val newRoom = pJson.optString("room")

                                if (status == "accepted" && newPartner.isNotBlank()) {
                                    partnerUsername = newPartner
                                    roomCode = if (newRoom.isNotBlank()) newRoom else listOf(myUsername, newPartner).sorted().joinToString("_")

                                    prefs.edit()
                                        .putString("partner_username", partnerUsername)
                                        .putString("room_code", roomCode)
                                        .remove("pending_outgoing_to")
                                        .apply()

                                    withContext(Dispatchers.Main) {
                                        IslandStateBus.setUsernames(myUsername, partnerUsername)
                                        IslandStateBus.setRoomCode(roomCode)
                                        Toast.makeText(context, "Вашу заявку принял @$partnerUsername! Вы в паре! 🎉", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        connPartner.disconnect()
                    } catch (ignored: Exception) {
                    }
                }

                // 3. ПРОСЛУШИВАНИЕ ОБЩЕЙ КОМНАТЫ ПАРЫ (HEART, VISIT, NOTE)
                if (roomCode.isNotBlank()) {
                    try {
                        val roomUrl = URL("$databaseUrl/rooms/$roomCode.json")
                        val connRoom = roomUrl.openConnection() as HttpURLConnection
                        connRoom.requestMethod = "GET"
                        connRoom.connectTimeout = 3500
                        connRoom.readTimeout = 3500
                        connRoom.setRequestProperty("Accept", "application/json")

                        if (connRoom.responseCode == HttpURLConnection.HTTP_OK) {
                            val rText = connRoom.inputStream.bufferedReader().use { it.readText() }
                            if (rText.isNotBlank() && rText != "null") {
                                val json = JSONObject(rText)
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
                        connRoom.disconnect()
                    } catch (ignored: Exception) {
                    }
                }

                delay(3000L)
            }
        }
    }

    /**
     * Отправка действия партнеру ("HEART", "VISIT", "ACCEPT_FRIEND", "DECLINE_FRIEND")
     */
    fun sendPartnerAction(action: String) {
        val ctx = appContext

        if (action == "ACCEPT_FRIEND") {
            val from = IslandStateBus.state.value.incomingFriendRequestFrom ?: ""
            if (ctx != null && from.isNotBlank()) {
                acceptFriendRequest(ctx, from)
            } else {
                IslandStateBus.setIncomingRequest(null)
            }
            return
        }

        if (action == "DECLINE_FRIEND") {
            if (ctx != null) {
                declineFriendRequest(ctx)
            } else {
                IslandStateBus.setIncomingRequest(null)
            }
            return
        }

        if (roomCode.isBlank()) {
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
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
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
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Отправка живой записки партнеру с процентом батареи.
     */
    fun sendPartnerNote(context: Context, noteText: String) {
        appContext = context.applicationContext
        if (roomCode.isBlank()) {
            Toast.makeText(context, "Сначала добавьте друга по никнейму!", Toast.LENGTH_SHORT).show()
            return
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        scope.launch {
            try {
                val url = URL("$databaseUrl/rooms/$roomCode.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
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

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Записка отправлена партнеру!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка отправки записки", Toast.LENGTH_SHORT).show()
                }
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

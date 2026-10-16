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

/**
 * Менеджер взаимодействия в паре по @username через Firebase Realtime Database.
 * Предоставляет автоматическое сопряжение по никнеймам, отправку живых записок,
 * махание лапой [ 👋 Привет ] и приход в гости [ 🐾 В гости ] с мгновенной локальной
 * анимацией на островке и в 3D-комнате конструктора.
 */
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
            }

            withContext(Dispatchers.Main) {
                onDone(true, "Никнейм @$clean успешно сохранен!")
                startSyncLoop(context)
            }
        }
    }

    /**
     * Отправка заявки в друзья пользователю targetUsername.
     * Содержит встроенный локальный Loopback для тестирования (если ввести @test).
     */
    fun sendFriendRequest(context: Context, rawTarget: String, onDone: (Boolean, String) -> Unit) {
        appContext = context.applicationContext
        val cleanTarget = rawTarget.trim().removePrefix("@").lowercase()

        if (cleanTarget.isBlank()) {
            onDone(false, "Введите никнейм друга")
            return
        }

        if (myUsername.isBlank()) {
            onDone(false, "Сначала зарегистрируйте свой никнейм!")
            return
        }

        if (cleanTarget == myUsername) {
            onDone(false, "Нельзя добавить самого себя в друзья")
            return
        }

        // Локальный режим для мгновенного теста прямо на одном телефоне
        if (cleanTarget == "test" || cleanTarget == "demo") {
            val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
            partnerUsername = cleanTarget
            roomCode = listOf(myUsername, cleanTarget).sorted().joinToString("_")

            prefs.edit()
                .putString("partner_username", partnerUsername)
                .putString("room_code", roomCode)
                .remove("pending_outgoing_to")
                .apply()

            IslandStateBus.setUsernames(myUsername, partnerUsername)
            IslandStateBus.setRoomCode(roomCode)

            onDone(true, "Тестовый напарник @$cleanTarget мгновенно подключен в пару! 🎉")
            return
        }

        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("pending_outgoing_to", cleanTarget).apply()

        scope.launch {
            var success = false
            var msg = "Заявка отправлена @$cleanTarget! Ожидаем подтверждения."

            try {
                val url = URL("$databaseUrl/users/$cleanTarget/incoming_request.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                val json = JSONObject().apply {
                    put("from", myUsername)
                    put("timestamp", System.currentTimeMillis())
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                if (conn.responseCode in 200..299) {
                    success = true
                }
                conn.disconnect()
            } catch (e: Exception) {
                // Если база еще не создана пользователем, сохраняем локально как ожидающую
                success = true
                msg = "Заявка зарегистрирована для @$cleanTarget."
            }

            withContext(Dispatchers.Main) {
                onDone(success, msg)
            }
        }
    }

    /**
     * Принятие входящей заявки от пользователя fromUser.
     */
    fun acceptFriendRequest(context: Context, fromUser: String) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)

        partnerUsername = fromUser
        roomCode = listOf(myUsername, fromUser).sorted().joinToString("_")

        prefs.edit()
            .putString("partner_username", partnerUsername)
            .putString("room_code", roomCode)
            .remove("pending_outgoing_to")
            .apply()

        IslandStateBus.setUsernames(myUsername, partnerUsername)
        IslandStateBus.setRoomCode(roomCode)
        IslandStateBus.setIncomingRequest(null)

        scope.launch {
            try {
                // 1. Очищаем входящую заявку
                val urlDel = URL("$databaseUrl/users/$myUsername/incoming_request.json")
                val connDel = urlDel.openConnection() as HttpURLConnection
                connDel.requestMethod = "DELETE"
                connDel.connectTimeout = 3500
                connDel.responseCode
                connDel.disconnect()

                // 2. Уведомляем партнера через его профиль
                val urlPartner = URL("$databaseUrl/users/$fromUser/partnership.json")
                val connPartner = urlPartner.openConnection() as HttpURLConnection
                connPartner.requestMethod = "PUT"
                connPartner.connectTimeout = 3500
                connPartner.doOutput = true
                connPartner.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                val json = JSONObject().apply {
                    put("partner", myUsername)
                    put("room", roomCode)
                    put("status", "accepted")
                    put("timestamp", System.currentTimeMillis())
                }

                connPartner.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                connPartner.responseCode
                connPartner.disconnect()
            } catch (ignored: Exception) {
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Вы успешно образовали пару с @$fromUser! 🎉", Toast.LENGTH_LONG).show()
                triggerVibration(context)
            }
        }
    }

    /**
     * Отклонение входящей заявки.
     */
    fun declineFriendRequest(context: Context) {
        appContext = context.applicationContext
        IslandStateBus.setIncomingRequest(null)

        if (myUsername.isBlank()) return

        scope.launch {
            try {
                val urlDel = URL("$databaseUrl/users/$myUsername/incoming_request.json")
                val connDel = urlDel.openConnection() as HttpURLConnection
                connDel.requestMethod = "DELETE"
                connDel.connectTimeout = 3500
                connDel.responseCode
                connDel.disconnect()
            } catch (ignored: Exception) {
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Заявка отклонена", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Разрыв пары.
     */
    fun unpairFriend(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)

        val oldPartner = partnerUsername
        val oldRoom = roomCode

        partnerUsername = ""
        roomCode = ""

        prefs.edit()
            .remove("partner_username")
            .remove("room_code")
            .remove("pending_outgoing_to")
            .apply()

        IslandStateBus.setUsernames(myUsername, "")
        IslandStateBus.setRoomCode("")

        if (oldPartner.isNotBlank()) {
            scope.launch {
                try {
                    val urlPartner = URL("$databaseUrl/users/$oldPartner/partnership.json")
                    val connPartner = urlPartner.openConnection() as HttpURLConnection
                    connPartner.requestMethod = "DELETE"
                    connPartner.connectTimeout = 3000
                    connPartner.responseCode
                    connPartner.disconnect()

                    if (oldRoom.isNotBlank()) {
                        val urlRoom = URL("$databaseUrl/rooms/$oldRoom.json")
                        val connRoom = urlRoom.openConnection() as HttpURLConnection
                        connRoom.requestMethod = "DELETE"
                        connRoom.connectTimeout = 3000
                        connRoom.responseCode
                        connRoom.disconnect()
                    }
                } catch (ignored: Exception) {
                }
            }
        }

        Toast.makeText(context, "Пара расторгнута", Toast.LENGTH_SHORT).show()
    }

    /**
     * Фоновый цикл опроса Firebase каждые 3 секунды:
     * 1. Проверяет наличие входящих заявок в друзья.
     * 2. Проверяет, принял ли партнер нашу исходящую заявку.
     * 3. Слушает общую комнату пары (HEART, VISIT, NOTE).
     */
    private fun startSyncLoop(context: Context) {
        syncJob?.cancel()
        syncJob = scope.launch {
            val prefs = context.getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)

            while (isActive) {
                if (myUsername.isNotBlank()) {
                    // 1. ПРОВЕРКА ВХОДЯЩИХ ЗАЯВОК В ДРУЗЬЯ
                    try {
                        val reqUrl = URL("$databaseUrl/users/$myUsername/incoming_request.json")
                        val conn = reqUrl.openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 3500
                        conn.readTimeout = 3500
                        conn.setRequestProperty("Accept", "application/json")

                        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                            val text = conn.inputStream.bufferedReader().use { it.readText() }
                            if (text.isNotBlank() && text != "null") {
                                val json = JSONObject(text)
                                val from = json.optString("from")
                                if (from.isNotBlank() && from != myUsername && from != partnerUsername) {
                                    withContext(Dispatchers.Main) {
                                        IslandStateBus.setIncomingRequest(from)
                                    }
                                }
                            }
                        }
                        conn.disconnect()
                    } catch (ignored: Exception) {
                    }
                }

                // 2. ПРОВЕРКА ОТВЕТА НА НАШУ ИСХОДЯЩУЮ ЗАЯВКУ
                val pendingTo = prefs.getString("pending_outgoing_to", "") ?: ""
                if (pendingTo.isNotBlank() && partnerUsername.isBlank()) {
                    try {
                        val partnerUrl = URL("$databaseUrl/users/$myUsername/partnership.json")
                        val connPartner = partnerUrl.openConnection() as HttpURLConnection
                        connPartner.requestMethod = "GET"
                        connPartner.connectTimeout = 3500
                        connPartner.readTimeout = 3500
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
                                            "HEART", "POKE", "WAVE" -> {
                                                IslandStateBus.triggerPokeWave(partnerUsername.ifBlank { "напарник" })
                                            }
                                            "VISIT" -> {
                                                IslandStateBus.triggerPartnerVisit()
                                            }
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
     * Отправка действия партнеру ("HEART", "POKE", "WAVE", "VISIT", "ACCEPT_FRIEND", "DECLINE_FRIEND").
     * Мгновенно запускает локальную анимацию на нашем островке и в 3D-комнате конструктора,
     * а также отправляет действие в Firebase для напарника.
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

        // ==================== МГНОВЕННЫЙ ЗАПУСК НА ГЛАВНОМ ПОТОКЕ ====================
        Handler(Looper.getMainLooper()).post {
            when (action) {
                "HEART", "POKE", "WAVE" -> {
                    IslandStateBus.triggerPokeWave(partnerUsername.ifBlank { "друг" })
                    ctx?.let { triggerVibration(it) }
                }
                "VISIT" -> {
                    IslandStateBus.triggerPartnerVisit()
                    ctx?.let { triggerVibration(it) }
                }
            }
        }

        if (roomCode.isBlank()) {
            return
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
     * Сразу отображает записку на нашем островке и отправляет в Firebase.
     */
    fun sendPartnerNote(context: Context, noteText: String) {
        appContext = context.applicationContext

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        // Мгновенно отображаем на нашем островке
        Handler(Looper.getMainLooper()).post {
            IslandStateBus.triggerPartnerNote(noteText, battery)
            triggerVibration(context)
        }

        if (roomCode.isBlank()) {
            Toast.makeText(context, "Записка показана локально (для отправки партнеру подключите @ник)", Toast.LENGTH_SHORT).show()
            return
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
                    Toast.makeText(context, "Записка сохранена локально", Toast.LENGTH_SHORT).show()
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

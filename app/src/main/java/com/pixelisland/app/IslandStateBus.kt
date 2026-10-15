package com.pixelisland.app

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PetMode {
    IDLE,
    SLEEP,
    MUSIC,
    CHARGING,
    NOTIFICATION,
    HAPPY
}

data class IslandState(
    val isExpanded: Boolean = false,
    val petMode: PetMode = PetMode.IDLE,
    val mediaTitle: String? = null,
    val lastNotificationText: String? = null,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val islandScale: Float = 1.0f,
    val isScreenOn: Boolean = true,
    val isCharging: Boolean = false,
    val roomCode: String = "",
    val hasGuestPet: Boolean = false,
    val isMyPetAway: Boolean = false,
    val partnerStatusText: String? = null,
    val adsWatchedCount: Int = 0,
    val customPetExpireMs: Long = 0L,
    val customPetVersion: Int = 0,
    val isProLifetime: Boolean = false,
    val tiltOffsetPx: Float = 0f,
    val isHangingOnEdge: Boolean = false,
    val doomscrollWarning: String? = null,
    val partnerNoteText: String? = null,
    val hunger: Int = 85,
    val energy: Int = 90,
    val mood: Int = 75,
    val petLevel: Int = 1,
    val xp: Int = 30,
    val myUsername: String = "",
    val partnerUsername: String = "",
    val incomingFriendRequestFrom: String? = null
)

object IslandStateBus {

    private val _state = MutableStateFlow(IslandState())
    val state: StateFlow<IslandState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    private var notificationRunnable: Runnable? = null
    private var partnerHeartRunnable: Runnable? = null
    private var partnerVisitRunnable: Runnable? = null
    private var doomscrollRunnable: Runnable? = null
    private var partnerNoteRunnable: Runnable? = null

    fun setPetMode(mode: PetMode) {
        _state.update { it.copy(petMode = mode) }
    }

    fun setMedia(title: String?) {
        _state.update { it.copy(mediaTitle = title) }
        recalculatePetMode()
    }

    fun setNotification(text: String?) {
        notificationRunnable?.let { handler.removeCallbacks(it) }
        _state.update {
            it.copy(
                lastNotificationText = text,
                petMode = PetMode.NOTIFICATION
            )
        }
        val runnable = Runnable {
            recalculatePetMode()
        }
        notificationRunnable = runnable
        handler.postDelayed(runnable, 4000L)
    }

    fun setExpanded(expanded: Boolean) {
        _state.update { it.copy(isExpanded = expanded) }
    }

    fun updateCalibration(x: Int, y: Int, scale: Float) {
        _state.update {
            it.copy(
                offsetX = x,
                offsetY = y,
                islandScale = scale
            )
        }
    }

    fun setScreenOn(on: Boolean) {
        _state.update { it.copy(isScreenOn = on) }
        recalculatePetMode()
    }

    fun setCharging(charging: Boolean) {
        _state.update { it.copy(isCharging = charging) }
        recalculatePetMode()
    }

    fun setRoomCode(code: String) {
        _state.update { it.copy(roomCode = code) }
    }

    fun setMyPetAway(away: Boolean) {
        _state.update { it.copy(isMyPetAway = away) }
    }

    fun triggerPartnerHeart() {
        partnerHeartRunnable?.let { handler.removeCallbacks(it) }
        _state.update {
            it.copy(
                petMode = PetMode.HAPPY,
                partnerStatusText = "❤️ Партнёр гладит кота!",
                isExpanded = true
            )
        }
        val runnable = Runnable {
            _state.update {
                it.copy(
                    isExpanded = false,
                    partnerStatusText = null
                )
            }
            recalculatePetMode()
        }
        partnerHeartRunnable = runnable
        handler.postDelayed(runnable, 4000L)
    }

    fun triggerPartnerVisit() {
        partnerVisitRunnable?.let { handler.removeCallbacks(it) }
        _state.update {
            it.copy(
                hasGuestPet = true,
                petMode = PetMode.HAPPY,
                partnerStatusText = "🐾 Кот партнёра в гостях!"
            )
        }
        val runnable = Runnable {
            _state.update {
                it.copy(
                    hasGuestPet = false,
                    partnerStatusText = null
                )
            }
            recalculatePetMode()
        }
        partnerVisitRunnable = runnable
        handler.postDelayed(runnable, 20000L)
    }

    fun recalculatePetMode() {
        val current = _state.value
        val newMode = when {
            !current.isScreenOn -> PetMode.SLEEP
            current.isCharging -> PetMode.CHARGING
            current.mediaTitle != null -> PetMode.MUSIC
            else -> PetMode.IDLE
        }
        _state.update { it.copy(petMode = newMode) }
    }

    fun updateCustomPetAuth(adsCount: Int, expireMs: Long) {
        _state.update {
            it.copy(
                adsWatchedCount = adsCount,
                customPetExpireMs = expireMs
            )
        }
    }

    fun setProLifetime(isPro: Boolean) {
        _state.update {
            it.copy(isProLifetime = isPro)
        }
    }

    fun notifyCustomPetFileChanged() {
        _state.update {
            it.copy(customPetVersion = it.customPetVersion + 1)
        }
    }

    fun updateTilt(tiltPx: Float, isHanging: Boolean) {
        _state.update {
            it.copy(
                tiltOffsetPx = tiltPx,
                isHangingOnEdge = isHanging
            )
        }
    }

    fun triggerDoomscrollWarning(warning: String) {
        doomscrollRunnable?.let { handler.removeCallbacks(it) }
        _state.update {
            it.copy(
                isExpanded = true,
                doomscrollWarning = warning
            )
        }
        val runnable = Runnable {
            _state.update {
                it.copy(
                    isExpanded = false,
                    doomscrollWarning = null
                )
            }
        }
        doomscrollRunnable = runnable
        handler.postDelayed(runnable, 6000L)
    }

    fun triggerPartnerNote(note: String, batteryPct: Int) {
        partnerNoteRunnable?.let { handler.removeCallbacks(it) }
        _state.update {
            it.copy(
                isExpanded = true,
                petMode = PetMode.HAPPY,
                partnerNoteText = "💌 $note (🔋$batteryPct%)"
            )
        }
        val runnable = Runnable {
            _state.update {
                it.copy(
                    isExpanded = false,
                    partnerNoteText = null
                )
            }
            recalculatePetMode()
        }
        partnerNoteRunnable = runnable
        handler.postDelayed(runnable, 7000L)
    }

    fun feedPet() {
        _state.update { current ->
            val newHunger = (current.hunger + 20).coerceAtMost(100)
            var newXp = current.xp + 15
            var newLevel = current.petLevel
            if (newXp >= 100) {
                newLevel++
                newXp -= 100
            }
            current.copy(
                hunger = newHunger,
                xp = newXp,
                petLevel = newLevel,
                petMode = PetMode.HAPPY
            )
        }
        partnerHeartRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            recalculatePetMode()
        }
        partnerHeartRunnable = runnable
        handler.postDelayed(runnable, 4000L)
    }

    fun playWithPet() {
        _state.update { current ->
            val newMood = (current.mood + 20).coerceAtMost(100)
            var newXp = current.xp + 10
            var newLevel = current.petLevel
            if (newXp >= 100) {
                newLevel++
                newXp -= 100
            }
            current.copy(
                mood = newMood,
                xp = newXp,
                petLevel = newLevel,
                petMode = PetMode.HAPPY
            )
        }
        partnerHeartRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            recalculatePetMode()
        }
        partnerHeartRunnable = runnable
        handler.postDelayed(runnable, 4000L)
    }

    fun setIncomingRequest(fromUser: String?) {
        _state.update { it.copy(incomingFriendRequestFrom = fromUser) }
    }

    fun setUsernames(me: String, partner: String) {
        _state.update { it.copy(myUsername = me, partnerUsername = partner) }
    }
}

package com.pixelisland.app

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PetSpecies {
    CAT,
    DOG
}

enum class PetMode {
    IDLE,
    SLEEP,
    MUSIC,
    CHARGING,
    NOTIFICATION,
    EATING,
    WAVE_POKE,
    DOOMSCROLL,
    COUPLE_VISIT
}

data class IslandState(
    val species: PetSpecies = PetSpecies.CAT,
    val isExpanded: Boolean = false,
    val petMode: PetMode = PetMode.IDLE,
    val mediaTitle: String? = null,
    val isMusicPlaying: Boolean = false,
    val lastNotificationText: String? = null,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val islandScale: Float = 1.0f,
    val isScreenOn: Boolean = true,
    val isCharging: Boolean = false,
    val hunger: Int = 85,
    val energy: Int = 90,
    val mood: Int = 80,
    val petLevel: Int = 1,
    val xp: Int = 40,
    val myUsername: String = "",
    val partnerUsername: String = "",
    val incomingFriendRequestFrom: String? = null,
    val hasGuestPet: Boolean = false,
    val isMyPetAway: Boolean = false,
    val partnerStatusText: String? = null,
    val partnerNoteText: String? = null,
    val doomscrollWarning: String? = null,
    val doomscrollLimitMinutes: Int = 15,
    val tiltOffsetPx: Float = 0f,
    val isHangingOnEdge: Boolean = false,
    val adsWatchedCount: Int = 0,
    val customPetExpireMs: Long = 0L,
    val customPetVersion: Int = 0,
    val isProLifetime: Boolean = false,
    val roomCode: String = "",
    val chargingPulseUntilMs: Long = 0L,
    val batteryLevel: Int = 100,
    val stepsToday: Int = 1240,
    val stepsGoal: Int = 5000,
    val coins: Int = 120,
    val forceSunglasses: Boolean = false,
    val forceNightcap: Boolean = false,
    val isPartnerBatteryLow: Boolean = false,
    val partnerBatteryPct: Int = 100
)

object IslandStateBus {

    private val _state = MutableStateFlow(IslandState())
    val state: StateFlow<IslandState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    private var notificationRunnable: Runnable? = null
    private var eatingRunnable: Runnable? = null
    private var pokeRunnable: Runnable? = null
    private var partnerHeartRunnable: Runnable? = null
    private var partnerVisitRunnable: Runnable? = null
    private var doomscrollRunnable: Runnable? = null
    private var partnerNoteRunnable: Runnable? = null

    fun setPetSpecies(species: PetSpecies) {
        _state.update { it.copy(species = species) }
    }

    fun setPetMode(mode: PetMode) {
        _state.update { it.copy(petMode = mode) }
    }

    fun setMedia(title: String?, playing: Boolean = title != null) {
        _state.update {
            it.copy(
                mediaTitle = title,
                isMusicPlaying = playing,
                petMode = if (playing && title != null) PetMode.MUSIC else it.petMode
            )
        }
        recalculatePetMode()
    }

    fun setNotification(text: String?) {
        notificationRunnable?.let { handler.removeCallbacks(it) }
        _state.update {
            it.copy(
                lastNotificationText = text,
                petMode = if (text != null) PetMode.NOTIFICATION else it.petMode
            )
        }
        val runnable = Runnable {
            _state.update { it.copy(lastNotificationText = null) }
            recalculatePetMode()
        }
        notificationRunnable = runnable
        handler.postDelayed(runnable, 4000L)
    }

    fun feedPet() {
        eatingRunnable?.let { handler.removeCallbacks(it) }
        _state.update { current ->
            val newHunger = (current.hunger + 20).coerceAtMost(100)
            val newMood = (current.mood + 5).coerceAtMost(100)
            var newXp = current.xp + 15
            var newLevel = current.petLevel
            if (newXp >= 100) {
                newLevel++
                newXp -= 100
            }
            current.copy(
                hunger = newHunger,
                mood = newMood,
                xp = newXp,
                petLevel = newLevel,
                petMode = PetMode.EATING
            )
        }
        val runnable = Runnable {
            recalculatePetMode()
        }
        eatingRunnable = runnable
        handler.postDelayed(runnable, 3500L)
    }

    fun triggerPokeWave(targetName: String) {
        pokeRunnable?.let { handler.removeCallbacks(it) }
        _state.update {
            it.copy(
                petMode = PetMode.WAVE_POKE,
                partnerStatusText = "👋 Привет и пинок для @$targetName!"
            )
        }
        val runnable = Runnable {
            _state.update {
                it.copy(
                    partnerStatusText = null
                )
            }
            recalculatePetMode()
        }
        pokeRunnable = runnable
        handler.postDelayed(runnable, 4000L)
    }

    fun triggerPartnerHeart() {
        partnerHeartRunnable?.let { handler.removeCallbacks(it) }
        _state.update {
            it.copy(
                petMode = PetMode.WAVE_POKE,
                partnerStatusText = "❤️ Напарник машет тебе лапой!"
            )
        }
        val runnable = Runnable {
            _state.update {
                it.copy(
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
                petMode = PetMode.COUPLE_VISIT,
                partnerStatusText = "🐾 Питомцы тусуются вместе в гостях!"
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
        handler.postDelayed(runnable, 15000L)
    }

    fun playWithPet() {
        pokeRunnable?.let { handler.removeCallbacks(it) }
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
                petMode = PetMode.WAVE_POKE
            )
        }
        val runnable = Runnable {
            recalculatePetMode()
        }
        pokeRunnable = runnable
        handler.postDelayed(runnable, 3500L)
    }

    fun addXpAndMood(xpAmount: Int, moodAmount: Int = 10) {
        _state.update { current ->
            val newMood = (current.mood + moodAmount).coerceAtMost(100)
            var newXp = current.xp + xpAmount
            var newLevel = current.petLevel
            if (newXp >= 100) {
                newLevel++
                newXp -= 100
            }
            current.copy(
                mood = newMood,
                xp = newXp,
                petLevel = newLevel
            )
        }
    }

    fun updateSteps(steps: Int) {
        _state.update { current ->
            val oldMilestone = current.stepsToday / 1000
            val newMilestone = steps / 1000
            if (newMilestone > oldMilestone) {
                val newCoins = current.coins + 25 * (newMilestone - oldMilestone)
                current.copy(stepsToday = steps, coins = newCoins)
            } else {
                current.copy(stepsToday = steps)
            }
        }
    }

    fun addSteps(delta: Int = 500) {
        _state.update { current ->
            val newSteps = current.stepsToday + delta
            val oldMilestone = current.stepsToday / 1000
            val newMilestone = newSteps / 1000
            if (newMilestone > oldMilestone) {
                val newCoins = current.coins + 25 * (newMilestone - oldMilestone)
                current.copy(stepsToday = newSteps, coins = newCoins)
            } else {
                current.copy(stepsToday = newSteps)
            }
        }
    }

    fun toggleSunglasses() {
        _state.update { it.copy(forceSunglasses = !it.forceSunglasses, forceNightcap = false) }
    }

    fun toggleNightcap() {
        _state.update { it.copy(forceNightcap = !it.forceNightcap, forceSunglasses = false) }
    }

    fun setLevel(level: Int) {
        _state.update { it.copy(petLevel = level.coerceIn(1, 10)) }
    }

    fun triggerDoomscrollWarning(warning: String) {
        doomscrollRunnable?.let { handler.removeCallbacks(it) }
        _state.update {
            it.copy(
                petMode = PetMode.DOOMSCROLL,
                doomscrollWarning = warning
            )
        }
        val runnable = Runnable {
            _state.update {
                it.copy(
                    doomscrollWarning = null
                )
            }
            recalculatePetMode()
        }
        doomscrollRunnable = runnable
        handler.postDelayed(runnable, 6000L)
    }

    fun triggerPartnerNote(note: String, batteryPct: Int) {
        partnerNoteRunnable?.let { handler.removeCallbacks(it) }
        val isLow = batteryPct in 1..15
        _state.update {
            it.copy(
                petMode = PetMode.WAVE_POKE,
                partnerNoteText = if (isLow) "🪫 У напарника $batteryPct% заряда! $note" else "💌 $note (🔋$batteryPct%)",
                isPartnerBatteryLow = isLow,
                partnerBatteryPct = batteryPct
            )
        }
        val runnable = Runnable {
            _state.update {
                it.copy(
                    partnerNoteText = null,
                    isPartnerBatteryLow = false
                )
            }
            recalculatePetMode()
        }
        partnerNoteRunnable = runnable
        handler.postDelayed(runnable, 7000L)
    }

    fun triggerLowBatteryAlert(pct: Int = 12) {
        triggerPartnerNote("Телефон на грани выключения!", pct)
    }

    fun recalculatePetMode() {
        val current = _state.value
        val newMode = when {
            !current.isScreenOn -> PetMode.SLEEP
            current.isMusicPlaying && current.mediaTitle != null -> PetMode.MUSIC
            current.hasGuestPet -> PetMode.COUPLE_VISIT
            else -> PetMode.IDLE
        }
        _state.update { it.copy(petMode = newMode) }
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

    fun setCharging(charging: Boolean, batteryPct: Int = -1, triggerPulse: Boolean = false) {
        val until = if (triggerPulse) System.currentTimeMillis() + 3200L else 0L
        _state.update { current ->
            val level = if (batteryPct in 0..100) batteryPct else current.batteryLevel
            current.copy(
                isCharging = charging,
                chargingPulseUntilMs = until,
                batteryLevel = level
            )
        }
    }

    fun setRoomCode(code: String) {
        _state.update { it.copy(roomCode = code) }
    }

    fun setMyPetAway(away: Boolean) {
        _state.update { it.copy(isMyPetAway = away) }
    }

    fun setDoomscrollLimit(minutes: Int) {
        _state.update { it.copy(doomscrollLimitMinutes = minutes) }
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

    fun setIncomingRequest(fromUser: String?) {
        _state.update { it.copy(incomingFriendRequestFrom = fromUser) }
    }

    fun setUsernames(me: String, partner: String) {
        _state.update { it.copy(myUsername = me, partnerUsername = partner) }
    }
}

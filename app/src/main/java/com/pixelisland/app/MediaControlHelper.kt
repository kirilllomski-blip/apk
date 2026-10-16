package com.pixelisland.app

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/**
 * Хелпер управления мультимедиа для любых плееров (Яндекс.Музыка, VK Музыка, Spotify, YouTube Music).
 * Отправляет глобальные медиа-события через AudioManager, а также поддерживает интерактивное
 * переключение тестовых треков для демонстрации и отладки без внешних плееров.
 */
object MediaControlHelper {

    private val TEST_TRACKS = listOf(
        "Crystal Castles - Kerosene",
        "Король и Шут - Кукла колдуна",
        "Macan - Asphalt 8"
    )

    private var currentTestTrackIndex = 0

    private fun sendMediaKeyEvent(context: Context, keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(eventDown)
            audioManager.dispatchMediaKeyEvent(eventUp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Ставит на паузу или возобновляет воспроизведение в системном плеере.
     * Если сейчас включен тестовый трек в IslandStateBus, переключает его состояние паузы/воспроизведения.
     */
    fun playPause(context: Context) {
        sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

        val state = IslandStateBus.state.value
        if (state.mediaTitle != null) {
            val newPlaying = !state.isMusicPlaying
            IslandStateBus.setMedia(state.mediaTitle, newPlaying)
        } else if (state.petMode == PetMode.MUSIC) {
            // Если включен режим музыки, но название еще не задано, запускаем тестовый трек
            IslandStateBus.setMedia(TEST_TRACKS[currentTestTrackIndex], true)
        }
    }

    /**
     * Переключает на следующий трек в плеере.
     * Если играет тестовый трек в IslandStateBus, листает список по кругу:
     * "Crystal Castles - Kerosene" -> "Король и Шут - Кукла колдуна" -> "Macan - Asphalt 8".
     */
    fun nextTrack(context: Context) {
        sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)

        val state = IslandStateBus.state.value
        val currentTitle = state.mediaTitle

        val isTestTrack = currentTitle != null && TEST_TRACKS.any {
            it.contains(currentTitle, ignoreCase = true) || currentTitle.contains(it, ignoreCase = true)
        }

        if (isTestTrack || currentTitle == null) {
            val foundIndex = if (currentTitle != null) {
                TEST_TRACKS.indexOfFirst {
                    it.contains(currentTitle, ignoreCase = true) || currentTitle.contains(it, ignoreCase = true)
                }
            } else -1

            val nextIndex = if (foundIndex != -1) {
                (foundIndex + 1) % TEST_TRACKS.size
            } else {
                (currentTestTrackIndex + 1) % TEST_TRACKS.size
            }
            currentTestTrackIndex = nextIndex
            IslandStateBus.setMedia(TEST_TRACKS[nextIndex], true)
        }
    }

    /**
     * Переключает на предыдущий трек в плеере (и листает тестовый трек назад).
     */
    fun prevTrack(context: Context) {
        sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

        val state = IslandStateBus.state.value
        val currentTitle = state.mediaTitle

        val isTestTrack = currentTitle != null && TEST_TRACKS.any {
            it.contains(currentTitle, ignoreCase = true) || currentTitle.contains(it, ignoreCase = true)
        }

        if (isTestTrack || currentTitle == null) {
            val foundIndex = if (currentTitle != null) {
                TEST_TRACKS.indexOfFirst {
                    it.contains(currentTitle, ignoreCase = true) || currentTitle.contains(it, ignoreCase = true)
                }
            } else -1

            val prevIndex = if (foundIndex != -1) {
                if (foundIndex - 1 < 0) TEST_TRACKS.size - 1 else foundIndex - 1
            } else {
                if (currentTestTrackIndex - 1 < 0) TEST_TRACKS.size - 1 else currentTestTrackIndex - 1
            }
            currentTestTrackIndex = prevIndex
            IslandStateBus.setMedia(TEST_TRACKS[prevIndex], true)
        }
    }
}

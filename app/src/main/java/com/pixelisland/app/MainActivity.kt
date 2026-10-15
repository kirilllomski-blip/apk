package com.pixelisland.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private var tvCustomStatus: TextView? = null
    private var btnWatchAd: Button? = null

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RewardedAdManager.initFromPrefs(this)
        val prefs = getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Заголовок
        val titleView = TextView(this).apply {
            text = "Pixel Dynamic Island\nПанель управления"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(16))
        }
        mainLayout.addView(titleView)

        fun createSectionHeader(title: String): TextView {
            return TextView(this).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#00E5FF"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(16), 0, dp(8))
            }
        }

        fun createButton(buttonText: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = buttonText
                setTextColor(Color.WHITE)
                textSize = 14f
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#262626"))
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(46)
                ).apply {
                    setMargins(0, dp(4), 0, dp(6))
                }
                setOnClickListener { onClick() }
            }
        }

        fun createEditText(hintText: String, initialText: String = ""): EditText {
            return EditText(this).apply {
                hint = hintText
                setHintTextColor(Color.parseColor("#777777"))
                setTextColor(Color.WHITE)
                textSize = 14f
                setText(initialText)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E1E1E"))
                    setStroke(dp(1), Color.parseColor("#3D3D3D"))
                    cornerRadius = dp(8).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(4), 0, dp(6))
                }
            }
        }

        fun createLabel(labelText: String): TextView {
            return TextView(this).apply {
                text = labelText
                textSize = 13f
                setTextColor(Color.parseColor("#B0B0B0"))
                setPadding(0, dp(4), 0, dp(2))
            }
        }

        // ==================== Раздел 1: Разрешения ====================
        mainLayout.addView(createSectionHeader("1. Разрешения системы"))

        val btnAccessibility = createButton("1. Включить Островок (Спецвозможности)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        mainLayout.addView(btnAccessibility)

        val btnNotification = createButton("2. Доступ к музыке и уведомлениям") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        mainLayout.addView(btnNotification)

        // ==================== Раздел 2: Калибровка ====================
        mainLayout.addView(createSectionHeader("2. Калибровка положения у камеры"))

        val savedX = prefs.getInt("offset_x", 0)
        val savedY = prefs.getInt("offset_y", 0)
        val savedScale = prefs.getFloat("island_scale", 1.0f)

        val labelX = createLabel("Смещение по горизонтали (X): $savedX px")
        mainLayout.addView(labelX)

        val seekX = SeekBar(this).apply {
            max = 600
            progress = (savedX + 300).coerceIn(0, 600)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(8))
            }
        }
        mainLayout.addView(seekX)

        val labelY = createLabel("Смещение по вертикали (Y): $savedY px")
        mainLayout.addView(labelY)

        val seekY = SeekBar(this).apply {
            max = 300
            progress = (savedY + 50).coerceIn(0, 300)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(8))
            }
        }
        mainLayout.addView(seekY)

        val labelScale = createLabel(String.format("Масштаб островка: %.2fx", savedScale))
        mainLayout.addView(labelScale)

        val seekScale = SeekBar(this).apply {
            max = 200
            progress = ((savedScale * 100).toInt()).coerceIn(50, 200)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }
        mainLayout.addView(seekScale)

        fun onCalibrationChanged() {
            val currentX = seekX.progress - 300
            val currentY = seekY.progress - 50
            val currentScale = (seekScale.progress.coerceAtLeast(50)) / 100f

            labelX.text = "Смещение по горизонтали (X): $currentX px"
            labelY.text = "Смещение по вертикали (Y): $currentY px"
            labelScale.text = String.format("Масштаб островка: %.2fx", currentScale)

            prefs.edit()
                .putInt("offset_x", currentX)
                .putInt("offset_y", currentY)
                .putFloat("island_scale", currentScale)
                .apply()

            IslandStateBus.updateCalibration(currentX, currentY, currentScale)
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    onCalibrationChanged()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekX.setOnSeekBarChangeListener(seekListener)
        seekY.setOnSeekBarChangeListener(seekListener)
        seekScale.setOnSeekBarChangeListener(seekListener)

        // ==================== Раздел 3: Свой питомец (GIF / PNG) ====================
        mainLayout.addView(createSectionHeader("3. Свой питомец (GIF / PNG) — 12 часов за 2 рекламы"))

        val statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#FFD54F"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(8))
        }
        tvCustomStatus = statusView
        mainLayout.addView(statusView)

        val btnPickImage = createButton("📁 1. Выбрать картинку / GIF из Галереи") {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
            startActivityForResult(intent, 1001)
        }
        mainLayout.addView(btnPickImage)

        val adButton = createButton("📺 2. Смотреть рекламу (0/2) -> Открыть на 12 часов") {
            RewardedAdManager.showRewardedAd(this) {
                updateCustomPetStatus()
            }
        }
        btnWatchAd = adButton
        mainLayout.addView(adButton)

        val btnResetTimer = createButton("Сбросить таймер 12 часов (Тест окончания времени)") {
            val adsCount = prefs.getInt("ads_watched_count", 0)
            prefs.edit().putLong("custom_pet_expire_ms", 0L).apply()
            IslandStateBus.updateCustomPetAuth(adsCount, 0L)
            updateCustomPetStatus()
            Toast.makeText(this, "Таймер сброшен! Питомец заблокирован.", Toast.LENGTH_SHORT).show()
        }
        mainLayout.addView(btnResetTimer)

        // ==================== Раздел 4: Парный режим Firebase ====================
        mainLayout.addView(createSectionHeader("4. Парный режим (Firebase)"))

        val savedDbUrl = prefs.getString("database_url", "") ?: ""
        val savedRoomCode = prefs.getString("room_code", "") ?: ""

        val etFirebaseUrl = createEditText("https://your-db.firebasedatabase.app", savedDbUrl)
        mainLayout.addView(etFirebaseUrl)

        val etRoomCode = createEditText("Код комнаты, например 777777", savedRoomCode).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        mainLayout.addView(etRoomCode)

        val btnGenCode = createButton("Сгенерировать код пары") {
            val randomCode = (100000..999999).random().toString()
            etRoomCode.setText(randomCode)
        }
        mainLayout.addView(btnGenCode)

        val btnConnectPair = createButton("Подключить парный островок") {
            val url = etFirebaseUrl.text.toString().trim()
            val code = etRoomCode.text.toString().trim()
            if (url.isBlank() || code.isBlank()) {
                Toast.makeText(this, "Введите URL базы и код комнаты!", Toast.LENGTH_SHORT).show()
            } else {
                FirebaseCoupleManager.startPairing(this, url, code)
                Toast.makeText(this, "Подключение к комнате $code...", Toast.LENGTH_SHORT).show()
            }
        }
        mainLayout.addView(btnConnectPair)

        val btnSendHeart = createButton("Тест: Отправить ❤️ партнёру") {
            FirebaseCoupleManager.sendPartnerAction("HEART")
            Toast.makeText(this, "❤️ Отправлено партнёру!", Toast.LENGTH_SHORT).show()
        }
        mainLayout.addView(btnSendHeart)

        val btnSendVisit = createButton("Тест: Отправить кота в гости") {
            FirebaseCoupleManager.sendPartnerAction("VISIT")
            Toast.makeText(this, "🐾 Кот отправлен в гости!", Toast.LENGTH_SHORT).show()
        }
        mainLayout.addView(btnSendVisit)

        val etPartnerNote = createEditText("Напиши записку, например: Купи кофе ☕️")
        mainLayout.addView(etPartnerNote)

        val btnSendNote = createButton("💌 Отправить записку и мой % заряда на островок партнёра") {
            val noteText = etPartnerNote.text.toString().trim()
            if (noteText.isBlank()) {
                Toast.makeText(this, "Введите текст записки!", Toast.LENGTH_SHORT).show()
            } else {
                FirebaseCoupleManager.sendPartnerNote(this, noteText)
                Toast.makeText(this, "Записка отправлена партнёру!", Toast.LENGTH_SHORT).show()
            }
        }
        mainLayout.addView(btnSendNote)

        // ==================== Раздел 5: Локальные тесты ====================
        mainLayout.addView(createSectionHeader("5. Локальные тесты островка"))

        val btnTestMusic = createButton("Тест: Играет музыка") {
            IslandStateBus.setMedia("Crystal Castles - Kerosene")
        }
        mainLayout.addView(btnTestMusic)

        val btnStopMusic = createButton("Тест: Стоп музыка") {
            IslandStateBus.setMedia(null)
        }
        mainLayout.addView(btnStopMusic)

        val btnTestCharge = createButton("Тест: Идет зарядка") {
            IslandStateBus.setCharging(true)
        }
        mainLayout.addView(btnTestCharge)

        val btnTestNotification = createButton("Тест: Пришло сообщение") {
            IslandStateBus.setNotification("Telegram: Привет, как дела?")
        }
        mainLayout.addView(btnTestNotification)

        val btnTestDoomscroll = createButton("Тест: Думскроллинг (Мемная табличка)") {
            IslandStateBus.triggerDoomscrollWarning("🌱 Хватит листать рилсы! Иди потрогай траву!")
        }
        mainLayout.addView(btnTestDoomscroll)

        val btnTestTilt = createButton("Тест: Кот повис на краю камеры (Наклон)") {
            IslandStateBus.updateTilt(35f, true)
            Handler(Looper.getMainLooper()).postDelayed({
                IslandStateBus.updateTilt(0f, false)
            }, 4000L)
        }
        mainLayout.addView(btnTestTilt)

        val btnToggleExpand = createButton("Раскрыть / Свернуть островок") {
            IslandStateBus.setExpanded(!IslandStateBus.state.value.isExpanded)
        }
        mainLayout.addView(btnToggleExpand)

        scrollView.addView(mainLayout)
        setContentView(scrollView)

        updateCustomPetStatus()
    }

    override fun onResume() {
        super.onResume()
        RewardedAdManager.initFromPrefs(this)
        updateCustomPetStatus()
    }

    private fun updateCustomPetStatus() {
        val state = IslandStateBus.state.value
        val timeFormatted = RewardedAdManager.getRemainingTimeFormatted(this)
        tvCustomStatus?.text = "Статус: $timeFormatted | Прогресс: ${state.adsWatchedCount}/2"
        btnWatchAd?.text = "📺 2. Смотреть рекламу (${state.adsWatchedCount}/2) -> Открыть на 12 часов"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val targetFile = File(filesDir, "custom_pet_asset")
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    IslandStateBus.notifyCustomPetFileChanged()

                    if (!RewardedAdManager.isCustomPetActive(this)) {
                        Toast.makeText(
                            this,
                            "Питомец загружен! Посмотрите 2 рекламы ниже, чтобы выпустить его на островок на 12 часов",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this, "Питомец успешно обновлен!", Toast.LENGTH_SHORT).show()
                    }
                    updateCustomPetStatus()
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка загрузки файла: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

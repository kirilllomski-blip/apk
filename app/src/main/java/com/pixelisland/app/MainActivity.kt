package com.pixelisland.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private var activeTab = 0
    private val tabButtons = mutableListOf<TextView>()
    private val tabContainers = mutableListOf<LinearLayout>()

    private var previewView: InteractiveToyPreviewView? = null
    private var tvCustomStatus: TextView? = null
    private var btnWatchAd: Button? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RewardedAdManager.initFromPrefs(this)
        val prefs = getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)

        val rootScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0C0E14"))
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(32))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ==================== 1. ВЕРХНИЙ ХЕДЕР ====================
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleText = TextView(this).apply {
            text = "Pixel Island 🏝️"
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitleText = TextView(this).apply {
            text = "Интерактивный тамагочи-островок"
            textSize = 12f
            setTextColor(Color.parseColor("#7E8B9B"))
            setPadding(0, dp(2), 0, 0)
        }
        titleContainer.addView(titleText)
        titleContainer.addView(subtitleText)
        headerLayout.addView(titleContainer)

        val statusPill = TextView(this).apply {
            text = "● Островок"
            textSize = 11f
            setTextColor(Color.parseColor("#4ECCA3"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#152D25"))
                setStroke(dp(1), Color.parseColor("#275A48"))
                cornerRadius = dp(20f)
            }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        headerLayout.addView(statusPill)
        mainLayout.addView(headerLayout)

        // ==================== 2. ЖИВАЯ ИГРУШКА-КОНСТРУКТОР (LIVE PREVIEW) ====================
        val previewCard = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141821"))
                setStroke(dp(1), Color.parseColor("#242B3B"))
                cornerRadius = dp(22f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(130)
            ).apply {
                setMargins(0, 0, 0, dp(16))
            }
        }

        val toyPreview = InteractiveToyPreviewView(this)
        previewView = toyPreview
        previewCard.addView(toyPreview)

        val hintPill = TextView(this).apply {
            text = "Нажми на кота в островке!"
            textSize = 10f
            setTextColor(Color.parseColor("#A4B3C6"))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 15, 20, 28))
                cornerRadius = dp(12f)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, dp(8))
            }
        }
        previewCard.addView(hintPill)
        mainLayout.addView(previewCard)

        // ==================== 3. НАВИГАЦИОННЫЕ ТАБЫ ====================
        val tabsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, dp(14))
        }

        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val tabTitles = listOf("🐾 Питомец", "📍 Позиция", "❤️ Пара", "⚡ Тесты")
        tabTitles.forEachIndexed { index, title ->
            val tabBtn = TextView(this).apply {
                text = title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(9), dp(16), dp(9))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, dp(8), 0)
                }
                setOnClickListener { selectTab(index) }
            }
            tabButtons.add(tabBtn)
            tabsLayout.addView(tabBtn)
        }
        tabsScroll.addView(tabsLayout)
        mainLayout.addView(tabsScroll)

        // ==================== 4. ХЕЛПЕРЫ ====================
        fun createCardContainer(): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#151922"))
                    setStroke(dp(1), Color.parseColor("#222938"))
                    cornerRadius = dp(18f)
                }
                setPadding(dp(16), dp(16), dp(16), dp(16))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        }

        fun createStyledInput(hintText: String, initialVal: String = ""): EditText {
            return EditText(this).apply {
                hint = hintText
                setHintTextColor(Color.parseColor("#606C7E"))
                setTextColor(Color.WHITE)
                textSize = 13.5f
                setText(initialVal)
                setPadding(dp(14), dp(11), dp(14), dp(11))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#10131B"))
                    setStroke(dp(1), Color.parseColor("#2B3346"))
                    cornerRadius = dp(10f)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(3), 0, dp(7))
                }
            }
        }

        // ==================== ВКЛАДКА 0: ПИТОМЕЦ ====================
        val petTab = createCardContainer()

        val petBadge = TextView(this).apply {
            text = "КАСТОМИЗАЦИЯ ТАМАГОЧИ"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(0, 0, 0, dp(4))
        }
        petTab.addView(petBadge)

        val statusView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#FFD54F"))
            setPadding(0, 0, 0, dp(12))
        }
        tvCustomStatus = statusView
        petTab.addView(statusView)

        val btnPick = createStyledButton("📁 Выбрать фото / GIF из галереи", "#6C5CE7", "#FFFFFF") {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(intent, 1001)
        }
        petTab.addView(btnPick)

        val adBtn = createStyledButton("📺 Смотреть рекламу -> +12 часов", "#2D3436", "#FFFFFF") {
            RewardedAdManager.showRewardedAd(this) {
                updateCustomPetStatus()
                previewView?.invalidate()
            }
        }
        btnWatchAd = adBtn
        petTab.addView(adBtn)

        val btnReset = createStyledButton("🔄 Сбросить скин на стандартного кота", "#1C212D", "#8A97A8") {
            val ads = prefs.getInt("ads_watched_count", 0)
            prefs.edit().putLong("custom_pet_expire_ms", 0L).apply()
            IslandStateBus.updateCustomPetAuth(ads, 0L)
            updateCustomPetStatus()
            previewView?.invalidate()
            Toast.makeText(this, "Скин сброшен на базового кота", Toast.LENGTH_SHORT).show()
        }
        petTab.addView(btnReset)

        tabContainers.add(petTab)
        mainLayout.addView(petTab)

        // ==================== ВКЛАДКА 1: ПОЗИЦИЯ / КОНСТРУКТОР ====================
        val posTab = createCardContainer()

        val posBadge = TextView(this).apply {
            text = "ТОЧНАЯ ПОДГОНКА ПОД КАМЕРУ"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(0, 0, 0, dp(6))
        }
        posTab.addView(posBadge)

        var savedX = prefs.getInt("offset_x", 0)
        var savedY = prefs.getInt("offset_y", 0)
        var savedScale = prefs.getFloat("island_scale", 1.0f)

        val joystickFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(12))
        }

        fun updateCalibrationAndSave(nx: Int, ny: Int, ns: Float) {
            savedX = nx
            savedY = ny
            savedScale = ns
            prefs.edit().putInt("offset_x", savedX).putInt("offset_y", savedY).putFloat("island_scale", savedScale).apply()
            IslandStateBus.updateCalibration(savedX, savedY, savedScale)
            previewView?.invalidate()
        }

        fun createDpadBtn(symbol: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = symbol
                textSize = 16f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#222938"))
                    cornerRadius = dp(10f)
                }
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(44)).apply {
                    setMargins(dp(3), dp(2), dp(3), dp(2))
                }
                setOnClickListener {
                    vibrateQuick()
                    onClick()
                }
            }
        }

        val rowTop = LinearLayout(this).apply { gravity = Gravity.CENTER }
        rowTop.addView(createDpadBtn("▲") { updateCalibrationAndSave(savedX, savedY - 4, savedScale) })
        joystickFrame.addView(rowTop)

        val rowMid = LinearLayout(this).apply { gravity = Gravity.CENTER }
        rowMid.addView(createDpadBtn("◄") { updateCalibrationAndSave(savedX - 6, savedY, savedScale) })
        rowMid.addView(createDpadBtn("● 0") { updateCalibrationAndSave(0, 0, 1.0f) })
        rowMid.addView(createDpadBtn("►") { updateCalibrationAndSave(savedX + 6, savedY, savedScale) })
        joystickFrame.addView(rowMid)

        val rowBot = LinearLayout(this).apply { gravity = Gravity.CENTER }
        rowBot.addView(createDpadBtn("▼") { updateCalibrationAndSave(savedX, savedY + 4, savedScale) })
        joystickFrame.addView(rowBot)

        posTab.addView(joystickFrame)

        val scaleLabel = TextView(this).apply {
            text = String.format("Размер островка: %.2fx", savedScale)
            textSize = 12f
            setTextColor(Color.parseColor("#A4B3C6"))
        }
        posTab.addView(scaleLabel)

        val scaleSeek = SeekBar(this).apply {
            max = 150
            progress = ((savedScale - 0.5f) * 100).toInt().coerceIn(0, 150)
            setPadding(0, dp(6), 0, dp(6))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val ns = 0.5f + (progress / 100f)
                        scaleLabel.text = String.format("Размер островка: %.2fx", ns)
                        updateCalibrationAndSave(savedX, savedY, ns)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        posTab.addView(scaleSeek)

        tabContainers.add(posTab)
        mainLayout.addView(posTab)

        // ==================== ВКЛАДКА 2: ПАРА ====================
        val pairTab = createCardContainer()

        val pairBadge = TextView(this).apply {
            text = "СИНХРОНИЗАЦИЯ ПАРЫ (FIREBASE)"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FF7675"))
            setPadding(0, 0, 0, dp(8))
        }
        pairTab.addView(pairBadge)

        val savedDbUrl = prefs.getString("database_url", "") ?: ""
        val savedRoomCode = prefs.getString("room_code", "") ?: ""

        val etDb = createStyledInput("URL базы: https://db.firebasedatabase.app", savedDbUrl)
        pairTab.addView(etDb)

        val etCode = createStyledInput("Код комнаты (например 777777)", savedRoomCode).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        pairTab.addView(etCode)

        val pairBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }

        val btnDice = Button(this).apply {
            text = "🎲 Код"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#262D3D"))
                cornerRadius = dp(10f)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(0, 0, dp(4), 0) }
            setOnClickListener {
                vibrateQuick()
                val rnd = (100000..999999).random().toString()
                etCode.setText(rnd)
            }
        }
        pairBtnsRow.addView(btnDice)

        val btnConnect = Button(this).apply {
            text = "🔗 Подключить"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0984E3"))
                cornerRadius = dp(10f)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.5f).apply { setMargins(dp(4), 0, 0, 0) }
            setOnClickListener {
                vibrateQuick()
                val url = etDb.text.toString().trim()
                val code = etCode.text.toString().trim()
                if (url.isBlank() || code.isBlank()) {
                    Toast.makeText(this@MainActivity, "Заполните URL и код!", Toast.LENGTH_SHORT).show()
                } else {
                    FirebaseCoupleManager.startPairing(this@MainActivity, url, code)
                    Toast.makeText(this@MainActivity, "Подключение к комнате $code...", Toast.LENGTH_SHORT).show()
                }
            }
        }
        pairBtnsRow.addView(btnConnect)
        pairTab.addView(pairBtnsRow)

        val noteBox = createStyledInput("Напиши записку (например: Люблю тебя ❤️)")
        pairTab.addView(noteBox)

        val btnSendNote = createStyledButton("💌 Отправить записку и заряд на островок", "#E84393", "#FFFFFF") {
            val text = noteBox.text.toString().trim()
            if (text.isNotBlank()) {
                FirebaseCoupleManager.sendPartnerNote(this, text)
                Toast.makeText(this, "Записка улетела на экран партнёра!", Toast.LENGTH_SHORT).show()
                noteBox.setText("")
            }
        }
        pairTab.addView(btnSendNote)

        val coupleActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnHeart = Button(this).apply {
            text = "❤️ Погладить"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#262D3D"))
                cornerRadius = dp(10f)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(0, 0, dp(4), 0) }
            setOnClickListener {
                vibrateQuick()
                FirebaseCoupleManager.sendPartnerAction("HEART")
                Toast.makeText(this@MainActivity, "❤️ Сердечко отправлено!", Toast.LENGTH_SHORT).show()
            }
        }
        val btnVisit = Button(this).apply {
            text = "🐾 В гости"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#262D3D"))
                cornerRadius = dp(10f)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), 0, 0, 0) }
            setOnClickListener {
                vibrateQuick()
                FirebaseCoupleManager.sendPartnerAction("VISIT")
                Toast.makeText(this@MainActivity, "🐾 Кот отправлен в гости на 20 сек!", Toast.LENGTH_SHORT).show()
            }
        }
        coupleActionsRow.addView(btnHeart)
        coupleActionsRow.addView(btnVisit)
        pairTab.addView(coupleActionsRow)

        tabContainers.add(pairTab)
        mainLayout.addView(pairTab)

        // ==================== ВКЛАДКА 3: ТЕСТЫ И СИСТЕМА ====================
        val testsTab = createCardContainer()

        val testsBadge = TextView(this).apply {
            text = "СИСТЕМНЫЕ РАЗРЕШЕНИЯ И ТЕСТЫ"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FDCB6E"))
            setPadding(0, 0, 0, dp(8))
        }
        testsTab.addView(testsBadge)

        val permRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnPerm1 = Button(this).apply {
            text = "⚙️ Спецвозможности"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222938"))
                cornerRadius = dp(10f)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(0, 0, dp(4), dp(8)) }
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val btnPerm2 = Button(this).apply {
            text = "🔔 Уведомления"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222938"))
                cornerRadius = dp(10f)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), 0, 0, dp(8)) }
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        permRow.addView(btnPerm1)
        permRow.addView(btnPerm2)
        testsTab.addView(permRow)

        fun createTestBtn(title: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = title
                textSize = 12f
                setTextColor(Color.parseColor("#CBD5E1"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1B212D"))
                    cornerRadius = dp(10f)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    setMargins(dp(2), dp(3), dp(2), dp(3))
                }
                setOnClickListener {
                    vibrateQuick()
                    onClick()
                    previewView?.invalidate()
                }
            }
        }

        val testRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        testRow1.addView(createTestBtn("🎵 Музыка") { IslandStateBus.setMedia("Crystal Castles - Kerosene") })
        testRow1.addView(createTestBtn("⏹️ Стоп") { IslandStateBus.setMedia(null) })
        testRow1.addView(createTestBtn("⚡ Зарядка") { IslandStateBus.setCharging(true) })
        testsTab.addView(testRow1)

        val testRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        testRow2.addView(createTestBtn("💬 Сообщение") { IslandStateBus.setNotification("Telegram: Привет, котик!") })
        testRow2.addView(createTestBtn("🌱 Рилсы") { IslandStateBus.triggerDoomscrollWarning("🌱 Хватит листать рилсы! Потрогай траву!") })
        testRow2.addView(createTestBtn("🤸 Наклон") {
            IslandStateBus.updateTilt(35f, true)
            Handler(Looper.getMainLooper()).postDelayed({ IslandStateBus.updateTilt(0f, false) }, 4000L)
        })
        testsTab.addView(testRow2)

        val btnToggle = createStyledButton("Развернуть / Свернуть островок", "#2D3436", "#FFFFFF") {
            IslandStateBus.setExpanded(!IslandStateBus.state.value.isExpanded)
        }
        testsTab.addView(btnToggle)

        tabContainers.add(testsTab)
        mainLayout.addView(testsTab)

        rootScrollView.addView(mainLayout)
        setContentView(rootScrollView)

        selectTab(0)
        updateCustomPetStatus()
    }

    private fun createStyledButton(
        title: String,
        bgColor: String = "#232A3B",
        textColor: String = "#FFFFFF",
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = title
            setTextColor(Color.parseColor(textColor))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = dp(12f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply {
                setMargins(0, dp(4), 0, dp(6))
            }
            setOnClickListener {
                vibrateQuick()
                onClick()
            }
        }
    }

    private fun selectTab(index: Int) {
        activeTab = index
        tabButtons.forEachIndexed { i, btn ->
            if (i == index) {
                btn.setTextColor(Color.WHITE)
                btn.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#6C5CE7"))
                    cornerRadius = dp(14f)
                }
            } else {
                btn.setTextColor(Color.parseColor("#7A889B"))
                btn.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#151922"))
                    cornerRadius = dp(14f)
                }
            }
        }

        tabContainers.forEachIndexed { i, container ->
            container.visibility = if (i == index) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        RewardedAdManager.initFromPrefs(this)
        updateCustomPetStatus()
        previewView?.startAnimation()
    }

    override fun onPause() {
        super.onPause()
        previewView?.stopAnimation()
    }

    private fun updateCustomPetStatus() {
        val state = IslandStateBus.state.value
        val timeFormatted = RewardedAdManager.getRemainingTimeFormatted(this)
        tvCustomStatus?.text = "Статус: $timeFormatted | Просмотрено: ${state.adsWatchedCount}/2"
        btnWatchAd?.text = "📺 Смотреть рекламу (${state.adsWatchedCount}/2) -> +12 часов"
    }

    private fun vibrateQuick() {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(18L)
            }
        } catch (ignored: Exception) {
        }
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
                            "Питомец загружен! Посмотрите 2 рекламы, чтобы активировать его на 12 часов",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this, "Питомец успешно обновлен!", Toast.LENGTH_SHORT).show()
                    }
                    updateCustomPetStatus()
                    previewView?.invalidate()
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class InteractiveToyPreviewView(context: Context) : View(context) {

        private val renderer = PixelPetRenderer(context)
        private val pillRect = RectF()
        private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val cameraDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#090B0F") }
        private val cameraGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A2333")
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sp(10.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF69B4")
            textSize = sp(10f)
            typeface = Typeface.DEFAULT_BOLD
        }

        private var frameIndex = 0
        private val animHandler = Handler(Looper.getMainLooper())
        private var isRunning = false

        private val animRunnable = object : Runnable {
            override fun run() {
                frameIndex++
                invalidate()
                animHandler.postDelayed(this, 300L)
            }
        }

        fun startAnimation() {
            if (!isRunning) {
                isRunning = true
                animHandler.post(animRunnable)
            }
        }

        fun stopAnimation() {
            isRunning = false
            animHandler.removeCallbacks(animRunnable)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            startAnimation()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val state = IslandStateBus.state.value

            val camCenterX = w / 2f
            val camCenterY = dp(28f)
            canvas.drawCircle(camCenterX, camCenterY, dp(7f), cameraGlowPaint)
            canvas.drawCircle(camCenterX, camCenterY, dp(6f), cameraDotPaint)

            val baseW = if (state.isExpanded) dp(210f) else dp(115f) * state.islandScale
            val baseH = if (state.isExpanded) dp(70f) else dp(30f) * state.islandScale

            val pillLeft = (w - baseW) / 2f + (state.offsetX * 0.25f)
            val pillTop = camCenterY - (baseH / 2f) + (state.offsetY * 0.25f)
            pillRect.set(pillLeft, pillTop, pillLeft + baseW, pillTop + baseH)

            val radius = baseH / 2f
            canvas.drawRoundRect(pillRect, radius, radius, pillPaint)

            val petSize = if (state.isExpanded) dp(32f) else dp(22f) * state.islandScale
            val petRect = if (state.isExpanded) {
                RectF(pillRect.left + dp(10f), pillRect.centerY() - petSize / 2f, pillRect.left + dp(10f) + petSize, pillRect.centerY() + petSize / 2f)
            } else {
                RectF(pillRect.right - dp(6f) - petSize, pillRect.centerY() - petSize / 2f, pillRect.right - dp(6f), pillRect.centerY() + petSize / 2f)
            }

            renderer.drawPet(canvas, petRect, state.petMode, frameIndex)

            if (state.isExpanded) {
                val tX = petRect.right + dp(8f)
                canvas.drawText("Pixel Island", tX, pillRect.top + dp(24f), textPaint)
                canvas.drawText("❤️ Сытый и довольный", tX, pillRect.top + dp(44f), heartPaint)
            } else {
                if (state.petMode == PetMode.MUSIC) {
                    canvas.drawCircle(pillRect.left + dp(12f), pillRect.centerY(), dp(3.5f), heartPaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                vibrateQuick()
                IslandStateBus.setExpanded(!IslandStateBus.state.value.isExpanded)
                invalidate()
                return true
            }
            return super.onTouchEvent(event)
        }
    }
}

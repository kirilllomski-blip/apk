package com.pixelisland.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var voxelRoomView: VoxelRoomView? = null
    private var activeTab = 0 // 0: ТЕСТЫ, 1: ДРУЗЬЯ, 2: КАМЕРА, 3: СКИНЫ

    private val dockButtons = mutableListOf<LinearLayout>()
    private val tabContainers = mutableListOf<LinearLayout>()

    private var friendsTabContainer: LinearLayout? = null
    private var tvCustomStatus: TextView? = null
    private var btnWatchAd: Button? = null

    // Цветовая палитра: Dark Tactical / Gunmetal Constructor
    private val C_SCREEN_BG      = Color.parseColor("#0F121A")
    private val C_CARD_BG        = Color.parseColor("#181D2A")
    private val C_CARD_BORDER    = Color.parseColor("#2A344B")
    private val C_DOCK_ITEM_BG   = Color.parseColor("#161B26")
    private val C_DOCK_SELECTED  = Color.parseColor("#00B8D4")
    private val C_BTN_BLUE       = Color.parseColor("#2563EB")
    private val C_BTN_CYAN       = Color.parseColor("#00B8D4")
    private val C_BTN_EMERALD    = Color.parseColor("#10B981")
    private val C_BTN_AMBER      = Color.parseColor("#F59E0B")
    private val C_BTN_DARK       = Color.parseColor("#242C3D")
    private val C_TEXT_MUTED     = Color.parseColor("#94A3B8")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RewardedAdManager.initFromPrefs(this)
        FirebaseCoupleManager.init(this)

        val rootScrollView = ScrollView(this).apply {
            setBackgroundColor(C_SCREEN_BG)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(36))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ==================== 1. ВЕРХНЯЯ СЦЕНА: VOXEL ROOM VIEW ====================
        val roomView = VoxelRoomView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(320)
            )
        }
        voxelRoomView = roomView
        mainLayout.addView(roomView)

        // ==================== 2. НИЖНИЙ ДОК ИЗ 5 БЛОЧНЫХ КНОПОК-КУБИКОВ ====================
        val dockLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dockItems = listOf(
            Triple("🍔", "КОРМ", -1),      // -1: Действие (не вкладка)
            Triple("⚡", "ТЕСТЫ", 0),
            Triple("🤝", "ДРУЗЬЯ", 1),
            Triple("🎯", "КАМЕРА", 2),
            Triple("⚙️", "СКИНЫ", 3)
        )

        dockButtons.clear()
        dockItems.forEach { (icon, title, tabIndex) ->
            val itemBtn = createDockButton(icon, title, tabIndex == activeTab) {
                vibrateQuick()
                if (tabIndex == -1) {
                    // [ 🍔 КОРМ ] — сразу кормит питомца без перехода во вкладку
                    IslandStateBus.feedPet()
                    Toast.makeText(this, "🍔 Кибер-Кот покормлен! (+20 сытости, +15 XP)", Toast.LENGTH_SHORT).show()
                } else {
                    selectTab(tabIndex)
                }
            }
            dockButtons.add(itemBtn)
            dockLayout.addView(itemBtn)
        }
        mainLayout.addView(dockLayout)

        // ==================== 3. КОНТЕЙНЕРЫ ВКЛАДОК ====================
        tabContainers.clear()

        // Вкладка 0: ТЕСТЫ И АНИМАЦИИ
        val tabTests = createTestsTab()
        tabContainers.add(tabTests)
        mainLayout.addView(tabTests)

        // Вкладка 1: ДРУЗЬЯ ПО @USERNAME
        val tabFriends = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        friendsTabContainer = tabFriends
        rebuildFriendsTab()
        tabContainers.add(tabFriends)
        mainLayout.addView(tabFriends)

        // Вкладка 2: КАМЕРА (КАЛИБРОВКА)
        val tabCamera = createCameraCalibrationTab()
        tabContainers.add(tabCamera)
        mainLayout.addView(tabCamera)

        // Вкладка 3: СКИНЫ (GIF/PNG + PRO И TELEGRAM)
        val tabSkins = createSkinsTab()
        tabContainers.add(tabSkins)
        mainLayout.addView(tabSkins)

        rootScrollView.addView(mainLayout)
        setContentView(rootScrollView)

        selectTab(0)
        observeState()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun observeState() {
        scope.launch {
            IslandStateBus.state.collect { state ->
                updateFriendBadge(state.incomingFriendRequestFrom != null)
                rebuildFriendsTab()
                updateCustomPetStatus()
            }
        }
    }

    // ==================== ДОК И ПЕРЕКЛЮЧЕНИЕ ВКЛАДОК ====================

    private fun createDockButton(icon: String, label: String, isSelected: Boolean, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
            val lp = LinearLayout.LayoutParams(0, dp(64), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            layoutParams = lp

            background = GradientDrawable().apply {
                setColor(C_DOCK_ITEM_BG)
                cornerRadius = dp(12f)
                setStroke(dp(2), if (isSelected) C_DOCK_SELECTED else C_CARD_BORDER)
            }

            val iconTv = TextView(context).apply {
                text = icon
                textSize = 19f
                gravity = Gravity.CENTER
            }

            val labelTv = TextView(context).apply {
                text = label
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isSelected) C_DOCK_SELECTED else C_TEXT_MUTED)
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            }

            addView(iconTv)
            addView(labelTv)

            setOnClickListener { onClick() }
        }
    }

    private fun updateFriendBadge(hasIncomingRequest: Boolean) {
        if (dockButtons.size > 2) {
            val friendsDockBtn = dockButtons[2]
            val labelTv = friendsDockBtn.getChildAt(1) as? TextView
            if (hasIncomingRequest) {
                labelTv?.text = "! Заявка"
                labelTv?.setTextColor(C_BTN_AMBER)
            } else {
                labelTv?.text = "ДРУЗЬЯ"
                if (activeTab == 1) {
                    labelTv?.setTextColor(C_DOCK_SELECTED)
                } else {
                    labelTv?.setTextColor(C_TEXT_MUTED)
                }
            }
        }
    }

    private fun selectTab(tabIndex: Int) {
        activeTab = tabIndex
        for (i in tabContainers.indices) {
            tabContainers[i].visibility = if (i == tabIndex) View.VISIBLE else View.GONE
        }

        // Обновляем визуальный фокус док-кнопок
        for (i in dockButtons.indices) {
            val btn = dockButtons[i]
            val isCurrent = (i == tabIndex + 1) // +1 т.к. 0-я кнопка [ 🍔 КОРМ ]
            val strokeColor = if (isCurrent) C_DOCK_SELECTED else C_CARD_BORDER
            (btn.background as? GradientDrawable)?.setStroke(dp(2), strokeColor)

            val labelTv = btn.getChildAt(1) as? TextView
            labelTv?.setTextColor(if (isCurrent) C_DOCK_SELECTED else C_TEXT_MUTED)
        }
    }

    // ==================== ВКЛАДКА 0: ТЕСТЫ И АНИМАЦИИ ====================

    private fun createTestsTab(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Карточка разрешений системы
        val permCard = createConstructorCard().apply {
            addView(createSectionTitle("⚙️ РАЗРЕШЕНИЯ СИСТЕМЫ"))
            addView(createDescriptionText("Для отображения островка поверх всех окон включите службы:"))

            val btnAccess = createTacticalButton("🔓 1. Служба специальных возможностей (Островок)", C_BTN_BLUE) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            addView(btnAccess)

            val btnNotif = createTacticalButton("🔔 2. Доступ к уведомлениям (Плеер и сообщения)", C_BTN_DARK) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            addView(btnNotif)
        }
        layout.addView(permCard)

        // 2. Карточка зрелищных 3D-анимаций
        val testCard = createConstructorCard().apply {
            addView(createSectionTitle("⚡️ ТЕСТЫ 3D-АНИМАЦИЙ КОНСТРУКТОРА"))
            addView(createDescriptionText("Нажмите для проверки многокадровых эффектов кота на островке:"))

            // Строка 1
            val row1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row1.addView(createSmallTestButton("🎵 Тест: DJ Музыка", C_BTN_CYAN, 1f) {
                vibrateQuick()
                val current = IslandStateBus.state.value.mediaTitle
                if (current != null) {
                    IslandStateBus.setMedia(null)
                    Toast.makeText(context, "Музыка выключена", Toast.LENGTH_SHORT).show()
                } else {
                    IslandStateBus.setMedia("Crystal Castles - Kerosene")
                    Toast.makeText(context, "🎵 DJ-пульт и неоновые ноты запущены!", Toast.LENGTH_SHORT).show()
                }
            })
            row1.addView(createSmallTestButton("⚡️ Тест: Энерго-Зарядка", C_BTN_EMERALD, 1f) {
                vibrateQuick()
                val isCharging = !IslandStateBus.state.value.isCharging
                IslandStateBus.setCharging(isCharging)
                Toast.makeText(context, if (isCharging) "⚡ Искры и энергетическое поле!" else "Зарядка отключена", Toast.LENGTH_SHORT).show()
            })
            addView(row1)

            // Строка 2
            val row2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row2.addView(createSmallTestButton("📩 Тест: Сообщение", C_BTN_BLUE, 1f) {
                vibrateQuick()
                IslandStateBus.setNotification("Telegram: Привет, напарник! 🐾")
                Toast.makeText(context, "Конверт в лапах и облачко [ ! ]", Toast.LENGTH_SHORT).show()
            })
            row2.addView(createSmallTestButton("🚨 Тест: Анти-Рилсы", C_BTN_AMBER, 1f) {
                vibrateQuick()
                IslandStateBus.triggerDoomscrollWarning("🌱 Хватит листать рилсы! Потрогай траву!")
                Toast.makeText(context, "Зум 1.4x, красные глаза и стук лапой!", Toast.LENGTH_SHORT).show()
            })
            addView(row2)

            // Строка 3
            val row3 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row3.addView(createSmallTestButton("📐 Тест: Наклон", C_BTN_DARK, 1f) {
                vibrateQuick()
                IslandStateBus.updateTilt(36f, true)
                Toast.makeText(context, "Кот повис на углу капсулы и раскачивается!", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    IslandStateBus.updateTilt(0f, false)
                }, 5000L)
            })
            row3.addView(createSmallTestButton("🍔 Тест: Кормление", C_BTN_AMBER, 1f) {
                vibrateQuick()
                IslandStateBus.feedPet()
                Toast.makeText(context, "3D Бургер падает, кот жует, салют XP!", Toast.LENGTH_SHORT).show()
            })
            addView(row3)
        }
        layout.addView(testCard)

        return layout
    }

    // ==================== ВКЛАДКА 1: ДРУЗЬЯ ПО @USERNAME ====================

    private fun rebuildFriendsTab() {
        val container = friendsTabContainer ?: return
        container.removeAllViews()

        val state = IslandStateBus.state.value
        val myName = FirebaseCoupleManager.getMyUsername(this)

        // БЛОК 1: Твой никнейм
        val myProfileCard = createConstructorCard().apply {
            addView(createSectionTitle("👤 ТВОЙ НИКНЕЙМ"))
            addView(createDescriptionText("Задай ник, по которому напарник сможет найти тебя:"))

            val nameInput = createConstructorInput(if (myName.isNotBlank()) "@$myName" else "@").apply {
                hint = "@твой_ник (например, ivan)"
            }
            addView(nameInput)

            val btnSave = createTacticalButton("💾 Сохранить мой @ник", C_BTN_BLUE) {
                vibrateQuick()
                val raw = nameInput.text.toString()
                FirebaseCoupleManager.registerMyUsername(this@MainActivity, raw) { success, msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    rebuildFriendsTab()
                }
            }
            addView(btnSave)
        }
        container.addView(myProfileCard)

        // БЛОК 2: Входящая заявка в друзья (если есть)
        if (!state.incomingFriendRequestFrom.isNullOrBlank()) {
            val fromUser = state.incomingFriendRequestFrom
            val reqCard = createConstructorCard().apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#261E14"))
                    cornerRadius = dp(14f)
                    setStroke(dp(2), C_BTN_AMBER)
                }

                addView(TextView(context).apply {
                    text = "🔔 ВХОДЯЩАЯ ЗАЯВКА В ДРУЗЬЯ"
                    setTextColor(C_BTN_AMBER)
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, 0, dp(4))
                })

                addView(TextView(context).apply {
                    text = "Пользователь @$fromUser хочет объединить островки в пару!"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setPadding(0, 0, 0, dp(12))
                })

                val btnRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                btnRow.addView(createSmallTestButton("✅ ПРИНЯТЬ", C_BTN_EMERALD, 1f) {
                    vibrateQuick()
                    FirebaseCoupleManager.acceptFriendRequest(this@MainActivity, fromUser)
                    rebuildFriendsTab()
                })

                btnRow.addView(createSmallTestButton("❌ ОТКЛОНИТЬ", C_BTN_DARK, 1f) {
                    vibrateQuick()
                    FirebaseCoupleManager.declineFriendRequest(this@MainActivity)
                    rebuildFriendsTab()
                })

                addView(btnRow)
            }
            container.addView(reqCard)
        }

        // БЛОК 3 / 4: Напарник не добавлен ИЛИ уже в паре
        if (state.partnerUsername.isBlank()) {
            val addFriendCard = createConstructorCard().apply {
                addView(createSectionTitle("🤝 ДОБАВИТЬ НАПАРНИКА"))
                addView(createDescriptionText("Введите ник друга для отправки инвайта:"))

                val friendInput = createConstructorInput("@")
                addView(friendInput)

                val btnSendReq = createTacticalButton("🚀 Отправить заявку в друзья", C_BTN_CYAN) {
                    vibrateQuick()
                    val target = friendInput.text.toString()
                    FirebaseCoupleManager.sendFriendRequest(this@MainActivity, target) { resultMsg ->
                        Toast.makeText(this@MainActivity, resultMsg, Toast.LENGTH_LONG).show()
                        rebuildFriendsTab()
                    }
                }
                addView(btnSendReq)

                addView(TextView(context).apply {
                    text = "💡 Подсказка: для мгновенного теста прямо на одном экране введите ник @test"
                    setTextColor(C_TEXT_MUTED)
                    textSize = 10f
                    setPadding(0, dp(8), 0, 0)
                })
            }
            container.addView(addFriendCard)
        } else {
            // Напарник уже в паре
            val partnerUser = state.partnerUsername
            val activePairCard = createConstructorCard().apply {
                background = GradientDrawable().apply {
                    setColor(C_CARD_BG)
                    cornerRadius = dp(14f)
                    setStroke(dp(2), C_BTN_EMERALD)
                }

                addView(TextView(context).apply {
                    text = "🟢 НАПАРНИК ПОДКЛЮЧЕН: @$partnerUser"
                    setTextColor(C_BTN_EMERALD)
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, 0, dp(8))
                })

                addView(createDescriptionText("Отправка живых записок и действий на островок напарника:"))

                val noteInput = createConstructorInput("").apply {
                    hint = "Короткое сообщение на островок..."
                }
                addView(noteInput)

                val btnSendNote = createTacticalButton("💬 Отправить сообщение на островок", C_BTN_CYAN) {
                    vibrateQuick()
                    val txt = noteInput.text.toString()
                    if (txt.isNotBlank()) {
                        FirebaseCoupleManager.sendPartnerNote(this@MainActivity, txt)
                        noteInput.setText("")
                    } else {
                        Toast.makeText(this@MainActivity, "Введите текст сообщения", Toast.LENGTH_SHORT).show()
                    }
                }
                addView(btnSendNote)

                val actionRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(4), 0, dp(4))
                }

                actionRow.addView(createSmallTestButton("⚡️ Пнуть / Привет", C_BTN_EMERALD, 1f) {
                    vibrateQuick()
                    FirebaseCoupleManager.sendPartnerAction("POKE")
                    IslandStateBus.playWithPet()
                    Toast.makeText(context, "⚡️ Привет отправлен на островок @$partnerUser!", Toast.LENGTH_SHORT).show()
                })

                actionRow.addView(createSmallTestButton("🐾 Отправить кота в гости", C_BTN_BLUE, 1f) {
                    vibrateQuick()
                    FirebaseCoupleManager.sendPartnerAction("VISIT")
                    Toast.makeText(context, "🐾 Кот побежал в гости к @$partnerUser!", Toast.LENGTH_SHORT).show()
                })
                addView(actionRow)

                val btnUnpair = TextView(context).apply {
                    text = "Отвязать друга"
                    setTextColor(Color.parseColor("#EF4444"))
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(4))
                    setOnClickListener {
                        vibrateQuick()
                        FirebaseCoupleManager.unpairFriend(this@MainActivity)
                        rebuildFriendsTab()
                    }
                }
                addView(btnUnpair)
            }
            container.addView(activePairCard)
        }
    }

    // ==================== ВКЛАДКА 2: КАЛИБРОВКА КАМЕРЫ ====================

    private fun createCameraCalibrationTab(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val prefs = getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)
        val savedX = prefs.getInt("offset_x", 0)
        val savedY = prefs.getInt("offset_y", 0)
        val savedScale = prefs.getFloat("island_scale", 1.0f)

        val calCard = createConstructorCard().apply {
            addView(createSectionTitle("🎯 ТОЧНАЯ ПОДГОНКА ПОД КАМЕРУ"))
            addView(createDescriptionText("Отрегулируйте положение островка под вырез вашей фронталки:"))

            // 1. Смещение по горизонтали X
            val tvX = TextView(context).apply {
                text = "Смещение по X: $savedX px"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(0, dp(6), 0, dp(2))
            }
            addView(tvX)

            val seekX = SeekBar(context).apply {
                max = 300
                progress = savedX + 150
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                        val realX = prog - 150
                        tvX.text = "Смещение по X: $realX px"
                        prefs.edit().putInt("offset_x", realX).apply()
                        IslandStateBus.updateCalibration(realX, prefs.getInt("offset_y", 0), prefs.getFloat("island_scale", 1.0f))
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            addView(seekX)

            // 2. Смещение по вертикали Y
            val tvY = TextView(context).apply {
                text = "Смещение по Y: $savedY px"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(0, dp(8), 0, dp(2))
            }
            addView(tvY)

            val seekY = SeekBar(context).apply {
                max = 200
                progress = savedY + 50
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                        val realY = prog - 50
                        tvY.text = "Смещение по Y: $realY px"
                        prefs.edit().putInt("offset_y", realY).apply()
                        IslandStateBus.updateCalibration(prefs.getInt("offset_x", 0), realY, prefs.getFloat("island_scale", 1.0f))
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            addView(seekY)

            // 3. Масштаб
            val scalePct = (savedScale * 100).toInt()
            val tvScale = TextView(context).apply {
                text = "Масштаб островка: $scalePct%"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(0, dp(8), 0, dp(2))
            }
            addView(tvScale)

            val seekScale = SeekBar(context).apply {
                max = 120
                progress = ((savedScale - 0.6f) * 100).toInt().coerceIn(0, 120)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                        val realScale = 0.6f + (prog / 100f)
                        val p = (realScale * 100).toInt()
                        tvScale.text = "Масштаб островка: $p%"
                        prefs.edit().putFloat("island_scale", realScale).apply()
                        IslandStateBus.updateCalibration(prefs.getInt("offset_x", 0), prefs.getInt("offset_y", 0), realScale)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            addView(seekScale)

            // Кнопка сброса по центру
            val btnReset = createTacticalButton("🔄 Центрировать по умолчанию", C_BTN_DARK) {
                vibrateQuick()
                prefs.edit().putInt("offset_x", 0).putInt("offset_y", 0).putFloat("island_scale", 1.0f).apply()
                seekX.progress = 150
                seekY.progress = 50
                seekScale.progress = 40
                IslandStateBus.updateCalibration(0, 0, 1.0f)
                Toast.makeText(context, "Калибровка сброшена в центр", Toast.LENGTH_SHORT).show()
            }
            addView(btnReset)
        }
        layout.addView(calCard)

        return layout
    }

    // ==================== ВКЛАДКА 3: СКИНЫ (GIF/PNG + PRO И TELEGRAM) ====================

    private fun createSkinsTab(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Карточка загрузки кастомного скина
        val skinCard = createConstructorCard().apply {
            addView(createSectionTitle("🎨 СВОЙ ПИТОМЕЦ (GIF / PNG)"))
            addView(createDescriptionText("Загрузите любую гифку или прозрачный PNG с любимым персонажем:"))

            tvCustomStatus = TextView(context).apply {
                textSize = 12.5f
                setTextColor(C_BTN_AMBER)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(4), 0, dp(8))
            }
            addView(tvCustomStatus)

            val btnPick = createTacticalButton("📁 1. Выбрать картинку / GIF из Галереи", C_BTN_BLUE) {
                vibrateQuick()
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                }
                startActivityForResult(intent, 1001)
            }
            addView(btnPick)

            btnWatchAd = createTacticalButton("▶️ 2. Смотреть рекламу (+6 часов)", C_BTN_EMERALD) {
                vibrateQuick()
                RewardedAdManager.showAdForCustomPet(this@MainActivity) { success, msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    updateCustomPetStatus()
                }
            }
            addView(btnWatchAd)

            val btnResetSkin = createTacticalButton("🗑️ Сбросить на стандартного 3D Кибер-Кота", C_BTN_DARK) {
                vibrateQuick()
                val targetFile = File(filesDir, "custom_pet_asset")
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                IslandStateBus.notifyCustomPetFileChanged()
                Toast.makeText(context, "Возвращен стандартный 3D Кибер-Кот конструктора", Toast.LENGTH_SHORT).show()
                updateCustomPetStatus()
            }
            addView(btnResetSkin)
        }
        layout.addView(skinCard)

        // 2. Карточка "🔑 Активация PRO и Коды из Telegram"
        val proCard = createConstructorCard().apply {
            addView(createSectionTitle("🔑 АКТИВАЦИЯ PRO И КОДЫ ИЗ TELEGRAM"))

            val myDevId = RewardedAdManager.getShortDeviceId(this@MainActivity)
            addView(TextView(context).apply {
                text = "Твой ID устройства: $myDevId"
                setTextColor(C_BTN_CYAN)
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(4))
            })
            addView(createDescriptionText("Отправьте этот ID автору в Telegram для получения личного вечного PRO-ключа."))

            val keyInput = createConstructorInput("").apply {
                hint = "Введи промокод TG12H или свой PRO-ключ"
            }
            addView(keyInput)

            val btnRedeem = createTacticalButton("✅ АКТИВИРОВАТЬ КЛЮЧ", C_BTN_BLUE) {
                vibrateQuick()
                val entered = keyInput.text.toString().trim()
                if (entered.equals("#GEN", ignoreCase = true)) {
                    // Секретная панель администратора для генерации ключа покупателю
                    showAdminKeyGeneratorDialog()
                } else if (entered.isBlank()) {
                    Toast.makeText(this@MainActivity, "Введите промокод или PRO-ключ", Toast.LENGTH_SHORT).show()
                } else {
                    val (success, message) = RewardedAdManager.redeemKey(this@MainActivity, entered)
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    if (success) {
                        keyInput.setText("")
                    }
                    updateCustomPetStatus()
                }
            }
            addView(btnRedeem)

            val btnTelegram = createTacticalButton("✈️ Взять бесплатный код или PRO в Telegram", C_BTN_CYAN) {
                vibrateQuick()
                try {
                    val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/your_channel_here"))
                    startActivity(tgIntent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Канал: https://t.me/your_channel_here", Toast.LENGTH_SHORT).show()
                }
            }
            addView(btnTelegram)
        }
        layout.addView(proCard)

        updateCustomPetStatus()
        return layout
    }

    private fun showAdminKeyGeneratorDialog() {
        val input = EditText(this).apply {
            hint = "ID покупателя (например, A7F2)"
            setTextColor(Color.WHITE)
            setHintTextColor(C_TEXT_MUTED)
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121622"))
                cornerRadius = dp(8f)
                setStroke(dp(1), C_CARD_BORDER)
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
            addView(TextView(context).apply {
                text = "🔑 Генератор PRO-ключей (Admin)"
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(6))
            })
            addView(TextView(context).apply {
                text = "Введите 4-значный ID покупателя:"
                setTextColor(C_TEXT_MUTED)
                textSize = 12f
                setPadding(0, 0, 0, dp(12))
            })
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton("Сгенерировать и скопировать") { _, _ ->
                val buyerId = input.text.toString().trim()
                if (buyerId.length >= 2) {
                    val generatedKey = RewardedAdManager.generateProKeyForDevice(buyerId)
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("PRO Key", generatedKey)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(this, "Готово! Ключ $generatedKey скопирован в буфер", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Введите корректный ID устройства", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(C_CARD_BG)
            cornerRadius = dp(14f)
            setStroke(dp(2), C_CARD_BORDER)
        })
        dialog.show()
    }

    private fun updateCustomPetStatus() {
        val tv = tvCustomStatus ?: return
        val state = IslandStateBus.state.value

        if (state.isProLifetime || RewardedAdManager.isProUnlocked(this)) {
            tv.text = "👑 PRO АКТИВЕН НАВСЕГДА"
            tv.setTextColor(C_BTN_EMERALD)
            btnWatchAd?.isEnabled = false
            btnWatchAd?.text = "👑 Вечный PRO активен"
        } else if (System.currentTimeMillis() < state.customPetExpireMs) {
            val remain = RewardedAdManager.getRemainingTimeFormatted(this)
            tv.text = "✅ Кастомный скин активен! Осталось: $remain"
            tv.setTextColor(C_BTN_EMERALD)
            btnWatchAd?.isEnabled = true
            btnWatchAd?.text = "▶️ 2. Смотреть рекламу (+6 часов)"
        } else {
            tv.text = "🔒 Скин не активен. Просмотрено реклам: ${state.adsWatchedCount} / 2"
            tv.setTextColor(C_BTN_AMBER)
            btnWatchAd?.isEnabled = true
            btnWatchAd?.text = "▶️ 2. Смотреть рекламу (+6 часов)"
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
                    Toast.makeText(this, "Файл питомца успешно загружен!", Toast.LENGTH_SHORT).show()
                    updateCustomPetStatus()
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка загрузки файла: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДИЗАЙНА ====================

    private fun createConstructorCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(14))
            }
            layoutParams = lp

            background = GradientDrawable().apply {
                setColor(C_CARD_BG)
                cornerRadius = dp(14f)
                setStroke(dp(2), C_CARD_BORDER)
            }
        }
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 13.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }
    }

    private fun createDescriptionText(desc: String): TextView {
        return TextView(this).apply {
            text = desc
            textSize = 11.5f
            setTextColor(C_TEXT_MUTED)
            setPadding(0, 0, 0, dp(10))
        }
    }

    private fun createConstructorInput(defaultText: String): EditText {
        return EditText(this).apply {
            setText(defaultText)
            setTextColor(Color.WHITE)
            setHintTextColor(C_TEXT_MUTED)
            textSize = 13f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(10))
            }
            layoutParams = lp

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121622"))
                cornerRadius = dp(10f)
                setStroke(dp(1), C_CARD_BORDER)
            }
        }
    }

    private fun createTacticalButton(title: String, bgColor: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply {
                setMargins(0, dp(4), 0, dp(6))
            }
            layoutParams = lp

            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(10f)
            }

            setOnClickListener { onClick() }
        }
    }

    private fun createSmallTestButton(title: String, bgColor: Int, weightVal: Float, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 11.5f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, dp(44), weightVal).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            layoutParams = lp

            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(9f)
            }

            setOnClickListener { onClick() }
        }
    }

    private fun vibrateQuick() {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
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
}

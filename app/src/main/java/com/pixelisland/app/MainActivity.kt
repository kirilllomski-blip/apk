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

/**
 * Главный экран управления приложением Pixel Island.
 * Выполнен в стиле уютного конструктора (Cozy Brick UI) с теплой палитрой:
 * 1. Интерактивная 3D-комната VoxelRoomView с питомцем и карточкой характеристик.
 * 2. Док из 5 цветных плиток конструктора на светло-кремовой подложке:
 *    [ 🍔 КОРМ ], [ ⚡️ ТЕСТЫ ], [ 🤝 ДРУЗЬЯ ], [ 🎯 КАМЕРА ], [ 🐶 СКИНЫ ].
 * 3. Вкладка "СКИНЫ" с мгновенным переключением: [ 🐱 Рыжий Кот ] или [ 🐶 Пес Сиба-Ину ].
 * 4. Вкладка "ДРУЗЬЯ" с мгновенным маханием лапой и приходом второго питомца в гости.
 * 5. Вкладка "ТЕСТЫ" с переключателем авто-детектора Анти-Рилсов (10 сек, 15 мин, 30 мин).
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var voxelRoomView: VoxelRoomView? = null
    private var activeTab = 0 // 0: ТЕСТЫ, 1: ДРУЗЬЯ, 2: КАМЕРА, 3: СКИНЫ

    private val dockButtons = mutableListOf<LinearLayout>()
    private val tabContainers = mutableListOf<LinearLayout>()

    private var friendsTabContainer: LinearLayout? = null
    private var testsTabContainer: LinearLayout? = null
    private var skinsTabContainer: LinearLayout? = null

    private var tvCustomStatus: TextView? = null
    private var btnWatchAd: Button? = null

    // Цветовая палитра: Уютный 3D-конструктор (Cozy Brick UI)
    private val C_SCREEN_BG       = Color.parseColor("#151821")
    private val C_CARD_BG         = Color.parseColor("#1E2330")
    private val C_CARD_BORDER     = Color.parseColor("#2B3346")
    private val C_TRAY_BG         = Color.parseColor("#F4ECE6") // Светло-кремовая подложка дока
    private val C_TRAY_BORDER     = Color.parseColor("#D8CDC3")
    private val C_INPUT_BG        = Color.parseColor("#171B26")
    private val C_TEXT_MUTED      = Color.parseColor("#9BA5B6")

    // Плитки конструктора: благородные унисекс тона
    private val C_BRICK_CORAL     = Color.parseColor("#E0694F") // Терракотовый
    private val C_BRICK_AMBER     = Color.parseColor("#E5A836") // Песочно-янтарный
    private val C_BRICK_MINT      = Color.parseColor("#38A37A") // Мятный
    private val C_BRICK_INDIGO    = Color.parseColor("#4C65D6") // Теплый индиго
    private val C_BRICK_CARAMEL   = Color.parseColor("#C87438") // Теплая карамель
    private val C_BRICK_DARK      = Color.parseColor("#283042")

    private val dockTileColors = listOf(
        C_BRICK_CORAL,   // 0: 🍔 КОРМ
        C_BRICK_AMBER,   // 1: ⚡ ТЕСТЫ
        C_BRICK_MINT,    // 2: 🤝 ДРУЗЬЯ
        C_BRICK_INDIGO,  // 3: 🎯 КАМЕРА
        C_BRICK_CARAMEL  // 4: 🐶 СКИНЫ
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RewardedAdManager.initFromPrefs(this)
        FirebaseCoupleManager.init(this)

        val prefs = getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)

        // Загрузка сохраненного базового скина
        val savedSpeciesStr = prefs.getString("pet_species", PetSpecies.CAT.name)
        val initialSpecies = if (savedSpeciesStr == PetSpecies.DOG.name) PetSpecies.DOG else PetSpecies.CAT
        IslandStateBus.setPetSpecies(initialSpecies)

        // Загрузка сохраненного лимита Анти-Рилсов
        val savedLimit = prefs.getInt("doomscroll_limit", 15)
        IslandStateBus.setDoomscrollLimit(savedLimit)

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

        // ==================== 2. ДОК: 5 ВЫПУКЛЫХ ПЛИТОК НА КРЕМОВОЙ ПОДЛОЖКЕ ====================
        val dockTray = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(14), 0, dp(14))
            }
            layoutParams = lp

            background = GradientDrawable().apply {
                setColor(C_TRAY_BG)
                cornerRadius = dp(16f)
                setStroke(dp(2), C_TRAY_BORDER)
            }
        }

        val dockItems = listOf(
            Triple("🍔", "КОРМ", -1),      // Действие: покормить
            Triple("⚡", "ТЕСТЫ", 0),      // Вкладка 0
            Triple("🤝", "ДРУЗЬЯ", 1),     // Вкладка 1
            Triple("🎯", "КАМЕРА", 2),     // Вкладка 2
            Triple("🐶", "СКИНЫ", 3)       // Вкладка 3
        )

        dockButtons.clear()
        dockItems.forEachIndexed { index, (icon, title, tabIndex) ->
            val tileColor = dockTileColors[index]
            val itemBtn = createDockTileButton(index, icon, title, tileColor, tabIndex == activeTab) {
                vibrateQuick()
                if (tabIndex == -1) {
                    IslandStateBus.feedPet()
                    FirebaseCoupleManager.sendPartnerAction("FEED")
                    Toast.makeText(this, "🍔 Питомец с аппетитом поел! (+20 сытости, +15 XP)", Toast.LENGTH_SHORT).show()
                } else {
                    selectTab(tabIndex)
                }
            }
            dockButtons.add(itemBtn)
            dockTray.addView(itemBtn)
        }
        mainLayout.addView(dockTray)

        // ==================== 3. КОНТЕЙНЕРЫ ВКЛАДОК ====================
        tabContainers.clear()

        // Вкладка 0: ТЕСТЫ И АВТО-ДЕТЕКТОР АНТИ-РИЛСОВ
        val tabTests = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        testsTabContainer = tabTests
        rebuildTestsTab()
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

        // Вкладка 2: КАЛИБРОВКА КАМЕРЫ
        val tabCamera = createCameraCalibrationTab()
        tabContainers.add(tabCamera)
        mainLayout.addView(tabCamera)

        // Вкладка 3: СКИНЫ (ВЫБОР КОТ/ПЕС + СВОЙ GIF/PNG + PRO)
        val tabSkins = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        skinsTabContainer = tabSkins
        rebuildSkinsTab()
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
                updateCustomPetStatus()
            }
        }
    }

    // ==================== ДОК И ПЕРЕКЛЮЧЕНИЕ ВКЛАДОК ====================

    private fun createDockTileButton(
        index: Int,
        icon: String,
        label: String,
        tileColor: Int,
        isSelected: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            val lp = LinearLayout.LayoutParams(0, dp(62), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            layoutParams = lp

            background = GradientDrawable().apply {
                setColor(tileColor)
                cornerRadius = dp(12f)
                if (isSelected) {
                    setStroke(dp(3), C_SCREEN_BG)
                } else {
                    setStroke(dp(1), Color.WHITE)
                }
            }

            val iconTv = TextView(context).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
            }

            val labelTv = TextView(context).apply {
                text = label
                textSize = 9.2f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
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
            } else {
                labelTv?.text = "ДРУЗЬЯ"
            }
        }
    }

    private fun selectTab(tabIndex: Int) {
        activeTab = tabIndex
        for (i in tabContainers.indices) {
            tabContainers[i].visibility = if (i == tabIndex) View.VISIBLE else View.GONE
        }

        // Обновляем визуальный фокус плиток конструктора
        for (i in dockButtons.indices) {
            val btn = dockButtons[i]
            val isCurrent = (i == tabIndex + 1) // +1 т.к. 0-я плитка [ 🍔 КОРМ ]
            val bg = btn.background as? GradientDrawable ?: continue

            if (isCurrent) {
                bg.setStroke(dp(3), Color.parseColor("#151821"))
            } else {
                bg.setStroke(dp(1), Color.WHITE)
            }
        }
    }

    // ==================== ВКЛАДКА 0: ТЕСТЫ И АНТИ-РИЛСЫ ====================

    private fun rebuildTestsTab() {
        val container = testsTabContainer ?: return
        container.removeAllViews()

        val state = IslandStateBus.state.value
        val prefs = getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)

        // 1. БЛОК АВТО-ДЕТЕКТОРА АНТИ-РИЛСОВ С ВЫБОРОМ ТАЙМЕРА
        val doomCard = createConstructorCard().apply {
            addView(createSectionTitle("⏱️ АНТИ-РИЛСЫ: ТАЙМЕР АВТО-НАПОМИНАНИЯ"))
            addView(createDescriptionText("Автоматическое срабатывание при сидении в TikTok, Instagram, YouTube Shorts или VK Клипах:"))

            val currentLimit = state.doomscrollLimitMinutes
            val curText = if (currentLimit <= 0) "Тест: через 10 секунд (режим проверки)" else "Через $currentLimit минут"

            val tvCurrent = TextView(context).apply {
                text = "Текущий порог: $curText"
                setTextColor(C_BRICK_AMBER)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(10))
            }
            addView(tvCurrent)

            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            // 1. "Тест 10 сек"
            btnRow.addView(createOptionButton(
                "⏱️ Сработать через 10 сек (для проверки)",
                currentLimit <= 0,
                C_BRICK_AMBER
            ) {
                vibrateQuick()
                prefs.edit().putInt("doomscroll_limit", 0).apply()
                IslandStateBus.setDoomscrollLimit(0)
                Toast.makeText(context, "⏱️ Порог Анти-Рилсов установлен: 10 секунд (проверка)!", Toast.LENGTH_SHORT).show()
                rebuildTestsTab()
            })

            // 2. "Через 15 мин"
            btnRow.addView(createOptionButton(
                "⏱️ Через 15 мин (стандартный режим)",
                currentLimit == 15,
                C_BRICK_MINT
            ) {
                vibrateQuick()
                prefs.edit().putInt("doomscroll_limit", 15).apply()
                IslandStateBus.setDoomscrollLimit(15)
                Toast.makeText(context, "⏱️ Порог Анти-Рилсов установлен: 15 минут!", Toast.LENGTH_SHORT).show()
                rebuildTestsTab()
            })

            // 3. "Через 30 мин"
            btnRow.addView(createOptionButton(
                "⏱️ Через 30 мин (длинный режим)",
                currentLimit == 30,
                C_BRICK_INDIGO
            ) {
                vibrateQuick()
                prefs.edit().putInt("doomscroll_limit", 30).apply()
                IslandStateBus.setDoomscrollLimit(30)
                Toast.makeText(context, "⏱️ Порог Анти-Рилсов установлен: 30 минут!", Toast.LENGTH_SHORT).show()
                rebuildTestsTab()
            })

            addView(btnRow)
        }
        container.addView(doomCard)

        // 2. Разрешения системы
        val permCard = createConstructorCard().apply {
            addView(createSectionTitle("⚙️ РАЗРЕШЕНИЯ СИСТЕМЫ"))
            addView(createDescriptionText("Для отображения островка поверх всех окон включите службы:"))

            val btnAccess = createCozyButton("🔓 1. Служба специальных возможностей (Островок)", C_BRICK_INDIGO) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            addView(btnAccess)

            val btnNotif = createCozyButton("🔔 2. Доступ к уведомлениям (Плеер и сообщения)", C_BRICK_DARK) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            addView(btnNotif)
        }
        container.addView(permCard)

        // 3. Тесты 3D-анимаций конструктора
        val testCard = createConstructorCard().apply {
            addView(createSectionTitle("⚡️ ТЕСТЫ 3D-АНИМАЦИЙ КОНСТРУКТОРА"))
            addView(createDescriptionText("Нажмите для проверки эффектов питомца на островке и в комнате:"))

            // Строка 1
            val row1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(3), 0, dp(3))
            }
            row1.addView(createSmallTestButton("🎵 DJ Музыка", C_BRICK_INDIGO, 1f) {
                vibrateQuick()
                val current = IslandStateBus.state.value.mediaTitle
                if (current != null) {
                    IslandStateBus.setMedia(null)
                    Toast.makeText(context, "Музыка выключена", Toast.LENGTH_SHORT).show()
                } else {
                    IslandStateBus.setMedia("Crystal Castles - Kerosene")
                    Toast.makeText(context, "🎵 Питомец надел красные наушники!", Toast.LENGTH_SHORT).show()
                }
            })
            row1.addView(createSmallTestButton("⚡️ Зарядка", C_BRICK_MINT, 1f) {
                vibrateQuick()
                val isCharging = !IslandStateBus.state.value.isCharging
                IslandStateBus.setCharging(isCharging, triggerPulse = isCharging)
                Toast.makeText(context, if (isCharging) "⚡ Supercharge Pop 3s!" else "Зарядка отключена", Toast.LENGTH_SHORT).show()
            })
            addView(row1)

            // Строка 2
            val row2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(3), 0, dp(3))
            }
            row2.addView(createSmallTestButton("📩 Сообщение", C_BRICK_CARAMEL, 1f) {
                vibrateQuick()
                IslandStateBus.setNotification("Telegram: Привет, напарник! 🐾")
                Toast.makeText(context, "Конвертик в лапках и облачко [ ! ]", Toast.LENGTH_SHORT).show()
            })
            row2.addView(createSmallTestButton("🚨 Анти-Рилсы", C_BRICK_AMBER, 1f) {
                vibrateQuick()
                IslandStateBus.triggerDoomscrollWarning("🚨 Ты листаешь ленту уже слишком долго! Отложи телефон!")
                Toast.makeText(context, "Желтый знак и тройная вибрация!", Toast.LENGTH_SHORT).show()
            })
            addView(row2)

            // Строка 3
            val row3 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(3), 0, dp(3))
            }
            row3.addView(createSmallTestButton("📐 Наклон", C_BRICK_DARK, 1f) {
                vibrateQuick()
                IslandStateBus.updateTilt(36f, true)
                Toast.makeText(context, "Питомец повис на углу капсулы!", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    IslandStateBus.updateTilt(0f, false)
                }, 5000L)
            })
            row3.addView(createSmallTestButton("🍔 Кормление", C_BRICK_CORAL, 1f) {
                vibrateQuick()
                IslandStateBus.feedPet()
                Toast.makeText(context, "3D-бургер падает, питомец жует, салют XP!", Toast.LENGTH_SHORT).show()
            })
            addView(row3)
        }
        container.addView(testCard)
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
            addView(createDescriptionText("Задайте никнейм, по которому напарник сможет найти вас:"))

            val nameInput = createConstructorInput(if (myName.isNotBlank()) "@$myName" else "@").apply {
                hint = "@твой_ник (например, ivan)"
            }
            addView(nameInput)

            val btnSave = createCozyButton("💾 Сохранить мой @ник", C_BRICK_INDIGO) {
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

        // БЛОК 2: Подключение пары или активная комната
        if (state.partnerUsername.isBlank()) {
            val addFriendCard = createConstructorCard().apply {
                addView(createSectionTitle("🔍 ПОИСК НАПАРНИКА ПО @USERNAME"))
                addView(createDescriptionText("Введите никнейм друга для отправки приглашения:"))

                val targetInput = createConstructorInput("@").apply {
                    hint = "@ник_друга (или @test для мгновенного теста)"
                }
                addView(targetInput)

                val btnSendReq = createCozyButton("🤝 Отправить заявку в пару", C_BRICK_MINT) {
                    vibrateQuick()
                    val raw = targetInput.text.toString()
                    FirebaseCoupleManager.sendFriendRequest(this@MainActivity, raw) { success, msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        rebuildFriendsTab()
                    }
                }
                addView(btnSendReq)

                addView(TextView(context).apply {
                    text = "💡 Подсказка: для мгновенного теста прямо на одном экране введите ник @test"
                    setTextColor(C_TEXT_MUTED)
                    textSize = 10.5f
                    setPadding(0, dp(6), 0, dp(10))
                })

                // Кнопки быстрой проверки анимаций даже до сопряжения
                val testFriendsRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                testFriendsRow.addView(createSmallTestButton("👋 Тест: Привет", C_BRICK_CORAL, 1f) {
                    vibrateQuick()
                    IslandStateBus.triggerPokeWave("тестовый друг")
                    Toast.makeText(context, "👋 Питомец машет лапой!", Toast.LENGTH_SHORT).show()
                })
                testFriendsRow.addView(createSmallTestButton("🐾 Тест: В гости", C_BRICK_MINT, 1f) {
                    vibrateQuick()
                    IslandStateBus.triggerPartnerVisit()
                    Toast.makeText(context, "🐾 В комнату прибежал второй питомец!", Toast.LENGTH_SHORT).show()
                })
                addView(testFriendsRow)
            }
            container.addView(addFriendCard)
        } else {
            val partnerUser = state.partnerUsername
            val activePairCard = createConstructorCard().apply {
                background = GradientDrawable().apply {
                    setColor(C_CARD_BG)
                    cornerRadius = dp(14f)
                    setStroke(dp(2), C_BRICK_MINT)
                }

                addView(TextView(context).apply {
                    text = "🟢 НАПАРНИК ПОДКЛЮЧЕН: @$partnerUser"
                    setTextColor(C_BRICK_MINT)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, 0, dp(6))
                })

                addView(createDescriptionText("Отправляйте живые записки, махайте лапой и ходите в гости:"))

                val noteInput = createConstructorInput("").apply {
                    hint = "Короткая записка на островок..."
                }
                addView(noteInput)

                val btnSendNote = createCozyButton("💬 Отправить записку на островок", C_BRICK_INDIGO) {
                    vibrateQuick()
                    val txt = noteInput.text.toString().trim()
                    if (txt.isNotBlank()) {
                        FirebaseCoupleManager.sendPartnerNote(this@MainActivity, txt)
                        noteInput.setText("")
                    } else {
                        Toast.makeText(this@MainActivity, "Введите текст записки", Toast.LENGTH_SHORT).show()
                    }
                }
                addView(btnSendNote)

                // КНОПКИ ДЕЙСТВИЙ С МГНОВЕННЫМ ЗАПУСКОМ АНИМАЦИИ
                val actionRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(4), 0, dp(4))
                }

                actionRow.addView(createSmallTestButton("⚡️ Пнуть / Привет", C_BRICK_CORAL, 1f) {
                    vibrateQuick()
                    val partner = state.partnerUsername.ifBlank { "друг" }
                    IslandStateBus.triggerPokeWave(partner)
                    FirebaseCoupleManager.sendPartnerAction("POKE")
                    Toast.makeText(context, "👋 Привет отправлен на островок @$partner!", Toast.LENGTH_SHORT).show()
                })

                actionRow.addView(createSmallTestButton("🐾 Отправить в гости", C_BRICK_MINT, 1f) {
                    vibrateQuick()
                    IslandStateBus.triggerPartnerVisit()
                    FirebaseCoupleManager.sendPartnerAction("VISIT")
                    Toast.makeText(context, "🐾 Питомец побежал в гости к @$partnerUser!", Toast.LENGTH_SHORT).show()
                })
                addView(actionRow)

                val btnUnpair = TextView(context).apply {
                    text = "Расторгнуть пару с @$partnerUser"
                    setTextColor(Color.parseColor("#EF4444"))
                    textSize = 11.5f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(14), 0, dp(4))
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
            addView(createDescriptionText("Отрегулируйте положение островка под вырез вашей фронтальной камеры:"))

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
            val btnReset = createCozyButton("🔄 Центрировать по умолчанию", C_BRICK_DARK) {
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

    // ==================== ВКЛАДКА 3: СКИНЫ (ВЫБОР КОТ/ПЕС + СВОЙ GIF/PNG + PRO) ====================

    private fun rebuildSkinsTab() {
        val container = skinsTabContainer ?: return
        container.removeAllViews()

        val state = IslandStateBus.state.value
        val prefs = getSharedPreferences("pixel_island_prefs", Context.MODE_PRIVATE)

        // 1. САМЫЙ ПЕРВЫЙ БЛОК: КРУПНЫЕ КНОПКИ ВЫБОРА БАЗОВОГО ПЕРСОНАЖА (КОТ / СОБАЧКА)
        val speciesCard = createConstructorCard().apply {
            addView(createSectionTitle("🐾 ВЫБОР БАЗОВОГО ПИТОМЦА"))
            addView(createDescriptionText("Выберите 3D-персонажа для Dynamic Island и вашей комнаты:"))

            val isCat = state.species == PetSpecies.CAT

            // Кнопка [ 🐱 Выбрать: Рыжий Кот ]
            addView(createOptionButton(
                if (isCat) "🐱 Рыжий Кот (Выбран ✅)" else "🐱 Выбрать: Рыжий Кот",
                isCat,
                C_BRICK_CORAL
            ) {
                vibrateQuick()
                prefs.edit().putString("pet_species", PetSpecies.CAT.name).apply()
                IslandStateBus.setPetSpecies(PetSpecies.CAT)
                Toast.makeText(context, "🐱 Выбран Рыжий Кот!", Toast.LENGTH_SHORT).show()
                rebuildSkinsTab()
            })

            // Кнопка [ 🐶 Выбрать: Пес Сиба-Ину ]
            addView(createOptionButton(
                if (!isCat) "🐶 Пес Сиба-Ину (Выбран ✅)" else "🐶 Выбрать: Пес Сиба-Ину",
                !isCat,
                C_BRICK_AMBER
            ) {
                vibrateQuick()
                prefs.edit().putString("pet_species", PetSpecies.DOG.name).apply()
                IslandStateBus.setPetSpecies(PetSpecies.DOG)
                Toast.makeText(context, "🐶 Выбран Пес Сиба-Ину!", Toast.LENGTH_SHORT).show()
                rebuildSkinsTab()
            })
        }
        container.addView(speciesCard)

        // 2. Карточка загрузки кастомного скина (GIF / PNG)
        val customSkinCard = createConstructorCard().apply {
            addView(createSectionTitle("🎨 СВОЙ ПИТОМЕЦ (GIF / PNG)"))
            addView(createDescriptionText("Загрузите любую прозрачную картинку или GIF из галереи:"))

            tvCustomStatus = TextView(context).apply {
                textSize = 12.5f
                setTextColor(C_BRICK_AMBER)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(4), 0, dp(8))
            }
            addView(tvCustomStatus)

            val btnPick = createCozyButton("📁 1. Выбрать картинку / GIF из Галереи", C_BRICK_INDIGO) {
                vibrateQuick()
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                }
                startActivityForResult(intent, 1001)
            }
            addView(btnPick)

            btnWatchAd = createCozyButton("▶️ 2. Смотреть рекламу (+6 часов)", C_BRICK_MINT) {
                vibrateQuick()
                RewardedAdManager.showAdForCustomPet(this@MainActivity) { success, msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    updateCustomPetStatus()
                }
            }
            addView(btnWatchAd)

            val btnResetSkin = createCozyButton("🗑️ Сбросить на стандартного 3D-питомца", C_BRICK_DARK) {
                vibrateQuick()
                val targetFile = File(filesDir, "custom_pet_asset")
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                IslandStateBus.notifyCustomPetFileChanged()
                Toast.makeText(context, "Возвращен стандартный 3D-питомец конструктора", Toast.LENGTH_SHORT).show()
                updateCustomPetStatus()
            }
            addView(btnResetSkin)
        }
        container.addView(customSkinCard)

        // 3. Карточка "🔑 Активация PRO и Коды из Telegram"
        val proCard = createConstructorCard().apply {
            addView(createSectionTitle("🔑 АКТИВАЦИЯ PRO И КОДЫ ИЗ TELEGRAM"))

            val myDevId = RewardedAdManager.getShortDeviceId(this@MainActivity)
            addView(TextView(context).apply {
                text = "Твой ID устройства: $myDevId"
                setTextColor(C_BRICK_AMBER)
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(4))
            })
            addView(createDescriptionText("Отправьте этот ID автору в Telegram для получения личного вечного PRO-ключа."))

            val keyInput = createConstructorInput("").apply {
                hint = "Введи промокод TG12H или свой PRO-ключ"
            }
            addView(keyInput)

            val btnRedeem = createCozyButton("✅ АКТИВИРОВАТЬ КЛЮЧ", C_BRICK_INDIGO) {
                vibrateQuick()
                val entered = keyInput.text.toString().trim()
                if (entered.equals("#GEN", ignoreCase = true)) {
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

            val btnTelegram = createCozyButton("✈️ Взять бесплатный промокод в Telegram", C_BRICK_MINT) {
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
        container.addView(proCard)

        updateCustomPetStatus()
    }

    private fun showAdminKeyGeneratorDialog() {
        val input = EditText(this).apply {
            hint = "ID покупателя (например, A7F2)"
            setTextColor(Color.WHITE)
            setHintTextColor(C_TEXT_MUTED)
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(C_INPUT_BG)
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
            tv.setTextColor(C_BRICK_MINT)
            btnWatchAd?.isEnabled = false
            btnWatchAd?.text = "👑 Вечный PRO активен"
        } else if (System.currentTimeMillis() < state.customPetExpireMs) {
            val remain = RewardedAdManager.getRemainingTimeFormatted(this)
            tv.text = "✅ Кастомный скин активен! Осталось: $remain"
            tv.setTextColor(C_BRICK_MINT)
            btnWatchAd?.isEnabled = true
            btnWatchAd?.text = "▶️ 2. Смотреть рекламу (+6 часов)"
        } else {
            tv.text = "🔒 Скин не активен. Просмотрено реклам: ${state.adsWatchedCount} / 2"
            tv.setTextColor(C_BRICK_AMBER)
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
                setStroke(dp(1), C_CARD_BORDER)
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
                setColor(C_INPUT_BG)
                cornerRadius = dp(10f)
                setStroke(dp(1), C_CARD_BORDER)
            }
        }
    }

    private fun createCozyButton(title: String, bgColor: Int, onClick: () -> Unit): Button {
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
                cornerRadius = dp(11f)
            }

            setOnClickListener { onClick() }
        }
    }

    private fun createOptionButton(title: String, isSelected: Boolean, activeColor: Int, onClick: () -> Unit): Button {
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
                setMargins(0, dp(3), 0, dp(6))
            }
            layoutParams = lp

            background = GradientDrawable().apply {
                setColor(if (isSelected) activeColor else C_BRICK_DARK)
                cornerRadius = dp(11f)
                if (isSelected) {
                    setStroke(dp(2), Color.WHITE)
                } else {
                    setStroke(dp(1), C_CARD_BORDER)
                }
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
                cornerRadius = dp(10f)
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

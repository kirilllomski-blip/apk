package com.pixelisland.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class VoxelRoomView(context: Context) : View(context) {

    private val renderer = PixelPetRenderer(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * scaledDensity

    // ==================== ПАЛИТРА COZY LEGO LOFT (ТЕПЛЫЙ УЮТНЫЙ КОНСТРУКТОР) ====================
    private val C_WALL_UPPER    = Color.parseColor("#F4EFEA") // Песочно-кремовый верх стены
    private val C_WALL_STUD     = Color.parseColor("#E5DDD4") // Пупырки Lego на стене
    private val C_WALL_STUD_SH  = Color.parseColor("#D4CAC0")
    private val C_WALL_LOWER    = Color.parseColor("#D8E2DC") // Мягкий мятно-голубой плинтус/панели
    private val C_WALL_BORDER   = Color.parseColor("#BCC8C0") // Бордюр разделителя стены
    private val C_FLOOR_WOOD    = Color.parseColor("#C89F78") // Теплый деревянный паркет из кубиков
    private val C_FLOOR_PLANK1  = Color.parseColor("#D4AB84")
    private val C_FLOOR_PLANK2  = Color.parseColor("#BD926B")
    private val C_FLOOR_SEAM    = Color.parseColor("#9E7550")

    // Окно и окружение
    private val C_SKY_BLUE      = Color.parseColor("#7DD3FC") // Ясное голубое небо
    private val C_CLOUD_WHITE   = Color.parseColor("#FFFFFF") // Пушистые облака
    private val C_SUN_GOLD      = Color.parseColor("#FDE047") // Солнышко
    private val C_WINDOW_FRAME  = Color.parseColor("#FFFFFF") // Белая Lego-рама окна
    private val C_WINDOW_SILL   = Color.parseColor("#E2E8F0")

    // Мебель и декор комнаты
    private val C_POT_TERRACOTTA= Color.parseColor("#D97706") // Горшок для цветка
    private val C_LEAF_GREEN    = Color.parseColor("#22C55E") // Листья цветка
    private val C_LEAF_LIGHT    = Color.parseColor("#4ADE80")
    private val C_CHAIR_CUSHION = Color.parseColor("#FB7185") // Розовый пуфик-кресло
    private val C_BOWL_BLUE     = Color.parseColor("#38BDF8") // Миска питомца
    private val C_FISH_ORANGE   = Color.parseColor("#FB923C") // Рыбка в миске кота
    private val C_BONE_WHITE    = Color.parseColor("#F8FAFC") // Косточка в миске собаки
    private val C_SHELF_WOOD    = Color.parseColor("#A16207") // Книжная полочка
    private val C_BOOK_RED      = Color.parseColor("#EF4444")
    private val C_BOOK_BLUE     = Color.parseColor("#3B82F6")
    private val C_BOOK_GREEN    = Color.parseColor("#10B981")

    // Верхняя HUD-плашка
    private val C_HUD_BG        = Color.parseColor("#FFFDF8") // Светлая кремовая карточка
    private val C_HUD_BORDER    = Color.parseColor("#B45309") // Терракотово-медовая 3D-окантовка
    private val C_HUD_TITLE     = Color.parseColor("#1E293B") // Четкий темно-графитовый заголовок
    private val C_STAT_BG       = Color.parseColor("#F1F5F9") // Фоновый трек шкалы
    private val C_STAT_HUNGER   = Color.parseColor("#EF4444") // Сытость (Кораллово-красный)
    private val C_STAT_MOOD     = Color.parseColor("#F59E0B") // Настроение (Янтарно-золотой)
    private val C_STAT_ENERGY   = Color.parseColor("#10B981") // Энергия (Изумрудно-зеленый)

    // Базовые кисти
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_HUD_TITLE
        textSize = 12f * scaledDensity
        typeface = Typeface.DEFAULT_BOLD
    }

    private val statLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569")
        textSize = 9f * scaledDensity
        typeface = Typeface.DEFAULT_BOLD
    }

    private val levelBadgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10f * scaledDensity
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val xpTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 8.5f * scaledDensity
        textAlign = Paint.Align.CENTER
    }

    // Области взаимодействия
    private val petTouchRect = RectF()
    private val panelRect = RectF()

    // Частицы от тапа (сердечки, звездочки, конфетти)
    private data class ToyParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val size: Float,
        val color: Int,
        val isHeart: Boolean,
        var alpha: Float = 1.0f,
        val life: Float = 1.0f
    )
    private val particles = mutableListOf<ToyParticle>()

    // Анимационный цикл 20 FPS (50ms)
    private var frameIndex = 0
    private val animHandler = Handler(Looper.getMainLooper())
    private var isAnimRunning = false

    private val animRunnable = object : Runnable {
        override fun run() {
            val state = IslandStateBus.state.value
            if (!state.isScreenOn) {
                isAnimRunning = false
                return
            }
            frameIndex++
            updateParticles()
            invalidate()
            animHandler.postDelayed(this, 50L)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = dp(320f).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
        scope.launch {
            IslandStateBus.state.collect { state ->
                if (state.isScreenOn && !isAnimRunning) {
                    startAnimation()
                } else if (!state.isScreenOn && isAnimRunning) {
                    stopAnimation()
                }
                invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
        scope.cancel()
    }

    private fun startAnimation() {
        if (!isAnimRunning) {
            isAnimRunning = true
            animHandler.post(animRunnable)
        }
    }

    private fun stopAnimation() {
        isAnimRunning = false
        animHandler.removeCallbacks(animRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val time = System.currentTimeMillis()
        val state = IslandStateBus.state.value

        // 1. СТЕНА И ПОЛ УЮТНОГО ЛОФТА (COZY LEGO LOFT)
        drawCozyRoomBackground(canvas, w, h, time)

        // 2. ДЕКОР И ИНТЕРЬЕР ИЗ КУБИКОВ (Окно, цветок, диванчик, миска)
        drawCozyFurniture(canvas, w, h, state, time)

        // 3. ЖИВОЙ ПИТОМЕЦ: ХОДИТ ПО КОМНАТЕ, ЛЕЖИТ КАЛАЧИКОМ И ПУСКАЕТ СЕРДЕЧКИ
        val petW = dp(116f)
        val petH = dp(116f)
        val petY = h - dp(142f)

        val minRoomX = dp(32f)
        val maxRoomX = (w - petW - dp(32f)).coerceAtLeast(minRoomX)
        val centerRoomX = (w - petW) / 2f

        val petX: Float
        val isWalking: Boolean
        val isFacingLeft: Boolean
        val isLyingDown: Boolean
        val isEmittingHearts: Boolean

        if (state.petMode != PetMode.IDLE) {
            petX = centerRoomX
            isWalking = false
            isFacingLeft = false
            isLyingDown = (state.petMode == PetMode.SLEEP)
            isEmittingHearts = (state.petMode == PetMode.WAVE_POKE)
        } else {
            // Автономный цикл жизни в комнате (28 секунд):
            // 0..7с: Идет от центра влево (к креслу и цветку)
            // 7..14с: Уютно лежит калачиком у цветка, дремлет
            // 14..16с: Просыпается, потягивается и поворачивается направо
            // 16..23с: Идет направо к миске с едой
            // 23..28с: Сидит около миски, довольно виляет хвостом и пускает парящие сердечки!
            val loopMs = 28_000L
            val t = ((time % loopMs) / 1000f)

            when {
                t < 7.0f -> {
                    val p = t / 7.0f
                    val smoothP = (1f - cos(p * Math.PI.toFloat())) / 2f
                    petX = centerRoomX - (centerRoomX - minRoomX) * smoothP
                    isWalking = true
                    isFacingLeft = true
                    isLyingDown = false
                    isEmittingHearts = false
                }
                t < 14.0f -> {
                    petX = minRoomX
                    isWalking = false
                    isFacingLeft = true
                    isLyingDown = true
                    isEmittingHearts = (t > 11.5f)
                }
                t < 16.0f -> {
                    petX = minRoomX
                    isWalking = false
                    isFacingLeft = false
                    isLyingDown = false
                    isEmittingHearts = false
                }
                t < 23.0f -> {
                    val p = (t - 16.0f) / 7.0f
                    val smoothP = (1f - cos(p * Math.PI.toFloat())) / 2f
                    petX = minRoomX + (maxRoomX - minRoomX) * smoothP
                    isWalking = true
                    isFacingLeft = false
                    isLyingDown = false
                    isEmittingHearts = false
                }
                else -> {
                    petX = maxRoomX
                    isWalking = false
                    isFacingLeft = false
                    isLyingDown = false
                    isEmittingHearts = true
                }
            }
        }

        petTouchRect.set(petX, petY, petX + petW, petY + petH)
        renderer.drawPet(
            canvas = canvas,
            dstRect = petTouchRect,
            mode = state.petMode,
            frameIndex = frameIndex,
            isWalking = isWalking,
            isFacingLeft = isFacingLeft,
            isLyingDown = isLyingDown,
            isEmittingHearts = isEmittingHearts
        )

        // 4. ЧАСТИЦЫ ОТ ТАПОВ
        drawToyParticles(canvas)

        // 5. ВЕРХНЯЯ КАРТОЧКА СТАТОВ (HUD)
        drawUpperHudCard(canvas, w, state)
    }

    /**
     * Отрисовка стен и паркета комнаты конструктора
     */
    private fun drawCozyRoomBackground(canvas: Canvas, w: Float, h: Float, time: Long) {
        val floorY = h - dp(96f)
        val wainscotY = floorY - dp(60f)

        // Верх стены: теплый песочно-кремовый
        fillPaint.color = C_WALL_UPPER
        canvas.drawRect(0f, 0f, w, wainscotY, fillPaint)

        // Lego-пупырки на верхней стене
        val studSpacing = dp(24f)
        val studR = dp(3.2f)
        var sy = dp(88f)
        while (sy < wainscotY - dp(6f)) {
            var sx = dp(14f)
            while (sx < w - dp(10f)) {
                fillPaint.color = C_WALL_STUD_SH
                canvas.drawCircle(sx, sy + dp(0.8f), studR, fillPaint)
                fillPaint.color = C_WALL_STUD
                canvas.drawCircle(sx, sy, studR, fillPaint)
                sx += studSpacing
            }
            sy += studSpacing
        }

        // Нижняя часть стены (плинтус/панели): мягкий мятно-голубой
        fillPaint.color = C_WALL_LOWER
        canvas.drawRect(0f, wainscotY, w, floorY, fillPaint)

        // Разделительный деревянный бордюр между стенами
        fillPaint.color = C_WALL_BORDER
        canvas.drawRect(0f, wainscotY - dp(4f), w, wainscotY, fillPaint)

        // Пол: теплый деревянный паркет из кубиков
        fillPaint.color = C_FLOOR_WOOD
        canvas.drawRect(0f, floorY, w, h, fillPaint)

        // Планки паркета со швами
        val plankW = dp(52f)
        val plankH = dp(18f)
        strokePaint.color = C_FLOOR_SEAM
        strokePaint.strokeWidth = dp(1.2f)

        var py = floorY
        var row = 0
        while (py < h) {
            val offset = if (row % 2 == 1) plankW / 2f else 0f
            var px = -offset
            while (px < w) {
                fillPaint.color = if ((row + (px / plankW).toInt()) % 2 == 0) C_FLOOR_PLANK1 else C_FLOOR_PLANK2
                canvas.drawRect(px, py, px + plankW, py + plankH, fillPaint)
                canvas.drawRect(px, py, px + plankW, py + plankH, strokePaint)
                px += plankW
            }
            py += plankH
            row++
        }
    }

    /**
     * Окно, комнатный цветок, диванчик, миска и книжная полка из кубиков
     */
    private fun drawCozyFurniture(canvas: Canvas, w: Float, h: Float, state: IslandState, time: Long) {
        val floorY = h - dp(96f)

        // ==================== 1. УЮТНОЕ ОКНО ПО ЦЕНТРУ ====================
        val winW = dp(106f)
        val winH = dp(78f)
        val winX = (w - winW) / 2f
        val winY = dp(90f)

        // Голубое небо за окном
        fillPaint.color = C_SKY_BLUE
        canvas.drawRect(winX, winY, winX + winW, winY + winH, fillPaint)

        // Желтое солнышко
        fillPaint.color = C_SUN_GOLD
        canvas.drawCircle(winX + winW - dp(18f), winY + dp(18f), dp(12f), fillPaint)

        // Белые мягкие облака, плавно плывущие по небу
        val cloudShift = (time * 0.005f) % (winW + dp(40f)) - dp(20f)
        fillPaint.color = C_CLOUD_WHITE
        canvas.drawCircle(winX + cloudShift, winY + dp(28f), dp(10f), fillPaint)
        canvas.drawCircle(winX + cloudShift + dp(12f), winY + dp(26f), dp(13f), fillPaint)
        canvas.drawCircle(winX + cloudShift + dp(24f), winY + dp(29f), dp(9f), fillPaint)

        // Белая рама окна с перекрестием
        strokePaint.color = C_WINDOW_FRAME
        strokePaint.strokeWidth = dp(4f)
        canvas.drawRect(winX, winY, winX + winW, winY + winH, strokePaint)
        canvas.drawLine(winX + winW / 2f, winY, winX + winW / 2f, winY + winH, strokePaint)
        canvas.drawLine(winX, winY + winH / 2f, winX + winW, winY + winH / 2f, strokePaint)

        // Подоконник
        fillPaint.color = C_WINDOW_SILL
        canvas.drawRect(winX - dp(4f), winY + winH, winX + winW + dp(4f), winY + winH + dp(4f), fillPaint)

        // ==================== 2. СЛЕВА: ЦВЕТОК В ГОРШКЕ И ПУФИК ====================
        val potX = dp(16f)
        val potY = floorY - dp(28f)
        // Терракотовый горшок
        fillPaint.color = C_POT_TERRACOTTA
        canvas.drawRect(potX, potY, potX + dp(26f), floorY, fillPaint)
        // Зеленые круглые листочки
        fillPaint.color = C_LEAF_GREEN
        canvas.drawCircle(potX + dp(8f), potY - dp(12f), dp(10f), fillPaint)
        fillPaint.color = C_LEAF_LIGHT
        canvas.drawCircle(potX + dp(18f), potY - dp(16f), dp(12f), fillPaint)
        fillPaint.color = C_LEAF_GREEN
        canvas.drawCircle(potX + dp(24f), potY - dp(8f), dp(9f), fillPaint)

        // Мягкий пуфик/креслице рядом
        val chairX = potX + dp(32f)
        fillPaint.color = C_CHAIR_CUSHION
        canvas.drawRoundRect(chairX, floorY - dp(22f), chairX + dp(36f), floorY, dp(6f), dp(6f), fillPaint)

        // ==================== 3. СПРАВА: МИСКА И ПОЛОЧКА ====================
        val bowlX = w - dp(52f)
        val bowlY = floorY - dp(14f)
        // Голубая мисочка
        fillPaint.color = C_BOWL_BLUE
        canvas.drawRoundRect(bowlX, bowlY, bowlX + dp(32f), floorY, dp(4f), dp(4f), fillPaint)

        // В миске: рыбка (для кота) или косточка (для собаки)
        if (state.species == PetSpecies.DOG) {
            fillPaint.color = C_BONE_WHITE
            canvas.drawRect(bowlX + dp(6f), bowlY - dp(3f), bowlX + dp(26f), bowlY + dp(3f), fillPaint)
        } else {
            fillPaint.color = C_FISH_ORANGE
            canvas.drawCircle(bowlX + dp(14f), bowlY - dp(2f), dp(5f), fillPaint)
            canvas.drawRect(bowlX + dp(16f), bowlY - dp(4f), bowlX + dp(24f), bowlY, fillPaint)
        }

        // Книжная полочка на стене справа
        val shelfX = w - dp(64f)
        val shelfY = dp(112f)
        fillPaint.color = C_SHELF_WOOD
        canvas.drawRect(shelfX, shelfY + dp(18f), shelfX + dp(48f), shelfY + dp(22f), fillPaint)
        // Книжки из кубиков
        fillPaint.color = C_BOOK_RED
        canvas.drawRect(shelfX + dp(6f), shelfY + dp(2f), shelfX + dp(13f), shelfY + dp(18f), fillPaint)
        fillPaint.color = C_BOOK_BLUE
        canvas.drawRect(shelfX + dp(16f), shelfY + dp(5f), shelfX + dp(24f), shelfY + dp(18f), fillPaint)
        fillPaint.color = C_BOOK_GREEN
        canvas.drawRect(shelfX + dp(27f), shelfY + dp(0f), shelfX + dp(34f), shelfY + dp(18f), fillPaint)
    }

    /**
     * Верхняя светлая карточка HUD со статами в стиле дорогого конструктора
     */
    private fun drawUpperHudCard(canvas: Canvas, w: Float, state: IslandState) {
        val cardX = dp(12f)
        val cardY = dp(10f)
        val cardW = w - dp(24f)
        val cardH = dp(72f)
        panelRect.set(cardX, cardY, cardX + cardW, cardY + cardH)

        // 1. Фон карточки: теплый кремовый #FFFDF8
        fillPaint.color = C_HUD_BG
        canvas.drawRoundRect(panelRect, dp(14f), dp(14f), fillPaint)

        // 2. Рамка конструктора: 2.5dp терракотовая
        strokePaint.color = C_HUD_BORDER
        strokePaint.strokeWidth = dp(2.2f)
        canvas.drawRoundRect(panelRect, dp(14f), dp(14f), strokePaint)

        // 3. Заголовок карточки слева
        val petName = if (state.species == PetSpecies.DOG) "🐶 ПЕС ХАТИКО" else "🐱 КОТ РЫЖИК"
        canvas.drawText(petName, cardX + dp(12f), cardY + dp(20f), titleTextPaint)

        // 4. Правый блок: УРОВЕНЬ X и XP
        val badgeW = dp(76f)
        val badgeH = dp(20f)
        val badgeX = cardX + cardW - badgeW - dp(10f)
        val badgeY = cardY + dp(10f)

        fillPaint.color = Color.parseColor("#F59E0B")
        canvas.drawRoundRect(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, dp(10f), dp(10f), fillPaint)
        canvas.drawText("УРОВЕНЬ ${state.petLevel}", badgeX + badgeW / 2f, badgeY + dp(14f), levelBadgePaint)

        canvas.drawText("XP: ${state.xp}/100", badgeX + badgeW / 2f, badgeY + dp(33f), xpTextPaint)

        // 5. ТРИ ЦВЕТНЫЕ ШКАЛЫ СТАТОВ
        val barStartX = cardX + dp(12f)
        val barY = cardY + dp(34f)
        val totalBarsW = cardW - badgeW - dp(34f)
        val eachBarW = (totalBarsW - dp(12f)) / 3f
        val barH = dp(12f)

        // Шкала 1: ❤️ Сытость
        drawSingleStatPill(canvas, barStartX, barY, eachBarW, barH, "❤️", state.hunger, C_STAT_HUNGER)

        // Шкала 2: 😊 Настроение
        drawSingleStatPill(canvas, barStartX + eachBarW + dp(6f), barY, eachBarW, barH, "😊", state.mood, C_STAT_MOOD)

        // Шкала 3: ⚡️ Энергия
        drawSingleStatPill(canvas, barStartX + (eachBarW + dp(6f)) * 2f, barY, eachBarW, barH, "⚡", state.energy, C_STAT_ENERGY)
    }

    private fun drawSingleStatPill(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        icon: String,
        pct: Int,
        fillColor: Int
    ) {
        // Подпись над шкалой
        canvas.drawText("$icon $pct%", x + dp(2f), y + dp(8f), statLabelPaint)

        val trackY = y + dp(12f)
        val trackH = dp(7f)

        // Серый трек
        fillPaint.color = C_STAT_BG
        canvas.drawRoundRect(x, trackY, x + w, trackY + trackH, dp(4f), dp(4f), fillPaint)

        // Цветное заполнение
        val fillW = (w * (pct.coerceIn(0, 100) / 100f)).coerceAtLeast(dp(4f))
        fillPaint.color = fillColor
        canvas.drawRoundRect(x, trackY, x + fillW, trackY + trackH, dp(4f), dp(4f), fillPaint)
    }

    private fun updateParticles() {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += dp(0.3f) // легкая гравитация
            p.alpha -= 0.04f
            if (p.alpha <= 0f) {
                iter.remove()
            }
        }
    }

    private fun drawToyParticles(canvas: Canvas) {
        for (p in particles) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.color
                alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            }
            if (p.isHeart) {
                paint.textSize = p.size * 2.2f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("❤️", p.x, p.y, paint)
            } else {
                canvas.drawCircle(p.x, p.y, p.size, paint)
            }
        }
    }

    private fun spawnToyBurst(cx: Float, cy: Float) {
        vibrateQuick()
        val colors = listOf(
            Color.parseColor("#EF4444"), // Red
            Color.parseColor("#F59E0B"), // Amber
            Color.parseColor("#10B981"), // Emerald
            Color.parseColor("#3B82F6"), // Blue
            Color.parseColor("#EC4899")  // Pink
        )
        for (i in 0..12) {
            val angle = Random.nextFloat() * 6.28f
            val speed = dp(Random.nextFloat() * 5f + 2f)
            particles.add(
                ToyParticle(
                    x = cx,
                    y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - dp(2.5f),
                    size = dp(Random.nextFloat() * 4f + 3f),
                    color = colors.random(),
                    isHeart = (i % 3 == 0)
                )
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            if (petTouchRect.contains(x, y)) {
                // Тапнули именно по питомцу -> салют конфеток, вибро и радостная анимация
                spawnToyBurst(x, y)
                IslandStateBus.playWithPet()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun vibrateQuick() {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(16L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(16L)
            }
        } catch (ignored: Exception) {
        }
    }
}

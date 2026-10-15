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

    // Палитра Dark Gunmetal / Cyber-Brick (строгий взрослый техно-стиль)
    private val C_WALL_BG       = Color.parseColor("#141824")
    private val C_STUD_BASE     = Color.parseColor("#1C2230")
    private val C_STUD_HI       = Color.parseColor("#263042")
    private val C_STUD_SH       = Color.parseColor("#0E121A")
    private val C_FLOOR_TILE    = Color.parseColor("#1E2638")
    private val C_FLOOR_HI      = Color.parseColor("#2B354C")
    private val C_FLOOR_SH      = Color.parseColor("#121722")
    private val C_PANEL_BG      = Color.parseColor("#1F273A")
    private val C_PANEL_BORDER  = Color.parseColor("#2D3850")
    private val C_NEON_CYAN     = Color.parseColor("#00E5FF")
    private val C_NEON_GREEN    = Color.parseColor("#00E676")
    private val C_TACTICAL_AMBER= Color.parseColor("#FFB300")
    private val C_GOLD          = Color.parseColor("#FFD700")
    private val C_RED_ACCENT    = Color.parseColor("#FF1744")
    private val C_MUTED_TEXT    = Color.parseColor("#94A3B8")

    // Базовые кисти
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_NEON_CYAN
        textSize = 10.5f * scaledDensity
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }

    private val statLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 9.5f * scaledDensity
        isFakeBoldText = true
    }

    private val levelTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_GOLD
        textSize = 12f * scaledDensity
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val xpTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_MUTED_TEXT
        textSize = 9f * scaledDensity
        textAlign = Paint.Align.CENTER
    }

    // Области взаимодействия
    private val petTouchRect = RectF()
    private val guestTouchRect = RectF()
    private val panelRect = RectF()

    // Частицы от тапа по питомцу
    private data class VoxelParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val size: Float,
        val color: Int,
        var alpha: Float = 1.0f,
        val life: Float = 1.0f
    )
    private val particles = mutableListOf<VoxelParticle>()

    // Анимационный таймер на 20 FPS (50ms)
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

    private fun updateParticles() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.35f // гравитация
            p.alpha -= 0.045f
            if (p.alpha <= 0f) {
                iterator.remove()
            }
        }
    }

    private fun spawnTapBurst(cx: Float, cy: Float) {
        val colors = intArrayOf(C_GOLD, C_NEON_CYAN, C_NEON_GREEN, Color.WHITE)
        for (i in 0 until 18) {
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val speed = dp(2f + Random.nextFloat() * 5f)
            val vx = cos(angle) * speed
            val vy = sin(angle) * speed - dp(2.5f)
            val size = dp(3.5f + Random.nextFloat() * 3.5f)
            val color = colors[Random.nextInt(colors.size)]
            particles.add(VoxelParticle(cx, cy, vx, vy, size, color))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val state = IslandStateBus.state.value

        val floorY = h * 0.62f

        // ==================== 1. ФОН СТЕНЫ И РЕЛЬЕФНАЯ СЕТКА КУБИКОВ ====================
        fillPaint.color = C_WALL_BG
        canvas.drawRect(0f, 0f, w, floorY, fillPaint)

        // Рельефная сетка 3D-пупырок конструктора (studs) на стене
        val gridStep = dp(18f)
        var gy = dp(6f)
        while (gy < floorY - dp(8f)) {
            var gx = dp(10f)
            while (gx < w - dp(10f)) {
                val r = dp(2.2f)
                fillPaint.color = C_STUD_SH
                canvas.drawCircle(gx + dp(0.8f), gy + dp(0.8f), r, fillPaint)
                fillPaint.color = C_STUD_BASE
                canvas.drawCircle(gx, gy, r, fillPaint)
                fillPaint.color = C_STUD_HI
                canvas.drawCircle(gx - dp(0.5f), gy - dp(0.5f), r * 0.45f, fillPaint)
                gx += gridStep
            }
            gy += gridStep
        }

        // ==================== 2. БЛОЧНОЕ ОКНО С КИБЕР-ГОРОДОМ ====================
        val winW = dp(84f)
        val winH = dp(56f)
        val winX = w * 0.46f
        val winY = floorY - winH - dp(32f)

        // Рама окна из темного титана
        for (bx in 0..6) {
            for (by in 0..4) {
                if (bx == 0 || bx == 6 || by == 0 || by == 4 || bx == 3) {
                    renderer.drawBrick(canvas, winX + bx * dp(12f), winY + by * dp(11.2f), dp(12f), C_FLOOR_TILE)
                }
            }
        }
        // Стекло и силуэты кибер-небоскребов
        fillPaint.color = Color.parseColor("#090C14")
        canvas.drawRect(winX + dp(12f), winY + dp(11f), winX + winW - dp(12f), winY + winH - dp(11f), fillPaint)

        // Огоньки города в окне
        fillPaint.color = C_NEON_CYAN
        canvas.drawRect(winX + dp(18f), winY + dp(22f), winX + dp(22f), winY + dp(40f), fillPaint)
        fillPaint.color = C_TACTICAL_AMBER
        canvas.drawRect(winX + dp(26f), winY + dp(28f), winX + dp(31f), winY + dp(42f), fillPaint)
        fillPaint.color = C_RED_ACCENT
        canvas.drawRect(winX + dp(52f), winY + dp(18f), winX + dp(57f), winY + dp(42f), fillPaint)
        fillPaint.color = C_GOLD
        canvas.drawCircle(winX + dp(64f), winY + dp(20f), dp(1.8f), fillPaint)

        // ==================== 3. ПОЛ ИЗ ТИТАНОВОЙ ПЛИТКИ ====================
        fillPaint.color = C_FLOOR_TILE
        canvas.drawRect(0f, floorY, w, h, fillPaint)

        // Швы и стыки блоков плитки пола
        strokePaint.color = C_FLOOR_HI
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawLine(0f, floorY, w, floorY, strokePaint)

        val tileW = dp(32f)
        var tx = 0f
        while (tx < w) {
            strokePaint.color = C_FLOOR_SH
            canvas.drawLine(tx, floorY, tx, h, strokePaint)
            strokePaint.color = C_FLOOR_HI
            canvas.drawLine(tx + dp(1f), floorY, tx + dp(1f), h, strokePaint)
            tx += tileW
        }

        // ==================== 4. БЛОЧНАЯ МЕБЕЛЬ И ДЕКОРАЦИИ ====================

        // СЛЕВА: Темный геймерский стол + 3D Монитор
        val deskX = dp(12f)
        val deskY = floorY - dp(18f)
        val bSize = dp(9f)
        for (bx in 0..3) {
            renderer.drawBrick(canvas, deskX + bx * bSize, deskY, bSize, C_PANEL_BG)
        }
        renderer.drawBrick(canvas, deskX, deskY + bSize, bSize, C_FLOOR_SH)
        renderer.drawBrick(canvas, deskX + bSize * 3, deskY + bSize, bSize, C_FLOOR_SH)

        // Монитор на столе с бегущим кибер-кодом
        val monX = deskX + bSize * 0.8f
        val monY = deskY - bSize * 2.8f
        renderer.drawBrick(canvas, monX + bSize * 0.6f, monY + bSize * 2f, bSize * 0.8f, C_STUD_BASE)
        fillPaint.color = Color.parseColor("#06101E")
        canvas.drawRect(monX, monY, monX + bSize * 2.2f, monY + bSize * 1.8f, fillPaint)
        strokePaint.color = C_NEON_CYAN
        strokePaint.strokeWidth = dp(1f)
        canvas.drawRect(monX, monY, monX + bSize * 2.2f, monY + bSize * 1.8f, strokePaint)

        // Линии кода на мониторе
        fillPaint.color = C_NEON_CYAN
        canvas.drawRect(monX + dp(2f), monY + dp(3f), monX + dp(12f), monY + dp(4.5f), fillPaint)
        canvas.drawRect(monX + dp(2f), monY + dp(7f), monX + dp(16f), monY + dp(8.5f), fillPaint)
        canvas.drawRect(monX + dp(2f), monY + dp(11f), monX + dp(9f), monY + dp(12.5f), fillPaint)

        // СПРАВА: Блочная аудио-стойка с колонкой и миской энергетика
        val rackX = w - dp(46f)
        val rackY = floorY - dp(24f)
        for (ry in 0..2) {
            for (rx in 0..2) {
                val c = if (rx == 1 && ry == 1) C_NEON_CYAN else C_STUD_BASE
                renderer.drawBrick(canvas, rackX + rx * bSize, rackY + ry * bSize, bSize, c)
            }
        }
        // Миска энергетика рядом со стойкой
        val bowlX = rackX - dp(18f)
        val bowlY = floorY - dp(6f)
        renderer.drawBrick(canvas, bowlX, bowlY, dp(7f), C_TACTICAL_AMBER)
        renderer.drawBrick(canvas, bowlX + dp(7f), bowlY, dp(7f), C_TACTICAL_AMBER)

        // КОВРИК В ЦЕНТРЕ КОМНАТЫ
        val matW = if (state.hasGuestPet) dp(170f) else dp(110f)
        val matH = dp(24f)
        val matLeft = (w - matW) / 2f
        val matTop = floorY + dp(28f)
        val matRect = RectF(matLeft, matTop, matLeft + matW, matTop + matH)

        fillPaint.color = Color.parseColor("#151D2A")
        canvas.drawRoundRect(matRect, dp(6f), dp(6f), fillPaint)
        strokePaint.color = C_NEON_CYAN
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(matRect, dp(6f), dp(6f), strokePaint)

        // ==================== 5. КРУПНЫЙ 3D-БЛОЧНЫЙ КИБЕР-КОТ ====================
        val petSize = dp(84f)
        val petBottom = matTop + dp(12f)
        val petTop = petBottom - petSize

        if (state.hasGuestPet) {
            val p1Left = (w / 2f) - petSize - dp(6f)
            petTouchRect.set(p1Left, petTop, p1Left + petSize, petBottom)
            renderer.drawPet(canvas, petTouchRect, state.petMode, frameIndex)

            val p2Left = (w / 2f) + dp(6f)
            guestTouchRect.set(p2Left, petTop, p2Left + petSize, petBottom)
            renderer.drawGuestPet(canvas, guestTouchRect, frameIndex)
        } else {
            val pLeft = (w - petSize) / 2f
            petTouchRect.set(pLeft, petTop, pLeft + petSize, petBottom)
            guestTouchRect.setEmpty()
            renderer.drawPet(canvas, petTouchRect, state.petMode, frameIndex)
        }

        // ==================== 6. РАЗЛЕТ ЧАСТИЦ (ВОКСЕЛЬНЫЙ ВЗРЫВ) ====================
        for (p in particles) {
            fillPaint.color = p.color
            fillPaint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, fillPaint)
        }

        // ==================== 7. ВЕРХНЯЯ ОБЪЕМНАЯ ПАНЕЛЬ СТАТУСА ====================
        val pLeft = dp(12f)
        val pTop = dp(8f)
        val pRight = w - dp(12f)
        val pH = dp(68f)
        panelRect.set(pLeft, pTop, pRight, pTop + pH)

        // Фон и 3D-рамка панели
        fillPaint.color = C_PANEL_BG
        canvas.drawRoundRect(panelRect, dp(12f), dp(12f), fillPaint)
        strokePaint.color = C_PANEL_BORDER
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(panelRect, dp(12f), dp(12f), strokePaint)

        // 3D-пупырки по верхнему краю панели для аутентичности конструктора
        var sX = panelRect.left + dp(14f)
        while (sX < panelRect.right - dp(14f)) {
            val sr = dp(2f)
            fillPaint.color = C_STUD_SH
            canvas.drawCircle(sX + dp(0.6f), panelRect.top + dp(3.5f), sr, fillPaint)
            fillPaint.color = C_STUD_BASE
            canvas.drawCircle(sX, panelRect.top + dp(3f), sr, fillPaint)
            sX += dp(16f)
        }

        // Аватарка слева
        val avSize = dp(40f)
        val avRect = RectF(panelRect.left + dp(10f), panelRect.centerY() - avSize / 2f + dp(2f), panelRect.left + dp(10f) + avSize, panelRect.centerY() + avSize / 2f + dp(2f))
        fillPaint.color = Color.parseColor("#121724")
        canvas.drawRoundRect(avRect, dp(8f), dp(8f), fillPaint)
        strokePaint.color = C_NEON_CYAN
        strokePaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(avRect, dp(8f), dp(8f), strokePaint)
        renderer.drawPet(canvas, avRect, PetMode.IDLE, frameIndex)

        // Правая секция: разделитель, иконка уровня и шкала XP
        val rightSecW = dp(84f)
        val divX = panelRect.right - rightSecW
        strokePaint.color = C_PANEL_BORDER
        strokePaint.strokeWidth = dp(1.2f)
        canvas.drawLine(divX, panelRect.top + dp(10f), divX, panelRect.bottom - dp(10f), strokePaint)

        val rightCenterX = divX + rightSecW / 2f

        // Блочная иконка кристалла
        val crX = rightCenterX - dp(4f)
        val crY = panelRect.top + dp(10f)
        renderer.drawBrick(canvas, crX, crY, dp(8f), C_GOLD)

        canvas.drawText("УРОВЕНЬ ${state.petLevel}", rightCenterX, panelRect.top + dp(34f), levelTextPaint)
        canvas.drawText("${state.xp}/100 XP", rightCenterX, panelRect.top + dp(47f), xpTextPaint)

        // Полоска XP
        val xpBarW = rightSecW - dp(24f)
        val xpBarH = dp(3.5f)
        val xpLeft = rightCenterX - xpBarW / 2f
        val xpTop = panelRect.top + dp(52f)
        val xpRect = RectF(xpLeft, xpTop, xpLeft + xpBarW, xpTop + xpBarH)
        fillPaint.color = Color.parseColor("#111622")
        canvas.drawRoundRect(xpRect, dp(2f), dp(2f), fillPaint)

        val xpFillW = (xpBarW * (state.xp.coerceIn(0, 100) / 100f)).coerceAtLeast(dp(2f))
        fillPaint.color = C_GOLD
        canvas.drawRoundRect(RectF(xpLeft, xpTop, xpLeft + xpFillW, xpTop + xpBarH), dp(2f), dp(2f), fillPaint)

        // Центральная секция: Заголовок и 3 прогресс-бара со значками
        val centerLeft = avRect.right + dp(10f)
        val centerRight = divX - dp(10f)
        val centerW = centerRight - centerLeft

        canvas.drawText("CYBER-PET // БЛОК-ЮНИТ", centerLeft, panelRect.top + dp(18f), titleTextPaint)

        val barW = centerW - dp(28f)
        val barH = dp(5.5f)
        val startBarsY = panelRect.top + dp(23f)
        val stepBarY = dp(14f)

        // 1) ⚡️ Энергия (неоновый изумруд)
        drawStatBar(canvas, centerLeft, startBarsY, barW, barH, "⚡", state.energy, C_NEON_GREEN)

        // 2) 🔥 Настроение (тактический янтарь)
        drawStatBar(canvas, centerLeft, startBarsY + stepBarY, barW, barH, "🔥", state.mood, C_TACTICAL_AMBER)

        // 3) 🍔 Сытость (электрический циан)
        drawStatBar(canvas, centerLeft, startBarsY + stepBarY * 2f, barW, barH, "🍔", state.hunger, C_NEON_CYAN)
    }

    private fun drawStatBar(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, icon: String, value: Int, color: Int) {
        canvas.drawText(icon, x, y + dp(6.5f), statLabelPaint)

        val barLeft = x + dp(16f)
        val barTop = y + dp(1f)
        val barRect = RectF(barLeft, barTop, barLeft + width, barTop + height)

        fillPaint.color = Color.parseColor("#111622")
        canvas.drawRoundRect(barRect, height / 2f, height / 2f, fillPaint)

        val fillW = (width * (value.coerceIn(0, 100) / 100f)).coerceAtLeast(height)
        fillPaint.color = color
        canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + fillW, barTop + height), height / 2f, height / 2f, fillPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            if (petTouchRect.contains(x, y)) {
                vibrateTap()
                spawnTapBurst(petTouchRect.centerX(), petTouchRect.centerY())
                IslandStateBus.playWithPet()
                FirebaseCoupleManager.sendPartnerAction("HEART")
                return true
            }

            if (!guestTouchRect.isEmpty && guestTouchRect.contains(x, y)) {
                vibrateTap()
                spawnTapBurst(guestTouchRect.centerX(), guestTouchRect.centerY())
                IslandStateBus.playWithPet()
                FirebaseCoupleManager.sendPartnerAction("HEART")
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun vibrateTap() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(35L)
                }
            }
        } catch (ignored: Exception) {
        }
    }
}

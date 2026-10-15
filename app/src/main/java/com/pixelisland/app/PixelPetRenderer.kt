package com.pixelisland.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PixelPetRenderer(private val context: Context) {

    // Кисти для 3D Lego / Voxel кубиков
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val studPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val studShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val studHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val pixelPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }

    private val guestPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
        colorFilter = PorterDuffColorFilter(Color.rgb(128, 222, 255), PorterDuff.Mode.MULTIPLY)
    }

    @Suppress("DEPRECATION")
    private var customMovie: Movie? = null
    private var customBitmap: Bitmap? = null
    private var loadedVersion: Int = -1

    // Премиальная палитра Cyber-Cat (Темный титан, керамический белый, неоновый циан и золото)
    private val C_TITAN_DARK = Color.parseColor("#15181E")
    private val C_TITAN_MID  = Color.parseColor("#252B36")
    private val C_TITAN_LGT  = Color.parseColor("#3D4657")
    private val C_WHITE_ARMOR= Color.parseColor("#EEF4FB")
    private val C_WHITE_SHAD = Color.parseColor("#B8C6D6")
    private val C_NEON_CYAN  = Color.parseColor("#00E5FF")
    private val C_CYAN_GLOW  = Color.parseColor("#18FFFF")
    private val C_RED_WARN   = Color.parseColor("#FF1744")
    private val C_AMBER_WARN = Color.parseColor("#FF9100")
    private val C_GOLD       = Color.parseColor("#FFD700")
    private val C_NEON_GREEN = Color.parseColor("#00E676")
    private val C_BURGER_BUN = Color.parseColor("#E59866")
    private val C_BURGER_MEAT= Color.parseColor("#6E2C00")
    private val C_BURGER_CHZ = Color.parseColor("#F1C40F")
    private val C_BURGER_SAL = Color.parseColor("#27AE60")

    private fun adjustBrightness(color: Int, amount: Int): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) + amount).coerceIn(0, 255)
        val g = (Color.green(color) + amount).coerceIn(0, 255)
        val b = (Color.blue(color) + amount).coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    /**
     * Отрисовка объемного 3D-кубика конструктора (Lego / Voxel Brick):
     * 1. Основной квадрат цвета [color]
     * 2. Светлая 3D-грань сверху и слева (+35 к яркости)
     * 3. Темная тень снизу и справа (-45 к яркости)
     * 4. Объемная пупырка (stud) конструктора по центру сверху со светотенью
     */
    fun drawBrick(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        if (color == Color.TRANSPARENT) return

        // 1. Основное тело кирпичика
        basePaint.color = color
        canvas.drawRect(x, y, x + size, y + size, basePaint)

        val edgeH = size * 0.16f

        // 2. Светлая грань сверху и слева (+35 к яркости)
        val highlightColor = adjustBrightness(color, 35)
        highlightPaint.color = highlightColor
        canvas.drawRect(x, y, x + size, y + edgeH, highlightPaint)
        canvas.drawRect(x, y, x + edgeH, y + size, highlightPaint)

        // 3. Теневая грань снизу и справа (-45 к яркости)
        val shadowColor = adjustBrightness(color, -45)
        shadowPaint.color = shadowColor
        canvas.drawRect(x, y + size - edgeH, x + size, y + size, shadowPaint)
        canvas.drawRect(x + size - edgeH, y, x + size, y + size, shadowPaint)

        // 4. Объемная круглая пупырка конструктора (stud)
        val studR = size * 0.26f
        val cx = x + size / 2f
        val cy = y + size / 2f

        studShadowPaint.color = shadowColor
        canvas.drawCircle(cx, cy + size * 0.06f, studR, studShadowPaint)

        studPaint.color = color
        canvas.drawCircle(cx, cy, studR, studPaint)

        studHighlightPaint.color = highlightColor
        canvas.drawCircle(cx - studR * 0.28f, cy - studR * 0.28f, studR * 0.35f, studHighlightPaint)
    }

    fun reloadCustomPetIfNeeded(version: Int) {
        if (version == loadedVersion) return
        loadedVersion = version
        customMovie = null
        customBitmap = null

        val file = File(context.filesDir, "custom_pet_asset")
        if (!file.exists() || file.length() <= 0) return

        try {
            @Suppress("DEPRECATION")
            val movie = Movie.decodeFile(file.absolutePath)
            if (movie != null && movie.duration() > 0) {
                customMovie = movie
                return
            }
        } catch (ignored: Exception) {
        }

        try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)

            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > 128 || boundsOpts.outHeight / sampleSize > 128) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            customBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (ignored: Exception) {
        }
    }

    fun drawPet(canvas: Canvas, dstRect: RectF, mode: PetMode, frameIndex: Int) {
        val state = IslandStateBus.state.value
        reloadCustomPetIfNeeded(state.customPetVersion)

        val canUseCustom = (state.isProLifetime || System.currentTimeMillis() < state.customPetExpireMs) &&
                (customMovie != null || customBitmap != null)

        if (canUseCustom) {
            drawCustomAsset(canvas, dstRect, mode, frameIndex, state)
            return
        }

        // ==================== РЕНДЕР 3D LEGO КИБЕР-КОТА ====================
        val time = System.currentTimeMillis()

        canvas.save()

        // 1. АНИМАЦИЯ НАКЛОНА / ВИСЕНИЯ НА КРАЮ (TILT / HANGING)
        if (state.isHangingOnEdge) {
            val pendulumAngle = sin(time * 0.007f) * 24f
            val pivotX = if (state.tiltOffsetPx < 0) dstRect.left + dstRect.width() * 0.2f else dstRect.right - dstRect.width() * 0.2f
            canvas.rotate(pendulumAngle, pivotX, dstRect.top)

            // Летящие капли пота от напряжения
            val sweatPhase = (time % 700) / 700f
            val sweatY = dstRect.top + sweatPhase * dstRect.height() * 0.9f
            val sweatX = dstRect.centerX() + sin(time * 0.02f) * dstRect.width() * 0.35f
            drawBrick(canvas, sweatX, sweatY, dstRect.width() * 0.08f, C_CYAN_GLOW)
        }

        // 2. АНИМАЦИЯ ДУМСКРОЛЛИНГА (ЗУМ 1.4x, ПОДХОД К ЭКРАНУ)
        val isDoomscroll = !state.doomscrollWarning.isNullOrBlank()
        if (isDoomscroll) {
            canvas.scale(1.4f, 1.4f, dstRect.centerX(), dstRect.centerY())
        }

        val bSize = dstRect.width() / 14f
        var startX = dstRect.left
        var startY = dstRect.top + dstRect.height() * 0.07f

        // Анимации вертикального смещения (дыхание, бит музыки, прыжок)
        when (mode) {
            PetMode.IDLE -> {
                val breathY = sin(time * 0.004f) * (bSize * 0.45f)
                startY += breathY
            }
            PetMode.CHARGING -> {
                val floatY = abs(sin(time * 0.012f)) * (bSize * 1.6f)
                startY -= floatY
            }
            PetMode.MUSIC -> {
                val beatY = abs(sin(time * 0.01f)) * (bSize * 1.2f)
                startY += beatY
            }
            PetMode.NOTIFICATION -> {
                val jumpY = abs(sin(time * 0.016f)) * (bSize * 1.8f)
                startY -= jumpY
            }
            else -> {}
        }

        // Цвет глаз: в режиме думскролла - агрессивно янтарно-красный, во сне - выключен, иначе неоново-голубой
        val eyeColor = when {
            isDoomscroll -> if ((time % 600) < 300) C_RED_WARN else C_AMBER_WARN
            mode == PetMode.SLEEP -> C_TITAN_MID
            else -> C_CYAN_GLOW
        }

        // УШИ
        val earTwitch = if (mode == PetMode.MUSIC) (sin(time * 0.02f) * bSize * 0.6f) else 0f
        drawBrick(canvas, startX + bSize * 2, startY + bSize * 1 + earTwitch, bSize, C_TITAN_MID)
        drawBrick(canvas, startX + bSize * 3, startY + bSize * 2, bSize, C_CYAN_GLOW)
        drawBrick(canvas, startX + bSize * 10, startY + bSize * 2, bSize, C_CYAN_GLOW)
        drawBrick(canvas, startX + bSize * 11, startY + bSize * 1 - earTwitch, bSize, C_TITAN_MID)

        // ГОЛОВА (ТИТАН И БЕЛАЯ БРОНЯ)
        for (gx in 3..10) {
            drawBrick(canvas, startX + bSize * gx, startY + bSize * 3, bSize, C_TITAN_DARK)
            drawBrick(canvas, startX + bSize * gx, startY + bSize * 4, bSize, C_WHITE_ARMOR)
            drawBrick(canvas, startX + bSize * gx, startY + bSize * 5, bSize, C_WHITE_ARMOR)
            drawBrick(canvas, startX + bSize * gx, startY + bSize * 6, bSize, C_TITAN_MID)
        }

        // НЕОНОВЫЕ ГЛАЗА
        if (mode == PetMode.SLEEP) {
            // Глаза закрыты (титановые щелки)
            drawBrick(canvas, startX + bSize * 4, startY + bSize * 4, bSize, C_TITAN_DARK)
            drawBrick(canvas, startX + bSize * 5, startY + bSize * 4, bSize, C_TITAN_DARK)
            drawBrick(canvas, startX + bSize * 8, startY + bSize * 4, bSize, C_TITAN_DARK)
            drawBrick(canvas, startX + bSize * 9, startY + bSize * 4, bSize, C_TITAN_DARK)
        } else {
            drawBrick(canvas, startX + bSize * 4, startY + bSize * 4, bSize, eyeColor)
            drawBrick(canvas, startX + bSize * 5, startY + bSize * 4, bSize, eyeColor)
            drawBrick(canvas, startX + bSize * 8, startY + bSize * 4, bSize, eyeColor)
            drawBrick(canvas, startX + bSize * 9, startY + bSize * 4, bSize, eyeColor)
        }

        // КИБЕР-НОСИК И ЩЕЧКИ / РОТИК
        val burgerPhase = (time % 2000) / 2000f
        if (mode == PetMode.HAPPY && burgerPhase in 0.35f..0.68f) {
            // Открытый рот во время жевания бургера (кусь!)
            drawBrick(canvas, startX + bSize * 6f, startY + bSize * 5.2f, bSize * 2f, C_TITAN_DARK)
            drawBrick(canvas, startX + bSize * 6.5f, startY + bSize * 5.6f, bSize, C_RED_WARN)
        } else {
            drawBrick(canvas, startX + bSize * 6.5f, startY + bSize * 5, bSize, C_NEON_CYAN)
        }

        // ТУЛОВИЩЕ И ЛАПЫ
        for (gx in 4..9) {
            drawBrick(canvas, startX + bSize * gx, startY + bSize * 7, bSize, C_TITAN_DARK)
            drawBrick(canvas, startX + bSize * gx, startY + bSize * 8, bSize, C_WHITE_ARMOR)
            drawBrick(canvas, startX + bSize * gx, startY + bSize * 9, bSize, C_TITAN_MID)
        }

        // ПЕРЕДНИЕ ЛАПЫ (Стук по экрану в режиме Doomscroll / махи в уведомлениях)
        val tapPaw = if (isDoomscroll && (time % 360 < 180)) -bSize * 1.3f else 0f
        val notifWave = if (mode == PetMode.NOTIFICATION) (sin(time * 0.02f) * bSize * 0.8f) else 0f

        drawBrick(canvas, startX + bSize * 4, startY + bSize * 10 + tapPaw + notifWave, bSize, C_WHITE_ARMOR)
        drawBrick(canvas, startX + bSize * 5, startY + bSize * 10, bSize, C_TITAN_DARK)
        drawBrick(canvas, startX + bSize * 8, startY + bSize * 10, bSize, C_TITAN_DARK)
        drawBrick(canvas, startX + bSize * 9, startY + bSize * 10 - notifWave, bSize, C_WHITE_ARMOR)

        // ХВОСТ (ДИНАМИЧЕСКИЙ СИНУС-МАЯТНИК)
        val tailWave = sin(time * 0.008f) * bSize * 1.5f
        drawBrick(canvas, startX + bSize * 11, startY + bSize * 8, bSize, C_TITAN_DARK)
        drawBrick(canvas, startX + bSize * 12 + tailWave * 0.5f, startY + bSize * 7, bSize, C_CYAN_GLOW)
        drawBrick(canvas, startX + bSize * 12.5f + tailWave, startY + bSize * 6, bSize, C_NEON_CYAN)

        // ==================== РЕЖИМНЫЕ СПЕЦЭФФЕКТЫ ====================

        // [MUSIC] БОЛЬШИЕ НАУШНИКИ + DJ БУМБОКС С ЛЕТЯЩИМИ НЕОНОВЫМИ НОТАМИ
        if (mode == PetMode.MUSIC) {
            // Наушники на голове
            drawBrick(canvas, startX + bSize * 2, startY + bSize * 4, bSize, C_CYAN_GLOW)
            drawBrick(canvas, startX + bSize * 11, startY + bSize * 4, bSize, C_CYAN_GLOW)
            for (bx in 3..10) {
                drawBrick(canvas, startX + bSize * bx, startY + bSize * 2.5f, bSize * 0.8f, C_NEON_CYAN)
            }

            // DJ Бумбокс слева
            val boomX = startX - bSize * 3.6f
            val boomY = startY + bSize * 6
            for (by in 0..3) {
                for (bx in 0..2) {
                    val c = if (bx == 1 && (by == 1 || by == 2)) C_CYAN_GLOW else C_TITAN_DARK
                    drawBrick(canvas, boomX + bx * bSize, boomY + by * bSize, bSize, c)
                }
            }

            // Летящие вверх ноты
            val note1T = (time % 1000) / 1000f
            val note1Y = startY + bSize * 5 - note1T * (bSize * 8f)
            val note1X = startX - bSize * 2 + sin(time * 0.008f) * bSize * 1.5f
            drawBrick(canvas, note1X, note1Y, bSize * 0.7f, C_CYAN_GLOW)

            val note2T = ((time + 500) % 1000) / 1000f
            val note2Y = startY + bSize * 5 - note2T * (bSize * 8f)
            val note2X = startX + bSize * 12 + sin(time * 0.006f) * bSize * 1.5f
            drawBrick(canvas, note2X, note2Y, bSize * 0.7f, C_GOLD)
        }

        // [CHARGING] ЭНЕРГЕТИЧЕСКОЕ ПОЛЕ, ИСКРЫ И ЗЕЛЕНАЯ МОЛНИЯ
        if (mode == PetMode.CHARGING) {
            // Молния справа
            val lx = startX + bSize * 11.5f
            drawBrick(canvas, lx + bSize * 1.5f, startY + bSize * 2, bSize, C_GOLD)
            drawBrick(canvas, lx + bSize * 1f, startY + bSize * 3, bSize, C_GOLD)
            drawBrick(canvas, lx + bSize * 0.5f, startY + bSize * 4, bSize, C_NEON_GREEN)
            drawBrick(canvas, lx + bSize * 1.5f, startY + bSize * 4, bSize, C_GOLD)
            drawBrick(canvas, lx + bSize * 1f, startY + bSize * 5, bSize, C_NEON_GREEN)
            drawBrick(canvas, lx + bSize * 0.5f, startY + bSize * 6, bSize, C_GOLD)

            // Летящие блочные искры снизу вверх
            for (i in 0..4) {
                val sparkPhase = ((time + i * 200) % 900) / 900f
                val spY = dstRect.bottom - sparkPhase * dstRect.height() * 1.1f
                val spX = dstRect.left + (i * bSize * 2.6f)
                val sparkColor = if (i % 2 == 0) C_NEON_GREEN else C_CYAN_GLOW
                drawBrick(canvas, spX, spY, bSize * 0.6f, sparkColor)
            }
        }

        // [NOTIFICATION] ЯРКИЙ ЖЕЛТО-ГОЛУБОЙ КОНВЕРТ И ОБЛАЧКО [ ! ]
        if (mode == PetMode.NOTIFICATION) {
            // Конверт в лапах
            val envX = startX + bSize * 5
            val envY = startY + bSize * 6.5f
            for (ey in 0..1) {
                for (ex in 0..3) {
                    val c = if (ex == 0 || ex == 3 || ey == 0) C_GOLD else C_CYAN_GLOW
                    drawBrick(canvas, envX + ex * bSize, envY + ey * bSize, bSize, c)
                }
            }

            // Облачко диалога [ ! ] над головой
            val bubbleY = startY - bSize * 2.5f
            val bubbleX = startX + bSize * 5.5f
            drawBrick(canvas, bubbleX, bubbleY, bSize * 1.2f, C_RED_WARN)
            textPaint.color = Color.WHITE
            textPaint.textSize = bSize * 1.1f
            canvas.drawText("!", bubbleX + bSize * 0.6f, bubbleY + bSize * 0.95f, textPaint)
        }

        // [DOOMSCROLL] ШИРОКАЯ ТАБЛИЧКА С ПРЕДУПРЕЖДЕНИЕМ
        if (isDoomscroll) {
            val signW = bSize * 10f
            val signX = startX + bSize * 2f
            val signY = startY + bSize * 8.5f
            for (sy in 0..2) {
                for (sx in 0..9) {
                    val c = if (sx == 0 || sx == 9 || sy == 0 || sy == 2) C_GOLD else C_TITAN_DARK
                    drawBrick(canvas, signX + sx * bSize, signY + sy * bSize, bSize, c)
                }
            }
            textPaint.color = C_RED_WARN
            textPaint.textSize = bSize * 1.15f
            canvas.drawText("STOP!", signX + signW / 2f, signY + bSize * 2.05f, textPaint)
        }

        // [HAPPY] ПАДАЮЩИЙ И СЪЕДАЕМЫЙ БУРГЕР + ВЗРЫВ ЗОЛОТЫХ +XP КУБИКОВ
        if (mode == PetMode.HAPPY) {
            val burgerY = startY + (burgerPhase * bSize * 6f).coerceAtMost(bSize * 5.5f)
            val burgerX = startX + bSize * 4.5f

            if (burgerPhase < 0.65f) {
                // 3D Voxel Burger (Булочка, салат, сыр, котлета, булочка)
                drawBrick(canvas, burgerX, burgerY, bSize * 1.2f, C_BURGER_BUN)
                drawBrick(canvas, burgerX + bSize * 1.2f, burgerY, bSize * 1.2f, C_BURGER_BUN)

                drawBrick(canvas, burgerX, burgerY + bSize * 0.8f, bSize * 1.2f, C_BURGER_SAL)
                drawBrick(canvas, burgerX + bSize * 1.2f, burgerY + bSize * 0.8f, bSize * 1.2f, C_BURGER_CHZ)

                drawBrick(canvas, burgerX, burgerY + bSize * 1.6f, bSize * 1.2f, C_BURGER_MEAT)
                drawBrick(canvas, burgerX + bSize * 1.2f, burgerY + bSize * 1.6f, bSize * 1.2f, C_BURGER_BUN)
            } else {
                // Взрыв золотых кубиков опыта +XP
                for (k in 0..5) {
                    val angle = (k * 60f) * (Math.PI.toFloat() / 180f)
                    val dist = (burgerPhase - 0.65f) * bSize * 12f
                    val xpX = startX + bSize * 6f + cos(angle) * dist
                    val xpY = startY + bSize * 4f + sin(angle) * dist
                    drawBrick(canvas, xpX, xpY, bSize * 0.8f, C_GOLD)
                }
            }
        }

        canvas.restore()
    }

    private fun drawCustomAsset(canvas: Canvas, dstRect: RectF, mode: PetMode, frameIndex: Int, state: IslandState) {
        val movie = customMovie
        val bitmap = customBitmap

        canvas.save()
        if (state.isHangingOnEdge) {
            val rot = if (state.tiltOffsetPx < 0) -25f else 25f
            canvas.rotate(rot, dstRect.centerX(), dstRect.centerY())
        }

        if (movie != null) {
            @Suppress("DEPRECATION")
            val duration = movie.duration().let { if (it <= 0) 1000 else it }
            val t = (System.currentTimeMillis() % duration).toInt()
            @Suppress("DEPRECATION")
            movie.setTime(t)

            @Suppress("DEPRECATION")
            val mw = movie.width().toFloat().let { if (it <= 0f) 128f else it }
            @Suppress("DEPRECATION")
            val mh = movie.height().toFloat().let { if (it <= 0f) 128f else it }

            canvas.save()
            canvas.translate(dstRect.left, dstRect.top)
            canvas.scale(dstRect.width() / mw, dstRect.height() / mh)
            @Suppress("DEPRECATION")
            movie.draw(canvas, 0f, 0f)
            canvas.restore()
        } else if (bitmap != null) {
            val density = context.resources.displayMetrics.density
            val bounceY = if (frameIndex % 2 != 0) -2f * density else 0f
            val targetRect = RectF(dstRect.left, dstRect.top + bounceY, dstRect.right, dstRect.bottom + bounceY)

            if (mode == PetMode.MUSIC) {
                val rot = if (frameIndex % 2 == 0) 6f else -6f
                canvas.rotate(rot, targetRect.centerX(), targetRect.centerY())
            }
            canvas.drawBitmap(bitmap, null, targetRect, pixelPaint)
        }

        canvas.restore()
    }

    fun drawGuestPet(canvas: Canvas, dstRect: RectF, frameIndex: Int) {
        canvas.save()
        canvas.scale(-1f, 1f, dstRect.centerX(), dstRect.centerY())
        drawPet(canvas, dstRect, PetMode.HAPPY, frameIndex)
        canvas.restore()
    }

    fun getFrameDelayMs(mode: PetMode): Long {
        // Плавные 20 FPS для эффектов 3D-конструктора
        return 50L
    }
}

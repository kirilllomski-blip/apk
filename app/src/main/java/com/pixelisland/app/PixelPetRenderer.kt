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
        colorFilter = PorterDuffColorFilter(Color.rgb(255, 230, 200), PorterDuff.Mode.MULTIPLY)
    }

    @Suppress("DEPRECATION")
    private var customMovie: Movie? = null
    private var customBitmap: Bitmap? = null
    private var loadedVersion: Int = -1

    // ==================== УЮТНАЯ ПАЛИТРА 3D-КОНСТРУКТОРА ИГРУШЕК ====================

    // Кот: Рыже-белый пушистик
    private val C_CAT_GINGER       = Color.parseColor("#F97316") // Яркий теплый рыжий
    private val C_CAT_GINGER_DARK  = Color.parseColor("#EA580C") // Темно-рыжие полоски/ушки
    private val C_CAT_WHITE        = Color.parseColor("#FFFDF7") // Кремово-белая мордочка/грудка
    private val C_CAT_SHADOW       = Color.parseColor("#E2D9CC") // Тень белой шерстки
    private val C_CAT_PINK         = Color.parseColor("#FB7185") // Розовый носик, ушки внутри, румянец
    private val C_CAT_EYE          = Color.parseColor("#1E293B") // Глазки-бусинки
    private val C_COLLAR_TEAL      = Color.parseColor("#06B6D4") // Бирюзовый ошейник
    private val C_BELL_GOLD        = Color.parseColor("#FBBF24") // Золотой колокольчик

    // Собака: Сиба-Ину / Корги
    private val C_DOG_HONEY        = Color.parseColor("#F59E0B") // Золотисто-медовый окрас
    private val C_DOG_TAN_DARK     = Color.parseColor("#D97706") // Темно-рыжие ушки и спинка
    private val C_DOG_WHITE        = Color.parseColor("#FFFDF7") // Белые щечки, манишка и лапки
    private val C_DOG_SHADOW       = Color.parseColor("#E5DCCE")
    private val C_DOG_NOSE         = Color.parseColor("#0F172A") // Черный носик-кнопка
    private val C_DOG_TONGUE       = Color.parseColor("#FB7185") // Розовый высунутый язычок
    private val C_COLLAR_RED       = Color.parseColor("#EF4444") // Красный ошейник

    // Аксессуары и эмоции
    private val C_HEADPHONES_RED   = Color.parseColor("#FF3D00") // ОГРОМНЫЕ красные наушники
    private val C_HEADPHONES_CUP   = Color.parseColor("#18181B") // Темные амбушюры
    private val C_VINYL_BLACK      = Color.parseColor("#09090B") // Виниловая пластинка
    private val C_NOTE_CYAN        = Color.parseColor("#06B6D4") // Ноты
    private val C_NOTE_YELLOW      = Color.parseColor("#FBBF24")
    private val C_HEART_RED        = Color.parseColor("#EF4444") // Сердечки
    private val C_STAR_GOLD        = Color.parseColor("#F59E0B") // Звездочки
    private val C_BURGER_BUN       = Color.parseColor("#F59E0B") // Булочка бургера
    private val C_BURGER_SAL       = Color.parseColor("#22C55E") // Лист салата
    private val C_BURGER_CHZ       = Color.parseColor("#FBBF24") // Сыр
    private val C_BURGER_MEAT      = Color.parseColor("#78350F") // Котлета
    private val C_BONE_WHITE       = Color.parseColor("#F8FAFC") // Сахарная косточка
    private val C_SIGN_YELLOW      = Color.parseColor("#FDE047") // Желтая табличка Анти-Рилсы
    private val C_SIGN_BORDER      = Color.parseColor("#DC2626")

    private fun adjustBrightness(color: Int, amount: Int): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) + amount).coerceIn(0, 255)
        val g = (Color.green(color) + amount).coerceIn(0, 255)
        val b = (Color.blue(color) + amount).coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    /**
     * Отрисовка объемного 3D-кубика конструктора (Lego Brick):
     * 1. Основной квадрат цвета [color]
     * 2. Светлая грань сверху и слева (+35 к яркости)
     * 3. Мягкая теневая грань снизу и справа (-40 к яркости)
     * 4. Объемная пупырка (stud) конструктора со светотенью
     */
    fun drawBrick(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        if (color == Color.TRANSPARENT || size <= 0f) return

        // 1. Тело кирпичика
        basePaint.color = color
        canvas.drawRect(x, y, x + size, y + size, basePaint)

        val edgeH = size * 0.16f

        // 2. Свет сверху и слева
        val highlightColor = adjustBrightness(color, 35)
        highlightPaint.color = highlightColor
        canvas.drawRect(x, y, x + size, y + edgeH, highlightPaint)
        canvas.drawRect(x, y, x + edgeH, y + size, highlightPaint)

        // 3. Тень снизу и справа
        val shadowColor = adjustBrightness(color, -40)
        shadowPaint.color = shadowColor
        canvas.drawRect(x, y + size - edgeH, x + size, y + size, shadowPaint)
        canvas.drawRect(x + size - edgeH, y, x + size, y + size, shadowPaint)

        // 4. Круглая пупырка конструктора (stud)
        val studR = size * 0.25f
        val cx = x + size / 2f
        val cy = y + size / 2f

        studShadowPaint.color = shadowColor
        canvas.drawCircle(cx, cy + size * 0.05f, studR, studShadowPaint)

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

    /**
     * Главная точка входа отрисовки питомца:
     * Выбирает Кот / Собачка, рисует анимацию соответствующего режима PetMode.
     */
    /**
     * Главная точка входа отрисовки питомца:
     * Выбирает Кот / Собачка, рисует анимацию соответствующего режима PetMode,
     * а также поддерживает ходьбу, поворот влево, уютное лежание калачиком и парящие сердечки!
     */
    fun drawPet(
        canvas: Canvas,
        dstRect: RectF,
        mode: PetMode,
        frameIndex: Int,
        isWalking: Boolean = false,
        isFacingLeft: Boolean = false,
        isLyingDown: Boolean = false,
        isEmittingHearts: Boolean = false
    ) {
        val state = IslandStateBus.state.value
        reloadCustomPetIfNeeded(state.customPetVersion)

        val canUseCustom = (state.isProLifetime || System.currentTimeMillis() < state.customPetExpireMs) &&
                (customMovie != null || customBitmap != null)

        if (canUseCustom) {
            drawCustomAsset(canvas, dstRect, mode, frameIndex, state)
            return
        }

        val time = System.currentTimeMillis()
        val timeSec = time * 0.001f

        canvas.save()

        // 1. АНИМАЦИЯ НАКЛОНА (ВИСИТ НА КРАЮ ОСТРОВКА)
        if (state.isHangingOnEdge) {
            val pendulumAngle = sin(timeSec * 4f) * 20f
            val pivotX = if (state.tiltOffsetPx < 0) dstRect.left + dstRect.width() * 0.2f else dstRect.right - dstRect.width() * 0.2f
            canvas.rotate(pendulumAngle, pivotX, dstRect.top)
        }

        // 2. РЕЖИМ ГОСТЕЙ (COUPLE_VISIT): Рисуем ДВУХ питомцев рядом!
        if (mode == PetMode.COUPLE_VISIT || state.hasGuestPet) {
            drawCouplePets(canvas, dstRect, state.species, time)
            canvas.restore()
            return
        }

        // Поворот в сторону движения (зеркально отражаем при ходьбе влево)
        if (isFacingLeft && !state.isHangingOnEdge) {
            canvas.scale(-1f, 1f, dstRect.centerX(), dstRect.centerY())
        }

        // 3. ОБЫЧНЫЙ ОДИНОЧНЫЙ ПИТОМЕЦ
        val bSize = dstRect.width() / 14f
        var startX = dstRect.left + dstRect.width() * 0.12f
        var startY = dstRect.top + dstRect.height() * 0.08f

        // Анимации вертикального смещения (шаги, дыхание, бит музыки, прыжок)
        when (mode) {
            PetMode.IDLE -> {
                if (isWalking) {
                    val walkBob = abs(sin(timeSec * 14f)) * (bSize * 0.35f)
                    startY += walkBob
                } else if (!isLyingDown) {
                    val breathY = sin(timeSec * 3f) * (bSize * 0.4f)
                    startY += breathY
                }
            }
            PetMode.MUSIC -> {
                val beatY = abs(sin(timeSec * 6.5f)) * (bSize * 1.2f)
                startY += beatY
            }
            PetMode.WAVE_POKE -> {
                val jumpY = abs(sin(timeSec * 7f)) * (bSize * 1.4f)
                startY -= jumpY
            }
            PetMode.EATING -> {
                val chewY = abs(sin(timeSec * 5f)) * (bSize * 0.5f)
                startY += chewY
            }
            PetMode.DOOMSCROLL -> {
                val shakeX = sin(timeSec * 22f) * (bSize * 0.35f)
                startX += shakeX
            }
            PetMode.SLEEP -> {
                startY += bSize * 0.8f
            }
            else -> {}
        }

        // Рисуем базовую модельку зверька: либо лежащий калачиком (Loaf), либо стоящий/шагающий
        if (isLyingDown || mode == PetMode.SLEEP) {
            drawLyingDownAnimalModel(canvas, startX, startY, bSize, state.species, mode, time)
        } else {
            drawVoxelAnimalModel(canvas, startX, startY, bSize, state.species, mode, time, isWalking)
        }

        // Рисуем специфические накладки режима
        when (mode) {
            PetMode.MUSIC -> {
                drawMusicAccessories(canvas, startX, startY, bSize, time)
            }
            PetMode.EATING -> {
                drawEatingAccessories(canvas, startX, startY, bSize, state.species, time)
            }
            PetMode.WAVE_POKE -> {
                drawWavePokeAccessories(canvas, startX, startY, bSize, time)
            }
            PetMode.DOOMSCROLL -> {
                drawDoomscrollSign(canvas, startX, startY, bSize, time)
            }
            PetMode.NOTIFICATION -> {
                drawNotificationEnvelope(canvas, startX, startY, bSize, time)
            }
            PetMode.SLEEP -> {
                drawSleepZzz(canvas, startX, startY, bSize, time)
            }
            else -> {}
        }

        canvas.restore()

        // 4. ПЛАВАЮЩИЕ СЕРДЕЧКИ И ЗВЕЗДОЧКИ (рисуем поверх без зеркального отражения)
        if (isEmittingHearts) {
            drawFloatingHearts(canvas, dstRect.centerX(), dstRect.top + bSize * 2f, bSize, time)
        }
    }

    fun getFrameDelayMs(mode: PetMode = PetMode.IDLE): Long {
        return when (mode) {
            PetMode.IDLE -> 50L
            PetMode.SLEEP -> 90L
            PetMode.MUSIC -> 50L
            PetMode.CHARGING -> 60L
            PetMode.NOTIFICATION -> 70L
            PetMode.EATING -> 65L
            PetMode.WAVE_POKE -> 50L
            PetMode.DOOMSCROLL -> 50L
            PetMode.COUPLE_VISIT -> 60L
        }
    }

    fun drawGuestPet(canvas: Canvas, dstRect: RectF, frameIndex: Int = 0) {
        val state = IslandStateBus.state.value
        val guestSpecies = if (state.species == PetSpecies.CAT) PetSpecies.DOG else PetSpecies.CAT
        val bSize = dstRect.width() / 14f
        val startX = dstRect.left + dstRect.width() * 0.12f
        val startY = dstRect.top + dstRect.height() * 0.08f
        drawVoxelAnimalModel(canvas, startX, startY, bSize, guestSpecies, PetMode.IDLE, System.currentTimeMillis(), isWalking = false)
    }

    /**
     * Отрисовка стоящего или шагающего зверька из 3D кубиков (Кот или Собачка Сиба-ину).
     */
    private fun drawVoxelAnimalModel(
        canvas: Canvas,
        x: Float,
        y: Float,
        bSize: Float,
        species: PetSpecies,
        mode: PetMode,
        time: Long,
        isWalking: Boolean = false
    ) {
        val timeSec = time * 0.001f
        val isDog = (species == PetSpecies.DOG)

        // Основные цвета
        val cMain = if (isDog) C_DOG_HONEY else C_CAT_GINGER
        val cDark = if (isDog) C_DOG_TAN_DARK else C_CAT_GINGER_DARK
        val cWhite = C_CAT_WHITE
        val cPink = C_CAT_PINK

        // 1. ХВОСТ (Сзади)
        if (isDog) {
            // У собаки Сиба-Ину хвостик закручен бубликом на спинке и быстро виляет
            val tailSpeed = if (isWalking) 16f else 12f
            val tailAmp = if (isWalking) 0.55f else 0.35f
            val tailWag = sin(timeSec * tailSpeed) * (bSize * tailAmp)
            drawBrick(canvas, x + bSize * 8.5f + tailWag, y + bSize * 5.2f, bSize * 1.1f, cDark)
            drawBrick(canvas, x + bSize * 9.2f + tailWag, y + bSize * 4.2f, bSize * 1.1f, cMain)
            drawBrick(canvas, x + bSize * 8.6f + tailWag, y + bSize * 3.4f, bSize * 1.0f, cWhite)
        } else {
            // У Кота длинный пушистый хвост, плавно покачивается
            val catTailSpeed = if (isWalking) 10f else 4f
            val tailSwish = sin(timeSec * catTailSpeed) * (bSize * 0.5f)
            drawBrick(canvas, x + bSize * 8.5f, y + bSize * 6.5f, bSize * 1.1f, cDark)
            drawBrick(canvas, x + bSize * 9.4f, y + bSize * 5.5f, bSize * 1.1f, cMain)
            drawBrick(canvas, x + bSize * 9.8f + tailSwish, y + bSize * 4.4f, bSize * 1.1f, cDark)
            drawBrick(canvas, x + bSize * 9.2f + tailSwish, y + bSize * 3.4f, bSize * 1.0f, cWhite)
        }

        // 2. ТЕЛО (Спина, бока, пузико)
        // Задние лапки: при ходьбе попеременно переступают
        val stepBL = if (isWalking) sin(timeSec * 14f) * (bSize * 0.6f) else 0f
        val liftBL = if (isWalking) abs(sin(timeSec * 14f)) * (bSize * 0.3f) else 0f
        val stepBR = -stepBL
        val liftBR = if (isWalking) abs(sin(timeSec * 14f + Math.PI.toFloat())) * (bSize * 0.3f) else 0f

        drawBrick(canvas, x + bSize * 2.2f - stepBL * 0.3f, y + bSize * 6.6f - liftBL, bSize * 1.2f, cWhite) // Левая задняя
        drawBrick(canvas, x + bSize * 7.0f - stepBR * 0.3f, y + bSize * 6.6f - liftBR, bSize * 1.2f, cWhite) // Правая задняя
        drawBrick(canvas, x + bSize * 3.2f, y + bSize * 5.8f, bSize * 1.3f, cMain)  // Животик
        drawBrick(canvas, x + bSize * 4.5f, y + bSize * 5.8f, bSize * 1.3f, cWhite) // Белое пузико
        drawBrick(canvas, x + bSize * 5.8f, y + bSize * 5.8f, bSize * 1.3f, cMain)

        // Грудка и спинка
        drawBrick(canvas, x + bSize * 3.2f, y + bSize * 4.6f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 4.5f, y + bSize * 4.6f, bSize * 1.3f, cWhite) // Манишка
        drawBrick(canvas, x + bSize * 5.8f, y + bSize * 4.6f, bSize * 1.3f, cMain)

        // Ошейник с колокольчиком / медальоном
        val collarColor = if (isDog) C_COLLAR_RED else C_COLLAR_TEAL
        drawBrick(canvas, x + bSize * 3.4f, y + bSize * 3.8f, bSize * 1.1f, collarColor)
        drawBrick(canvas, x + bSize * 4.5f, y + bSize * 3.8f, bSize * 1.1f, C_BELL_GOLD) // Медальон
        drawBrick(canvas, x + bSize * 5.6f, y + bSize * 3.8f, bSize * 1.1f, collarColor)

        // 3. ГОЛОВА (Мордочка, ушки, щечки)
        // Верх головы
        drawBrick(canvas, x + bSize * 3.2f, y + bSize * 1.2f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 4.5f, y + bSize * 1.2f, bSize * 1.3f, if (isDog) cWhite else cDark)
        drawBrick(canvas, x + bSize * 5.8f, y + bSize * 1.2f, bSize * 1.3f, cMain)

        // Ушки
        if (isDog) {
            // У Сиба-ину аккуратные треугольные ушки
            drawBrick(canvas, x + bSize * 2.2f, y + bSize * 0.1f, bSize * 1.2f, cDark)
            drawBrick(canvas, x + bSize * 3.2f, y + bSize * 0.2f, bSize * 1.0f, cWhite)
            drawBrick(canvas, x + bSize * 6.8f, y + bSize * 0.1f, bSize * 1.2f, cDark)
            drawBrick(canvas, x + bSize * 5.9f, y + bSize * 0.2f, bSize * 1.0f, cWhite)
        } else {
            // У Кота остренькие ушки с розовой серединкой
            drawBrick(canvas, x + bSize * 2.2f, y + bSize * 0.0f, bSize * 1.1f, cDark)
            drawBrick(canvas, x + bSize * 3.1f, y + bSize * 0.3f, bSize * 0.9f, cPink)
            drawBrick(canvas, x + bSize * 6.9f, y + bSize * 0.0f, bSize * 1.1f, cDark)
            drawBrick(canvas, x + bSize * 6.2f, y + bSize * 0.3f, bSize * 0.9f, cPink)
        }

        // Щечки и глазки
        drawBrick(canvas, x + bSize * 2.2f, y + bSize * 2.3f, bSize * 1.3f, if (isDog) cWhite else cMain)
        drawBrick(canvas, x + bSize * 6.8f, y + bSize * 2.3f, bSize * 1.3f, if (isDog) cWhite else cMain)

        // Глазки: в WAVE_POKE жмурится от радости (^ ^), во сне спящие (- -), иначе круглые бусинки
        when (mode) {
            PetMode.WAVE_POKE -> {
                // Глазки-дуги от счастья ^ ^
                drawBrick(canvas, x + bSize * 3.5f, y + bSize * 2.1f, bSize * 1.0f, C_CAT_EYE)
                drawBrick(canvas, x + bSize * 5.8f, y + bSize * 2.1f, bSize * 1.0f, C_CAT_EYE)
                // Розовый румянец
                drawBrick(canvas, x + bSize * 2.4f, y + bSize * 2.9f, bSize * 0.9f, cPink)
                drawBrick(canvas, x + bSize * 6.9f, y + bSize * 2.9f, bSize * 0.9f, cPink)
            }
            PetMode.SLEEP -> {
                // Спящие глазки - -
                drawBrick(canvas, x + bSize * 3.4f, y + bSize * 2.5f, bSize * 1.1f, C_CAT_EYE)
                drawBrick(canvas, x + bSize * 5.7f, y + bSize * 2.5f, bSize * 1.1f, C_CAT_EYE)
            }
            else -> {
                // Большие круглые глазки-бусинки с белым бликом
                drawBrick(canvas, x + bSize * 3.4f, y + bSize * 2.2f, bSize * 1.1f, C_CAT_EYE)
                drawBrick(canvas, x + bSize * 5.7f, y + bSize * 2.2f, bSize * 1.1f, C_CAT_EYE)
                // Белый блик
                drawBrick(canvas, x + bSize * 3.5f, y + bSize * 2.2f, bSize * 0.45f, Color.WHITE)
                drawBrick(canvas, x + bSize * 5.8f, y + bSize * 2.2f, bSize * 0.45f, Color.WHITE)
            }
        }

        // Мордочка и носик
        drawBrick(canvas, x + bSize * 4.1f, y + bSize * 2.8f, bSize * 1.0f, cWhite)
        drawBrick(canvas, x + bSize * 5.1f, y + bSize * 2.8f, bSize * 1.0f, cWhite)

        // Носик
        val noseColor = if (isDog) C_DOG_NOSE else cPink
        drawBrick(canvas, x + bSize * 4.6f, y + bSize * 2.7f, bSize * 0.95f, noseColor)

        // Ротик / язычок
        if (isDog && mode != PetMode.SLEEP) {
            // У собаки милый высунутый язычок
            drawBrick(canvas, x + bSize * 4.6f, y + bSize * 3.5f, bSize * 0.9f, C_DOG_TONGUE)
        } else if (!isDog && mode == PetMode.WAVE_POKE) {
            // У кота улыбающийся ротик
            drawBrick(canvas, x + bSize * 4.7f, y + bSize * 3.4f, bSize * 0.7f, cPink)
        }

        // 4. ПЕРЕДНИЕ ЛАПКИ
        if (mode == PetMode.WAVE_POKE) {
            // Левая лапка стоит на полу
            drawBrick(canvas, x + bSize * 3.2f, y + bSize * 6.5f, bSize * 1.2f, cWhite)
            // ПРАВАЯ ЛАПКА ВЫСОКО ПОДНЯТА И МАШЕТ!
            val waveOffset = sin(timeSec * 14f) * (bSize * 0.8f)
            drawBrick(canvas, x + bSize * 6.8f + waveOffset, y + bSize * 3.6f, bSize * 1.2f, cMain)
            drawBrick(canvas, x + bSize * 7.4f + waveOffset, y + bSize * 2.7f, bSize * 1.1f, cWhite) // Белая ладошка
        } else if (isWalking) {
            // При ходьбе: передние лапки ритмично вышагивают вперед и назад
            val stepFL = sin(timeSec * 14f) * (bSize * 0.8f)
            val liftFL = abs(stepFL) * 0.45f
            val stepFR = -stepFL
            val liftFR = abs(stepFR) * 0.45f

            drawBrick(canvas, x + bSize * 3.2f + stepFL * 0.5f, y + bSize * 6.6f - liftFL, bSize * 1.2f, cWhite)
            drawBrick(canvas, x + bSize * 5.8f + stepFR * 0.5f, y + bSize * 6.6f - liftFR, bSize * 1.2f, cWhite)
        } else {
            // Обе лапки аккуратно сидят впереди
            drawBrick(canvas, x + bSize * 3.2f, y + bSize * 6.6f, bSize * 1.2f, cWhite)
            drawBrick(canvas, x + bSize * 5.8f, y + bSize * 6.6f, bSize * 1.2f, cWhite)
        }
    }

    /**
     * Отрисовка уютно лежащего питомца ("буханочка" / Loaf pose):
     * Тело прижато к полу, лапки поджаты, глаза спокойно прикрыты, мягкое дыхание.
     */
    private fun drawLyingDownAnimalModel(
        canvas: Canvas,
        x: Float,
        y: Float,
        bSize: Float,
        species: PetSpecies,
        mode: PetMode,
        time: Long
    ) {
        val timeSec = time * 0.001f
        val isDog = (species == PetSpecies.DOG)

        val cMain = if (isDog) C_DOG_HONEY else C_CAT_GINGER
        val cDark = if (isDog) C_DOG_TAN_DARK else C_CAT_GINGER_DARK
        val cWhite = C_CAT_WHITE
        val cPink = C_CAT_PINK
        val collarColor = if (isDog) C_COLLAR_RED else C_COLLAR_TEAL

        // Мягкое мерное дыхание
        val breathe = sin(timeSec * 2.5f) * (bSize * 0.25f)
        val lieY = y + bSize * 1.2f + breathe

        // 1. Хвостик
        if (isDog) {
            drawBrick(canvas, x + bSize * 0.8f, lieY + bSize * 3.8f, bSize * 1.1f, cDark)
            drawBrick(canvas, x + bSize * 0.4f, lieY + bSize * 2.9f, bSize * 1.0f, cWhite)
        } else {
            // У кота хвостик свернут вдоль тела
            drawBrick(canvas, x + bSize * 0.6f, lieY + bSize * 4.6f, bSize * 1.1f, cDark)
            drawBrick(canvas, x + bSize * 1.4f, lieY + bSize * 5.6f, bSize * 1.1f, cMain)
            drawBrick(canvas, x + bSize * 2.6f, lieY + bSize * 6.2f, bSize * 1.0f, cWhite)
        }

        // 2. Тело калачиком (Loaf)
        // Нижний ряд спинки и бока
        drawBrick(canvas, x + bSize * 1.8f, lieY + bSize * 4.8f, bSize * 1.3f, cDark)
        drawBrick(canvas, x + bSize * 3.1f, lieY + bSize * 4.8f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 4.4f, lieY + bSize * 4.8f, bSize * 1.3f, cWhite) // Белый животик
        drawBrick(canvas, x + bSize * 5.7f, lieY + bSize * 4.8f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 7.0f, lieY + bSize * 4.8f, bSize * 1.3f, cDark)

        // Верхний хребет
        drawBrick(canvas, x + bSize * 2.4f, lieY + bSize * 3.6f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 3.7f, lieY + bSize * 3.6f, bSize * 1.3f, cWhite)
        drawBrick(canvas, x + bSize * 5.0f, lieY + bSize * 3.6f, bSize * 1.3f, cMain)

        // 3. Поджатые лапки на полу
        drawBrick(canvas, x + bSize * 3.8f, lieY + bSize * 6.0f, bSize * 1.2f, cWhite)
        drawBrick(canvas, x + bSize * 5.4f, lieY + bSize * 6.0f, bSize * 1.2f, cWhite)

        // 4. Ошейник с кулоном
        drawBrick(canvas, x + bSize * 6.2f, lieY + bSize * 3.5f, bSize * 1.1f, collarColor)
        drawBrick(canvas, x + bSize * 7.1f, lieY + bSize * 3.6f, bSize * 0.95f, C_BELL_GOLD)

        // 5. Голова, уютно лежащая на лапках
        // Лоб
        drawBrick(canvas, x + bSize * 5.8f, lieY + bSize * 1.2f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 7.1f, lieY + bSize * 1.2f, bSize * 1.3f, if (isDog) cWhite else cDark)
        drawBrick(canvas, x + bSize * 8.4f, lieY + bSize * 1.2f, bSize * 1.3f, cMain)

        // Ушки
        if (isDog) {
            drawBrick(canvas, x + bSize * 5.2f, lieY + bSize * 0.2f, bSize * 1.1f, cDark)
            drawBrick(canvas, x + bSize * 6.2f, lieY + bSize * 0.3f, bSize * 0.9f, cWhite)
            drawBrick(canvas, x + bSize * 8.6f, lieY + bSize * 0.2f, bSize * 1.1f, cDark)
        } else {
            drawBrick(canvas, x + bSize * 5.2f, lieY + bSize * 0.1f, bSize * 1.1f, cDark)
            drawBrick(canvas, x + bSize * 6.0f, lieY + bSize * 0.4f, bSize * 0.9f, cPink)
            drawBrick(canvas, x + bSize * 8.6f, lieY + bSize * 0.1f, bSize * 1.1f, cDark)
            drawBrick(canvas, x + bSize * 7.9f, lieY + bSize * 0.4f, bSize * 0.9f, cPink)
        }

        // Щечки
        drawBrick(canvas, x + bSize * 5.6f, lieY + bSize * 2.3f, bSize * 1.3f, if (isDog) cWhite else cMain)
        drawBrick(canvas, x + bSize * 8.6f, lieY + bSize * 2.3f, bSize * 1.3f, if (isDog) cWhite else cMain)

        // Закрытые глазки блаженства (^ ^ или сон - -)
        drawBrick(canvas, x + bSize * 6.7f, lieY + bSize * 2.2f, bSize * 1.0f, C_CAT_EYE)
        drawBrick(canvas, x + bSize * 8.0f, lieY + bSize * 2.2f, bSize * 1.0f, C_CAT_EYE)

        // Розовый румянец на щечках
        drawBrick(canvas, x + bSize * 5.8f, lieY + bSize * 3.1f, bSize * 0.85f, cPink)
        drawBrick(canvas, x + bSize * 8.6f, lieY + bSize * 3.1f, bSize * 0.85f, cPink)

        // Мордочка и носик
        drawBrick(canvas, x + bSize * 7.1f, lieY + bSize * 2.8f, bSize * 1.0f, cWhite)
        drawBrick(canvas, x + bSize * 7.9f, lieY + bSize * 2.8f, bSize * 1.0f, cWhite)
        drawBrick(canvas, x + bSize * 7.4f, lieY + bSize * 2.6f, bSize * 0.9f, if (isDog) C_DOG_NOSE else cPink)
    }

    /**
     * Парящие в воздухе сердечки (❤️) и мерцающие золотые звездочки (✨).
     */
    fun drawFloatingHearts(canvas: Canvas, cx: Float, topY: Float, bSize: Float, time: Long) {
        val timeSec = time * 0.001f
        for (i in 0..2) {
            val p = ((timeSec * 0.7f + i * 0.33f) % 1.0f)
            val riseY = topY - p * (bSize * 6.5f)
            val driftX = sin(p * 5.0f + i * 2.1f) * (bSize * 1.6f) + (i - 1) * bSize * 1.3f
            val px = cx + driftX

            val alpha = ((1.0f - p) * 255).toInt().coerceIn(0, 255)
            textPaint.alpha = alpha
            textPaint.textSize = bSize * (1.8f + p * 0.4f)

            if (i % 2 == 0) {
                textPaint.color = C_HEART_RED
                canvas.drawText("❤️", px, riseY, textPaint)
            } else {
                textPaint.color = C_STAR_GOLD
                canvas.drawText("✨", px, riseY, textPaint)
            }
        }
    }

    /**
     * PetMode.MUSIC: БОЛЬШИЕ ЯРКО-КРАСНЫЕ СТУДИЙНЫЕ НАУШНИКИ (#FF3D00)
     * с дугой через голову, вращающаяся пластинка и летящие ноты!
     */
    private fun drawMusicAccessories(canvas: Canvas, x: Float, y: Float, bSize: Float, time: Long) {
        val timeSec = time * 0.001f

        // 1. ОГРОМНЫЕ КРАСНЫЕ НАУШНИКИ
        // Красная дуга оголовья
        drawBrick(canvas, x + bSize * 3.1f, y - bSize * 0.8f, bSize * 1.2f, C_HEADPHONES_RED)
        drawBrick(canvas, x + bSize * 4.4f, y - bSize * 1.0f, bSize * 1.4f, C_HEADPHONES_RED)
        drawBrick(canvas, x + bSize * 5.9f, y - bSize * 0.8f, bSize * 1.2f, C_HEADPHONES_RED)

        // Большие круглые красные чашки наушников поверх ушек
        drawBrick(canvas, x + bSize * 1.3f, y + bSize * 0.8f, bSize * 1.8f, C_HEADPHONES_RED)
        drawBrick(canvas, x + bSize * 1.6f, y + bSize * 1.1f, bSize * 1.2f, C_HEADPHONES_CUP)

        drawBrick(canvas, x + bSize * 7.1f, y + bSize * 0.8f, bSize * 1.8f, C_HEADPHONES_RED)
        drawBrick(canvas, x + bSize * 7.4f, y + bSize * 1.1f, bSize * 1.2f, C_HEADPHONES_CUP)

        // 2. ВРАЩАЮЩАЯСЯ ВИНИЛОВАЯ ПЛАСТИНКА СПРАВА
        val vinylX = x + bSize * 9.6f
        val vinylY = y + bSize * 4.6f
        drawBrick(canvas, vinylX, vinylY, bSize * 2.2f, C_VINYL_BLACK)
        drawBrick(canvas, vinylX + bSize * 0.6f, vinylY + bSize * 0.6f, bSize * 1.0f, C_HEADPHONES_RED) // Центр пластинки

        // 3. ЛЕТЯЩИЕ ЦВЕТНЫЕ МУЗЫКАЛЬНЫЕ НОТЫ (♪ ♫)
        textPaint.textSize = bSize * 2.4f

        for (i in 0..2) {
            val notePhase = ((timeSec * 1.4f + i * 0.33f) % 1.0f)
            val noteY = y + bSize * 2.5f - notePhase * bSize * 6.5f
            val noteX = x + bSize * 8.2f + sin(notePhase * 6.28f + i) * (bSize * 2.2f)

            textPaint.color = if (i % 2 == 0) C_NOTE_CYAN else C_NOTE_YELLOW
            textPaint.alpha = ((1f - notePhase) * 255).toInt().coerceIn(0, 255)
            canvas.drawText(if (i == 1) "♫" else "♪", noteX, noteY, textPaint)
        }
    }

    /**
     * PetMode.EATING: Бургер для кота или косточка для собаки, анимация жевания и плюсики +XP!
     */
    private fun drawEatingAccessories(
        canvas: Canvas,
        x: Float,
        y: Float,
        bSize: Float,
        species: PetSpecies,
        time: Long
    ) {
        val timeSec = time * 0.001f
        val chewPhase = (sin(timeSec * 8f) + 1f) * 0.5f

        val foodX = x + bSize * 4.1f
        val foodY = y + bSize * 4.0f + chewPhase * (bSize * 0.3f)

        if (species == PetSpecies.DOG) {
            // Сахарная косточка для собачки
            drawBrick(canvas, foodX - bSize * 0.8f, foodY - bSize * 0.3f, bSize * 0.8f, C_BONE_WHITE)
            drawBrick(canvas, foodX - bSize * 0.8f, foodY + bSize * 0.5f, bSize * 0.8f, C_BONE_WHITE)
            drawBrick(canvas, foodX, foodY + bSize * 0.1f, bSize * 1.6f, C_BONE_WHITE) // Тело кости
            drawBrick(canvas, foodX + bSize * 1.6f, foodY - bSize * 0.3f, bSize * 0.8f, C_BONE_WHITE)
            drawBrick(canvas, foodX + bSize * 1.6f, foodY + bSize * 0.5f, bSize * 0.8f, C_BONE_WHITE)
        } else {
            // Аппетитный 3D-бургер для кота
            drawBrick(canvas, foodX, foodY - bSize * 0.6f, bSize * 1.8f, C_BURGER_BUN)  // Верх булочки
            drawBrick(canvas, foodX, foodY, bSize * 1.8f, C_BURGER_SAL)                // Лист салата
            drawBrick(canvas, foodX + bSize * 0.3f, foodY + bSize * 0.4f, bSize * 1.3f, C_BURGER_CHZ) // Сыр
            drawBrick(canvas, foodX, foodY + bSize * 0.7f, bSize * 1.8f, C_BURGER_MEAT) // Котлетка
            drawBrick(canvas, foodX, foodY + bSize * 1.3f, bSize * 1.8f, C_BURGER_BUN)  // Низ булочки
        }

        // Летящие зеленые плюсики +XP
        textPaint.textSize = bSize * 1.5f
        textPaint.color = Color.parseColor("#10B981")
        for (k in 0..2) {
            val xpPhase = ((timeSec * 1.8f + k * 0.35f) % 1.0f)
            val xpY = y + bSize * 2.0f - xpPhase * (bSize * 5.0f)
            val xpX = x + bSize * 2.0f + k * (bSize * 2.8f) + sin(xpPhase * 5f) * (bSize * 0.8f)
            textPaint.alpha = ((1f - xpPhase) * 255).toInt().coerceIn(0, 255)
            canvas.drawText("+XP", xpX, xpY, textPaint)
        }
    }

    /**
     * PetMode.WAVE_POKE: Сердечки, звездочки и искры вокруг радостного машущего питомца!
     */
    private fun drawWavePokeAccessories(canvas: Canvas, x: Float, y: Float, bSize: Float, time: Long) {
        val timeSec = time * 0.001f

        // Летающие сердечки и звездочки
        textPaint.textSize = bSize * 2.2f

        for (i in 0..3) {
            val pPhase = ((timeSec * 1.5f + i * 0.25f) % 1.0f)
            val pY = y + bSize * 4.0f - pPhase * (bSize * 6.5f)
            val pX = x + bSize * (1.0f + i * 2.4f) + sin(pPhase * 6f + i) * (bSize * 1.2f)

            textPaint.alpha = ((1f - pPhase) * 255).toInt().coerceIn(0, 255)
            if (i % 2 == 0) {
                textPaint.color = C_HEART_RED
                canvas.drawText("❤️", pX, pY, textPaint)
            } else {
                textPaint.color = C_STAR_GOLD
                canvas.drawText("⭐", pX, pY, textPaint)
            }
        }
    }

    /**
     * PetMode.COUPLE_VISIT: Рисует СРАЗУ ДВУХ питомцев рядом (Кот и Собачка тусуются вместе)
     * и пульсирующее сердечко между ними!
     */
    private fun drawCouplePets(canvas: Canvas, dstRect: RectF, mySpecies: PetSpecies, time: Long) {
        val timeSec = time * 0.001f
        val guestSpecies = if (mySpecies == PetSpecies.CAT) PetSpecies.DOG else PetSpecies.CAT

        val bSize = dstRect.width() / 19f
        val yBase = dstRect.top + dstRect.height() * 0.12f

        // 1. Наш питомец (слева)
        val pet1X = dstRect.left + dstRect.width() * 0.04f
        val jump1 = abs(sin(timeSec * 4f)) * (bSize * 0.6f)
        drawVoxelAnimalModel(canvas, pet1X, yBase - jump1, bSize, mySpecies, PetMode.WAVE_POKE, time)

        // 2. Гостевой питомец (справа, повернут навстречу)
        val pet2X = dstRect.left + dstRect.width() * 0.52f
        val jump2 = abs(cos(timeSec * 4f)) * (bSize * 0.6f)
        drawVoxelAnimalModel(canvas, pet2X, yBase - jump2, bSize, guestSpecies, PetMode.WAVE_POKE, time + 400L)

        // 3. Большое пульсирующее сердце по центру между ними
        val heartPulse = 1.0f + sin(timeSec * 7f) * 0.25f
        textPaint.textSize = bSize * 4.2f * heartPulse
        textPaint.color = C_HEART_RED
        textPaint.alpha = 255
        val heartX = dstRect.centerX()
        val heartY = dstRect.centerY() + bSize * 1.5f
        canvas.drawText("❤️", heartX, heartY, textPaint)
    }

    /**
     * PetMode.DOOMSCROLL: Широкая желтая табличка "STOP REELS!" в лапах.
     */
    private fun drawDoomscrollSign(canvas: Canvas, x: Float, y: Float, bSize: Float, time: Long) {
        val signX = x + bSize * 1.0f
        val signY = y + bSize * 3.8f
        val signW = bSize * 8.4f
        val signH = bSize * 3.4f

        // Желтая табличка
        basePaint.color = C_SIGN_YELLOW
        canvas.drawRect(signX, signY, signX + signW, signY + signH, basePaint)

        // Красная рамка
        highlightPaint.color = C_SIGN_BORDER
        canvas.drawRect(signX, signY, signX + signW, signY + bSize * 0.4f, highlightPaint)
        canvas.drawRect(signX, signY + signH - bSize * 0.4f, signX + signW, signY + signH, highlightPaint)

        // Текст "STOP REELS!"
        textPaint.color = Color.BLACK
        textPaint.textSize = bSize * 1.35f
        textPaint.alpha = 255
        canvas.drawText("⛔ STOP REELS!", signX + signW / 2f, signY + signH * 0.65f, textPaint)
    }

    private fun drawNotificationEnvelope(canvas: Canvas, x: Float, y: Float, bSize: Float, time: Long) {
        val envX = x + bSize * 4.2f
        val envY = y + bSize * 4.6f
        // Белый конверт
        drawBrick(canvas, envX, envY, bSize * 1.8f, Color.WHITE)
        drawBrick(canvas, envX + bSize * 0.4f, envY + bSize * 0.4f, bSize * 1.0f, Color.parseColor("#EF4444"))
    }

    private fun drawSleepZzz(canvas: Canvas, x: Float, y: Float, bSize: Float, time: Long) {
        val timeSec = time * 0.001f
        textPaint.color = Color.parseColor("#38BDF8")
        textPaint.textSize = bSize * 2.0f
        for (k in 0..2) {
            val p = (timeSec * 0.8f + k * 0.33f) % 1f
            val zy = y + bSize * 2.5f - p * bSize * 5.5f
            val zx = x + bSize * 7.5f + p * bSize * 2.5f
            textPaint.alpha = ((1f - p) * 255).toInt().coerceIn(0, 255)
            canvas.drawText("Z", zx, zy, textPaint)
        }
    }

    private fun drawCustomAsset(canvas: Canvas, dstRect: RectF, mode: PetMode, frameIndex: Int, state: IslandState) {
        val movie = customMovie
        val bmp = customBitmap

        if (movie != null && movie.duration() > 0) {
            val timeMs = (System.currentTimeMillis() % movie.duration()).toInt()
            movie.setTime(timeMs)
            val w = movie.width().toFloat().coerceAtLeast(1f)
            val h = movie.height().toFloat().coerceAtLeast(1f)
            val scale = (dstRect.width() / w).coerceAtMost(dstRect.height() / h)
            val drawW = w * scale
            val drawH = h * scale
            val dx = dstRect.left + (dstRect.width() - drawW) / 2f
            val dy = dstRect.top + (dstRect.height() - drawH) / 2f
            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            movie.draw(canvas, 0f, 0f)
            canvas.restore()
        } else if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, null, dstRect, pixelPaint)
        }
    }
}

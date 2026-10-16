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

    // Лис (Chester): Яркий рыже-красный с белой манишкой и черными сапожками
    private val C_FOX_RED          = Color.parseColor("#EA580C")
    private val C_FOX_DARK         = Color.parseColor("#C2410C")
    private val C_FOX_PAWS         = Color.parseColor("#18181B")

    // Утенок (Nugget): Солнечно-желтый пушок с оранжевым клювиком
    private val C_DUCK_YELLOW      = Color.parseColor("#FACC15")
    private val C_DUCK_DARK        = Color.parseColor("#EAB308")
    private val C_DUCK_BEAK        = Color.parseColor("#FB923C")
    private val C_DUCK_FEET        = Color.parseColor("#EA580C")

    // Панда (Po): Классический черно-белый мишка с круглыми ушками
    private val C_PANDA_WHITE      = Color.parseColor("#FFFDF8")
    private val C_PANDA_BLACK      = Color.parseColor("#1E293B")
    private val C_PANDA_DARK       = Color.parseColor("#0F172A")

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
        isEmittingHearts: Boolean = false,
        isSprinting: Boolean = false,
        hasBallInMouth: Boolean = false
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

        // 2. РЕЖИМ ГОСТЕЙ (COUPLE_VISIT): Рисуем ДВУХ питомцев рядом только в режиме COUPLE_VISIT
        if (mode == PetMode.COUPLE_VISIT) {
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

        // Анимации вертикального смещения (шаги, спринт, дыхание, бит музыки, прыжок)
        when (mode) {
            PetMode.IDLE -> {
                if (isWalking || isSprinting) {
                    val bobSpeed = if (isSprinting) 24f else 14f
                    val bobAmp = if (isSprinting) 0.45f else 0.35f
                    val walkBob = abs(sin(timeSec * bobSpeed)) * (bSize * bobAmp)
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

        if (state.isPartnerBatteryLow) {
            val shiverX = sin(timeSec * 28f) * (bSize * 0.35f)
            startX += shiverX
        }

        // Рисуем базовую модельку зверька: либо лежащий калачиком (Loaf), либо стоящий/шагающий/бегущий
        if (isLyingDown || mode == PetMode.SLEEP) {
            drawLyingDownAnimalModel(canvas, startX, startY, bSize, state.species, mode, time)
        } else {
            drawVoxelAnimalModel(canvas, startX, startY, bSize, state.species, mode, time, isWalking || isSprinting, isSprinting, hasBallInMouth)
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

        // 4. Аксессуары времени суток (день / ночь):
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val isSleepingOrLoaf = isLyingDown || mode == PetMode.SLEEP
        val showNightcap = state.forceNightcap || ((hour >= 22 || hour < 7) && isSleepingOrLoaf)
        val showSunglasses = state.forceSunglasses // Никогда не навязывать очки днем автоматически

        if (showNightcap) {
            drawNightCap(canvas, startX, startY, bSize, isSleepingOrLoaf)
        } else {
            if (state.petLevel >= 5) {
                drawLegoCrown(canvas, startX, startY, bSize, isSleepingOrLoaf)
            }
            if (showSunglasses) {
                drawSunglasses(canvas, startX, startY, bSize)
            }
        }

        canvas.restore()

        // 4. ПЛАВАЮЩИЕ СЕРДЕЧКИ: строго НАД головой питомца, мягко улетающие в небо
        if (isEmittingHearts) {
            drawFloatingHearts(canvas, dstRect.centerX(), dstRect.top - bSize * 0.8f, bSize, time)
        }

        if (state.isPartnerBatteryLow) {
            textPaint.alpha = 255
            textPaint.textSize = bSize * 2.2f
            textPaint.color = Color.parseColor("#EF4444")
            val alertY = dstRect.top + bSize * 1.5f + sin(timeSec * 6f) * (bSize * 0.4f)
            canvas.drawText("🪫", dstRect.centerX(), alertY, textPaint)
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
     * Отрисовка стоящего или шагающего зверька из 3D кубиков:
     * 🐱 Кот Оливер, 🐶 Пес Хьюго, 🦊 Лис Честер, 🦆 Утенок Наггет, 🐼 Панда По
     */
    private fun drawVoxelAnimalModel(
        canvas: Canvas,
        x: Float,
        y: Float,
        bSize: Float,
        species: PetSpecies,
        mode: PetMode,
        time: Long,
        isWalking: Boolean = false,
        isSprinting: Boolean = false,
        hasBallInMouth: Boolean = false
    ) {
        val timeSec = time * 0.001f

        // Цветовые палитры для каждого персонажа Pixel Pals
        val cMain: Int
        val cDark: Int
        val cWhite: Int
        val cPaw: Int
        val cNose: Int
        val collarColor: Int

        when (species) {
            PetSpecies.DOG -> {
                cMain = C_DOG_HONEY
                cDark = C_DOG_TAN_DARK
                cWhite = C_DOG_WHITE
                cPaw = C_DOG_WHITE
                cNose = C_DOG_NOSE
                collarColor = C_COLLAR_RED
            }
            PetSpecies.FOX -> {
                cMain = C_FOX_RED
                cDark = C_FOX_DARK
                cWhite = C_CAT_WHITE
                cPaw = C_FOX_PAWS
                cNose = C_FOX_PAWS
                collarColor = Color.parseColor("#059669")
            }
            PetSpecies.DUCK -> {
                cMain = C_DUCK_YELLOW
                cDark = C_DUCK_DARK
                cWhite = C_DUCK_YELLOW
                cPaw = C_DUCK_FEET
                cNose = C_DUCK_BEAK
                collarColor = Color.parseColor("#3B82F6")
            }
            PetSpecies.PANDA -> {
                cMain = C_PANDA_WHITE
                cDark = C_PANDA_BLACK
                cWhite = C_PANDA_WHITE
                cPaw = C_PANDA_BLACK
                cNose = C_PANDA_DARK
                collarColor = Color.parseColor("#EF4444")
            }
            PetSpecies.CAT -> {
                cMain = C_CAT_GINGER
                cDark = C_CAT_GINGER_DARK
                cWhite = C_CAT_WHITE
                cPaw = C_CAT_WHITE
                cNose = C_CAT_PINK
                collarColor = C_COLLAR_TEAL
            }
        }

        // 1. ХВОСТ (Сзади)
        when (species) {
            PetSpecies.DOG -> {
                // Закрученный бубликом хвостик, виляет
                val tailSpeed = if (isSprinting) 22f else if (isWalking) 16f else 12f
                val tailAmp = if (isSprinting) 0.65f else if (isWalking) 0.55f else 0.35f
                val tailWag = sin(timeSec * tailSpeed) * (bSize * tailAmp)
                drawBrick(canvas, x + bSize * 8.5f + tailWag, y + bSize * 5.2f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 9.2f + tailWag, y + bSize * 4.2f, bSize * 1.1f, cMain)
                drawBrick(canvas, x + bSize * 8.6f + tailWag, y + bSize * 3.4f, bSize * 1.0f, cWhite)
            }
            PetSpecies.FOX -> {
                // Большой пушистый лисий хвост с белым кончиком
                val foxTailSpeed = if (isSprinting) 18f else if (isWalking) 11f else 5f
                val tailSwish = sin(timeSec * foxTailSpeed) * (bSize * 0.6f)
                drawBrick(canvas, x + bSize * 8.4f, y + bSize * 6.2f, bSize * 1.3f, cDark)
                drawBrick(canvas, x + bSize * 9.4f, y + bSize * 5.0f, bSize * 1.4f, cMain)
                drawBrick(canvas, x + bSize * 10.1f + tailSwish, y + bSize * 3.8f, bSize * 1.3f, cDark)
                drawBrick(canvas, x + bSize * 9.6f + tailSwish, y + bSize * 2.6f, bSize * 1.2f, C_CAT_WHITE) // Белый кончик
            }
            PetSpecies.DUCK -> {
                // Короткий задорный желтый хвостик
                val duckWag = sin(timeSec * 14f) * (bSize * 0.4f)
                drawBrick(canvas, x + bSize * 8.4f + duckWag, y + bSize * 4.8f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 9.2f + duckWag, y + bSize * 4.2f, bSize * 0.9f, cMain)
            }
            PetSpecies.PANDA -> {
                // Круглый пушистый черный хвостик
                drawBrick(canvas, x + bSize * 8.4f, y + bSize * 5.4f, bSize * 1.2f, C_PANDA_BLACK)
            }
            PetSpecies.CAT -> {
                // Длинный кошачий хвост
                val catTailSpeed = if (isSprinting) 18f else if (isWalking) 10f else 4f
                val tailSwish = sin(timeSec * catTailSpeed) * (bSize * 0.5f)
                drawBrick(canvas, x + bSize * 8.5f, y + bSize * 6.5f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 9.4f, y + bSize * 5.5f, bSize * 1.1f, cMain)
                drawBrick(canvas, x + bSize * 9.8f + tailSwish, y + bSize * 4.4f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 9.2f + tailSwish, y + bSize * 3.4f, bSize * 1.0f, cWhite)
            }
        }

        // 2. ТЕЛО (Спина, бока, пузико)
        val stepSpeed = if (isSprinting) 24f else 14f
        val stepAmp = if (isSprinting) 1.0f else 0.6f
        val stepBL = if (isWalking) sin(timeSec * stepSpeed) * (bSize * stepAmp) else 0f
        val liftBL = if (isWalking) abs(sin(timeSec * stepSpeed)) * (bSize * 0.35f) else 0f
        val stepBR = -stepBL
        val liftBR = if (isWalking) abs(sin(timeSec * stepSpeed + Math.PI.toFloat())) * (bSize * 0.35f) else 0f

        // Задние лапки
        drawBrick(canvas, x + bSize * 2.2f - stepBL * 0.3f, y + bSize * 6.6f - liftBL, bSize * 1.2f, cPaw)
        drawBrick(canvas, x + bSize * 7.0f - stepBR * 0.3f, y + bSize * 6.6f - liftBR, bSize * 1.2f, cPaw)

        // Тело и пузико
        val bodyMidColor = if (species == PetSpecies.PANDA) C_PANDA_BLACK else cMain
        drawBrick(canvas, x + bSize * 3.2f, y + bSize * 5.8f, bSize * 1.3f, bodyMidColor)
        drawBrick(canvas, x + bSize * 4.5f, y + bSize * 5.8f, bSize * 1.3f, if (species == PetSpecies.DUCK) cMain else cWhite)
        drawBrick(canvas, x + bSize * 5.8f, y + bSize * 5.8f, bSize * 1.3f, bodyMidColor)

        // Грудка
        drawBrick(canvas, x + bSize * 3.2f, y + bSize * 4.6f, bSize * 1.3f, bodyMidColor)
        drawBrick(canvas, x + bSize * 4.5f, y + bSize * 4.6f, bSize * 1.3f, cWhite)
        drawBrick(canvas, x + bSize * 5.8f, y + bSize * 4.6f, bSize * 1.3f, bodyMidColor)

        // Крылышки для Утенка по бокам
        if (species == PetSpecies.DUCK) {
            val wingFlap = if (isWalking || isSprinting) sin(timeSec * 20f) * (bSize * 0.5f) else 0f
            drawBrick(canvas, x + bSize * 2.0f, y + bSize * 4.4f - wingFlap, bSize * 1.2f, C_DUCK_DARK)
            drawBrick(canvas, x + bSize * 6.9f, y + bSize * 4.4f - wingFlap, bSize * 1.2f, C_DUCK_DARK)
        }

        // Ошейник с кулончиком
        if (species != PetSpecies.DUCK) {
            drawBrick(canvas, x + bSize * 3.4f, y + bSize * 3.8f, bSize * 1.1f, collarColor)
            drawBrick(canvas, x + bSize * 4.5f, y + bSize * 3.8f, bSize * 1.1f, C_BELL_GOLD)
            drawBrick(canvas, x + bSize * 5.6f, y + bSize * 3.8f, bSize * 1.1f, collarColor)
        }

        // 3. ГОЛОВА
        drawBrick(canvas, x + bSize * 3.2f, y + bSize * 1.2f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 4.5f, y + bSize * 1.2f, bSize * 1.3f, if (species == PetSpecies.CAT) cDark else cWhite)
        drawBrick(canvas, x + bSize * 5.8f, y + bSize * 1.2f, bSize * 1.3f, cMain)

        // Ушки / Хохолок
        when (species) {
            PetSpecies.DUCK -> {
                // Милый желтый хохолок на макушке
                drawBrick(canvas, x + bSize * 4.4f, y - bSize * 0.1f, bSize * 0.9f, C_DUCK_DARK)
            }
            PetSpecies.PANDA -> {
                // Круглые черные ушки панды
                drawBrick(canvas, x + bSize * 2.0f, y - bSize * 0.1f, bSize * 1.3f, C_PANDA_BLACK)
                drawBrick(canvas, x + bSize * 6.8f, y - bSize * 0.1f, bSize * 1.3f, C_PANDA_BLACK)
            }
            PetSpecies.FOX -> {
                // Большие лисьи ушки с белой опушкой
                drawBrick(canvas, x + bSize * 2.1f, y - bSize * 0.2f, bSize * 1.2f, C_FOX_DARK)
                drawBrick(canvas, x + bSize * 3.1f, y + bSize * 0.1f, bSize * 0.9f, C_CAT_WHITE)
                drawBrick(canvas, x + bSize * 6.9f, y - bSize * 0.2f, bSize * 1.2f, C_FOX_DARK)
                drawBrick(canvas, x + bSize * 6.1f, y + bSize * 0.1f, bSize * 0.9f, C_CAT_WHITE)
            }
            PetSpecies.DOG -> {
                // Аккуратные ушки Сиба-ину
                drawBrick(canvas, x + bSize * 2.2f, y + bSize * 0.1f, bSize * 1.2f, cDark)
                drawBrick(canvas, x + bSize * 3.2f, y + bSize * 0.2f, bSize * 1.0f, cWhite)
                drawBrick(canvas, x + bSize * 6.8f, y + bSize * 0.1f, bSize * 1.2f, cDark)
                drawBrick(canvas, x + bSize * 5.9f, y + bSize * 0.2f, bSize * 1.0f, cWhite)
            }
            PetSpecies.CAT -> {
                // Острые кошачьи ушки с розовым центром
                drawBrick(canvas, x + bSize * 2.2f, y + bSize * 0.0f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 3.1f, y + bSize * 0.3f, bSize * 0.9f, C_CAT_PINK)
                drawBrick(canvas, x + bSize * 6.9f, y + bSize * 0.0f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 6.2f, y + bSize * 0.3f, bSize * 0.9f, C_CAT_PINK)
            }
        }

        // Щечки
        drawBrick(canvas, x + bSize * 2.2f, y + bSize * 2.3f, bSize * 1.3f, if (species == PetSpecies.CAT) cMain else cWhite)
        drawBrick(canvas, x + bSize * 6.8f, y + bSize * 2.3f, bSize * 1.3f, if (species == PetSpecies.CAT) cMain else cWhite)

        // Черные круги вокруг глаз у панды
        if (species == PetSpecies.PANDA) {
            drawBrick(canvas, x + bSize * 3.0f, y + bSize * 1.9f, bSize * 1.6f, C_PANDA_BLACK)
            drawBrick(canvas, x + bSize * 5.6f, y + bSize * 1.9f, bSize * 1.6f, C_PANDA_BLACK)
        }

        // Глазки: в WAVE_POKE жмурится от радости (^ ^), во сне спящие (- -), иначе бусинки
        when (mode) {
            PetMode.WAVE_POKE -> {
                val eyeColor = if (species == PetSpecies.PANDA) Color.WHITE else C_CAT_EYE
                drawBrick(canvas, x + bSize * 3.5f, y + bSize * 2.1f, bSize * 1.0f, eyeColor)
                drawBrick(canvas, x + bSize * 5.8f, y + bSize * 2.1f, bSize * 1.0f, eyeColor)
                // Розовый румянец на щечках
                drawBrick(canvas, x + bSize * 2.4f, y + bSize * 2.9f, bSize * 0.85f, C_CAT_PINK)
                drawBrick(canvas, x + bSize * 6.9f, y + bSize * 2.9f, bSize * 0.85f, C_CAT_PINK)
            }
            PetMode.SLEEP -> {
                val eyeColor = if (species == PetSpecies.PANDA) Color.WHITE else C_CAT_EYE
                drawBrick(canvas, x + bSize * 3.4f, y + bSize * 2.5f, bSize * 1.1f, eyeColor)
                drawBrick(canvas, x + bSize * 5.7f, y + bSize * 2.5f, bSize * 1.1f, eyeColor)
            }
            else -> {
                drawBrick(canvas, x + bSize * 3.4f, y + bSize * 2.2f, bSize * 1.1f, if (species == PetSpecies.PANDA) Color.WHITE else C_CAT_EYE)
                drawBrick(canvas, x + bSize * 5.7f, y + bSize * 2.2f, bSize * 1.1f, if (species == PetSpecies.PANDA) Color.WHITE else C_CAT_EYE)
                // Белый блик
                val shineColor = if (species == PetSpecies.PANDA) Color.parseColor("#38BDF8") else Color.WHITE
                drawBrick(canvas, x + bSize * 3.5f, y + bSize * 2.2f, bSize * 0.45f, shineColor)
                drawBrick(canvas, x + bSize * 5.8f, y + bSize * 2.2f, bSize * 0.45f, shineColor)
            }
        }

        // Мордочка и клюв / носик
        if (species == PetSpecies.DUCK) {
            // Оранжевый плоский клювик утенка
            drawBrick(canvas, x + bSize * 4.0f, y + bSize * 2.7f, bSize * 2.2f, C_DUCK_BEAK)
            drawBrick(canvas, x + bSize * 4.5f, y + bSize * 2.7f, bSize * 1.2f, Color.parseColor("#FED7AA"))
        } else {
            drawBrick(canvas, x + bSize * 4.1f, y + bSize * 2.8f, bSize * 1.0f, cWhite)
            drawBrick(canvas, x + bSize * 5.1f, y + bSize * 2.8f, bSize * 1.0f, cWhite)
            drawBrick(canvas, x + bSize * 4.6f, y + bSize * 2.7f, bSize * 0.95f, cNose)
        }

        // Язычок / мячик
        if (hasBallInMouth) {
            val ballColor = if (species == PetSpecies.DOG) Color.parseColor("#EAB308") else Color.parseColor("#EF4444")
            val ballHighlight = if (species == PetSpecies.DOG) Color.WHITE else Color.parseColor("#FECDD3")
            drawBrick(canvas, x + bSize * 4.2f, y + bSize * 3.3f, bSize * 1.5f, ballColor)
            drawBrick(canvas, x + bSize * 4.6f, y + bSize * 3.5f, bSize * 0.7f, ballHighlight)
        } else if (species == PetSpecies.DOG && mode != PetMode.SLEEP) {
            drawBrick(canvas, x + bSize * 4.6f, y + bSize * 3.5f, bSize * 0.9f, C_DOG_TONGUE)
        } else if (species != PetSpecies.DUCK && mode == PetMode.WAVE_POKE) {
            drawBrick(canvas, x + bSize * 4.7f, y + bSize * 3.4f, bSize * 0.7f, C_CAT_PINK)
        }

        // 4. ПЕРЕДНИЕ ЛАПКИ
        if (mode == PetMode.WAVE_POKE) {
            drawBrick(canvas, x + bSize * 3.2f, y + bSize * 6.5f, bSize * 1.2f, cPaw)
            // Поднятая лапка машет
            val waveOffset = sin(timeSec * 14f) * (bSize * 0.8f)
            drawBrick(canvas, x + bSize * 6.8f + waveOffset, y + bSize * 3.6f, bSize * 1.2f, cMain)
            drawBrick(canvas, x + bSize * 7.4f + waveOffset, y + bSize * 2.7f, bSize * 1.1f, cPaw)
        } else if (isWalking) {
            val stepFL = sin(timeSec * stepSpeed) * (bSize * (stepAmp * 1.3f))
            val liftFL = abs(stepFL) * 0.45f
            val stepFR = -stepFL
            val liftFR = abs(stepFR) * 0.45f

            drawBrick(canvas, x + bSize * 3.2f + stepFL * 0.5f, y + bSize * 6.6f - liftFL, bSize * 1.2f, cPaw)
            drawBrick(canvas, x + bSize * 5.8f + stepFR * 0.5f, y + bSize * 6.6f - liftFR, bSize * 1.2f, cPaw)
        } else {
            drawBrick(canvas, x + bSize * 3.2f, y + bSize * 6.6f, bSize * 1.2f, cPaw)
            drawBrick(canvas, x + bSize * 5.8f, y + bSize * 6.6f, bSize * 1.2f, cPaw)
        }
    }

    /**
     * Отрисовка уютно лежащего питомца ("буханочка" / Loaf pose):
     * Все 5 персонажей Pixel Pals уютно дремлют, лапки поджаты, дыхание мерное.
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

        val cMain: Int
        val cDark: Int
        val cWhite: Int
        val cPaw: Int
        val cNose: Int
        val collarColor: Int

        when (species) {
            PetSpecies.DOG -> {
                cMain = C_DOG_HONEY
                cDark = C_DOG_TAN_DARK
                cWhite = C_DOG_WHITE
                cPaw = C_DOG_WHITE
                cNose = C_DOG_NOSE
                collarColor = C_COLLAR_RED
            }
            PetSpecies.FOX -> {
                cMain = C_FOX_RED
                cDark = C_FOX_DARK
                cWhite = C_CAT_WHITE
                cPaw = C_FOX_PAWS
                cNose = C_FOX_PAWS
                collarColor = Color.parseColor("#059669")
            }
            PetSpecies.DUCK -> {
                cMain = C_DUCK_YELLOW
                cDark = C_DUCK_DARK
                cWhite = C_DUCK_YELLOW
                cPaw = C_DUCK_FEET
                cNose = C_DUCK_BEAK
                collarColor = Color.parseColor("#3B82F6")
            }
            PetSpecies.PANDA -> {
                cMain = C_PANDA_WHITE
                cDark = C_PANDA_BLACK
                cWhite = C_PANDA_WHITE
                cPaw = C_PANDA_BLACK
                cNose = C_PANDA_DARK
                collarColor = Color.parseColor("#EF4444")
            }
            PetSpecies.CAT -> {
                cMain = C_CAT_GINGER
                cDark = C_CAT_GINGER_DARK
                cWhite = C_CAT_WHITE
                cPaw = C_CAT_WHITE
                cNose = C_CAT_PINK
                collarColor = C_COLLAR_TEAL
            }
        }

        // Мягкое мерное дыхание
        val breathe = sin(timeSec * 2.5f) * (bSize * 0.25f)
        val lieY = y + bSize * 1.2f + breathe

        // 1. Хвостик
        when (species) {
            PetSpecies.DOG -> {
                drawBrick(canvas, x + bSize * 0.8f, lieY + bSize * 3.8f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 0.4f, lieY + bSize * 2.9f, bSize * 1.0f, cWhite)
            }
            PetSpecies.FOX -> {
                // Пушистый лисий хвост обернут вокруг тела, белый кончик
                drawBrick(canvas, x + bSize * 0.6f, lieY + bSize * 4.6f, bSize * 1.3f, cDark)
                drawBrick(canvas, x + bSize * 1.4f, lieY + bSize * 5.6f, bSize * 1.3f, cMain)
                drawBrick(canvas, x + bSize * 2.6f, lieY + bSize * 6.2f, bSize * 1.2f, C_CAT_WHITE)
            }
            PetSpecies.DUCK -> {
                drawBrick(canvas, x + bSize * 0.8f, lieY + bSize * 4.6f, bSize * 1.0f, cDark)
            }
            PetSpecies.PANDA -> {
                drawBrick(canvas, x + bSize * 0.7f, lieY + bSize * 4.6f, bSize * 1.2f, C_PANDA_BLACK)
            }
            PetSpecies.CAT -> {
                drawBrick(canvas, x + bSize * 0.6f, lieY + bSize * 4.6f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 1.4f, lieY + bSize * 5.6f, bSize * 1.1f, cMain)
                drawBrick(canvas, x + bSize * 2.6f, lieY + bSize * 6.2f, bSize * 1.0f, cWhite)
            }
        }

        // 2. Тело калачиком (Loaf)
        val bodyColor = if (species == PetSpecies.PANDA) C_PANDA_BLACK else cDark
        drawBrick(canvas, x + bSize * 1.8f, lieY + bSize * 4.8f, bSize * 1.3f, bodyColor)
        drawBrick(canvas, x + bSize * 3.1f, lieY + bSize * 4.8f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 4.4f, lieY + bSize * 4.8f, bSize * 1.3f, cWhite)
        drawBrick(canvas, x + bSize * 5.7f, lieY + bSize * 4.8f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 7.0f, lieY + bSize * 4.8f, bSize * 1.3f, bodyColor)

        // Верхний хребет
        drawBrick(canvas, x + bSize * 2.4f, lieY + bSize * 3.6f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 3.7f, lieY + bSize * 3.6f, bSize * 1.3f, cWhite)
        drawBrick(canvas, x + bSize * 5.0f, lieY + bSize * 3.6f, bSize * 1.3f, cMain)

        // 3. Поджатые лапки
        drawBrick(canvas, x + bSize * 3.8f, lieY + bSize * 6.0f, bSize * 1.2f, cPaw)
        drawBrick(canvas, x + bSize * 5.4f, lieY + bSize * 6.0f, bSize * 1.2f, cPaw)

        // 4. Ошейник с кулоном
        if (species != PetSpecies.DUCK) {
            drawBrick(canvas, x + bSize * 6.2f, lieY + bSize * 3.5f, bSize * 1.1f, collarColor)
            drawBrick(canvas, x + bSize * 7.1f, lieY + bSize * 3.6f, bSize * 0.95f, C_BELL_GOLD)
        }

        // 5. Голова, лежащая на лапках
        drawBrick(canvas, x + bSize * 5.8f, lieY + bSize * 1.2f, bSize * 1.3f, cMain)
        drawBrick(canvas, x + bSize * 7.1f, lieY + bSize * 1.2f, bSize * 1.3f, if (species == PetSpecies.CAT) cDark else cWhite)
        drawBrick(canvas, x + bSize * 8.4f, lieY + bSize * 1.2f, bSize * 1.3f, cMain)

        // Ушки
        when (species) {
            PetSpecies.DUCK -> {
                drawBrick(canvas, x + bSize * 7.0f, lieY + bSize * 0.2f, bSize * 0.9f, C_DUCK_DARK)
            }
            PetSpecies.PANDA -> {
                drawBrick(canvas, x + bSize * 5.0f, lieY + bSize * 0.1f, bSize * 1.3f, C_PANDA_BLACK)
                drawBrick(canvas, x + bSize * 8.6f, lieY + bSize * 0.1f, bSize * 1.3f, C_PANDA_BLACK)
            }
            PetSpecies.FOX -> {
                drawBrick(canvas, x + bSize * 5.1f, lieY + bSize * 0.0f, bSize * 1.2f, C_FOX_DARK)
                drawBrick(canvas, x + bSize * 6.0f, lieY + bSize * 0.3f, bSize * 0.9f, C_CAT_WHITE)
                drawBrick(canvas, x + bSize * 8.6f, lieY + bSize * 0.0f, bSize * 1.2f, C_FOX_DARK)
            }
            PetSpecies.DOG -> {
                drawBrick(canvas, x + bSize * 5.2f, lieY + bSize * 0.2f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 6.2f, lieY + bSize * 0.3f, bSize * 0.9f, cWhite)
                drawBrick(canvas, x + bSize * 8.6f, lieY + bSize * 0.2f, bSize * 1.1f, cDark)
            }
            PetSpecies.CAT -> {
                drawBrick(canvas, x + bSize * 5.2f, lieY + bSize * 0.1f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 6.0f, lieY + bSize * 0.4f, bSize * 0.9f, C_CAT_PINK)
                drawBrick(canvas, x + bSize * 8.6f, lieY + bSize * 0.1f, bSize * 1.1f, cDark)
                drawBrick(canvas, x + bSize * 7.9f, lieY + bSize * 0.4f, bSize * 0.9f, C_CAT_PINK)
            }
        }

        // Щечки
        drawBrick(canvas, x + bSize * 5.6f, lieY + bSize * 2.3f, bSize * 1.3f, if (species == PetSpecies.CAT) cMain else cWhite)
        drawBrick(canvas, x + bSize * 8.6f, lieY + bSize * 2.3f, bSize * 1.3f, if (species == PetSpecies.CAT) cMain else cWhite)

        if (species == PetSpecies.PANDA) {
            drawBrick(canvas, x + bSize * 6.5f, lieY + bSize * 2.0f, bSize * 1.3f, C_PANDA_BLACK)
            drawBrick(canvas, x + bSize * 7.9f, lieY + bSize * 2.0f, bSize * 1.3f, C_PANDA_BLACK)
        }

        // Закрытые глазки блаженства
        val eyeColor = if (species == PetSpecies.PANDA) Color.WHITE else C_CAT_EYE
        drawBrick(canvas, x + bSize * 6.7f, lieY + bSize * 2.2f, bSize * 1.0f, eyeColor)
        drawBrick(canvas, x + bSize * 8.0f, lieY + bSize * 2.2f, bSize * 1.0f, eyeColor)

        // Розовый румянец на щечках
        drawBrick(canvas, x + bSize * 5.8f, lieY + bSize * 3.1f, bSize * 0.85f, C_CAT_PINK)
        drawBrick(canvas, x + bSize * 8.6f, lieY + bSize * 3.1f, bSize * 0.85f, C_CAT_PINK)

        // Мордочка и носик
        if (species == PetSpecies.DUCK) {
            drawBrick(canvas, x + bSize * 7.0f, lieY + bSize * 2.7f, bSize * 2.0f, C_DUCK_BEAK)
        } else {
            drawBrick(canvas, x + bSize * 7.1f, lieY + bSize * 2.8f, bSize * 1.0f, cWhite)
            drawBrick(canvas, x + bSize * 7.9f, lieY + bSize * 2.8f, bSize * 1.0f, cWhite)
            drawBrick(canvas, x + bSize * 7.4f, lieY + bSize * 2.6f, bSize * 0.9f, cNose)
    }

    /**
     * Отрисовка летящего или катящегося 3D-мячика (клубка для кота / теннисного для собаки)
     */
    fun drawFetchBall(canvas: Canvas, cx: Float, cy: Float, size: Float, species: PetSpecies, rotation: Float = 0f) {
        val isDog = (species == PetSpecies.DOG)
        val mainColor = if (isDog) Color.parseColor("#EAB308") else Color.parseColor("#EF4444")
        val accentColor = if (isDog) Color.WHITE else Color.parseColor("#FECDD3")

        canvas.save()
        canvas.rotate(rotation, cx, cy)
        val half = size / 2f
        drawBrick(canvas, cx - half, cy - half, size, mainColor)
        drawBrick(canvas, cx - half * 0.35f, cy - half * 0.35f, size * 0.42f, accentColor)
        canvas.restore()
    }

    /**
     * Парящие в воздухе уютные сердечки (❤️) строго НАД головой питомца.
     */
    fun drawFloatingHearts(canvas: Canvas, cx: Float, topY: Float, bSize: Float, time: Long) {
        val timeSec = time * 0.001f
        for (i in 0..2) {
            val p = ((timeSec * 0.7f + i * 0.33f) % 1.0f)
            val riseY = topY - p * (bSize * 5.0f)
            val driftX = sin(p * 4.0f + i * 2.1f) * (bSize * 1.0f) + (i - 1) * bSize * 0.8f
            val px = cx + driftX

            val alpha = ((1.0f - p) * 255).toInt().coerceIn(0, 255)
            textPaint.alpha = alpha
            textPaint.textSize = bSize * (1.5f + p * 0.3f)
            textPaint.color = C_HEART_RED
            canvas.drawText("❤️", px, riseY, textPaint)
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
     * PetMode.WAVE_POKE: Мягкие парящие сердечки НАД головой радостного машущего питомца.
     */
    private fun drawWavePokeAccessories(canvas: Canvas, x: Float, y: Float, bSize: Float, time: Long) {
        val timeSec = time * 0.001f
        textPaint.textSize = bSize * 1.6f
        textPaint.color = C_HEART_RED

        for (i in 0..2) {
            val pPhase = ((timeSec * 1.2f + i * 0.33f) % 1.0f)
            val pY = y - bSize * 0.8f - pPhase * (bSize * 4.5f)
            val pX = x + bSize * (2.5f + i * 1.8f) + sin(pPhase * 5f + i) * (bSize * 0.8f)

            textPaint.alpha = ((1f - pPhase) * 255).toInt().coerceIn(0, 255)
            canvas.drawText("❤️", pX, pY, textPaint)
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

    /**
     * Ночной синий колпак для уютного сна калачиком (после 22:00 или в режиме сна)
     */
    private fun drawNightCap(canvas: Canvas, x: Float, y: Float, bSize: Float, isLying: Boolean) {
        val capColor = Color.parseColor("#4F46E5")
        val capAccent = Color.parseColor("#818CF8")
        val pompomColor = Color.parseColor("#FACC15")

        if (isLying) {
            drawBrick(canvas, x + bSize * 4.6f, y - bSize * 0.4f, bSize * 1.6f, capColor)
            drawBrick(canvas, x + bSize * 3.8f, y - bSize * 1.3f, bSize * 1.3f, capAccent)
            drawBrick(canvas, x + bSize * 2.8f, y - bSize * 1.8f, bSize * 1.1f, pompomColor)
        } else {
            drawBrick(canvas, x + bSize * 4.2f, y - bSize * 0.8f, bSize * 1.8f, capColor)
            drawBrick(canvas, x + bSize * 3.2f, y - bSize * 1.8f, bSize * 1.4f, capAccent)
            drawBrick(canvas, x + bSize * 2.2f, y - bSize * 2.4f, bSize * 1.1f, pompomColor)
        }
    }

    /**
     * Стильные темные очки для солнечных дневных прогулок
     */
    private fun drawSunglasses(canvas: Canvas, x: Float, y: Float, bSize: Float) {
        val frameColor = Color.parseColor("#18181B")
        val lensShine = Color.parseColor("#38BDF8")

        drawBrick(canvas, x + bSize * 2.6f, y + bSize * 1.3f, bSize * 1.6f, frameColor)
        drawBrick(canvas, x + bSize * 3.0f, y + bSize * 1.5f, bSize * 0.6f, lensShine)

        drawBrick(canvas, x + bSize * 4.2f, y + bSize * 1.6f, bSize * 1.0f, frameColor)

        drawBrick(canvas, x + bSize * 5.2f, y + bSize * 1.3f, bSize * 1.6f, frameColor)
        drawBrick(canvas, x + bSize * 5.6f, y + bSize * 1.5f, bSize * 0.6f, lensShine)
    }

    /**
     * Королевская золотая 3D LEGO-корона для питомцев уровня 5+
     */
    private fun drawLegoCrown(canvas: Canvas, x: Float, y: Float, bSize: Float, isLying: Boolean = false) {
        val cGold = Color.parseColor("#FACC15")
        val cGoldDark = Color.parseColor("#CA8A04")
        val cRuby = Color.parseColor("#EF4444")
        val cDiamond = Color.parseColor("#38BDF8")

        val baseOffsetX = if (isLying) bSize * 0.4f else 0f
        val baseOffsetY = if (isLying) bSize * 0.4f else 0f
        val crownY = y - bSize * 0.9f + baseOffsetY

        // Золотой ободок короны из ряда аккуратных кубиков:
        for (i in 0..3) {
            drawBrick(canvas, x + bSize * (3.3f + i * 0.85f) + baseOffsetX, crownY, bSize * 0.85f, cGoldDark)
            drawBrick(canvas, x + bSize * (3.3f + i * 0.85f) + baseOffsetX, crownY - bSize * 0.45f, bSize * 0.85f, cGold)
        }

        // Зубцы короны с драгоценными камнями:
        // Левый зубец с рубином
        drawBrick(canvas, x + bSize * 3.3f + baseOffsetX, crownY - bSize * 1.3f, bSize * 0.85f, cGold)
        drawBrick(canvas, x + bSize * 3.4f + baseOffsetX, crownY - bSize * 1.8f, bSize * 0.65f, cRuby)

        // Центральный высокий королевский зубец с алмазом
        drawBrick(canvas, x + bSize * 4.5f + baseOffsetX, crownY - bSize * 1.4f, bSize * 0.95f, cGold)
        drawBrick(canvas, x + bSize * 4.6f + baseOffsetX, crownY - bSize * 2.15f, bSize * 0.75f, cDiamond)

        // Правый зубец с рубином
        drawBrick(canvas, x + bSize * 5.85f + baseOffsetX, crownY - bSize * 1.3f, bSize * 0.85f, cGold)
        drawBrick(canvas, x + bSize * 5.95f + baseOffsetX, crownY - bSize * 1.8f, bSize * 0.65f, cRuby)

        // Искорка блеска на алмазе
        val sparkle = (sin(System.currentTimeMillis() * 0.006f) + 1f) * 0.5f
        if (sparkle > 0.65f) {
            drawBrick(canvas, x + bSize * 4.8f + baseOffsetX, crownY - bSize * 2.6f, bSize * 0.4f, Color.WHITE)
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

package com.pixelisland.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Интерактивный островок Dynamic Island в стиле уютного 3D-конструктора (Lego Creator).
 * В свернутом виде — аккуратная блочная капсула с питомцем и индикатором музыки/зарядки.
 * В раскрытом виде (155dp) — панель со статами, питомцем в красных наушниках,
 * 3 кнопками управления плеером [ ⏮️ ], [ ⏯️ Пауза ], [ ⏭️ След. ]
 * и нижним рядом действий [ 🍔 Кормить ], [ 👋 Привет ], [ 🐾 В гости ].
 */
class IslandOverlayView(context: Context) : View(context) {

    private val renderer = PixelPetRenderer(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * scaledDensity

    private val capsuleRect = RectF()

    // Кнопки плеера
    private val btnPrevRect = RectF()
    private val btnPlayPauseRect = RectF()
    private val btnNextRect = RectF()

    // Кнопки действий тамагочи
    private val btnFeedRect = RectF()
    private val btnPokeRect = RectF()
    private val btnVisitRect = RectF()

    // Кнопки заявки в друзья
    private val btnAcceptRect = RectF()
    private val btnDeclineRect = RectF()

    // Оформление капсулы
    private val capsuleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#12151B")
        style = Paint.Style.FILL
    }

    private val capsuleHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#252B36")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    // Кнопки тамагочи
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F242D")
        style = Paint.Style.FILL
    }

    private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#323B49")
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
    }

    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 11.5f * scaledDensity
        isFakeBoldText = true
    }

    // Кнопки плеера
    private val mediaBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#18202C")
        style = Paint.Style.FILL
    }

    private val mediaBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E3B52")
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
    }

    private val mediaBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textAlign = Paint.Align.CENTER
        textSize = 11.5f * scaledDensity
        isFakeBoldText = true
    }

    private val mediaTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 12f * scaledDensity
        isFakeBoldText = true
    }

    // Кнопки заявок в друзья
    private val acceptBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.FILL
    }

    private val acceptTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A2B14")
        textAlign = Paint.Align.CENTER
        textSize = 12f * scaledDensity
        isFakeBoldText = true
    }

    private val declineBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A303C")
        style = Paint.Style.FILL
    }

    private val declineTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252")
        textAlign = Paint.Align.CENTER
        textSize = 12f * scaledDensity
        isFakeBoldText = true
    }

    private val friendBannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D2533")
        style = Paint.Style.FILL
    }

    private val friendBannerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val friendBannerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 12f * scaledDensity
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    // Шрифты для статов
    private val statsLevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        textSize = 10.5f * scaledDensity
        isFakeBoldText = true
    }

    private val statsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E6ED")
        textSize = 10f * scaledDensity
        isFakeBoldText = true
    }

    private val whiteTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * scaledDensity
        isFakeBoldText = true
    }

    private val subWhitePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AA4B2")
        textSize = 10f * scaledDensity
    }

    private val pinkTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6584")
        textSize = 11.5f * scaledDensity
        isFakeBoldText = true
    }

    private val doomBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        style = Paint.Style.FILL
    }

    private val doomTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 11f * scaledDensity
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val notificationCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.FILL
    }

    private val eqPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E5FF") }
    private val eqPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#10B981") }
    private val eqPaint3 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FACC15") }
    private val eqPaint4 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F43F5E") }

    private val neonTrackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.FILL
    }
    private val neonTrackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }
    private val neonThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F43F5E")
        style = Paint.Style.FILL
    }
    private val mediaTimePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 9.5f * scaledDensity
        isFakeBoldText = true
    }

    // Supercharge Pop
    private val chargePillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#063319")
        style = Paint.Style.FILL
    }
    private val chargePillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val chargeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        textSize = 10f * scaledDensity
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    // ==================== МИНИ-ИГРА В МЯЧИК (FETCH MINI-GAME) ====================
    private enum class FetchPhase {
        IDLE,
        BALL_FLYING,
        PET_CHASING,
        PET_RETURNING,
        CELEBRATING
    }

    private var fetchPhase = FetchPhase.IDLE
    private var fetchStartTime = 0L
    private var celebrateStartTime = 0L

    private var ballStartX = 0f
    private var ballStartY = 0f
    private var ballTargetX = 0f
    private var ballTargetY = 0f
    private var ballCurrentX = 0f
    private var ballCurrentY = 0f
    private var ballRotation = 0f

    private var petFetchStartX = 0f
    private var petFetchTargetX = 0f
    private var petFetchCurrentX = 0f
    private var xpToastText: String? = null

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    private val xpBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        textSize = 10f * scaledDensity
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val xpBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2100")
        style = Paint.Style.FILL
    }

    private val xpBadgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private fun startFetchGame(throwRight: Boolean) {
        val state = IslandStateBus.state.value
        val petSize = dp(28f) * state.islandScale
        val minX = capsuleRect.left + dp(40f) * state.islandScale
        val maxX = (capsuleRect.right - dp(8f) * state.islandScale - petSize).coerceAtLeast(minX)

        val currentPetX = petFetchCurrentX.coerceIn(minX, maxX)
        ballStartX = currentPetX + petSize / 2f
        ballStartY = capsuleRect.centerY()

        if (throwRight) {
            ballTargetX = (capsuleRect.right - dp(18f) * state.islandScale).coerceAtLeast(minX + dp(14f))
        } else {
            ballTargetX = capsuleRect.left + dp(22f) * state.islandScale
        }
        ballTargetY = capsuleRect.centerY() + dp(4f) * state.islandScale

        petFetchStartX = currentPetX
        fetchStartTime = System.currentTimeMillis()
        fetchPhase = FetchPhase.BALL_FLYING
        xpToastText = null
        vibrateClick()
        invalidate()
    }

    private var frameIndex = 0
    private val animHandler = Handler(Looper.getMainLooper())
    private var isAnimRunning = false
    private var autoCollapseRunnable: Runnable? = null

    private fun scheduleAutoCollapse(delayMs: Long = 5000L) {
        autoCollapseRunnable?.let { animHandler.removeCallbacks(it) }
        val r = Runnable {
            IslandStateBus.setExpanded(false)
        }
        autoCollapseRunnable = r
        animHandler.postDelayed(r, delayMs)
    }

    private fun cancelAutoCollapse() {
        autoCollapseRunnable?.let { animHandler.removeCallbacks(it) }
        autoCollapseRunnable = null
    }

    private val animRunnable = object : Runnable {
        override fun run() {
            val state = IslandStateBus.state.value
            if (!state.isScreenOn) {
                isAnimRunning = false
                return
            }
            frameIndex++
            invalidate()
            val delay = renderer.getFrameDelayMs(state.petMode)
            animHandler.postDelayed(this, delay)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthPx = dp(320f).toInt()
        val heightPx = dp(170f).toInt()
        setMeasuredDimension(widthPx, heightPx)
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
        cancelAutoCollapse()
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
        val state = IslandStateBus.state.value
        val viewW = width.toFloat()

        if (!state.isExpanded) {
            // ==================== СВЕРНУТЫЙ РЕЖИМ (3D БЛОЧНЫЙ ОСТРОВОК) ====================
            btnPrevRect.setEmpty()
            btnPlayPauseRect.setEmpty()
            btnNextRect.setEmpty()
            btnFeedRect.setEmpty()
            btnPokeRect.setEmpty()
            btnVisitRect.setEmpty()
            btnAcceptRect.setEmpty()
            btnDeclineRect.setEmpty()

            val baseWidthDp = if (state.hasGuestPet) 180f else 140f
            val capsuleW = dp(baseWidthDp) * state.islandScale
            val capsuleH = dp(36f) * state.islandScale
            val left = (viewW - capsuleW) / 2f
            val top = dp(4f)
            val right = left + capsuleW
            val bottom = top + capsuleH
            capsuleRect.set(left, top, right, bottom)
            val cornerRadius = capsuleH / 2f

            canvas.drawRoundRect(capsuleRect, cornerRadius, cornerRadius, capsuleBgPaint)
            canvas.drawRoundRect(capsuleRect, cornerRadius, cornerRadius, capsuleHighlightPaint)

            // Левый индикатор активности:
            val timeNow = System.currentTimeMillis()
            val isChargingPulse = timeNow < state.chargingPulseUntilMs

            if (isChargingPulse) {
                // СУПЕР-ЧИП БЫСТРОЙ ЗАРЯДКИ (Supercharge Status Pop) на 3 секунды
                val timeLeft = (state.chargingPulseUntilMs - timeNow).toFloat()
                val alpha = (timeLeft / 400f).coerceIn(0f, 1f)

                val pillH = dp(16f) * state.islandScale
                val pillW = dp(46f) * state.islandScale
                val chipStartX = capsuleRect.left + dp(9f) * state.islandScale
                val chipCenterY = capsuleRect.centerY()
                val pillRect = RectF(chipStartX, chipCenterY - pillH / 2f, chipStartX + pillW, chipCenterY + pillH / 2f)

                val aInt = (alpha * 255).toInt().coerceIn(0, 255)
                chargePillBgPaint.alpha = aInt
                chargePillBorderPaint.alpha = aInt
                chargeTextPaint.alpha = aInt

                canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, chargePillBgPaint)
                canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, chargePillBorderPaint)

                val chargeStr = "⚡ ${state.batteryLevel}%"
                val textY = pillRect.centerY() - (chargeTextPaint.descent() + chargeTextPaint.ascent()) / 2f
                canvas.drawText(chargeStr, pillRect.centerX(), textY, chargeTextPaint)
            } else if (state.petMode == PetMode.MUSIC || state.isMusicPlaying) {
                val barW = dp(2.8f) * state.islandScale
                val barGap = dp(1.8f) * state.islandScale
                val eqStartX = capsuleRect.left + dp(12f) * state.islandScale
                val centerY = capsuleRect.centerY()

                val timeSec = System.currentTimeMillis() * 0.001f
                val h1 = dp(4f + abs(sin(timeSec * 8f)) * 12f) * state.islandScale
                val h2 = dp(5f + abs(sin(timeSec * 12f + 1.2f)) * 14f) * state.islandScale
                val h3 = dp(3f + abs(sin(timeSec * 9f + 2.5f)) * 10f) * state.islandScale
                val h4 = dp(4f + abs(sin(timeSec * 14f + 0.7f)) * 11f) * state.islandScale

                canvas.drawRoundRect(RectF(eqStartX, centerY - h1 / 2f, eqStartX + barW, centerY + h1 / 2f), barW / 2f, barW / 2f, eqPaint1)
                canvas.drawRoundRect(RectF(eqStartX + (barW + barGap), centerY - h2 / 2f, eqStartX + barW * 2 + barGap, centerY + h2 / 2f), barW / 2f, barW / 2f, eqPaint2)
                canvas.drawRoundRect(RectF(eqStartX + (barW + barGap) * 2, centerY - h3 / 2f, eqStartX + (barW + barGap) * 2 + barW, centerY + h3 / 2f), barW / 2f, barW / 2f, eqPaint3)
                canvas.drawRoundRect(RectF(eqStartX + (barW + barGap) * 3, centerY - h4 / 2f, eqStartX + (barW + barGap) * 3 + barW, centerY + h4 / 2f), barW / 2f, barW / 2f, eqPaint4)

                // НЕОНОВЫЙ ПРОГРЕСС-БАР ТРЕКА ПО НИЖНЕЙ КРОМКЕ КАПСУЛЫ
                val barMargin = cornerRadius * 0.85f
                val trackLeft = capsuleRect.left + barMargin
                val trackRight = capsuleRect.right - barMargin
                val trackY = capsuleRect.bottom - dp(2.4f) * state.islandScale
                val trackH = dp(2.2f) * state.islandScale
                val totalTrackW = (trackRight - trackLeft).coerceAtLeast(dp(20f))

                // Фоновая подложка
                canvas.drawRoundRect(RectF(trackLeft, trackY - trackH / 2f, trackRight, trackY + trackH / 2f), trackH / 2f, trackH / 2f, neonTrackBgPaint)

                // Неоновый заполняющийся прогресс
                val progress = ((timeSec * 0.12f) % 1.0f)
                val fillRight = trackLeft + totalTrackW * progress
                canvas.drawRoundRect(RectF(trackLeft, trackY - trackH / 2f, fillRight, trackY + trackH / 2f), trackH / 2f, trackH / 2f, neonTrackFillPaint)

                // Неоновая светящаяся головка прогресса
                canvas.drawCircle(fillRight, trackY, dp(2.2f) * state.islandScale, neonThumbPaint)
            } else if (state.petMode == PetMode.NOTIFICATION) {
                val dotRadius = dp(4.5f) * state.islandScale
                val dotX = capsuleRect.left + dp(16f) * state.islandScale
                val dotY = capsuleRect.centerY()
                canvas.drawCircle(dotX, dotY, dotRadius, notificationCirclePaint)
            } else {
                val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                val clockX = capsuleRect.left + dp(12f) * state.islandScale
                val clockY = capsuleRect.centerY() - (subWhitePaint.descent() + subWhitePaint.ascent()) / 2f
                canvas.drawText(timeStr, clockX, clockY, subWhitePaint)
            }

            // Правая часть: Питомец из 3D-кубиков конструктора
            if (state.isMyPetAway) {
                val text = "В гостях..."
                val textW = subWhitePaint.measureText(text)
                val textX = capsuleRect.right - dp(12f) * state.islandScale - textW
                val textY = capsuleRect.centerY() - (subWhitePaint.descent() + subWhitePaint.ascent()) / 2f
                canvas.drawText(text, textX, textY, subWhitePaint)
            } else {
                val petSize = dp(28f) * state.islandScale
                if (state.hasGuestPet) {
                    val guestRight = capsuleRect.right - dp(8f) * state.islandScale
                    val guestRect = RectF(guestRight - petSize, capsuleRect.centerY() - petSize / 2f, guestRight, capsuleRect.centerY() + petSize / 2f)

                    val baseMyRight = guestRect.left - dp(2f) * state.islandScale
                    val minX = capsuleRect.left + dp(32f) * state.islandScale
                    val maxX = guestRect.left - dp(2f) * state.islandScale
                    val clampedX = (baseMyRight - petSize + state.tiltOffsetPx).coerceIn(minX, (maxX - petSize).coerceAtLeast(minX))
                    val myRect = RectF(clampedX, capsuleRect.centerY() - petSize / 2f, clampedX + petSize, capsuleRect.centerY() + petSize / 2f)

                    renderer.drawPet(canvas, myRect, state.petMode, frameIndex)
                    renderer.drawGuestPet(canvas, guestRect, frameIndex)
                } else {
                    val time = System.currentTimeMillis()
                    val isPhysicsActive = state.isHangingOnEdge || abs(state.tiltOffsetPx) >= 8f

                    val minX = capsuleRect.left + dp(40f) * state.islandScale
                    val maxX = (capsuleRect.right - dp(8f) * state.islandScale - petSize).coerceAtLeast(minX)

                    var clampedX: Float
                    var isWalking: Boolean
                    var isFacingLeft: Boolean
                    var isLyingDown: Boolean
                    var isEmittingHearts: Boolean
                    var isSprinting = false
                    var hasBallInMouth = false

                    if (fetchPhase != FetchPhase.IDLE) {
                        isLyingDown = false
                        when (fetchPhase) {
                            FetchPhase.BALL_FLYING -> {
                                val elapsed = ((time - fetchStartTime) / 550f).coerceIn(0f, 1f)
                                ballCurrentX = ballStartX + (ballTargetX - ballStartX) * elapsed
                                val arc = sin(elapsed * Math.PI.toFloat()) * dp(12f) * state.islandScale
                                ballCurrentY = ballStartY + (ballTargetY - ballStartY) * elapsed - arc
                                ballRotation = elapsed * 720f

                                clampedX = petFetchStartX
                                isWalking = false
                                isSprinting = false
                                isFacingLeft = (ballTargetX < clampedX)
                                hasBallInMouth = false
                                isEmittingHearts = false

                                if (elapsed >= 1f) {
                                    fetchPhase = FetchPhase.PET_CHASING
                                    fetchStartTime = time
                                    petFetchStartX = clampedX
                                    petFetchTargetX = if (ballTargetX > petFetchStartX) {
                                        (ballTargetX - petSize * 0.7f).coerceIn(minX, maxX)
                                    } else {
                                        (ballTargetX - petSize * 0.15f).coerceIn(minX, maxX)
                                    }
                                }
                            }
                            FetchPhase.PET_CHASING -> {
                                val elapsed = ((time - fetchStartTime) / 750f).coerceIn(0f, 1f)
                                val smoothP = (1f - cos(elapsed * Math.PI.toFloat())) / 2f
                                clampedX = petFetchStartX + (petFetchTargetX - petFetchStartX) * smoothP
                                isWalking = true
                                isSprinting = true
                                isFacingLeft = (petFetchTargetX < petFetchStartX)
                                hasBallInMouth = false
                                isEmittingHearts = false

                                ballCurrentX = ballTargetX
                                ballCurrentY = ballTargetY

                                if (elapsed >= 1f) {
                                    fetchPhase = FetchPhase.PET_RETURNING
                                    fetchStartTime = time
                                    hasBallInMouth = true
                                    petFetchStartX = clampedX
                                    petFetchTargetX = maxX
                                }
                            }
                            FetchPhase.PET_RETURNING -> {
                                val elapsed = ((time - fetchStartTime) / 900f).coerceIn(0f, 1f)
                                val smoothP = (1f - cos(elapsed * Math.PI.toFloat())) / 2f
                                clampedX = petFetchStartX + (petFetchTargetX - petFetchStartX) * smoothP
                                isWalking = true
                                isSprinting = true
                                isFacingLeft = (petFetchTargetX < petFetchStartX)
                                hasBallInMouth = true
                                isEmittingHearts = false

                                if (elapsed >= 1f) {
                                    fetchPhase = FetchPhase.CELEBRATING
                                    celebrateStartTime = time
                                    hasBallInMouth = false
                                    isSprinting = false
                                    isWalking = false
                                    isFacingLeft = false
                                    ballCurrentX = (clampedX - dp(10f) * state.islandScale).coerceAtLeast(capsuleRect.left + dp(14f))
                                    ballCurrentY = capsuleRect.centerY() + dp(4f) * state.islandScale
                                    IslandStateBus.addXpAndMood(20, 15)
                                    xpToastText = "+20 XP ✨"
                                    vibrateClick()
                                }
                            }
                            FetchPhase.CELEBRATING -> {
                                val elapsed = ((time - celebrateStartTime) / 1600f).coerceIn(0f, 1f)
                                clampedX = petFetchTargetX
                                isWalking = false
                                isSprinting = false
                                isFacingLeft = false
                                hasBallInMouth = false
                                isEmittingHearts = true

                                if (elapsed >= 1f) {
                                    fetchPhase = FetchPhase.IDLE
                                    xpToastText = null
                                }
                            }
                            else -> {
                                clampedX = maxX
                                isWalking = false
                                isFacingLeft = false
                                isLyingDown = false
                                isEmittingHearts = false
                            }
                        }
                    } else if (isPhysicsActive) {
                        clampedX = if (state.isHangingOnEdge) {
                            if (state.tiltOffsetPx < 0) capsuleRect.left - petSize * 0.25f else capsuleRect.right - petSize * 0.75f
                        } else {
                            (maxX + state.tiltOffsetPx).coerceIn(minX, maxX)
                        }
                        isWalking = false
                        isFacingLeft = state.tiltOffsetPx < 0
                        isLyingDown = false
                        isEmittingHearts = false
                    } else if (state.petMode == PetMode.SLEEP) {
                        clampedX = minX
                        isWalking = false
                        isFacingLeft = true
                        isLyingDown = true
                        isEmittingHearts = false
                    } else if (state.petMode == PetMode.WAVE_POKE) {
                        clampedX = maxX
                        isWalking = false
                        isFacingLeft = false
                        isLyingDown = false
                        isEmittingHearts = true
                    } else if (state.petMode == PetMode.EATING) {
                        clampedX = maxX
                        isWalking = false
                        isFacingLeft = false
                        isLyingDown = false
                        isEmittingHearts = false
                    } else {
                        // АКТИВНАЯ АВТОНОМНАЯ ЖИЗНЬ НА ОСТРОВКЕ (20 сек полный цикл):
                        // 0..5с: Идет справа налево через островок (мордочка влево, лапки шагают, хвост виляет)
                        // 5..10с: Ложится калачиком у левого края (поза Loaf, дышит, сладко дремлет)
                        // 10..11.5с: Просыпается, потягивается и разворачивается направо
                        // 11.5..16.5с: Идет слева направо
                        // 16.5..20с: Сидит справа, виляет хвостом и пускает парящие сердечки со звездочками!
                        val loopMs = 20_000L
                        val t = ((time % loopMs) / 1000f)

                        when {
                            t < 5.0f -> {
                                val p = t / 5.0f
                                val smoothP = (1f - cos(p * Math.PI.toFloat())) / 2f
                                clampedX = maxX - (maxX - minX) * smoothP
                                isWalking = true
                                isFacingLeft = true
                                isLyingDown = false
                                isEmittingHearts = false
                            }
                            t < 10.0f -> {
                                clampedX = minX
                                isWalking = false
                                isFacingLeft = true
                                isLyingDown = true
                                isEmittingHearts = (t > 8.0f)
                            }
                            t < 11.5f -> {
                                clampedX = minX
                                isWalking = false
                                isFacingLeft = false
                                isLyingDown = false
                                isEmittingHearts = false
                            }
                            t < 16.5f -> {
                                val p = (t - 11.5f) / 5.0f
                                val smoothP = (1f - cos(p * Math.PI.toFloat())) / 2f
                                clampedX = minX + (maxX - minX) * smoothP
                                isWalking = true
                                isFacingLeft = false
                                isLyingDown = false
                                isEmittingHearts = false
                            }
                            else -> {
                                clampedX = maxX
                                isWalking = false
                                isFacingLeft = false
                                isLyingDown = false
                                isEmittingHearts = true
                            }
                        }
                    }

                    petFetchCurrentX = clampedX

                    val myRect = RectF(clampedX, capsuleRect.centerY() - petSize / 2f, clampedX + petSize, capsuleRect.centerY() + petSize / 2f)
                    renderer.drawPet(
                        canvas = canvas,
                        dstRect = myRect,
                        mode = state.petMode,
                        frameIndex = frameIndex,
                        isWalking = isWalking,
                        isFacingLeft = isFacingLeft,
                        isLyingDown = isLyingDown,
                        isEmittingHearts = isEmittingHearts,
                        isSprinting = isSprinting,
                        hasBallInMouth = hasBallInMouth
                    )

                    // Отрисовка мячика при броске, подборе и отдыхе
                    if (fetchPhase == FetchPhase.BALL_FLYING || fetchPhase == FetchPhase.PET_CHASING || fetchPhase == FetchPhase.CELEBRATING) {
                        renderer.drawFetchBall(
                            canvas = canvas,
                            cx = ballCurrentX,
                            cy = ballCurrentY,
                            size = dp(11f) * state.islandScale,
                            species = state.species,
                            rotation = ballRotation
                        )
                    }

                    // Всплывающая плашка +20 XP при победном возвращении мячика
                    if (fetchPhase == FetchPhase.CELEBRATING && xpToastText != null) {
                        val elapsedBadge = ((time - celebrateStartTime) / 1600f).coerceIn(0f, 1f)
                        val badgeW = dp(64f) * state.islandScale
                        val badgeH = dp(17f) * state.islandScale
                        val badgeX = clampedX + petSize / 2f
                        val badgeY = capsuleRect.top - dp(2f) - (elapsedBadge * dp(10f))
                        val alphaInt = ((1f - elapsedBadge) * 255).toInt().coerceIn(0, 255)

                        xpBadgeBgPaint.alpha = alphaInt
                        xpBadgeBorderPaint.alpha = alphaInt
                        xpBadgePaint.alpha = alphaInt

                        val badgeRect = RectF(badgeX - badgeW / 2f, badgeY - badgeH / 2f, badgeX + badgeW / 2f, badgeY + badgeH / 2f)
                        canvas.drawRoundRect(badgeRect, badgeH / 2f, badgeH / 2f, xpBadgeBgPaint)
                        canvas.drawRoundRect(badgeRect, badgeH / 2f, badgeH / 2f, xpBadgeBorderPaint)
                        val textY = badgeRect.centerY() - (xpBadgePaint.descent() + xpBadgePaint.ascent()) / 2f
                        canvas.drawText(xpToastText!!, badgeX, textY, xpBadgePaint)
                    }
                }
            }
        } else {
            // ==================== РАСКРЫТЫЙ РЕЖИМ (155dp ПАНЕЛЬ УПРАВЛЕНИЯ) ====================
            val capsuleW = dp(306f)
            val capsuleH = dp(155f) // Увеличено до 155dp по требованиям
            val left = (viewW - capsuleW) / 2f
            val top = dp(4f)
            val right = left + capsuleW
            val bottom = top + capsuleH
            capsuleRect.set(left, top, right, bottom)
            val cornerRadius = dp(24f)

            canvas.drawRoundRect(capsuleRect, cornerRadius, cornerRadius, capsuleBgPaint)
            canvas.drawRoundRect(capsuleRect, cornerRadius, cornerRadius, capsuleHighlightPaint)

            // Тамагочи-статы в верхнем правом углу
            val statsX = capsuleRect.right - dp(14f)
            val statsY = capsuleRect.top + dp(16f)
            val statsInfo = "🍖 ${state.hunger}%  ⚡ ${state.energy}%  💖 ${state.mood}%"
            statsTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(statsInfo, statsX, statsY, statsTextPaint)

            val levelAndSteps = "Lv.${state.petLevel} (${state.xp} XP) • 👟 ${state.stepsToday} • 🪙 ${state.coins}"
            statsLevelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(levelAndSteps, statsX, statsY + dp(12.5f), statsLevelPaint)

            // 3D-питомец слева (в наушниках при музыке)
            val petSize = dp(56f)
            val petTop = capsuleRect.top + dp(14f)
            val myPetRect = RectF(capsuleRect.left + dp(14f), petTop, capsuleRect.left + dp(14f) + petSize, petTop + petSize)
            val timeExpanded = System.currentTimeMillis()
            val isExpandedHearts = (state.petMode == PetMode.WAVE_POKE) || (state.petMode == PetMode.IDLE && ((timeExpanded / 2500L) % 2L == 0L))
            renderer.drawPet(canvas, myPetRect, state.petMode, frameIndex, isEmittingHearts = isExpandedHearts)

            var textStartX = myPetRect.right + dp(12f)
            if (state.hasGuestPet) {
                val guestRect = RectF(myPetRect.right + dp(4f), petTop, myPetRect.right + dp(4f) + petSize, petTop + petSize)
                renderer.drawGuestPet(canvas, guestRect, frameIndex)
                textStartX = guestRect.right + dp(8f)
            }

            val maxTextWidth = (capsuleRect.right - dp(14f) - textStartX).coerceAtLeast(dp(60f))

            // ==================== БАННЕР ЗАЯВКИ В ДРУЗЬЯ (ЕСЛИ ЕСТЬ) ====================
            val hasFriendRequest = !state.incomingFriendRequestFrom.isNullOrBlank()
            if (hasFriendRequest) {
                btnPrevRect.setEmpty()
                btnPlayPauseRect.setEmpty()
                btnNextRect.setEmpty()
                btnFeedRect.setEmpty()
                btnPokeRect.setEmpty()
                btnVisitRect.setEmpty()

                val reqBannerRect = RectF(textStartX, capsuleRect.top + dp(38f), capsuleRect.right - dp(14f), capsuleRect.top + dp(82f))
                canvas.drawRoundRect(reqBannerRect, dp(10f), dp(10f), friendBannerPaint)
                canvas.drawRoundRect(reqBannerRect, dp(10f), dp(10f), friendBannerBorderPaint)

                val reqTitle = "Заявка в друзья от @${state.incomingFriendRequestFrom}"
                val reqEllipsized = TextUtils.ellipsize(reqTitle, friendBannerTextPaint, reqBannerRect.width() - dp(8f), TextUtils.TruncateAt.END).toString()
                canvas.drawText(reqEllipsized, reqBannerRect.centerX(), reqBannerRect.centerY() + dp(4f), friendBannerTextPaint)

                // Кнопки [ Принять ] и [ Отклонить ]
                val btnTop = capsuleRect.bottom - dp(46f)
                val btnBottom = capsuleRect.bottom - dp(10f)
                val availW = capsuleW - dp(24f) - dp(10f)
                val halfW = availW / 2f

                val b1L = capsuleRect.left + dp(12f)
                btnAcceptRect.set(b1L, btnTop, b1L + halfW, btnBottom)

                val b2L = btnAcceptRect.right + dp(10f)
                btnDeclineRect.set(b2L, btnTop, b2L + halfW, btnBottom)

                val btnCorner = dp(12f)
                canvas.drawRoundRect(btnAcceptRect, btnCorner, btnCorner, acceptBtnPaint)
                canvas.drawRoundRect(btnDeclineRect, btnCorner, btnCorner, declineBtnPaint)
                canvas.drawRoundRect(btnDeclineRect, btnCorner, btnCorner, btnBorderPaint)

                val acceptBase = btnAcceptRect.centerY() - (acceptTextPaint.descent() + acceptTextPaint.ascent()) / 2f
                canvas.drawText("[ Принять ]", btnAcceptRect.centerX(), acceptBase, acceptTextPaint)

                val declineBase = btnDeclineRect.centerY() - (declineTextPaint.descent() + declineTextPaint.ascent()) / 2f
                canvas.drawText("[ Отклонить ]", btnDeclineRect.centerX(), declineBase, declineTextPaint)
            } else {
                btnAcceptRect.setEmpty()
                btnDeclineRect.setEmpty()

                val isMusicActive = !state.mediaTitle.isNullOrBlank() || state.petMode == PetMode.MUSIC

                if (!state.doomscrollWarning.isNullOrBlank()) {
                    btnPrevRect.setEmpty()
                    btnPlayPauseRect.setEmpty()
                    btnNextRect.setEmpty()

                    val bannerTop = capsuleRect.top + dp(38f)
                    val bannerBottom = capsuleRect.top + dp(82f)
                    val bannerRect = RectF(textStartX, bannerTop, capsuleRect.right - dp(14f), bannerBottom)
                    canvas.drawRoundRect(bannerRect, dp(10f), dp(10f), doomBgPaint)

                    val doomText = TextUtils.ellipsize(state.doomscrollWarning, doomTextPaint, bannerRect.width() - dp(10f), TextUtils.TruncateAt.END).toString()
                    val doomBaseline = bannerRect.centerY() - (doomTextPaint.descent() + doomTextPaint.ascent()) / 2f
                    canvas.drawText(doomText, bannerRect.centerX(), doomBaseline, doomTextPaint)
                } else if (isMusicActive) {
                    // ==================== РЕЖИМ МУЗЫКИ: НАЗВАНИЕ + НЕОНОВЫЙ SEEKBAR + 3 КНОПКИ ====================
                    val trackTitle = state.mediaTitle ?: "Тестовый трек играет"
                    val fullTitle = "🎵 $trackTitle"
                    val ellipsizedTitle = TextUtils.ellipsize(fullTitle, mediaTitlePaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(ellipsizedTitle, textStartX, capsuleRect.top + dp(38f), mediaTitlePaint)

                    // Неоновый интерактивный seekbar
                    val seekTop = capsuleRect.top + dp(44f)
                    val seekBottom = seekTop + dp(3f)
                    val seekW = maxTextWidth
                    val timeSec = System.currentTimeMillis() * 0.001f
                    val trackProgress = ((timeSec * 0.08f) % 1.0f)

                    // Фоновая дорожка
                    canvas.drawRoundRect(RectF(textStartX, seekTop, textStartX + seekW, seekBottom), dp(1.5f), dp(1.5f), neonTrackBgPaint)
                    // Неоновый заполненный отрезок
                    val fillW = seekW * trackProgress
                    canvas.drawRoundRect(RectF(textStartX, seekTop, textStartX + fillW, seekBottom), dp(1.5f), dp(1.5f), neonTrackFillPaint)
                    // Неоновая светящаяся точка
                    canvas.drawCircle(textStartX + fillW, (seekTop + seekBottom) / 2f, dp(3f), neonThumbPaint)

                    // Таймкоды трека (01:15 / 03:30)
                    val currentSecTotal = (trackProgress * 210f).toInt()
                    val curStr = String.format(java.util.Locale.US, "%02d:%02d", currentSecTotal / 60, currentSecTotal % 60)
                    val totalStr = "03:30"
                    mediaTimePaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(curStr, textStartX, seekBottom + dp(8.5f), mediaTimePaint)
                    mediaTimePaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(totalStr, textStartX + seekW, seekBottom + dp(8.5f), mediaTimePaint)

                    // 3 кнопки плеера прямо под seekbar: [ ⏮️ ], [ ⏯️ Пауза ], [ ⏭️ След. ]
                    val mediaBtnTop = capsuleRect.top + dp(58f)
                    val mediaBtnBottom = capsuleRect.top + dp(90f)
                    val availMediaW = capsuleRect.right - dp(14f) - textStartX
                    val mediaGap = dp(6f)

                    val prevW = dp(42f)
                    val nextW = dp(58f)
                    val playW = (availMediaW - prevW - nextW - mediaGap * 2).coerceAtLeast(dp(68f))

                    val p1L = textStartX
                    btnPrevRect.set(p1L, mediaBtnTop, p1L + prevW, mediaBtnBottom)

                    val p2L = btnPrevRect.right + mediaGap
                    btnPlayPauseRect.set(p2L, mediaBtnTop, p2L + playW, mediaBtnBottom)

                    val p3L = btnPlayPauseRect.right + mediaGap
                    btnNextRect.set(p3L, mediaBtnTop, p3L + nextW, mediaBtnBottom)

                    val mediaCorner = dp(8f)
                    val mOffset = (mediaBtnTextPaint.descent() + mediaBtnTextPaint.ascent()) / 2f

                    // [ ⏮️ ]
                    canvas.drawRoundRect(btnPrevRect, mediaCorner, mediaCorner, mediaBtnPaint)
                    canvas.drawRoundRect(btnPrevRect, mediaCorner, mediaCorner, mediaBtnBorderPaint)
                    canvas.drawText("⏮️", btnPrevRect.centerX(), btnPrevRect.centerY() - mOffset, mediaBtnTextPaint)

                    // [ ⏯️ Пауза / ▶️ Старт ]
                    val playPauseLabel = if (state.isMusicPlaying) "⏸️ Пауза" else "▶️ Старт"
                    canvas.drawRoundRect(btnPlayPauseRect, mediaCorner, mediaCorner, mediaBtnPaint)
                    canvas.drawRoundRect(btnPlayPauseRect, mediaCorner, mediaCorner, mediaBtnBorderPaint)
                    canvas.drawText(playPauseLabel, btnPlayPauseRect.centerX(), btnPlayPauseRect.centerY() - mOffset, mediaBtnTextPaint)

                    // [ ⏭️ След. ]
                    canvas.drawRoundRect(btnNextRect, mediaCorner, mediaCorner, mediaBtnPaint)
                    canvas.drawRoundRect(btnNextRect, mediaCorner, mediaCorner, mediaBtnBorderPaint)
                    canvas.drawText("⏭️ След.", btnNextRect.centerX(), btnNextRect.centerY() - mOffset, mediaBtnTextPaint)
                } else {
                    btnPrevRect.setEmpty()
                    btnPlayPauseRect.setEmpty()
                    btnNextRect.setEmpty()

                    if (!state.partnerNoteText.isNullOrBlank()) {
                        val noteText = TextUtils.ellipsize(state.partnerNoteText, pinkTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                        canvas.drawText(noteText, textStartX, capsuleRect.top + dp(48f), pinkTextPaint)

                        val subText = state.lastNotificationText ?: "Питомец получил записку!"
                        val ellipsizedSub = TextUtils.ellipsize(subText, subWhitePaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                        canvas.drawText(ellipsizedSub, textStartX, capsuleRect.top + dp(68f), subWhitePaint)
                    } else if (!state.partnerStatusText.isNullOrBlank()) {
                        val partnerText = TextUtils.ellipsize(state.partnerStatusText, pinkTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                        canvas.drawText(partnerText, textStartX, capsuleRect.top + dp(48f), pinkTextPaint)

                        val subText = state.lastNotificationText ?: "Питомец на связи"
                        val ellipsizedSub = TextUtils.ellipsize(subText, subWhitePaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                        canvas.drawText(ellipsizedSub, textStartX, capsuleRect.top + dp(68f), subWhitePaint)
                    } else {
                        val mainText = state.lastNotificationText ?: "Питомец готов к игре!"
                        val ellipsizedMain = TextUtils.ellipsize(mainText, whiteTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                        canvas.drawText(ellipsizedMain, textStartX, capsuleRect.top + dp(48f), whiteTextPaint)

                        val subText = "Покорми или отправь привет напарнику"
                        val ellipsizedSub = TextUtils.ellipsize(subText, subWhitePaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                        canvas.drawText(ellipsizedSub, textStartX, capsuleRect.top + dp(68f), subWhitePaint)
                    }
                }

                // ==================== НИЖНИЙ РЯД ДЕЙСТВИЙ ====================
                // [ 🍔 Кормить ], [ 👋 Привет ], [ 🐾 В гости ]
                val btnTop = capsuleRect.bottom - dp(46f)
                val btnBottom = capsuleRect.bottom - dp(10f)
                val btnPaddingH = dp(12f)
                val btnSpacing = dp(8f)
                val totalAvailW = capsuleW - (btnPaddingH * 2) - (btnSpacing * 2)
                val eachBtnW = totalAvailW / 3f

                val b1L = capsuleRect.left + btnPaddingH
                btnFeedRect.set(b1L, btnTop, b1L + eachBtnW, btnBottom)

                val b2L = btnFeedRect.right + btnSpacing
                btnPokeRect.set(b2L, btnTop, b2L + eachBtnW, btnBottom)

                val b3L = btnPokeRect.right + btnSpacing
                btnVisitRect.set(b3L, btnTop, b3L + eachBtnW, btnBottom)

                val btnCornerRadius = dp(11f)
                canvas.drawRoundRect(btnFeedRect, btnCornerRadius, btnCornerRadius, btnPaint)
                canvas.drawRoundRect(btnFeedRect, btnCornerRadius, btnCornerRadius, btnBorderPaint)

                canvas.drawRoundRect(btnPokeRect, btnCornerRadius, btnCornerRadius, btnPaint)
                canvas.drawRoundRect(btnPokeRect, btnCornerRadius, btnCornerRadius, btnBorderPaint)

                canvas.drawRoundRect(btnVisitRect, btnCornerRadius, btnCornerRadius, btnPaint)
                canvas.drawRoundRect(btnVisitRect, btnCornerRadius, btnCornerRadius, btnBorderPaint)

                val textBaselineOffset = (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f
                canvas.drawText("🍔 Кормить", btnFeedRect.centerX(), btnFeedRect.centerY() - textBaselineOffset, btnTextPaint)
                canvas.drawText("👋 Привет", btnPokeRect.centerX(), btnPokeRect.centerY() - textBaselineOffset, btnTextPaint)
                canvas.drawText("🐾 В гости", btnVisitRect.centerX(), btnVisitRect.centerY() - textBaselineOffset, btnTextPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val state = IslandStateBus.state.value

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!capsuleRect.contains(event.x, event.y)) {
                    return false
                }
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!capsuleRect.contains(downX, downY) && !capsuleRect.contains(event.x, event.y)) {
                    return false
                }

                val dx = event.x - downX
                val dt = System.currentTimeMillis() - downTime

                if (!state.isExpanded) {
                    // СВЕРНУТЫЙ РЕЖИМ: свайп бросает мячик / клубок, одиночный тап раскрывает панель
                    if (abs(dx) > dp(14f) && dt < 650L) {
                        startFetchGame(throwRight = (dx > 0))
                    } else if (capsuleRect.contains(event.x, event.y)) {
                        vibrateClick()
                        IslandStateBus.setExpanded(true)
                        scheduleAutoCollapse(5000L)
                    }
                } else {
                    // РАСКРЫТЫЙ РЕЖИМ
                    when {
                        // Кнопки управления плеером
                        btnPrevRect.contains(event.x, event.y) || btnPrevRect.contains(downX, downY) -> {
                            vibrateClick()
                            scheduleAutoCollapse(4000L)
                            MediaControlHelper.prevTrack(context)
                        }
                        btnPlayPauseRect.contains(event.x, event.y) || btnPlayPauseRect.contains(downX, downY) -> {
                            vibrateClick()
                            scheduleAutoCollapse(4000L)
                            MediaControlHelper.playPause(context)
                        }
                        btnNextRect.contains(event.x, event.y) || btnNextRect.contains(downX, downY) -> {
                            vibrateClick()
                            scheduleAutoCollapse(4000L)
                            MediaControlHelper.nextTrack(context)
                        }

                        // Кнопки заявки в друзья
                        !state.incomingFriendRequestFrom.isNullOrBlank() && (btnAcceptRect.contains(event.x, event.y) || btnAcceptRect.contains(downX, downY)) -> {
                            vibrateClick()
                            IslandStateBus.setIncomingRequest(null)
                            FirebaseCoupleManager.sendPartnerAction("ACCEPT_FRIEND")
                            scheduleAutoCollapse(2000L)
                        }
                        !state.incomingFriendRequestFrom.isNullOrBlank() && (btnDeclineRect.contains(event.x, event.y) || btnDeclineRect.contains(downX, downY)) -> {
                            vibrateClick()
                            IslandStateBus.setIncomingRequest(null)
                            FirebaseCoupleManager.sendPartnerAction("DECLINE_FRIEND")
                            scheduleAutoCollapse(2000L)
                        }

                        // Нижний ряд кнопок действий
                        btnFeedRect.contains(event.x, event.y) || btnFeedRect.contains(downX, downY) -> {
                            vibrateClick()
                            IslandStateBus.feedPet()
                            FirebaseCoupleManager.sendPartnerAction("FEED")
                            scheduleAutoCollapse(2500L)
                        }
                        btnPokeRect.contains(event.x, event.y) || btnPokeRect.contains(downX, downY) -> {
                            vibrateClick()
                            val partnerName = state.partnerUsername.ifBlank { "напарник" }
                            IslandStateBus.triggerPokeWave(partnerName)
                            FirebaseCoupleManager.sendPartnerAction("HEART")
                            scheduleAutoCollapse(2500L)
                        }
                        btnVisitRect.contains(event.x, event.y) || btnVisitRect.contains(downX, downY) -> {
                            vibrateClick()
                            IslandStateBus.triggerPartnerVisit()
                            FirebaseCoupleManager.sendPartnerAction("VISIT")
                            scheduleAutoCollapse(2500L)
                        }
                        else -> {
                            // Тапнули мимо кнопок по карточке -> моментально закрываем!
                            vibrateClick()
                            cancelAutoCollapse()
                            IslandStateBus.setExpanded(false)
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun vibrateClick() {
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
                    vibrator.vibrate(VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(25L)
                }
            }
        } catch (ignored: Exception) {
        }
    }
}

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
    private val eqPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E676") }
    private val eqPaint3 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF1744") }

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

            // Левый индикатор активности
            if (state.petMode == PetMode.MUSIC || state.isMusicPlaying) {
                val barW = dp(3f) * state.islandScale
                val barGap = dp(2f) * state.islandScale
                val eqStartX = capsuleRect.left + dp(14f) * state.islandScale
                val centerY = capsuleRect.centerY()

                val h1 = dp(4f + ((frameIndex * 3) % 9)) * state.islandScale
                val h2 = dp(3f + ((frameIndex * 5 + 4) % 11)) * state.islandScale
                val h3 = dp(5f + ((frameIndex * 2 + 3) % 8)) * state.islandScale

                canvas.drawRoundRect(RectF(eqStartX, centerY - h1 / 2f, eqStartX + barW, centerY + h1 / 2f), barW / 2f, barW / 2f, eqPaint1)
                canvas.drawRoundRect(RectF(eqStartX + barW + barGap, centerY - h2 / 2f, eqStartX + barW * 2 + barGap, centerY + h2 / 2f), barW / 2f, barW / 2f, eqPaint2)
                canvas.drawRoundRect(RectF(eqStartX + (barW + barGap) * 2, centerY - h3 / 2f, eqStartX + (barW + barGap) * 2 + barW, centerY + h3 / 2f), barW / 2f, barW / 2f, eqPaint3)
            } else if (state.petMode == PetMode.NOTIFICATION) {
                val dotRadius = dp(4.5f) * state.islandScale
                val dotX = capsuleRect.left + dp(16f) * state.islandScale
                val dotY = capsuleRect.centerY()
                canvas.drawCircle(dotX, dotY, dotRadius, notificationCirclePaint)
            } else if (state.petMode == PetMode.CHARGING) {
                val boltX = capsuleRect.left + dp(14f) * state.islandScale
                val boltY = capsuleRect.centerY() - dp(6f) * state.islandScale
                renderer.drawBrick(canvas, boltX, boltY, dp(4f) * state.islandScale, Color.parseColor("#00E676"))
                renderer.drawBrick(canvas, boltX + dp(3f) * state.islandScale, boltY + dp(5f) * state.islandScale, dp(4f) * state.islandScale, Color.parseColor("#FFD700"))
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
                    val baseMyRight = capsuleRect.right - dp(10f) * state.islandScale
                    val minX = capsuleRect.left + dp(28f) * state.islandScale
                    val maxX = capsuleRect.right - dp(6f) * state.islandScale

                    val clampedX = if (state.isHangingOnEdge) {
                        if (state.tiltOffsetPx < 0) capsuleRect.left - petSize * 0.25f else capsuleRect.right - petSize * 0.75f
                    } else {
                        (baseMyRight - petSize + state.tiltOffsetPx).coerceIn(minX, (maxX - petSize).coerceAtLeast(minX))
                    }

                    val myRect = RectF(clampedX, capsuleRect.centerY() - petSize / 2f, clampedX + petSize, capsuleRect.centerY() + petSize / 2f)
                    renderer.drawPet(canvas, myRect, state.petMode, frameIndex)
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
            val statsY = capsuleRect.top + dp(18f)
            val statsInfo = "🍖 ${state.hunger}%  ⚡ ${state.energy}%  💖 ${state.mood}%"
            statsTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(statsInfo, statsX, statsY, statsTextPaint)

            val levelInfo = "Lv.${state.petLevel} (${state.xp}/100 XP)"
            statsLevelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(levelInfo, statsX, statsY + dp(13f), statsLevelPaint)

            // 3D-питомец слева (в наушниках при музыке)
            val petSize = dp(56f)
            val petTop = capsuleRect.top + dp(14f)
            val myPetRect = RectF(capsuleRect.left + dp(14f), petTop, capsuleRect.left + dp(14f) + petSize, petTop + petSize)
            renderer.drawPet(canvas, myPetRect, state.petMode, frameIndex)

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
                    // ==================== РЕЖИМ МУЗЫКИ: НАЗВАНИЕ + 3 КНОПКИ ПЛЕЕРА ====================
                    val trackTitle = state.mediaTitle ?: "Тестовый трек играет"
                    val fullTitle = "🎵 $trackTitle"
                    val ellipsizedTitle = TextUtils.ellipsize(fullTitle, mediaTitlePaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(ellipsizedTitle, textStartX, capsuleRect.top + dp(46f), mediaTitlePaint)

                    // 3 кнопки плеера прямо под названием трека: [ ⏮️ ], [ ⏯️ Пауза ], [ ⏭️ След. ]
                    val mediaBtnTop = capsuleRect.top + dp(56f)
                    val mediaBtnBottom = capsuleRect.top + dp(88f)
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
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (!capsuleRect.contains(event.x, event.y)) {
                return false
            }

            val state = IslandStateBus.state.value
            if (!state.isExpanded) {
                vibrateClick()
                IslandStateBus.setExpanded(true)
            } else {
                when {
                    // Кнопки управления плеером
                    btnPrevRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        MediaControlHelper.prevTrack(context)
                    }
                    btnPlayPauseRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        MediaControlHelper.playPause(context)
                    }
                    btnNextRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        MediaControlHelper.nextTrack(context)
                    }

                    // Кнопки заявки в друзья
                    !state.incomingFriendRequestFrom.isNullOrBlank() && btnAcceptRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        IslandStateBus.setIncomingRequest(null)
                        FirebaseCoupleManager.sendPartnerAction("ACCEPT_FRIEND")
                    }
                    !state.incomingFriendRequestFrom.isNullOrBlank() && btnDeclineRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        IslandStateBus.setIncomingRequest(null)
                        FirebaseCoupleManager.sendPartnerAction("DECLINE_FRIEND")
                    }

                    // Нижний ряд кнопок действий
                    btnFeedRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        IslandStateBus.feedPet()
                        FirebaseCoupleManager.sendPartnerAction("FEED")
                    }
                    btnPokeRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        val partnerName = state.partnerUsername.ifBlank { "напарник" }
                        IslandStateBus.triggerPokeWave(partnerName)
                        FirebaseCoupleManager.sendPartnerAction("HEART")
                    }
                    btnVisitRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        IslandStateBus.triggerPartnerVisit()
                        FirebaseCoupleManager.sendPartnerAction("VISIT")
                    }
                    else -> {
                        vibrateClick()
                        IslandStateBus.setExpanded(false)
                    }
                }
            }
            return true
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

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

class IslandOverlayView(context: Context) : View(context) {

    private val renderer = PixelPetRenderer(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val capsuleRect = RectF()
    private val btnFeedRect = RectF()
    private val btnHeartRect = RectF()
    private val btnVisitRect = RectF()
    private val btnAcceptRect = RectF()
    private val btnDeclineRect = RectF()

    // Кисти для 3D оформления капсулы
    private val capsuleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#12151B")
        style = Paint.Style.FILL
    }

    private val capsuleHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#252B36")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F242D")
        style = Paint.Style.FILL
    }

    private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#323B49")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sp(11.5f)
        isFakeBoldText = true
    }

    private val acceptBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.FILL
    }

    private val acceptTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A2B14")
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        isFakeBoldText = true
    }

    private val declineBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A303C")
        style = Paint.Style.FILL
    }

    private val declineTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252")
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        isFakeBoldText = true
    }

    private val friendBannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D2533")
        style = Paint.Style.FILL
    }

    private val friendBannerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    private val friendBannerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = sp(12f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val statsLevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        textSize = sp(10.5f)
        isFakeBoldText = true
    }

    private val statsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E6ED")
        textSize = sp(10f)
        isFakeBoldText = true
    }

    private val pinkTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4081")
        textSize = sp(12.5f)
        isFakeBoldText = true
    }

    private val partnerNotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = sp(12f)
        isFakeBoldText = true
    }

    private val whiteTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(12f)
    }

    private val smallWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(10f)
    }

    private val doomBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        style = Paint.Style.FILL
    }

    private val doomTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = sp(11f)
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

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthPx = dp(320f).toInt()
        val heightPx = dp(150f).toInt()
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
            if (state.petMode == PetMode.MUSIC) {
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

            // Правая часть: Кибер-Кот в стиле 3D-конструктора
            if (state.isMyPetAway) {
                val text = "В гостях..."
                val textW = smallWhitePaint.measureText(text)
                val textX = capsuleRect.right - dp(12f) * state.islandScale - textW
                val textY = capsuleRect.centerY() - (smallWhitePaint.descent() + smallWhitePaint.ascent()) / 2f
                canvas.drawText(text, textX, textY, smallWhitePaint)
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

                    // При свисании на краю капсулы
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
            // ==================== РАСКРЫТЫЙ РЕЖИМ (3D ПАНЕЛЬ УПРАВЛЕНИЯ) ====================
            val capsuleW = dp(304f)
            val capsuleH = dp(138f)
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

            // 3D-Кибер-Кот слева
            val petSize = dp(54f)
            val myPetRect = RectF(capsuleRect.left + dp(14f), capsuleRect.top + dp(12f), capsuleRect.left + dp(14f) + petSize, capsuleRect.top + dp(12f) + petSize)
            renderer.drawPet(canvas, myPetRect, state.petMode, frameIndex)

            var textStartX = myPetRect.right + dp(12f)
            if (state.hasGuestPet) {
                val guestRect = RectF(myPetRect.right + dp(6f), myPetRect.top, myPetRect.right + dp(6f) + petSize, myPetRect.bottom)
                renderer.drawGuestPet(canvas, guestRect, frameIndex)
                textStartX = guestRect.right + dp(10f)
            }

            val maxTextWidth = (capsuleRect.right - dp(14f) - textStartX).coerceAtLeast(dp(60f))

            // ==================== БАННЕР ЗАЯВКИ В ДРУЗЬЯ (ЕСЛИ ЕСТЬ) ====================
            val hasFriendRequest = !state.incomingFriendRequestFrom.isNullOrBlank()
            if (hasFriendRequest) {
                val reqBannerRect = RectF(textStartX, capsuleRect.top + dp(36f), capsuleRect.right - dp(14f), capsuleRect.top + dp(78f))
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

                btnFeedRect.setEmpty()
                btnHeartRect.setEmpty()
                btnVisitRect.setEmpty()
            } else {
                btnAcceptRect.setEmpty()
                btnDeclineRect.setEmpty()

                // Центральная информационная область
                if (!state.doomscrollWarning.isNullOrBlank()) {
                    val bannerTop = capsuleRect.top + dp(36f)
                    val bannerBottom = capsuleRect.top + dp(68f)
                    val bannerRect = RectF(textStartX, bannerTop, capsuleRect.right - dp(14f), bannerBottom)
                    canvas.drawRoundRect(bannerRect, dp(8f), dp(8f), doomBgPaint)

                    val doomText = TextUtils.ellipsize(state.doomscrollWarning, doomTextPaint, bannerRect.width() - dp(8f), TextUtils.TruncateAt.END).toString()
                    val doomBaseline = bannerRect.centerY() - (doomTextPaint.descent() + doomTextPaint.ascent()) / 2f
                    canvas.drawText(doomText, bannerRect.centerX(), doomBaseline, doomTextPaint)
                } else if (!state.partnerNoteText.isNullOrBlank()) {
                    val noteText = TextUtils.ellipsize(state.partnerNoteText, partnerNotePaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(noteText, textStartX, capsuleRect.top + dp(46f), partnerNotePaint)

                    val subText = state.mediaTitle ?: state.lastNotificationText ?: "Кибер-Кот активен"
                    val ellipsizedSub = TextUtils.ellipsize(subText, whiteTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(ellipsizedSub, textStartX, capsuleRect.top + dp(66f), whiteTextPaint)
                } else if (!state.partnerStatusText.isNullOrBlank()) {
                    val partnerText = TextUtils.ellipsize(state.partnerStatusText, pinkTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(partnerText, textStartX, capsuleRect.top + dp(46f), pinkTextPaint)

                    val subText = state.mediaTitle ?: state.lastNotificationText ?: "Кибер-Кот активен"
                    val ellipsizedSub = TextUtils.ellipsize(subText, whiteTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(ellipsizedSub, textStartX, capsuleRect.top + dp(66f), whiteTextPaint)
                } else {
                    val mainText = state.mediaTitle ?: state.lastNotificationText ?: "Кибер-Кот готов к игре!"
                    val ellipsizedMain = TextUtils.ellipsize(mainText, whiteTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(ellipsizedMain, textStartX, capsuleRect.top + dp(54f), whiteTextPaint)
                }

                // 3 нижние кнопки управления
                val btnTop = capsuleRect.bottom - dp(46f)
                val btnBottom = capsuleRect.bottom - dp(10f)
                val btnPaddingH = dp(12f)
                val btnSpacing = dp(8f)
                val totalAvailW = capsuleW - (btnPaddingH * 2) - (btnSpacing * 2)
                val eachBtnW = totalAvailW / 3f

                val b1L = capsuleRect.left + btnPaddingH
                btnFeedRect.set(b1L, btnTop, b1L + eachBtnW, btnBottom)

                val b2L = btnFeedRect.right + btnSpacing
                btnHeartRect.set(b2L, btnTop, b2L + eachBtnW, btnBottom)

                val b3L = btnHeartRect.right + btnSpacing
                btnVisitRect.set(b3L, btnTop, b3L + eachBtnW, btnBottom)

                val btnCornerRadius = dp(12f)
                canvas.drawRoundRect(btnFeedRect, btnCornerRadius, btnCornerRadius, btnPaint)
                canvas.drawRoundRect(btnFeedRect, btnCornerRadius, btnCornerRadius, btnBorderPaint)

                canvas.drawRoundRect(btnHeartRect, btnCornerRadius, btnCornerRadius, btnPaint)
                canvas.drawRoundRect(btnHeartRect, btnCornerRadius, btnCornerRadius, btnBorderPaint)

                canvas.drawRoundRect(btnVisitRect, btnCornerRadius, btnCornerRadius, btnPaint)
                canvas.drawRoundRect(btnVisitRect, btnCornerRadius, btnCornerRadius, btnBorderPaint)

                val textBaselineOffset = (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f
                canvas.drawText("🍔 Кормить", btnFeedRect.centerX(), btnFeedRect.centerY() - textBaselineOffset, btnTextPaint)
                canvas.drawText("❤️ Погладить", btnHeartRect.centerX(), btnHeartRect.centerY() - textBaselineOffset, btnTextPaint)
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
                    // Обработка заявки в друзья
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
                    // Обработка 3 стандартных кнопок
                    btnFeedRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        IslandStateBus.feedPet()
                    }
                    btnHeartRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        IslandStateBus.playWithPet()
                        FirebaseCoupleManager.sendPartnerAction("HEART")
                    }
                    btnVisitRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        FirebaseCoupleManager.sendPartnerAction("VISIT")
                    }
                    else -> {
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

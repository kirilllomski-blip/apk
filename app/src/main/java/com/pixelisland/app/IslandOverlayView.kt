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

    private val capsulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        style = Paint.Style.FILL
    }

    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        isFakeBoldText = true
    }

    private val pinkTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF69B4")
        textSize = sp(13f)
        isFakeBoldText = true
    }

    private val partnerNotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
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

    private val skinStatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFA726")
        textSize = sp(9.5f)
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
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
    private val eqPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#76FF03") }
    private val eqPaint3 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF4081") }

    private var frameIndex = 0
    private val animHandler = Handler(Looper.getMainLooper())
    private var isAnimRunning = false

    private val feedHandler = Handler(Looper.getMainLooper())
    private var feedRunnable: Runnable? = null

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
        val heightPx = dp(140f).toInt()
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
        feedRunnable?.let { feedHandler.removeCallbacks(it) }
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
            // Свернутый режим
            val baseWidthDp = if (state.hasGuestPet) 180f else 140f
            val capsuleW = dp(baseWidthDp) * state.islandScale
            val capsuleH = dp(36f) * state.islandScale
            val left = (viewW - capsuleW) / 2f
            val top = dp(4f)
            val right = left + capsuleW
            val bottom = top + capsuleH
            capsuleRect.set(left, top, right, bottom)
            val cornerRadius = capsuleH / 2f

            canvas.drawRoundRect(capsuleRect, cornerRadius, cornerRadius, capsulePaint)

            // Левый индикатор
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
            }

            // Правая часть с питомцем (с учетом наклона tiltOffsetPx)
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
                    val minX = capsuleRect.left + dp(32f) * state.islandScale
                    val maxX = capsuleRect.right - dp(8f) * state.islandScale
                    val clampedX = (baseMyRight - petSize + state.tiltOffsetPx).coerceIn(minX, (maxX - petSize).coerceAtLeast(minX))
                    val myRect = RectF(clampedX, capsuleRect.centerY() - petSize / 2f, clampedX + petSize, capsuleRect.centerY() + petSize / 2f)

                    renderer.drawPet(canvas, myRect, state.petMode, frameIndex)
                }
            }
        } else {
            // Раскрытый режим: 300dp x 125dp
            val capsuleW = dp(300f)
            val capsuleH = dp(125f)
            val left = (viewW - capsuleW) / 2f
            val top = dp(4f)
            val right = left + capsuleW
            val bottom = top + capsuleH
            capsuleRect.set(left, top, right, bottom)
            val cornerRadius = dp(24f)

            canvas.drawRoundRect(capsuleRect, cornerRadius, cornerRadius, capsulePaint)

            // Статус кастомного скина в правом верхнем углу
            val skinStatusText = if (System.currentTimeMillis() < state.customPetExpireMs) {
                val remainingMs = state.customPetExpireMs - System.currentTimeMillis()
                val hours = remainingMs / (1000L * 60L * 60L)
                val mins = (remainingMs % (1000L * 60L * 60L)) / (1000L * 60L)
                "⏳ Скин: ${hours}ч ${mins}м"
            } else {
                "🔒 Свой скин: ${state.adsWatchedCount}/2 рекл."
            }
            canvas.drawText(skinStatusText, capsuleRect.right - dp(14f), capsuleRect.top + dp(18f), skinStatusPaint)

            // Питомец(-цы) слева сверху
            val petSize = dp(52f)
            val myPetRect = RectF(capsuleRect.left + dp(14f), capsuleRect.top + dp(12f), capsuleRect.left + dp(14f) + petSize, capsuleRect.top + dp(12f) + petSize)
            renderer.drawPet(canvas, myPetRect, state.petMode, frameIndex)

            var textStartX = myPetRect.right + dp(12f)
            if (state.hasGuestPet) {
                val guestRect = RectF(myPetRect.right + dp(6f), myPetRect.top, myPetRect.right + dp(6f) + petSize, myPetRect.bottom)
                renderer.drawGuestPet(canvas, guestRect, frameIndex)
                textStartX = guestRect.right + dp(10f)
            }

            // Центральная информационная зона
            val maxTextWidth = (capsuleRect.right - dp(14f) - textStartX).coerceAtLeast(dp(60f))

            if (!state.doomscrollWarning.isNullOrBlank()) {
                // Мемная плашка анти-думскроллинга
                val bannerTop = capsuleRect.top + dp(28f)
                val bannerBottom = capsuleRect.top + dp(56f)
                val bannerRect = RectF(textStartX, bannerTop, capsuleRect.right - dp(14f), bannerBottom)
                canvas.drawRoundRect(bannerRect, dp(6f), dp(6f), doomBgPaint)

                val doomText = TextUtils.ellipsize(state.doomscrollWarning, doomTextPaint, bannerRect.width() - dp(8f), TextUtils.TruncateAt.END).toString()
                val doomBaseline = bannerRect.centerY() - (doomTextPaint.descent() + doomTextPaint.ascent()) / 2f
                canvas.drawText(doomText, bannerRect.centerX(), doomBaseline, doomTextPaint)
            } else if (!state.partnerNoteText.isNullOrBlank()) {
                // Записка и заряд партнёра (голубой цвет #40C4FF)
                val noteText = TextUtils.ellipsize(state.partnerNoteText, partnerNotePaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                canvas.drawText(noteText, textStartX, capsuleRect.top + dp(28f), partnerNotePaint)

                val subText = state.mediaTitle ?: state.lastNotificationText ?: "Питомец отдыхает"
                val ellipsizedSub = TextUtils.ellipsize(subText, whiteTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                canvas.drawText(ellipsizedSub, textStartX, capsuleRect.top + dp(48f), whiteTextPaint)
            } else if (!state.partnerStatusText.isNullOrBlank()) {
                val partnerText = TextUtils.ellipsize(state.partnerStatusText, whiteTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                canvas.drawText(partnerText, textStartX, capsuleRect.top + dp(28f), pinkTextPaint)

                val subText = state.mediaTitle ?: state.lastNotificationText ?: "Питомец отдыхает"
                val ellipsizedSub = TextUtils.ellipsize(subText, whiteTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                canvas.drawText(ellipsizedSub, textStartX, capsuleRect.top + dp(48f), whiteTextPaint)
            } else {
                val mainText = state.mediaTitle ?: state.lastNotificationText ?: "Питомец отдыхает"
                val ellipsizedMain = TextUtils.ellipsize(mainText, whiteTextPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
                canvas.drawText(ellipsizedMain, textStartX, capsuleRect.top + dp(38f), whiteTextPaint)
            }

            // 3 кнопки снизу
            val btnTop = capsuleRect.bottom - dp(44f)
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

            val btnCornerRadius = dp(10f)
            canvas.drawRoundRect(btnFeedRect, btnCornerRadius, btnCornerRadius, btnPaint)
            canvas.drawRoundRect(btnHeartRect, btnCornerRadius, btnCornerRadius, btnPaint)
            canvas.drawRoundRect(btnVisitRect, btnCornerRadius, btnCornerRadius, btnPaint)

            val textBaselineOffset = (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f
            canvas.drawText("Покормить", btnFeedRect.centerX(), btnFeedRect.centerY() - textBaselineOffset, btnTextPaint)
            canvas.drawText("❤️ Пара", btnHeartRect.centerX(), btnHeartRect.centerY() - textBaselineOffset, btnTextPaint)
            canvas.drawText("В гости", btnVisitRect.centerX(), btnVisitRect.centerY() - textBaselineOffset, btnTextPaint)
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
                    btnFeedRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        feedRunnable?.let { feedHandler.removeCallbacks(it) }
                        IslandStateBus.setPetMode(PetMode.HAPPY)
                        val runnable = Runnable { IslandStateBus.recalculatePetMode() }
                        feedRunnable = runnable
                        feedHandler.postDelayed(runnable, 3000L)
                    }
                    btnHeartRect.contains(event.x, event.y) -> {
                        vibrateClick()
                        FirebaseCoupleManager.sendPartnerAction("HEART")
                        IslandStateBus.setPetMode(PetMode.HAPPY)
                        feedRunnable?.let { feedHandler.removeCallbacks(it) }
                        val runnable = Runnable { IslandStateBus.recalculatePetMode() }
                        feedRunnable = runnable
                        feedHandler.postDelayed(runnable, 3000L)
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

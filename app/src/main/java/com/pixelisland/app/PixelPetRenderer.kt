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

class PixelPetRenderer(private val context: Context) {

    private val pixelPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }

    private val guestPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
        colorFilter = PorterDuffColorFilter(Color.rgb(180, 220, 255), PorterDuff.Mode.MULTIPLY)
    }

    private val sweatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.FILL
    }

    private val sweatTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val spriteMap = mutableMapOf<PetMode, List<Bitmap>>()

    @Suppress("DEPRECATION")
    private var customMovie: Movie? = null
    private var customBitmap: Bitmap? = null
    private var loadedVersion: Int = -1

    init {
        generateProceduralSprites()
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

    private fun generateProceduralSprites() {
        val palette = mapOf(
            '.' to Color.TRANSPARENT,
            'W' to Color.WHITE,
            'G' to Color.rgb(200, 200, 200),
            'P' to Color.rgb(255, 175, 189),
            'B' to Color.rgb(30, 30, 30),
            'H' to Color.rgb(255, 64, 129),
            'C' to Color.rgb(0, 229, 255),
            'D' to Color.rgb(0, 140, 200),
            'L' to Color.rgb(76, 217, 100),
            'Y' to Color.rgb(255, 235, 59),
            'Z' to Color.rgb(170, 205, 255)
        )

        val idle0 = listOf(
            "................",
            "................",
            "................",
            "................",
            "....W......W....",
            "...WPW....WPW...",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WW.BWWWW.BWW..",
            "..WW.BWWWW.BWW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            "...WWWWWWWWWW.W.",
            "...WWWWWWWWWWWW.",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val idle1 = listOf(
            "................",
            "................",
            "................",
            "................",
            "...WW......W....",
            "...WPW....WPW...",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WW.BWWWW.BWW..",
            "..WW.BWWWW.BWW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            ".W.WWWWWWWWWW...",
            ".WWWWWWWWWWWW...",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val sleep0 = listOf(
            "..........ZZZZ..",
            ".............Z..",
            "............Z...",
            "...........ZZZZ.",
            "....W......W....",
            "...WPW....WPW...",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WWBBWWWWBBWW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            "...WWWWWWWWWW...",
            "...WWWWWWWWWWWW.",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val sleep1 = listOf(
            "........ZZZZ....",
            "...........Z....",
            "..........Z..zz.",
            ".........ZZZZ.z.",
            "....W......W.zz.",
            "...WPW....WPW...",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WWBBWWWWBBWW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            "...WWWWWWWWWW...",
            "...WWWWWWWWWWWW.",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val music0 = listOf(
            ".....DDDDDD.....",
            "....D......D....",
            "...D..W..W..D...",
            "...D.WP..PW.D...",
            "..CCWWWWWWWWCC..",
            "..CCWWWWWWWWCC..",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WW.BWWWW.BWW..",
            "..WW.BWWWW.BWW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            "...WWWWWWWWWW.W.",
            "...WWWWWWWWWWWW.",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val music1 = listOf(
            "....DDDDDD......",
            "...D......D...C.",
            "..D...W..W.D..C.",
            "..D..WP..PW.D.CC",
            "..CCWWWWWWWWCC..",
            "..CCWWWWWWWWCC..",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WW.BWWWW.BWW..",
            "..WW.BWWWW.BWW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            ".W.WWWWWWWWWW...",
            ".WWWWWWWWWWWW...",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val charging0 = listOf(
            "............LL..",
            "...........LL...",
            "..........LL....",
            ".........LLLL...",
            "....W.....LL....",
            "...WPW...LL.....",
            "..WWWWWWLLWWWW..",
            "..WWWWWW.LWWWW..",
            "..WW.BWWWW.BWW..",
            "..WW.BWWWW.BWW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            "...WWWWWWWWWW.W.",
            "...WWWWWWWWWWWW.",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val charging1 = listOf(
            "............YY..",
            "...........YY...",
            "..........YY....",
            ".........YYYY...",
            "....W.....YY....",
            "...WPW...YY.....",
            "..WWWWWWYYWWWW..",
            "..WWWWWW.YWWWW..",
            "..WW.BWWWW.BWW..",
            "..WW.BWWWW.BWW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            ".W.WWWWWWWWWW...",
            ".WWWWWWWWWWWW...",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val happy0 = listOf(
            "......HH..HH....",
            ".....HHHHHHHH...",
            "......HHHHHH....",
            ".......HHHH.....",
            "....W...HH.W....",
            "...WPW....WPW...",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WW.BWWWW.BWW..",
            "..WWB.BWWWB.BW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            "...WWWWWWWWWW.W.",
            "...WWWWWWWWWWWW.",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val happy1 = listOf(
            ".....HHH..HHH...",
            "....HHHHHHHHHH..",
            ".....HHHHHHHH...",
            "......HHHHHH....",
            "....W..HHHHW....",
            "...WPW....WPW...",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WW.BWWWW.BWW..",
            "..WWB.BWWWB.BW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            ".W.WWWWWWWWWW...",
            ".WWWWWWWWWWWW...",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val notification0 = listOf(
            "......HH..HH....",
            ".....HHHHHHHH...",
            "......HHHHHH....",
            ".......HHHH.....",
            "....W...HH.W....",
            "...WPW....WPW...",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WW.BWWWW.BWW..",
            "..WW.BWWWW.BWW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            "...WWWWWWWWWW.W.",
            "...WWWWWWWWWWWW.",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        val notification1 = listOf(
            ".....HHH..HHH...",
            "....HHHHHHHHHH..",
            ".....HHHHHHHH...",
            "......HHHHHH....",
            "....W..HHHHW....",
            "...WPW....WPW...",
            "..WWWWWWWWWWWW..",
            "..WWWWWWWWWWWW..",
            "..WW.BWWWW.BWW..",
            "..WW.BWWWW.BWW..",
            "..WWWWWPWWWWWW..",
            "...WWWWWWWWWW...",
            ".W.WWWWWWWWWW...",
            ".WWWWWWWWWWWW...",
            "...WWWWWWWWWW...",
            "...GG......GG..."
        )

        spriteMap[PetMode.IDLE] = listOf(createSprite(idle0, palette), createSprite(idle1, palette))
        spriteMap[PetMode.SLEEP] = listOf(createSprite(sleep0, palette), createSprite(sleep1, palette))
        spriteMap[PetMode.MUSIC] = listOf(createSprite(music0, palette), createSprite(music1, palette))
        spriteMap[PetMode.CHARGING] = listOf(createSprite(charging0, palette), createSprite(charging1, palette))
        spriteMap[PetMode.HAPPY] = listOf(createSprite(happy0, palette), createSprite(happy1, palette))
        spriteMap[PetMode.NOTIFICATION] = listOf(createSprite(notification0, palette), createSprite(notification1, palette))
    }

    private fun createSprite(pattern: List<String>, palette: Map<Char, Int>): Bitmap {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) {
            val row = if (y < pattern.size) pattern[y] else ""
            for (x in 0 until 16) {
                val char = if (x < row.length) row[x] else '.'
                val color = palette[char] ?: Color.TRANSPARENT
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    fun drawPet(canvas: Canvas, dstRect: RectF, mode: PetMode, frameIndex: Int) {
        val state = IslandStateBus.state.value
        reloadCustomPetIfNeeded(state.customPetVersion)

        val canUseCustom = (System.currentTimeMillis() < state.customPetExpireMs) &&
                (customMovie != null || customBitmap != null)

        canvas.save()

        if (state.isHangingOnEdge) {
            val rot = if (state.tiltOffsetPx < 0) -25f else 25f
            canvas.rotate(rot, dstRect.centerX(), dstRect.centerY())
        }

        if (canUseCustom) {
            val movie = customMovie
            val bitmap = customBitmap

            if (movie != null) {
                @Suppress("DEPRECATION")
                val duration = movie.duration().let { if (it <= 0) 1000 else it }
                val time = (System.currentTimeMillis() % duration).toInt()
                @Suppress("DEPRECATION")
                movie.setTime(time)

                @Suppress("DEPRECATION")
                val movieW = movie.width().toFloat().let { if (it <= 0f) 128f else it }
                @Suppress("DEPRECATION")
                val movieH = movie.height().toFloat().let { if (it <= 0f) 128f else it }

                canvas.save()
                canvas.translate(dstRect.left, dstRect.top)
                canvas.scale(dstRect.width() / movieW, dstRect.height() / movieH)
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
        } else {
            // Стандартный процедурный пиксельный кот
            val frames = spriteMap[mode]
            if (frames != null && frames.isNotEmpty()) {
                val defaultCatBitmap = frames[frameIndex % 2]
                canvas.drawBitmap(defaultCatBitmap, null, dstRect, pixelPaint)
            }
        }

        if (state.isHangingOnEdge) {
            val sweatX = dstRect.centerX() + dstRect.width() * 0.32f
            val sweatY = dstRect.top - dstRect.height() * 0.12f
            val radius = dstRect.width() * 0.14f
            canvas.drawCircle(sweatX, sweatY, radius, sweatPaint)
            sweatTextPaint.textSize = radius * 1.5f
            val baseline = sweatY - (sweatTextPaint.descent() + sweatTextPaint.ascent()) / 2f
            canvas.drawText("!", sweatX, baseline, sweatTextPaint)
        }

        canvas.restore()
    }

    fun drawGuestPet(canvas: Canvas, dstRect: RectF, frameIndex: Int) {
        canvas.save()
        canvas.scale(-1f, 1f, dstRect.centerX(), dstRect.centerY())
        val bitmap = spriteMap[PetMode.HAPPY]?.get(frameIndex % 2)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, dstRect, guestPaint)
        }
        canvas.restore()
    }

    fun getFrameDelayMs(mode: PetMode): Long {
        return when (mode) {
            PetMode.SLEEP -> 800L
            PetMode.IDLE -> 450L
            else -> 180L
        }
    }
}

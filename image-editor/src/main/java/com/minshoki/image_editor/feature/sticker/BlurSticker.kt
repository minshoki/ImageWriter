package com.minshoki.image_editor.feature.sticker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.IntRange
import com.minshoki.core.util.bitmap.blur
import com.minshoki.core.util.bitmap.getCircledBitmap
import com.minshoki.image_editor.core.StickerType
import com.minshoki.image_editor.model.StickerJsonFileDataModel
import java.lang.ref.WeakReference

open class BlurSticker(
    override var drawable: Drawable? = null,
    private val customWidth: Int = -1, private val customHeight: Int = -1
) : Sticker() {
    private val realBounds: Rect

    init {
        if(customWidth != -1 && customHeight != -1) {
            realBounds = Rect(0, 0, customWidth, customHeight)
        } else {
            realBounds = Rect(0, 0, width, height)
        }
    }

    override fun setDrawable(drawable: Drawable): BlurSticker {
        this.drawable = drawable
        return this
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.concat(matrix)
        canvas.restore()
    }

    fun draw(canvas: Canvas, defaultCanvasBitmap: Bitmap, bitmapCache: HashMap<StickerView.BitmapCacheModel, WeakReference<Bitmap>>) {
        val bound = mappedBound

        val x = if(bound.left.toInt() < 0) {
            0
        } else bound.left.toInt()

        val y = if(bound.top.toInt() < 0) {
            0
        } else bound.top.toInt()

        val width = if(x + bound.width().toInt() > defaultCanvasBitmap.width) {
            defaultCanvasBitmap.width - x
        } else {
            bound.width().toInt()
        }

        val height = if(y + bound.height().toInt() > defaultCanvasBitmap.height) {
            defaultCanvasBitmap.height - y
        } else {
            bound.height().toInt()
        }

        val cache = StickerView.BitmapCacheModel(x, y, width, height)
        val cacheBitmap = bitmapCache.get(cache)?.get()
        if(cacheBitmap == null) {
            val leftOffset = if(bound.width() > defaultCanvasBitmap.width) 0 else if(bound.left.toInt() < 0) bound.left.toInt() else 0
            val topOffset = if(bound.height() > defaultCanvasBitmap.height) 0 else if(bound.top.toInt() < 0) bound.top.toInt() else 0
            if(height + (topOffset) <= 0) return
            if(width + (leftOffset) <= 0) return
            val new = Bitmap.createBitmap(defaultCanvasBitmap, x, y, width + (leftOffset), height + (topOffset))
            if(new.width <= 0 || new.height <= 0 || new.width/10 <= 0 || new.height/10 <= 0) {
                new.recycle()
                return
            }
            try {
                val blur = new.blur()
                val circleMosaic = blur.getCircledBitmap(widthOffset = bound.width().toInt(), heightOffset = bound.height().toInt(), bound = bound)
                canvas.drawBitmap(circleMosaic, x.toFloat(), y.toFloat(), null)
                bitmapCache[cache] = WeakReference(circleMosaic)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            canvas.drawBitmap(cacheBitmap, x.toFloat(), y.toFloat(), null)
        }

        draw(canvas)
    }

    override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int): BlurSticker {
        drawable?.alpha = alpha
        return this
    }

    override val width: Int
        get() = if(customWidth != -1) customWidth else drawable?.intrinsicWidth ?: 0
    override val height: Int
        get() = if(customHeight != -1) customHeight else drawable?.intrinsicHeight ?: 0


    override fun mapperSaveFileJsonDataModel(): StickerJsonFileDataModel {
        val floatArray = FloatArray(9)
        matrix.getValues(floatArray)
        return StickerJsonFileDataModel(
            type = StickerType.BLUR,
            matrixValues = floatArray,
            imageUrl = "",
            width = width,
            height = height
        )
    }

    override fun release() {
        super.release()
        if (drawable != null) {
            drawable = null
        }
    }
}
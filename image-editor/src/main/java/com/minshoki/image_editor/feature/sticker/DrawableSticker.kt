package com.minshoki.image_editor.feature.sticker

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.IntRange
import com.minshoki.image_editor.core.StickerType
import com.minshoki.image_editor.model.StickerJsonFileDataModel

open class DrawableSticker(
    override var drawable: Drawable?,
    private val url: String,
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

    override fun setDrawable(drawable: Drawable): DrawableSticker {
        this.drawable = drawable
        return this
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.concat(matrix)
        drawable?.bounds = realBounds
        drawable?.draw(canvas)
        canvas.restore()
    }

    override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int): DrawableSticker {
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
            type = StickerType.DRAWABLE,
            matrixValues = floatArray,
            imageUrl = url,
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
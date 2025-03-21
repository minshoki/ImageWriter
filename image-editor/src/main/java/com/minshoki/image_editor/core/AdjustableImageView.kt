package com.minshoki.image_editor.core

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

class AdjustableImageView : AppCompatImageView {
    private var adjustViewBounds = false

    private var onMeasureListener: ((width: Int, height: Int) -> Unit)? = null

    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    )

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr
    )

    override fun setAdjustViewBounds(adjustViewBounds: Boolean) {
        this.adjustViewBounds = adjustViewBounds
        super.setAdjustViewBounds(adjustViewBounds)
    }

    fun setOnMeasureListener(listener: (width: Int, height: Int) -> Unit) {
        onMeasureListener = listener
    }

    fun clearOnMeasureListener() {
        onMeasureListener = null
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (adjustViewBounds) {
            val drawable = getDrawable()
            if (drawable == null) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            val drawableWidth = drawable.intrinsicWidth
            val drawableHeight = drawable.intrinsicHeight
            if (drawableWidth == 0 || drawableHeight == 0) {
                // intrinsicWidth や intrinsicHeight の幅が0のときがあるので 0 じゃないかチェック。
                // Android 2.3系列でこの現象が発生することはなかったが、
                // Sony Tablet Sで試したところ、ImageViewにnullをセットすると、
                // Drawable自体はnullにならずに、幅・高さが0になりエラーになったので急遽対応した。
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            val heightSize = MeasureSpec.getSize(heightMeasureSpec)
            val widthSize = MeasureSpec.getSize(widthMeasureSpec)
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val paddingHorizontal = getPaddingLeft() + getPaddingRight()
            val paddingVertical = paddingTop + paddingBottom
            val coreWidthSize = widthSize - paddingHorizontal
            val coreHeightSize = heightSize - paddingVertical
            if (heightMode == MeasureSpec.EXACTLY && widthMode != MeasureSpec.EXACTLY) {
                val width = coreHeightSize * drawableWidth / drawableHeight + paddingHorizontal
                setMeasuredDimension(
                    min(width.toDouble(), widthSize.toDouble()).toInt(), heightSize
                )
                onMeasureListener?.let { it(min(width.toDouble(), widthSize.toDouble()).toInt(), heightSize) }
            } else if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                val height = coreWidthSize * drawableHeight / drawableWidth + paddingVertical
                setMeasuredDimension(
                    widthSize, min(height.toDouble(), heightSize.toDouble())
                        .toInt()
                )
                onMeasureListener?.let { it(widthSize, min(height.toDouble(), heightSize.toDouble()).toInt()) }
            } else {
                val widthScale = coreWidthSize.toDouble() / drawableWidth
                val heightScale = coreHeightSize.toDouble() / drawableHeight
                if (widthScale == heightScale) {
                    setMeasuredDimension(widthSize, heightSize)
                    onMeasureListener?.let { it(widthSize, heightSize) }
                } else if (widthScale < heightScale) {
                    setMeasuredDimension(
                        widthSize,
                        (drawableHeight * coreWidthSize / drawableWidth + paddingVertical)
                    )
                    onMeasureListener?.let { it( widthSize, (drawableHeight * coreWidthSize / drawableWidth + paddingVertical)) }
                } else {
                    setMeasuredDimension(
                        (drawableWidth * coreHeightSize / drawableHeight + paddingHorizontal),
                        heightSize
                    )
                    onMeasureListener?.let { it((drawableWidth * coreHeightSize / drawableHeight + paddingHorizontal), heightSize) }
                }
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
package com.minshoki.image_editor.core

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.MotionEventCompat
import kotlin.math.min

class TouchableAdjustableImageView : AppCompatImageView {
    private var adjustViewBounds = false

    private var onMeasureListener: ((width: Int, height: Int) -> Unit)? = null
    private var onTouchUpListener: ((array: FloatArray) -> Boolean)? = null
    var fixedWidth = 0
    var fixedHeight = 0

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

    fun setOnTouchUpListener(listener: (array: FloatArray) -> Boolean) {
        onTouchUpListener = listener
    }

    fun clearOnMeasureListener() {
        onMeasureListener = null
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return true
        val action = MotionEventCompat.getActionMasked(event)
        when (action) {
            MotionEvent.ACTION_UP -> {
                val x = event.x
                val y = event.y
                val tmp = FloatArray(2)
                tmp[0] = x
                tmp[1] = y

                Log.i("TouchableAdjustableImageView", "x $x y $y")
                return onTouchUpListener?.invoke(tmp) ?: true
            }
        }
        return true
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
                fixedWidth = min(width.toDouble(), widthSize.toDouble()).toInt()
                fixedHeight = heightSize
                onMeasureListener?.let {
                    it(
                        min(width.toDouble(), widthSize.toDouble()).toInt(),
                        heightSize
                    )
                }
            } else if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                val height = coreWidthSize * drawableHeight / drawableWidth + paddingVertical
                setMeasuredDimension(
                    widthSize, min(height.toDouble(), heightSize.toDouble())
                        .toInt()
                )
                fixedWidth = widthSize
                fixedHeight = min(height.toDouble(), heightSize.toDouble()).toInt()
                onMeasureListener?.let {
                    it(
                        widthSize,
                        min(height.toDouble(), heightSize.toDouble()).toInt()
                    )
                }
            } else {
                val widthScale = coreWidthSize.toDouble() / drawableWidth
                val heightScale = coreHeightSize.toDouble() / drawableHeight
                if (widthScale == heightScale) {
                    setMeasuredDimension(widthSize, heightSize)
                    fixedWidth = widthSize
                    fixedHeight = heightSize
                    onMeasureListener?.let { it(widthSize, heightSize) }
                } else if (widthScale < heightScale) {
                    setMeasuredDimension(
                        widthSize,
                        (drawableHeight * coreWidthSize / drawableWidth + paddingVertical)
                    )
                    fixedWidth = widthSize
                    fixedHeight = (drawableHeight * coreWidthSize / drawableWidth + paddingVertical)
                    onMeasureListener?.let {
                        it(
                            widthSize,
                            (drawableHeight * coreWidthSize / drawableWidth + paddingVertical)
                        )
                    }
                } else {
                    setMeasuredDimension(
                        (drawableWidth * coreHeightSize / drawableHeight + paddingHorizontal),
                        heightSize
                    )
                    fixedWidth = (drawableWidth * coreHeightSize / drawableHeight + paddingHorizontal)
                    fixedHeight = heightSize
                    onMeasureListener?.let {
                        it(
                            (drawableWidth * coreHeightSize / drawableHeight + paddingHorizontal),
                            heightSize
                        )
                    }
                }
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
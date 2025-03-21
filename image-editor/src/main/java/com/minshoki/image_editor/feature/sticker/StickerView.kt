package com.minshoki.image_editor.feature.sticker

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.annotation.IntDef
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewCompat
import com.minshoki.core.util.dp
import com.minshoki.image_editor.R
import com.minshoki.image_editor.core.ext.haptic
import java.io.File
import java.lang.ref.WeakReference
import java.util.Arrays
import java.util.Collections
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt


/**
 * Sticker View
 * @author wupanjie
 */
class StickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var showIcons = false
    private var showBorder = false
    private var bringToFrontCurrentSticker = false

    @IntDef(*[ActionMode.NONE, ActionMode.DRAG, ActionMode.ZOOM_WITH_TWO_FINGER, ActionMode.ICON, ActionMode.CLICK, ActionMode.DELETE])
    @Retention(AnnotationRetention.SOURCE)
    protected annotation class ActionMode {
        companion object {
            const val NONE = 0
            const val DRAG = 1
            const val ZOOM_WITH_TWO_FINGER = 2
            const val ICON = 3
            const val CLICK = 4
            const val DELETE = 7
        }
    }

    @IntDef(flag = true, value = [FLIP_HORIZONTALLY, FLIP_VERTICALLY])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Flip

    private val stickers: MutableList<Sticker> = ArrayList()
    private val icons: MutableList<BitmapStickerIcon> = ArrayList(4)
    private val borderPaint = Paint()
    private val stickerRect = RectF()
    private val sizeMatrix = Matrix()
    private val downMatrix = Matrix()
    private val moveMatrix = Matrix()

    // region storing variables
    private val bitmapPoints = FloatArray(8)
    private val bounds = FloatArray(8)
    private val point = FloatArray(2)
    private val currentCenterPoint = PointF()
    private val tmp = FloatArray(2)
    private var midPoint = PointF()

    // endregion
    private val touchSlop: Int
    private var currentIcon: BitmapStickerIcon? = null

    //the first point down position
    private var downX = 0f
    private var downY = 0f
    private var oldDistance = 0f
    private var oldRotation = 0f

    data class BitmapCacheModel(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    private val bitmapCache: HashMap<BitmapCacheModel, WeakReference<Bitmap>> = hashMapOf()

    @ActionMode
    private var currentMode = ActionMode.NONE

    private var isShowStickers: Boolean = true
    var currentSticker: Sticker? = null
        private set
    var isLocked = false
        private set
    var isConstrained = false
        private set
    var onStickerOperationListener: OnStickerOperationListener? = null
    private var lastClickTime: Long = 0
    var minClickDelayTime = DEFAULT_MIN_CLICK_DELAY_TIME
        private set

    private var defaultBackgroundImageBitmap: Bitmap? = null
    private var defaultCanvasBitmap: Bitmap? = null
    private var extractCanvas: Canvas? = null
    private var deleteIconRect: Rect? = null

    enum class HapticState {
        NONE, HAPTIC, ALREADY_HAPTIC
    }
    private var hapticState: HapticState = HapticState.NONE

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var a: TypedArray? = null
        try {
            a = context.obtainStyledAttributes(attrs, R.styleable.StickerView)
            showIcons = a.getBoolean(R.styleable.StickerView_showIcons, false)
            showBorder = a.getBoolean(R.styleable.StickerView_showBorder, true)
            bringToFrontCurrentSticker =
                a.getBoolean(R.styleable.StickerView_bringToFrontCurrentSticker, true)
            borderPaint.isAntiAlias = true
            borderPaint.setColor(a.getColor(R.styleable.StickerView_borderColor, Color.WHITE))
//            borderPaint.setAlpha(a.getInteger(R.styleable.StickerView_borderAlpha, 128))
            borderPaint.strokeWidth = 1.dp.toFloat()
            configDefaultIcons()
        } finally {
            a?.recycle()
        }
    }

    fun configDefaultIcons() {
        val deleteIcon = BitmapStickerIcon(
            ContextCompat.getDrawable(context, R.drawable.ic_android_black_24dp),
            BitmapStickerIcon.LEFT_TOP
        )
        deleteIcon.iconEvent = DeleteIconEvent()
        val zoomIcon = BitmapStickerIcon(
            ContextCompat.getDrawable(context, R.drawable.ic_android_black_24dp),
            BitmapStickerIcon.RIGHT_BOTOM
        )
        zoomIcon.iconEvent = ZoomIconEvent()
        val flipIcon = BitmapStickerIcon(
            ContextCompat.getDrawable(context, R.drawable.ic_android_black_24dp),
            BitmapStickerIcon.RIGHT_TOP
        )
        flipIcon.iconEvent = FlipHorizontallyEvent()
        icons.clear()
        icons.add(deleteIcon)
        icons.add(zoomIcon)
        icons.add(flipIcon)
    }

    fun setDefaultBackgroundImage(
        bitmap: Bitmap,
        customWidth: Int,
        customHeight: Int,
        offset: Int = 0,
        deleteIconRect: Rect
    ) {

        defaultCanvasBitmap =
            Bitmap.createBitmap(customWidth, customHeight, Bitmap.Config.ARGB_8888)
                .copy(Bitmap.Config.ARGB_8888, true)
        val originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        defaultBackgroundImageBitmap = originalBitmap

        extractCanvas = Canvas(defaultCanvasBitmap!!)
        extractCanvas!!.drawBitmap(originalBitmap, offset.toFloat(), 0f, null)

        this.deleteIconRect = deleteIconRect
    }


    /**
     * Swaps sticker at layer [[oldPos]] with the one at layer [[newPos]].
     * Does nothing if either of the specified layers doesn't exist.
     */
    fun swapLayers(oldPos: Int, newPos: Int) {
        if (stickers.size >= oldPos && stickers.size >= newPos) {
            Collections.swap(stickers, oldPos, newPos)
            invalidate()
        }
    }

    fun getStickers() = stickers

    /**
     * Sends sticker from layer [[oldPos]] to layer [[newPos]].
     * Does nothing if either of the specified layers doesn't exist.
     */
    fun sendToLayer(oldPos: Int, newPos: Int) {
        if (stickers.size >= oldPos && stickers.size >= newPos) {
            val s = stickers[oldPos]
            stickers.removeAt(oldPos)
            stickers.add(newPos, s)
            invalidate()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            stickerRect.left = left.toFloat()
            stickerRect.top = top.toFloat()
            stickerRect.right = right.toFloat()
            stickerRect.bottom = bottom.toFloat()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isShowStickers) {
            drawStickers(canvas)
        }
    }

    fun hideStickers() {
        isShowStickers = false
        invalidate()
    }

    fun showStickers() {
        isShowStickers = true
        invalidate()
    }

    fun selectSticker(sticker: Sticker) {
        stickers.remove(sticker)
        stickers.add(sticker!!)
        currentSticker = sticker
        invalidate()
    }


    protected fun drawStickers(canvas: Canvas) {
        for (i in stickers.indices) {
            val sticker = stickers[i]

            if (sticker is MosaicSticker) {
                defaultCanvasBitmap ?: return
                sticker.draw(canvas, defaultCanvasBitmap!!, bitmapCache)
            } else {
                sticker.draw(canvas)
            }

            if (sticker is BlurSticker) {
                defaultCanvasBitmap ?: return
                sticker.draw(canvas, defaultCanvasBitmap!!, bitmapCache)
            } else {
                sticker.draw(canvas)
            }
        }
        if (currentSticker != null && !isLocked && (showBorder || showIcons)) {
            getStickerPoints(currentSticker, bitmapPoints)
            val x1 = bitmapPoints[0]
            val y1 = bitmapPoints[1]
            val x2 = bitmapPoints[2]
            val y2 = bitmapPoints[3]
            val x3 = bitmapPoints[4]
            val y3 = bitmapPoints[5]
            val x4 = bitmapPoints[6]
            val y4 = bitmapPoints[7]
            val offset = (5.2f).dp
            if (showBorder) {
                canvas.drawLine(x1, y1, x2, y2, borderPaint)
                canvas.drawLine(x1, y1, x3, y3, borderPaint)
                canvas.drawLine(x2, y2, x4, y4, borderPaint)
                canvas.drawLine(x4, y4, x3, y3, borderPaint)
//                if(currentSticker is TextSticker) {
//                    canvas.drawLine(x1, y1, x2, y2, borderPaint)
//                    canvas.drawLine(x1, y1, x3, y3, borderPaint)
//                    canvas.drawLine(x2, y2, x4, y4, borderPaint)
//                    canvas.drawLine(x4, y4, x3, y3, borderPaint)
//                } else {
//                    canvas.drawLine(x1 - offset, y1 - offset, x2 + offset, y2 - offset, borderPaint)
//                    canvas.drawLine(x1 - offset, y1 - offset, x3 - offset, y3 + offset, borderPaint)
//                    canvas.drawLine(x2 + offset, y2 - offset, x4 + offset, y4 + offset, borderPaint)
//                    canvas.drawLine(x4 + offset, y4 + offset, x3 - offset, y3 + offset, borderPaint)
//                }
            }

            //draw icons
            if (showIcons) {
                val rotation = calculateRotation(x4, y4, x3, y3)
                for (i in icons.indices) {
                    val icon = icons[i]
                    when (icon.position) {
                        BitmapStickerIcon.LEFT_TOP -> configIconMatrix(
                            icon,
                            x1,
                            y1,
                            rotation
                        )

                        BitmapStickerIcon.RIGHT_TOP -> configIconMatrix(
                            icon,
                            x2,
                            y2,
                            rotation
                        )

                        BitmapStickerIcon.LEFT_BOTTOM -> configIconMatrix(
                            icon,
                            x3,
                            y3,
                            rotation
                        )

                        BitmapStickerIcon.RIGHT_BOTOM -> configIconMatrix(
                            icon,
                            x4,
                            y4,
                            rotation
                        )
                    }
                    icon.draw(canvas, borderPaint)
                }
            }
        }
    }


    protected fun configIconMatrix(
        icon: BitmapStickerIcon, x: Float, y: Float,
        rotation: Float
    ) {
        icon.x = x
        icon.y = y
        icon.matrix.reset()
        icon.matrix.postRotate(rotation, (icon.width / 2).toFloat(), (icon.height / 2).toFloat())
        icon.matrix.postTranslate(x - icon.width / 2, y - icon.height / 2)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isLocked) return super.onInterceptTouchEvent(ev)
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                if (currentMode == ActionMode.ZOOM_WITH_TWO_FINGER) {
                    return false
                }
                return findCurrentIconTouched() != null || findHandlingSticker() != null
            }
//            MotionEvent.ACTION_UP -> {
//                val result = findHandlingSticker(x = ev.x, y = ev.y) != null
//                Log.i("shokitest", "actionup ${findHandlingSticker()}")
//                if(result) {
//                    downX = ev.x
//                    downY = ev.y
//                }
//                return result
//            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    private var lastTimeForActionDown = 0L

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLocked) {
            return super.onTouchEvent(event)
        }
        val action = MotionEventCompat.getActionMasked(event)
        when (action) {
            MotionEvent.ACTION_DOWN -> if (!onTouchDown(event)) {
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if(currentSticker == null) return false

                oldDistance = calculateDistance(event)
                oldRotation = calculateRotation(event)
                midPoint = calculateMidPoint(event)
                var isInStickerArea = isInStickerArea(
                    currentSticker!!, event.getX(1),
                    event.getY(1)
                )
                if (findCurrentIconTouched() == null && !isInStickerArea) {
                    isInStickerArea = isInStickerArea(
                        currentSticker!!, event.getX(0),
                        event.getY(0)
                    )
                }
                if (currentSticker != null && isInStickerArea && findCurrentIconTouched() == null
                ) {
                    currentMode = ActionMode.ZOOM_WITH_TWO_FINGER
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if(lastTimeForActionDown != 0L) {
                    val diff = System.currentTimeMillis() - lastTimeForActionDown
                    if(diff >= 300) {
                        if(currentMode == ActionMode.DRAG || currentMode == ActionMode.CLICK || currentMode == ActionMode.ZOOM_WITH_TWO_FINGER) {
                            onStickerOperationListener?.showStickerDeleteArea()
                        }
                    }
                }
                handleCurrentMode(event)
                invalidate()
            }

            MotionEvent.ACTION_UP -> onTouchUp(event)
            MotionEvent.ACTION_POINTER_UP -> {
                if (currentMode == ActionMode.ZOOM_WITH_TWO_FINGER && currentSticker != null) {
                    if (onStickerOperationListener != null) {
                        onStickerOperationListener!!.onStickerZoomFinished(currentSticker!!)
                    }
                }
                lastTimeForActionDown = 0L
                currentMode = ActionMode.NONE
            }
        }
        return true
    }

    /**
     * @param event MotionEvent received from [)][.onTouchEvent]
     */
    protected fun onTouchDown(event: MotionEvent): Boolean {
        currentMode = ActionMode.DRAG
        downX = event.x
        downY = event.y
        midPoint = calculateMidPoint()
        oldDistance = calculateDistance(midPoint.x, midPoint.y, downX, downY)
        oldRotation = calculateRotation(midPoint.x, midPoint.y, downX, downY)
        currentIcon = findCurrentIconTouched()
        if (currentIcon != null) {
            currentMode = ActionMode.ICON
            currentIcon!!.onActionDown(this, event)
        } else {
            currentSticker = findHandlingSticker()
        }
        if (currentSticker != null) {
            lastTimeForActionDown = System.currentTimeMillis()
            downMatrix.set(currentSticker!!.matrix)
            if (bringToFrontCurrentSticker) {
                stickers.remove(currentSticker)
                stickers.add(currentSticker!!)
            }
            if (onStickerOperationListener != null) {
                onStickerOperationListener!!.onStickerTouchedDown(currentSticker!!)
            }
        }
        if (currentIcon == null && currentSticker == null) {
            lastTimeForActionDown = 0L
            return false
        }
        invalidate()
        return true
    }

    protected fun onTouchUp(event: MotionEvent) {
        lastTimeForActionDown = 0L
        val currentTime = SystemClock.uptimeMillis()
        if (currentMode == ActionMode.ICON && currentIcon != null && currentSticker != null) {
            currentIcon!!.onActionUp(this, event)
        }
        if (currentMode == ActionMode.DRAG && abs((event.x - downX).toDouble()) < touchSlop && abs((event.y - downY).toDouble()) < touchSlop && currentSticker != null) {
            currentMode = ActionMode.CLICK
            if (onStickerOperationListener != null) {
                onStickerOperationListener!!.onStickerClicked(currentSticker!!)
            }
            if (currentTime - lastClickTime < minClickDelayTime) {
                if (onStickerOperationListener != null) {
                    onStickerOperationListener!!.onStickerDoubleTapped(currentSticker!!)
                }
            }
        }
        if (currentMode == ActionMode.DRAG && currentSticker != null) {
            if (onStickerOperationListener != null) {
                onStickerOperationListener!!.onStickerDragFinished(currentSticker!!)
            }
        }
        if (currentMode == ActionMode.DELETE && currentSticker != null) {
            val completeRemove = removeCurrentSticker()
            if (completeRemove) {
                invalidate()
            }
        }
        lastTimeForActionDown = 0L
        currentMode = ActionMode.NONE
        lastClickTime = currentTime
    }

    private fun containsDeleteIcon(x: Int, y: Int): Boolean {
        deleteIconRect ?: return false
        return x >= deleteIconRect!!.left && x <= deleteIconRect!!.right && y >= deleteIconRect!!.top && y <= deleteIconRect!!.bottom
    }

    private var isRunningScaleDownAnimator = false
    private var isRunningScaleUpAnimator = false
    private fun animationScaleDown() {
        if(isRunningScaleDownAnimator) return
        if (currentSticker!!.width * currentSticker!!.getMatrixScale(moveMatrix) > LIMIT_STICKER_MIN_SIZE) {
            val newFactor =
                LIMIT_STICKER_MIN_SIZE / (currentSticker!!.getMatrixScale(moveMatrix) * currentSticker!!.width)

            val deleteIconCenterX = deleteIconRect!!.centerX().toFloat()
            val deleteIconCenterY = deleteIconRect!!.centerY().toFloat()

            val animator = ValueAnimator.ofFloat(1.0f, newFactor)
            animator.duration = 100L
            animator.interpolator = LinearInterpolator()
            val tempMatrix = Matrix()
            animator.addUpdateListener {
                isRunningScaleDownAnimator = true
                if(currentSticker == null) {
                    animator.cancel()
                    return@addUpdateListener
                }
                val value = it.animatedValue as Float
                tempMatrix.reset()
                tempMatrix.set(moveMatrix)
                tempMatrix.postScale(value, value, deleteIconCenterX, deleteIconCenterY)
                val tempSize = currentSticker!!.getMatrixScale(tempMatrix) * currentSticker!!.width
                if(tempSize < LIMIT_STICKER_MIN_SIZE) {
                    animator.cancel()
                    return@addUpdateListener
                } else {
                    moveMatrix.postScale(value, value, deleteIconCenterX, deleteIconCenterY)
                    currentSticker!!.setMatrix(moveMatrix)
                    invalidate()
                }
            }
            animator.doOnCancel {
                isRunningScaleDownAnimator = false
            }
            animator.doOnEnd {
                isRunningScaleDownAnimator = false
            }
            animator.start()
        }
    }

    private fun animationScaleUp(tempMatrix: Matrix, x: Float, y: Float) {
        if(isRunningScaleUpAnimator) {
            return
        }
        val currentWidth = currentSticker!!.getMatrixScale(tempMatrix) * currentSticker!!.width
        val scaleUpWidth = currentSticker!!.getMatrixScale(moveMatrix) * currentSticker!!.width
        if(currentWidth != scaleUpWidth) {
            val newFactor = if(currentWidth > scaleUpWidth) {
                scaleUpWidth / currentWidth
            } else {
                scaleUpWidth / currentWidth
            }
            val inTempMatrix = Matrix()
            val animator = ValueAnimator.ofFloat(1.0f, newFactor)
            animator.duration = 1000L
            animator.interpolator = LinearInterpolator()
            animator.addUpdateListener {
                isRunningScaleUpAnimator = true
                if(currentSticker == null) {
                    animator.cancel()
                    return@addUpdateListener
                }
                val value = it.animatedValue as Float

                inTempMatrix.reset()
                inTempMatrix.set(tempMatrix)
                inTempMatrix.postScale(value, value, x, y)
                val tempSize = currentSticker!!.getMatrixScale(inTempMatrix) * currentSticker!!.width
                if(tempSize >= scaleUpWidth) {
                    animator.cancel()
                    return@addUpdateListener
                } else {
                    moveMatrix.postScale(value, value, x, y)
                    currentSticker!!.setMatrix(moveMatrix)
                    invalidate()
                }
            }
            animator.doOnCancel {
                isRunningScaleUpAnimator = false
            }
            animator.doOnEnd {
                isRunningScaleUpAnimator = false
            }
            animator.start()
        } else {
            currentSticker!!.setMatrix(moveMatrix)
        }
    }


    protected fun handleCurrentMode(event: MotionEvent) {
        when (currentMode) {
            ActionMode.NONE, ActionMode.CLICK -> {}
            ActionMode.DRAG, ActionMode.DELETE -> if (currentSticker != null) {
                val scaleTempMatrix = Matrix()
                scaleTempMatrix.set(moveMatrix)
                moveMatrix.set(downMatrix)
                moveMatrix.postTranslate(event.x - downX, event.y - downY)
                if (containsDeleteIcon(x = event.x.toInt(), y = event.y.toInt())) {
                    onStickerOperationListener?.hasStickerEnteredDeletionArea()
                    currentMode = ActionMode.DELETE
                    if(hapticState == HapticState.NONE) {
                        hapticState = HapticState.HAPTIC
                    }
                    if(hapticState == HapticState.HAPTIC) {
                        animationScaleDown()
                        this.haptic()
                        hapticState = HapticState.ALREADY_HAPTIC
                    } else {
                        if(isRunningScaleDownAnimator.not()) {
                            val newFactor =
                                LIMIT_STICKER_MIN_SIZE / (currentSticker!!.getMatrixScale(moveMatrix) * currentSticker!!.width)

                            val deleteIconCenterX = deleteIconRect!!.centerX().toFloat()
                            val deleteIconCenterY = deleteIconRect!!.centerY().toFloat()
                            moveMatrix.postScale(newFactor, newFactor, deleteIconCenterX, deleteIconCenterY)
                            currentSticker!!.setMatrix(moveMatrix)
                        }
                    }
                } else {
                    onStickerOperationListener?.notStickerEnterDeletionArea()
//                    animationScaleUp(scaleTempMatrix, event.x, event.y)
                    currentSticker!!.setMatrix(moveMatrix)

                    if (currentMode == ActionMode.DELETE) {
                        currentMode = ActionMode.DRAG
                    }
                    hapticState = HapticState.NONE
                }

                if (isConstrained) {
                    constrainSticker(currentSticker!!)
                }

                checkImageInsideSticker(currentSticker!!)
            }

            ActionMode.ZOOM_WITH_TWO_FINGER -> if (currentSticker != null) {
                val newDistance = calculateDistance(event)
                val newRotation = calculateRotation(event)
                val tmpMatrix = Matrix()
                tmpMatrix.set(downMatrix)
                tmpMatrix.preScale(
                    newDistance / oldDistance, newDistance / oldDistance, midPoint.x,
                    midPoint.y
                )
                moveMatrix.set(downMatrix)
                moveMatrix.postScale(
                    newDistance / oldDistance, newDistance / oldDistance, midPoint.x,
                    midPoint.y
                )
                if (currentSticker !is MosaicSticker && currentSticker !is BlurSticker) {
                    moveMatrix.postRotate(newRotation - oldRotation, midPoint.x, midPoint.y)
                }
//                if (currentSticker!!.width * currentSticker!!.getMatrixScale(moveMatrix) > 500f) {
//                    val newFactor = 500f/(currentSticker!!.getMatrixScale(tmpMatrix) * currentSticker!!.width)
//                    moveMatrix.postScale(newFactor, newFactor, midPoint.x, midPoint.y)
//                    currentSticker!!.setMatrix(moveMatrix)
//                    return
//                }
                if (currentSticker!!.width * currentSticker!!.getMatrixScale(moveMatrix) < LIMIT_STICKER_MIN_SIZE) {
                    val newFactor =
                        LIMIT_STICKER_MIN_SIZE / (currentSticker!!.getMatrixScale(tmpMatrix) * currentSticker!!.width)
                    moveMatrix.postScale(newFactor, newFactor, midPoint.x, midPoint.y)
                    currentSticker!!.setMatrix(moveMatrix)
                    return
                }

                if (currentSticker!!.width * currentSticker!!.getMatrixScale(moveMatrix) > LIMIT_STICKER_MAX_SIZE) {
                    val newFactor =
                        LIMIT_STICKER_MAX_SIZE / (currentSticker!!.getMatrixScale(tmpMatrix) * currentSticker!!.width)
                    moveMatrix.postScale(newFactor, newFactor, midPoint.x, midPoint.y)
                    currentSticker!!.setMatrix(moveMatrix)
                    return
                }
                currentSticker!!.setMatrix(moveMatrix)
            }

            ActionMode.ICON -> if (currentSticker != null && currentIcon != null) {
                currentIcon!!.onActionMove(this, event)
            }
        }
    }

    fun zoomAndRotateCurrentSticker(event: MotionEvent) {
        zoomAndRotateSticker(currentSticker, event)
    }

    fun zoomAndRotateSticker(sticker: Sticker?, event: MotionEvent) {
        if (sticker != null) {
            var newDistance = calculateDistance(midPoint.x, midPoint.y, event.x, event.y)
            val newRotation = calculateRotation(midPoint.x, midPoint.y, event.x, event.y)
//
            var scaleFactor = newDistance / oldDistance

            val tmpMatrix = Matrix()

            moveMatrix.set(downMatrix)
            tmpMatrix.set(downMatrix)
            moveMatrix.postScale(
                scaleFactor, scaleFactor, midPoint.x,
                midPoint.y
            )

            if (currentSticker !is MosaicSticker && currentSticker !is BlurSticker) {
                moveMatrix.postRotate(newRotation - oldRotation, midPoint.x, midPoint.y)
                tmpMatrix.postRotate(newRotation - oldRotation, midPoint.x, midPoint.y)
            }

            if (currentSticker !is TextSticker) {
                if (sticker.width * sticker.getMatrixScale(moveMatrix) < 100f) {
                    val values = FloatArray(9)
                    moveMatrix.getValues(values)
                    val translateX: Float = values[Matrix.MTRANS_X]
                    val translateY: Float = values[Matrix.MTRANS_Y]
                    tmpMatrix.postScale(
                        100f / sticker.width,
                        100f / sticker.width,
                        midPoint.x,
                        midPoint.y
                    )
//                    val translateMatrix = Matrix()
//                    translateMatrix.postTranslate(translateX, translateY)
//                    tmpMatrix.postConcat(translateMatrix)
//                    currentSticker!!.setMatrix(tmpMatrix)
                    return
                }

                if (sticker.width * sticker.getMatrixScale(moveMatrix) > 300f) {
                    val values = FloatArray(9)
                    moveMatrix.getValues(values)
                    val translateX: Float = values[Matrix.MTRANS_X]
                    val translateY: Float = values[Matrix.MTRANS_Y]
                    tmpMatrix.postScale(
                        300f / sticker.width,
                        300f / sticker.height,
                        midPoint.x,
                        midPoint.y
                    )
//                    val translateMatrix = Matrix()
//                    translateMatrix.postTranslate(translateX, translateY)
//                    tmpMatrix.postConcat(translateMatrix)

//                    currentSticker!!.setMatrix(tmpMatrix)
                    return
                }
            }
            currentSticker!!.setMatrix(moveMatrix)
        }
    }

    protected fun constrainSticker(sticker: Sticker) {
        var moveX = 0f
        var moveY = 0f
        val width = width
        val height = height
        sticker.getMappedCenterPoint(currentCenterPoint, point, tmp)
        if (currentCenterPoint.x < 0) {
            moveX = -currentCenterPoint.x
        }
        if (currentCenterPoint.x > width) {
            moveX = width - currentCenterPoint.x
        }
        if (currentCenterPoint.y < 0) {
            moveY = -currentCenterPoint.y
        }
        if (currentCenterPoint.y > height) {
            moveY = height - currentCenterPoint.y
        }
        sticker.matrix.postTranslate(moveX, moveY)
    }

    protected fun checkImageInsideSticker(sticker: Sticker) {
        val rect = sticker.mappedBound
        val limitWidth = width
        val limitHeight = height
        if(rect.right < 0 || rect.left > limitWidth || rect.top > limitHeight || rect.bottom < 0) {
            val completeRemove = removeSticker(sticker)
            if (completeRemove) {
                this.haptic()
                invalidate()
            }
        }
    }

    protected fun findCurrentIconTouched(): BitmapStickerIcon? {
        for (icon in icons) {
            val x = icon.x - downX
            val y = icon.y - downY
            val distance_pow_2 = x * x + y * y
            if (distance_pow_2 <= (icon.iconRadius + icon.iconRadius).pow(2.0f)) {
                return icon
            }
        }
        return null
    }

    /**
     * find the touched Sticker
     */
    protected fun findHandlingSticker(): Sticker? {
        for (i in stickers.indices.reversed()) {
            if (isInStickerArea(stickers[i]!!, downX, downY)) {
                return stickers[i]
            }
        }
        return null
    }


    protected fun findHandlingSticker(x: Float, y: Float): Sticker? {
        for (i in stickers.indices.reversed()) {
            if (isInStickerArea(stickers[i]!!, x, y)) {
                return stickers[i]
            }
        }
        return null
    }

    protected fun isInStickerArea(sticker: Sticker, downX: Float, downY: Float): Boolean {
        tmp[0] = downX
        tmp[1] = downY
        return sticker.contains(tmp)
    }

    protected fun calculateMidPoint(event: MotionEvent?): PointF {
        if (event == null || event.pointerCount < 2) {
            midPoint[0f] = 0f
            return midPoint
        }
        val x = (event.getX(0) + event.getX(1)) / 2
        val y = (event.getY(0) + event.getY(1)) / 2
        midPoint[x] = y
        return midPoint
    }

    protected fun calculateMidPoint(): PointF {
        if (currentSticker == null) {
            midPoint[0f] = 0f
            return midPoint
        }
        currentSticker!!.getMappedCenterPoint(midPoint, point, tmp)
        return midPoint
    }

    /**
     * calculate rotation in line with two fingers and x-axis
     */
    protected fun calculateRotation(event: MotionEvent?): Float {
        return if (event == null || event.pointerCount < 2) {
            0f
        } else calculateRotation(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
    }

    protected fun calculateRotation(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val x = (x1 - x2).toDouble()
        val y = (y1 - y2).toDouble()
        val radians = atan2(y, x)
        return Math.toDegrees(radians).toFloat()
    }

    /**
     * calculate Distance in two fingers
     */
    protected fun calculateDistance(event: MotionEvent?): Float {
        return if (event == null || event.pointerCount < 2) {
            0f
        } else calculateDistance(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
    }

    protected fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val x = (x1 - x2).toDouble()
        val y = (y1 - y2).toDouble()
        return sqrt(x * x + y * y).toFloat()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
//        for (i in stickers.indices) {
//            val sticker = stickers[i]
//            sticker?.let { transformSticker(it) }
//        }
    }


    /**
     * Sticker's drawable will be too bigger or smaller
     * This method is to transform it to fit
     * step 1：let the center of the sticker image is coincident with the center of the View.
     * step 2：Calculate the zoom and zoom
     */
    protected fun transformSticker(sticker: Sticker?) {
        if (sticker == null) {
            Log.e(
                TAG,
                "transformSticker: the bitmapSticker is null or the bitmapSticker bitmap is null"
            )
            return
        }
        sizeMatrix.reset()
        val width = width.toFloat()
        val height = height.toFloat()
        val stickerWidth = sticker.width.toFloat()
        val stickerHeight = sticker.height.toFloat()
        //step 1
        val offsetX = (width - stickerWidth) / 2
        val offsetY = (height - stickerHeight) / 2
        sizeMatrix.postTranslate(offsetX, offsetY)

        //step 2
        val scaleFactor: Float = if (width < height) {
            width / stickerWidth
        } else {
            height / stickerHeight
        }
        sizeMatrix.postScale(scaleFactor / 2f, scaleFactor / 2f, width / 2f, height / 2f)
        sticker.matrix.reset()
        sticker.setMatrix(sizeMatrix)
        invalidate()
    }

    fun flipCurrentSticker(direction: Int) {
        flip(currentSticker, direction)
    }

    fun flip(sticker: Sticker?, @Flip direction: Int) {
        if (sticker != null) {
            sticker.getCenterPoint(midPoint)
            if (direction and FLIP_HORIZONTALLY > 0) {
                sticker.matrix.preScale(-1f, 1f, midPoint.x, midPoint.y)
                sticker.setFlippedHorizontally(!sticker.isFlippedHorizontally)
            }
            if (direction and FLIP_VERTICALLY > 0) {
                sticker.matrix.preScale(1f, -1f, midPoint.x, midPoint.y)
                sticker.setFlippedVertically(!sticker.isFlippedVertically)
            }
            if (onStickerOperationListener != null) {
                onStickerOperationListener!!.onStickerFlipped(sticker)
            }
            invalidate()
        }
    }

    @JvmOverloads
    fun replace(sticker: Sticker?, needStayState: Boolean = true): Boolean {
        return if (currentSticker != null && sticker != null) {
            val width = width.toFloat()
            val height = height.toFloat()
            if (needStayState) {
                sticker.setMatrix(currentSticker!!.matrix)
                sticker.setFlippedVertically(currentSticker!!.isFlippedVertically)
                sticker.setFlippedHorizontally(currentSticker!!.isFlippedHorizontally)
            } else {
                currentSticker!!.matrix.reset()
                // reset scale, angle, and put it in center
                val offsetX = (width - currentSticker!!.width) / 2f
                val offsetY = (height - currentSticker!!.height) / 2f
                sticker.matrix.postTranslate(offsetX, offsetY)
                val scaleFactor: Float
                scaleFactor = if (width < height) {
                    width / currentSticker!!.drawable!!.intrinsicWidth
                } else {
                    height / currentSticker!!.drawable!!.intrinsicHeight
                }
                sticker.matrix.postScale(
                    scaleFactor / 2f,
                    scaleFactor / 2f,
                    width / 2f,
                    height / 2f
                )
            }
            val index = stickers.indexOf(currentSticker)
            stickers[index] = sticker
            currentSticker = sticker
            invalidate()
            true
        } else {
            false
        }
    }

    fun removeAllAiSticker() {
        stickers.removeAll(stickers.filterIsInstance<AiSticker>())
        currentSticker = null
        invalidate()
    }

    fun removeAllBlurSticker() {
        stickers.removeAll(stickers.filterIsInstance<BlurSticker>())
        currentSticker = null
        invalidate()
    }

    fun remove(sticker: Sticker?): Boolean {
        return if (stickers.contains(sticker)) {
            stickers.remove(sticker)
            if (onStickerOperationListener != null) {
                onStickerOperationListener!!.onStickerDeleted(sticker!!)
            }
            if (currentSticker === sticker) {
                currentSticker = null
            }
            invalidate()
            true
        } else {
            Log.d(
                TAG,
                "remove: the sticker is not in this StickerView"
            )
            false
        }
    }

    fun removeSticker(sticker: Sticker): Boolean {
        return remove(currentSticker)
    }

    fun removeCurrentSticker(): Boolean {
        return remove(currentSticker)
    }

    fun removeAllStickers() {
        stickers.clear()
        if (currentSticker != null) {
            currentSticker!!.release()
            currentSticker = null
        }
        invalidate()
    }

    fun addSticker(sticker: Sticker): StickerView {
        return addSticker(sticker, Sticker.Position.Center)
    }

    fun addSticker(
        sticker: Sticker,
        position: Sticker.Position
    ): StickerView {
        if (ViewCompat.isLaidOut(this)) {
            addStickerImmediately(sticker, position)
        } else {
            post { addStickerImmediately(sticker, position) }
        }
        return this
    }

    protected fun addStickerImmediately(sticker: Sticker, position: Sticker.Position) {
        setStickerPosition(sticker, position)
//        val scaleFactor: Float
//        val widthScaleFactor: Float = width.toFloat() / sticker.drawable!!.intrinsicWidth
//        val heightScaleFactor: Float = height.toFloat() / sticker.drawable!!.intrinsicHeight
//        scaleFactor =
//            if (widthScaleFactor > heightScaleFactor) heightScaleFactor else widthScaleFactor
//        sticker.matrix
//            .postScale(
//                scaleFactor / 2,
//                scaleFactor / 2,
//                (width / 2).toFloat(),
//                (height / 2).toFloat()
//            )
        currentSticker = sticker
        stickers.add(sticker)
        if (onStickerOperationListener != null) {
            onStickerOperationListener!!.onStickerAdded(sticker)
        }
        invalidate()
    }

    protected fun setStickerPosition(sticker: Sticker, position: Sticker.Position) {
        if (position is Sticker.Position.Copy) {
            val temp = Matrix(sticker.matrix)
            sticker.setMatrix(temp)
        } else if (position is Sticker.Position.Custom) {
            sticker.matrix.postTranslate(position.x, position.y)
//            sticker.matrix.postTranslate(position.x - (sticker.width/4), position.y - (sticker.height/4))
        } else {
            val width = width.toFloat()
            val height = height.toFloat()
            var offsetX = width - sticker.width
            var offsetY = height - sticker.height
            if (position.value and Sticker.Position.Top.value > 0) {
                offsetY /= 4f
            } else if (position.value and Sticker.Position.Bottom.value > 0) {
                offsetY *= 3f / 4f
            } else {
                offsetY /= 2f
            }
            if (position.value and Sticker.Position.Left.value > 0) {
                offsetX /= 4f
            } else if (position.value and Sticker.Position.Right.value > 0) {
                offsetX *= 3f / 4f
            } else {
                offsetX /= 2f
            }
            sticker.matrix.postTranslate(offsetX, offsetY)
        }
    }

    fun getStickerPoints(sticker: Sticker?): FloatArray {
        val points = FloatArray(8)
        getStickerPoints(sticker, points)
        return points
    }

    fun getStickerPoints(sticker: Sticker?, dst: FloatArray) {
        if (sticker == null) {
            Arrays.fill(dst, 0f)
            return
        }
        sticker.getBoundPoints(bounds)
        sticker.getMappedPoints(dst, bounds)
    }

    fun save(file: File) {
        try {
            StickerUtils.saveImageToGallery(file, createBitmap())
            StickerUtils.notifySystemGallery(context, file)
        } catch (ignored: IllegalArgumentException) {
            //
        } catch (ignored: IllegalStateException) {
        }
    }

    @Throws(OutOfMemoryError::class)
    fun createBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            currentSticker = null
            val canvas = Canvas(bitmap)
            drawStickers(canvas)
//            this.draw(canvas)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            return bitmap
        }
    }

    val stickerCount: Int
        get() = stickers.size
    val isNoneSticker: Boolean
        get() = stickerCount == 0

    fun setLocked(locked: Boolean): StickerView {
        isLocked = locked
        invalidate()
        return this
    }

    fun setMinClickDelayTime(minClickDelayTime: Int): StickerView {
        this.minClickDelayTime = minClickDelayTime
        return this
    }

    fun setConstrained(constrained: Boolean): StickerView {
        isConstrained = constrained
        postInvalidate()
        return this
    }

    fun setOnStickerOperationListener(
        onStickerOperationListener: OnStickerOperationListener?
    ): StickerView {
        this.onStickerOperationListener = onStickerOperationListener
        return this
    }

    fun getIcons(): List<BitmapStickerIcon> {
        return icons
    }

    fun setIcons(icons: List<BitmapStickerIcon>) {
        this.icons.clear()
        this.icons.addAll(icons)
        invalidate()
    }

    interface OnStickerOperationListener {
        fun onStickerAdded(sticker: Sticker)
        fun onStickerClicked(sticker: Sticker)
        fun onStickerDeleted(sticker: Sticker)
        fun onStickerDragFinished(sticker: Sticker)
        fun onStickerTouchedDown(sticker: Sticker)
        fun onStickerZoomFinished(sticker: Sticker)
        fun onStickerFlipped(sticker: Sticker)
        fun onStickerDoubleTapped(sticker: Sticker)

        fun hasStickerEnteredDeletionArea()
        fun notStickerEnterDeletionArea()

        fun showStickerDeleteArea()
    }

    companion object {
        private const val TAG = "StickerView"
        private const val DEFAULT_MIN_CLICK_DELAY_TIME = 200
        var LIMIT_STICKER_MIN_SIZE = 50.dp.toFloat()
        var LIMIT_STICKER_MAX_SIZE = 512.dp.toFloat()
        var DEFAULT_STICKER_SIZE = 150.dp
        const val FLIP_HORIZONTALLY = 1
        const val FLIP_VERTICALLY = 1 shl 1
    }
}
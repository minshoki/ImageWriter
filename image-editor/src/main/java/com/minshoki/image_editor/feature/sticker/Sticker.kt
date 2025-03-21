package com.minshoki.image_editor.feature.sticker

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.IntRange
import com.minshoki.image_editor.model.StickerJsonFileDataModel
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * @author wupanjie
 */
abstract class Sticker {
    sealed class Position(
        open val value: Int
    ) {
        data object Center: Position(value = 1)
        data object Top: Position(value = 1 shl 1)
        data object Left: Position(value = 1 shl 2)
        data object Right: Position(value = 1 shl 3)
        data object Bottom: Position(value = 1 shl 4)

        data object Copy: Position(value = -2)
        data class Custom(
            val x: Float,
            val y: Float
        ): Position(value = -1)
    }

    private val matrixValues = FloatArray(9)
    private val unrotatedWrapperCorner = FloatArray(8)
    private val unrotatedPoint = FloatArray(2)
    private val boundPoints = FloatArray(8)
    private val mappedBounds = FloatArray(8)
    private val trappedRect = RectF()
    val matrix = Matrix()
    var isFlippedHorizontally = false
        private set
    var isFlippedVertically = false
        private set

    fun setFlippedHorizontally(flippedHorizontally: Boolean): Sticker {
        isFlippedHorizontally = flippedHorizontally
        return this
    }

    fun setFlippedVertically(flippedVertically: Boolean): Sticker {
        isFlippedVertically = flippedVertically
        return this
    }

    fun setMatrix(matrix: Matrix?): Sticker {
        this.matrix.set(matrix)
        return this
    }

    abstract fun draw(canvas: Canvas)
    abstract val width: Int
    abstract val height: Int
    abstract fun mapperSaveFileJsonDataModel(): StickerJsonFileDataModel
    abstract fun setDrawable(drawable: Drawable): Sticker
    abstract val drawable: Drawable?
    abstract fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int): Sticker
    fun getBoundPoints(): FloatArray {
        val points = FloatArray(8)
        getBoundPoints(points)
        return points
    }

    fun getBoundPoints(points: FloatArray) {
        if (!isFlippedHorizontally) {
            if (!isFlippedVertically) {
                points[0] = 0f
                points[1] = 0f
                points[2] = width.toFloat()
                points[3] = 0f
                points[4] = 0f
                points[5] = height.toFloat()
                points[6] = width.toFloat()
                points[7] = height.toFloat()
            } else {
                points[0] = 0f
                points[1] = height.toFloat()
                points[2] = width.toFloat()
                points[3] = height.toFloat()
                points[4] = 0f
                points[5] = 0f
                points[6] = width.toFloat()
                points[7] = 0f
            }
        } else {
            if (!isFlippedVertically) {
                points[0] = width.toFloat()
                points[1] = 0f
                points[2] = 0f
                points[3] = 0f
                points[4] = width.toFloat()
                points[5] = height.toFloat()
                points[6] = 0f
                points[7] = height.toFloat()
            } else {
                points[0] = width.toFloat()
                points[1] = height.toFloat()
                points[2] = 0f
                points[3] = height.toFloat()
                points[4] = width.toFloat()
                points[5] = 0f
                points[6] = 0f
                points[7] = 0f
            }
        }
    }

    val mappedBoundPoints: FloatArray
        get() {
            val dst = FloatArray(8)
            getMappedPoints(dst, getBoundPoints())
            return dst
        }

    fun getMappedPoints(src: FloatArray): FloatArray {
        val dst = FloatArray(src.size)
        matrix.mapPoints(dst, src)
        return dst
    }

    fun getMappedPoints(dst: FloatArray, src: FloatArray) {
        matrix.mapPoints(dst, src)
    }

    val bound: RectF
        get() {
            val bound = RectF()
            getBound(bound)
            return bound
        }

    fun getBound(dst: RectF) {
        dst[0f, 0f, width.toFloat()] = height.toFloat()
    }

    val mappedBound: RectF
        get() {
            val dst = RectF()
            getMappedBound(dst, bound)
            return dst
        }

    fun getMappedBound(dst: RectF, bound: RectF) {
        matrix.mapRect(dst, bound)
    }

    val centerPoint: PointF
        get() {
            val center = PointF()
            getCenterPoint(center)
            return center
        }

    fun getCenterPoint(dst: PointF) {
        dst[width * 1f / 2] = height * 1f / 2
    }

    val mappedCenterPoint: PointF
        get() {
            val pointF = centerPoint
            getMappedCenterPoint(pointF, FloatArray(2), FloatArray(2))
            return pointF
        }

    fun getMappedCenterPoint(
        dst: PointF, mappedPoints: FloatArray,
        src: FloatArray
    ) {
        getCenterPoint(dst)
        src[0] = dst.x
        src[1] = dst.y
        getMappedPoints(mappedPoints, src)
        dst[mappedPoints[0]] = mappedPoints[1]
    }

    val currentScale: Float
        get() = getMatrixScale(matrix)
    val currentHeight: Float
        get() = getMatrixScale(matrix) * height
    val currentWidth: Float
        get() = getMatrixScale(matrix) * width

    /**
     * This method calculates scale value for given Matrix object.
     */
    fun getMatrixScale(matrix: Matrix): Float {
        return sqrt(
            getMatrixValue(matrix, Matrix.MSCALE_X).pow(2.0f) +
                    getMatrixValue(matrix, Matrix.MSKEW_Y).pow(2.0f)
        ).toFloat()
    }

    val currentAngle: Float
        /**
         * @return - current image rotation angle.
         */
        get() = getMatrixAngle(matrix)

    /**
     * This method calculates rotation angle for given Matrix object.
     */
    fun getMatrixAngle(matrix: Matrix): Float {
        return Math.toDegrees(
            -atan2(
                getMatrixValue(matrix, Matrix.MSKEW_X).toDouble(),
                getMatrixValue(matrix, Matrix.MSCALE_X).toDouble()
            )
        ).toFloat()
    }

    fun getMatrixValue(matrix: Matrix, @IntRange(from = 0, to = 9) valueIndex: Int): Float {
        matrix.getValues(matrixValues)
        return matrixValues[valueIndex]
    }

    fun contains(x: Float, y: Float): Boolean {
        return contains(floatArrayOf(x, y))
    }

    operator fun contains(point: FloatArray): Boolean {
        val tempMatrix = Matrix()
        tempMatrix.setRotate(-currentAngle)
        getBoundPoints(boundPoints)
        getMappedPoints(mappedBounds, boundPoints)
        tempMatrix.mapPoints(unrotatedWrapperCorner, mappedBounds)
        tempMatrix.mapPoints(unrotatedPoint, point)
        StickerUtils.trapToRect(trappedRect, unrotatedWrapperCorner)
        return trappedRect.contains(unrotatedPoint[0], unrotatedPoint[1])
    }

    open fun release() {}
}
package com.minshoki.core.util.bitmap

import android.R.attr.bitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.minshoki.core.util.dp
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.abs


fun Bitmap.resizeBitmap(resizeWidth: Int, resizeHeight: Int): Bitmap {
    return Bitmap.createScaledBitmap(
        this,
        resizeWidth,
        resizeHeight,
        true
    )
}

fun Bitmap.rotateBitmap(degress: Float): Bitmap {
    val rotateMatrix = Matrix()
    rotateMatrix.postRotate(degress)
    return Bitmap.createBitmap(this, 0, 0, width, height, rotateMatrix, false)
}

fun Bitmap.mosaic(range: Int = 30): Bitmap {
    val temp = Bitmap.createScaledBitmap(this, width/range, height/range, false)
    return Bitmap.createScaledBitmap(temp, width, height, false)
}

fun Bitmap.blur(): Bitmap {
    val srcMat = Mat()
    Utils.bitmapToMat(this, srcMat)
    val size = Size(35.0, 35.0)
    val blurredMat = Mat()
    Imgproc.GaussianBlur(srcMat, blurredMat, size, 100.0)
    val blurredBitmap = Bitmap.createBitmap(blurredMat.cols(), blurredMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(blurredMat, blurredBitmap)
    return blurredBitmap
}

fun Bitmap.blur(blurRect: Rect): Bitmap {
    val srcMat = Mat()
    Utils.bitmapToMat(this, srcMat)

    val rr =org.opencv.core.Rect(blurRect.left, blurRect.top, blurRect.width(), blurRect.height())
    // 블러 처리를 위한 영역을 정의
    val roi = Mat(srcMat, rr)

    // 블러 처리
    val blurredRoi = Mat()
    Imgproc.GaussianBlur(roi, blurredRoi, Size(15.0, 15.0), 0.0)

    // 블러 처리된 영역을 새로운 Mat에 복사
    val resultMat = Mat(blurredRoi.size(), blurredRoi.type())
    blurredRoi.copyTo(resultMat)

    // 결과를 Bitmap으로 변환
    val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(resultMat, resultBitmap)

    return resultBitmap
}

fun Bitmap.eraser(eraseRect: Rect): Bitmap {

    // OpenCV Mat 객체로 변환
    val dd = Mat()
    Utils.bitmapToMat(this, dd)

    val srcMat = Mat()
    Imgproc.cvtColor(dd, srcMat, Imgproc.COLOR_RGBA2BGR)

    // 지울 영역을 마스크로 생성
    val mask = Mat.zeros(srcMat.size(), CvType.CV_8UC1)
    Imgproc.rectangle(mask, Point(eraseRect.left.toDouble(), eraseRect.top.toDouble()),
        Point(eraseRect.right.toDouble(), eraseRect.bottom.toDouble()),
        Scalar(255.0), -1)

    // 주변 영역으로 복구하기 위해 Inpainting 수행
    val restoredMat = Mat()
    Photo.inpaint(srcMat, mask, restoredMat, 3.0, Photo.INPAINT_TELEA)
    // 원본 이미지에서 inpainting이 진행된 영역만 추출
// inpainting이 진행된 rect 영역만 추출
    val croppedRestoredMat = Mat(restoredMat, org.opencv.core.Rect(Point(eraseRect.left.toDouble(), eraseRect.top.toDouble()),
        Point(eraseRect.right.toDouble(), eraseRect.bottom.toDouble())))

    // 결과를 Bitmap으로 변환
    val resultBitmap = Bitmap.createBitmap(croppedRestoredMat.cols(), croppedRestoredMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(croppedRestoredMat, resultBitmap)

    return resultBitmap
}

fun Bitmap.getCircledBitmap(widthOffset: Int, heightOffset: Int, bound: RectF): Bitmap {
    val output =
        Bitmap.createBitmap(widthOffset, heightOffset, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint()

    val rect = Rect(0, 0, width, height)
    val w = width - widthOffset
    val h = height - heightOffset
    val centerY = if(bound.top < 0) (heightOffset / 2).toFloat() - abs(bound.top)
    else (heightOffset / 2).toFloat()

    val centerX = if(bound.left < 0) (widthOffset / 2).toFloat() - abs(bound.left)
    else (widthOffset/ 2).toFloat()

    paint.isAntiAlias = true
    canvas.drawARGB(0, 0, 0, 0)
    canvas.clipRect(w,h, width, height)
    canvas.drawCircle(
        centerX,
        centerY,
        (heightOffset / 2).toFloat(),
        paint
    )
    paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
    canvas.drawBitmap(this, rect, rect, paint)
    return output
}

fun Bitmap.crop(rect: Rect): Bitmap {
    var newWidth = if(rect.left > this.width) {
        rect.width() - (rect.left - this.width)
    } else rect.width()
    var newHeight = if(rect.bottom > this.height) {
        rect.height() - (rect.bottom - this.height)
    } else rect.height()

    val x = if (rect.left < 0) 0 else rect.left
    val y = if (rect.top < 0) 0 else rect.top

    val totalWidth = x + newWidth
    val totalHeight = y + newHeight
    if(totalWidth > this.width) {
        newWidth -= (totalWidth - this.width)
    }

    if(totalHeight > this.height) {
        newHeight -= (totalHeight - this.height)
    }

    return Bitmap.createBitmap(
        this,
        x,
        y,
        newWidth,
        newHeight
    )
}

fun Bitmap.roundCorner(): Bitmap {
    val output = Bitmap.createBitmap(
        width,
        height, Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(output)
    val paint = Paint()
    val rect = Rect(0, 0, width, height)
    val rectF = RectF(rect)

    val roundPx = 4.dp.toFloat()
    paint.isAntiAlias = true
    canvas.drawARGB(0, 0, 0, 0)
    canvas.drawRoundRect(rectF, roundPx, roundPx, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(this, rect, rect, paint)

    return output
}
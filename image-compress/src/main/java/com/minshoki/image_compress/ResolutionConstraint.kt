package com.minshoki.image_compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

//이미지 스케일링시 sampleSize 변경 후 width/height 값을 기준으로 한번 더 scaling 진행
class ResolutionConstraint(private val context: Context, private val width: Int, private val height: Int) :
    ICompressionConstraint {

    private fun Pair<Int, Int>.fixedResolution(block: (percent: Float) -> Unit) {
        val (bitmapHeight, bitmapWidth) = this.first to this.second
        if(bitmapWidth > bitmapHeight) {
            if(bitmapWidth > this@ResolutionConstraint.width) {
                block( bitmapWidth/this@ResolutionConstraint.width.toFloat())
            }
        } else {
            if(bitmapHeight > this@ResolutionConstraint.height) {
                block( bitmapHeight/this@ResolutionConstraint.height.toFloat())
            }
        }
    }

    override fun isSatisfied(imageFile: File): Boolean {
        return BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(imageFile.absolutePath, this)
            var result = calculateInSampleSize(this, width, height) <= 1
            if(result) {
                Log.i("Compressor", "ResolutionConstraint isSatisfied before $result")
                (this.outHeight to this.outWidth).fixedResolution {
                    result = false
                }
                Log.i("Compressor", "ResolutionConstraint isSatisfied after $result")
            }
            result
        }
    }

    override fun satisfy(imageFile: File): Pair<File, Bitmap> {
        return decodeSampledBitmapFromFile(imageFile, width, height).run {
            val samplingHeight = this.height
            val samplingWidth = this.width
            Log.i("Compressor-Result", "sampling Bitmap ${samplingHeight}/${samplingWidth}")
            var resultBitmap = this
            (samplingHeight to samplingWidth).fixedResolution { percent ->
                Log.i("Compressor", "scaling $percent")
                resultBitmap = Bitmap.createScaledBitmap(this, (samplingWidth/percent).toInt(), (samplingHeight/percent).toInt(), true)
            }

            Log.i("Compressor-Result", "scaled Bitmap ${resultBitmap.height}/${resultBitmap.width}")
            Log.i("Compressor-Result", "=======================================================")
            determineImageRotation(context, imageFile, resultBitmap).run {
                overWrite(imageFile, this)
            } to resultBitmap
        }
    }
}

fun ImageCompression.resolution(context: Context, width: Int, height: Int) {
    constraint(ResolutionConstraint(context, width, height))
}
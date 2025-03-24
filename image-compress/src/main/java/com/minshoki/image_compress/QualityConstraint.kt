package com.minshoki.image_compress

import android.content.Context
import android.graphics.Bitmap
import java.io.File

class QualityConstraint(private val context: Context, private val quality: Int) :
    ICompressionConstraint {
    private var isResolved = false

    override fun isSatisfied(imageFile: File): Boolean {
        return isResolved
    }

    override fun satisfy(imageFile: File): Pair<File, Bitmap> {
        val resultBitmap = loadBitmap(context, imageFile)
        val result = overWrite(imageFile, resultBitmap, quality = quality)
        isResolved = true
        return result to resultBitmap
    }
}

fun ImageCompression.quality(context: Context, quality: Int) {
    constraint(QualityConstraint(context, quality))
}
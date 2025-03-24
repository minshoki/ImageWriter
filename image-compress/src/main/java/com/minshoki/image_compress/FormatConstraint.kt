package com.minshoki.image_compress

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File

class FormatConstraint(private val context: Context) : ICompressionConstraint {

    override fun isSatisfied(imageFile: File): Boolean {
        val result = imageFile.compressFormat() != CompressCustomFormat.HEIC
        Log.i("Compressor", "FormatConstraint extension ${imageFile.compressFormat().extension}")
        return result
    }

    override fun satisfy(imageFile: File): Pair<File, Bitmap> {
        val resultBitmap = loadBitmap(context, imageFile)
        return overWrite(imageFile, resultBitmap, imageFile.compressFormat().changeCustomFormat()) to resultBitmap
    }
}

fun ImageCompression.format(context: Context) {
    constraint(FormatConstraint(context))
}
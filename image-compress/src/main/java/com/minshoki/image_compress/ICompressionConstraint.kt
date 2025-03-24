package com.minshoki.image_compress

import android.graphics.Bitmap
import java.io.File

interface ICompressionConstraint {
    fun isSatisfied(imageFile: File): Boolean

    fun satisfy(imageFile: File): Pair<File, Bitmap>
}
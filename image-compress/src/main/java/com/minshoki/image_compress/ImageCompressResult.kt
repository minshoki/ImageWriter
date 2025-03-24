package com.minshoki.image_compress

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

data class ImageCompressResult(
    val file: File,
    val bitmap: Bitmap
)
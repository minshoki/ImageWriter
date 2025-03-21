package com.minshoki.image_editor.core.ext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.minshoki.image_editor.feature.crop.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

suspend fun Uri.toBitmap(context: Context): Bitmap {
    return when {
        scheme == "file" -> {
            try {
                withContext(Dispatchers.IO) {
                    Glide.with(context)
                        .asBitmap()
                        .override(1123)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .load(this@toBitmap)
                        .submit().get()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, this))
                } else MediaStore.Images.Media.getBitmap(context.contentResolver, this)
            }
        }

        else -> {
            try {
                withContext(Dispatchers.IO) {
                    Glide.with(context)
                        .asBitmap()
                        .override(1123)
                        .load(this@toBitmap)
                        .submit().get()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val bitmap = BitmapUtils.decodeSampledBitmap(context, this, 1123, 1123)
                return bitmap.bitmap!!
            }
        }
    }
}

fun Uri.imageEditorPrefix(): String {
    val parentPath = this.pathSegments.getOrNull(pathSegments.size -2)
    return if(parentPath == "original" || parentPath == "remote_original") {
        this.pathSegments[pathSegments.size - 3]
    } else parentPath?: ""
}

fun Uri.getExifRotationDegrees(context: Context): Int {
    try {
        val inputStream = context.contentResolver.openInputStream(this) ?: return 0
        val exif = ExifInterface(inputStream)
        return exif.rotationDegrees
    } catch (e: FileNotFoundException) {
        return 0
    }
}

fun Uri?.removeEditorSuffix(): String {
    return this?.lastPathSegment?.removeSuffix(".jpg")?.removeSuffix(".gif").toString()
}
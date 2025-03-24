package com.minshoki.image_compress

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import com.minshoki.core.util.isGif
import java.io.File
import java.io.FileOutputStream

object ImageCompress {
    fun compress(
        context: Context,
        imageFile: File,
        compressionPatch: ImageCompression.() -> Unit = {
            format(context)
            resolution(context, 1123, 1123)
        }
    ): File? {
        val compression = ImageCompression().apply(compressionPatch)
        var result = copyToCache(context, imageFile)
        result?.let {
            compression.constraints.forEach { constraint ->
                while (constraint.isSatisfied(it).not()) {
                    result = constraint.satisfy(it).first
                }
            }
        }
        return result
    }

    fun compress(
        context: Context,
        imageFileUri: Uri,
        compressionPatch: ImageCompression.() -> Unit = {
            format(context)
            resolution(context, 1123, 1123)
        }
    ): File {
        val compression = ImageCompression().apply(compressionPatch)
        var result = copyToCache(context, imageFileUri)
        compression.constraints.forEach { constraint ->
            if(imageFileUri.isGif(context)) {
                if(constraint !is ResolutionConstraint) {
                    while (constraint.isSatisfied(result).not()) {
                        result = constraint.satisfy(result).first
                    }
                }
            } else {
                while (constraint.isSatisfied(result).not()) {
                    result = constraint.satisfy(result).first
                }
            }
        }
        return result
    }

    fun compress(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        cacheDir: String = "${context.cacheDir}",
        rotationDegrees: Int = 0,
        compressionPatch: ImageCompression.() -> Unit = {
            format(context)
            resolution(context, 1123, 1123)
        }
    ): ImageCompressResult {
        val compression = ImageCompression().apply(compressionPatch)

        var tempFile = File(cacheDir, fileName)
        tempFile.parentFile?.mkdirs()
        var result = ImageCompressResult(tempFile, bitmap)
        try {
            if (tempFile.exists()) tempFile.delete()
            tempFile.createNewFile()

            FileOutputStream(tempFile).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            }
            ExifInterface(tempFile.path).also { exif ->
                exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    when(rotationDegrees) {
                        90 -> ExifInterface.ORIENTATION_ROTATE_90
                        180 -> ExifInterface.ORIENTATION_ROTATE_180
                        270 -> ExifInterface.ORIENTATION_ROTATE_270
                        else -> ExifInterface.ORIENTATION_UNDEFINED
                    }.toString()
                )
            }.saveAttributes()

            compression.constraints.forEach { constraint ->
                while (constraint.isSatisfied(tempFile).not()) {
                    val r = constraint.satisfy(tempFile)
                    tempFile = r.first
                    result = ImageCompressResult(tempFile, r.second)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
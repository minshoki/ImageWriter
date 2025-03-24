package com.minshoki.image_compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream


private val separator = File.separator

private fun cachePath(context: Context): String {
    return "${context.cacheDir.path}${separator}compressor$separator"
}

private fun getFileName(context: Context, uri: Uri) : String {
    val cursor = context.contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
    )
    return if(cursor != null && cursor.moveToFirst()) {
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.getString(nameIndex)
    } else {
        File(uri.path).name
    }
}

sealed class CompressCustomFormat(
    val extension: String
){
    object PNG: CompressCustomFormat("png")
    object HEIC: CompressCustomFormat("heic")
    object JPG: CompressCustomFormat("jpg")
}

//PNG,HEIC, JPG만 구분
fun File.compressFormat() = when (extension.toLowerCase()) {
    CompressCustomFormat.PNG.extension -> CompressCustomFormat.PNG
    CompressCustomFormat.HEIC.extension -> CompressCustomFormat.HEIC
    else -> CompressCustomFormat.JPG
}

//HEIC -> JPG 로 변환하고 나머지는 원본포맷 유지
fun CompressCustomFormat.changeCustomFormat() = when(this) {
    CompressCustomFormat.HEIC -> CompressCustomFormat.JPG
    else -> this
}
fun CompressCustomFormat.extension() = extension

//최종적으로 업로드되는 이미지 포맷은 PNG/JPEG
fun CompressCustomFormat.toBitmapCompressFormat() = when(this) {
    CompressCustomFormat.PNG -> Bitmap.CompressFormat.PNG
    else -> Bitmap.CompressFormat.JPEG
}

fun loadBitmap(context: Context, imageFile: File) = BitmapFactory.decodeFile(imageFile.absolutePath).run {
    determineImageRotation(context, imageFile, this)
}

fun decodeSampledBitmapFromFile(imageFile: File, reqWidth: Int, reqHeight: Int): Bitmap {
    return BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(imageFile.absolutePath, this)
        Log.i("Compressor-Result", "Origin Bitmap $outHeight/$outWidth")
        inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)

        inJustDecodeBounds = false
        BitmapFactory.decodeFile(imageFile.absolutePath, this)
    }
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {

        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

fun determineImageRotation(context: Context, imageFile: File, bitmap: Bitmap): Bitmap {
    val exif = try {
        ExifInterface(imageFile.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
        context.contentResolver.openInputStream(imageFile.toUri())?.let { inputStream ->
            ExifInterface(inputStream)
        }
    }
    val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
    val matrix = Matrix()
    when (orientation) {
        6 -> matrix.postRotate(90f)
        3 -> matrix.postRotate(180f)
        8 -> matrix.postRotate(270f)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

internal fun copyToCache(context: Context, imageFile: File): File? {
    return try {
        imageFile.copyTo(File("${cachePath(context)}${imageFile.name}"), true)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

internal fun copyToCache(context: Context, imageFile: Uri): File {
    val cacheFile = File("${cachePath(context)}${getFileName(context, imageFile)}")
    Log.i("shokitest", "copyToCache ${cacheFile.name}")
    cacheFile.parentFile?.mkdirs()
    if (cacheFile.exists()) {
        cacheFile.delete()
    }
    cacheFile.createNewFile()
    cacheFile.deleteOnExit()
    val fd = context.contentResolver.openFileDescriptor(imageFile, "r")
    val inputStream = ParcelFileDescriptor.AutoCloseInputStream(fd)
    val outputStream = FileOutputStream(cacheFile)
    fd.use {
        outputStream.use {
            inputStream.copyTo(outputStream)
        }
    }
    return cacheFile
}

fun overWrite(imageFile: File, bitmap: Bitmap, format: CompressCustomFormat = imageFile.compressFormat(), quality: Int = 100): File {
    val result = if (format == imageFile.compressFormat()) {
        imageFile
    } else {
        File("${imageFile.absolutePath.substringBeforeLast(".")}.${format.extension()}")
    }
    imageFile.delete()
    saveBitmap(bitmap, result, format, quality)
    return result
}

fun saveBitmap(bitmap: Bitmap, destination: File, format: CompressCustomFormat = destination.compressFormat(), quality: Int = 100) {
    destination.parentFile?.mkdirs()
    var fileOutputStream: FileOutputStream? = null
    try {
        fileOutputStream = FileOutputStream(destination.absolutePath)
        bitmap.compress(format.toBitmapCompressFormat(), quality, fileOutputStream)
    } finally {
        fileOutputStream?.run {
            flush()
            close()
        }
    }
}
package com.minshoki.image_editor.core

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.text.Layout
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.google.gson.Gson
import com.minshoki.core.util.dp
import com.minshoki.image_editor.R
import com.minshoki.image_editor.core.ext.getExifRotationDegrees
import com.minshoki.image_editor.core.ext.imageEditorPrefix
import com.minshoki.image_editor.core.ext.removeEditorSuffix
import com.minshoki.image_editor.core.ext.stickerAsDrawable
import com.minshoki.image_editor.feature.sticker.AiSticker
import com.minshoki.image_editor.feature.sticker.BlurSticker
import com.minshoki.image_editor.feature.sticker.DrawableSticker
import com.minshoki.image_editor.feature.sticker.MosaicSticker
import com.minshoki.image_editor.feature.sticker.Sticker
import com.minshoki.image_editor.feature.sticker.TextSticker
import com.minshoki.image_editor.model.ImageEditJsonFileDataModel
import com.minshoki.image_editor.model.ImageEditorViewerModel
import com.minshoki.image_editor.model.StickerJsonFileDataModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter


val Context.imageEditorCacheDir: String
    get() {
        val separator = File.separator
        return "$cacheDir${separator}hiclass_imageEditor$separator"
    }

fun Context.deleteImageEditorDataFile(uri: Uri, remoteImagePath: String? = null) {
    if(uri.scheme == "file" && uri.toFile().startsWith(imageEditorCacheDir)) {
        val key = uri.removeEditorSuffix() ?: return
        val prefix = uri.imageEditorPrefix()
        deleteImageEditorDataFile(key = key, prefix = prefix, remoteImagePath = remoteImagePath)
    }
}

fun Context.deleteAllImageEditorDataFiles() {
    File(imageEditorCacheDir).deleteRecursively()
}

internal fun Context.deleteImageEditorDataFile(key: String, prefix: String, remoteImagePath: String?, shouldRemoveBitmapFile: Boolean = false) {
    val jsonFileName = "$key.json"
    val bitmapFileName = "$key.jpg"
    val file = File("${imageEditorCacheDir}${prefix}", jsonFileName)
    if (file.exists()) {
        val data = loadImageEditorData(key, prefix) ?: return
        file.delete()
        saveImageEditorDataEmptyString(uri = data.originalPathFromUri.toUri(), prefix = prefix, remoteImagePath = remoteImagePath)
    }
    if(shouldRemoveBitmapFile) {
        val bitmapFile = File("${imageEditorCacheDir}${prefix}", bitmapFileName)
        if (bitmapFile.exists()) {
            bitmapFile.delete()
        }
    }
}


internal suspend fun Context.getStickersFromLoadImageEditorData(key: String, prefix: String): List<Sticker> {
    val data = loadImageEditorData(key = key, prefix = prefix)
    return (data?.stickers ?: emptyList()).mapNotNull {
        makeSticker(it)
    }
}

fun Uri.isUpdatedBitmap(context: Context): Boolean {
    val key = removeEditorSuffix() ?: return false
    val prefix = imageEditorPrefix()
    return context.loadImageEditorData(key = key, prefix = prefix)?.isUpdatedBitmap ?: false
}

fun Uri.getOriginalUri(context: Context): String? {
    val key = removeEditorSuffix() ?: return null
    val prefix = imageEditorPrefix()
    return context.loadImageEditorData(key = key, prefix = prefix)?.originalPathFromUri
}
internal fun Context.loadImageEditorData(key: String, prefix: String): ImageEditJsonFileDataModel? {
    val jsonFileName = "$key.json"
    val fileText = StringBuilder()
    Log.i("imageEditorTest", "loadImageEditorData $key / $prefix")
    val file = File("${imageEditorCacheDir}${prefix}", jsonFileName)
    val gson = Gson()
    if (file.exists()) {
        BufferedReader(FileReader(file)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                fileText.append(line)
            }
        }

        return gson.fromJson(fileText.toString(), ImageEditJsonFileDataModel::class.java)
    } else return null
}

fun Context.saveImageEditorDataEmptyString(uri: Uri, prefix: String, remoteImagePath: String?): ImageEditJsonFileDataModel {
    val key = uri.removeEditorSuffix()
    val jsonFileName = "$key.json"
    val rotate = uri.getExifRotationDegrees(this@saveImageEditorDataEmptyString)
    Log.i("imageEditorTest", "saveImageEditorDataEmptyString $uri / $key")
    val data = ImageEditJsonFileDataModel(
        stickers = emptyList(),
        rotate = rotate,
        originalRotate = rotate,
        originalPathFromUri = uri.toString(),
        isUpdatedBitmap = false,
        remoteImagePath = remoteImagePath,
        progressPathFromUri = uri.toString(),
    )
    val gson = Gson()
    val jsonFile = File("${imageEditorCacheDir}${prefix}${File.separator}", jsonFileName)
    jsonFile.parentFile?.mkdirs()

    FileWriter(jsonFile).use {
        it.write(gson.toJson(data))
    }
    return data
}
internal fun Context.saveImageEditorDataString(
    item: ImageEditorViewerModel,
    stickers: List<Sticker>,
    remoteImagePath: String?,
) {
    val jsonFileName = item.key + ".json"
    Log.i("imageEditorTest", "saveImageEditorDataString $item")
    val data = ImageEditJsonFileDataModel(
        stickers = stickers.map { it.mapperSaveFileJsonDataModel() },
        rotate = item.rotate,
        originalRotate = item.originalRotate,
        originalPathFromUri = item.originalUri.toString(),
        isUpdatedBitmap = if(stickers.isNotEmpty()) true else item.updatedUri != item.originalUri && item.updatedUri != Uri.EMPTY,
        remoteImagePath = remoteImagePath,
        progressPathFromUri = if(item.copyOriginalUri != Uri.EMPTY) item.copyOriginalUri.toString() else if(item.updatedUri != Uri.EMPTY) item.updatedUri.toString() else item.originalUri.toString()
    )
    val gson = Gson()

    val jsonFile = File("${imageEditorCacheDir}${item.prefix}${File.separator}", jsonFileName)
    jsonFile.parentFile?.mkdirs()

    FileWriter(jsonFile).use {
        it.write(gson.toJson(data))
    }
}

internal suspend fun Context.makeSticker(data: StickerJsonFileDataModel): Sticker? {
    val sticker = when {
        data.type == StickerType.DRAWABLE -> {
            val drawable = withContext(Dispatchers.IO) { stickerAsDrawable(data.imageUrl) }
            DrawableSticker(
                drawable = drawable,
                url = data.imageUrl,
                customHeight = data.height,
                customWidth = data.width
            ).apply {
                val m = Matrix()
                m.setValues(data.matrixValues)
                setMatrix(m)
            }
        }

        data.type == StickerType.TEXT && data.textData != null -> {
            TextSticker(
                text = data.textData.text,
                drawable = ContextCompat.getDrawable(
                    this@makeSticker,
                    data.textData.backgroundColor.drawableRes
                ),
                maxWidth = 500.dp
            )
                .setTypeface(ResourcesCompat.getFont(this, com.minshoki.core.design.R.font.nanumsquare_otf_ac_b))
                .setTextColor(this@makeSticker, data.textData.textColor)
                .setTextAlign(Layout.Alignment.ALIGN_CENTER)
                .setTextBackgroundColor(this@makeSticker, data.textData.backgroundColor)
                .apply {
                    val m = Matrix()
                    m.setValues(data.matrixValues)
                    setMatrix(m)
                }
                .resizeText()

        }

        data.type == StickerType.MOSAIC -> {
            MosaicSticker(customWidth = data.width, customHeight = data.height).apply {
                val m = Matrix()
                m.setValues(data.matrixValues)
                setMatrix(m)
            }
        }

        data.type == StickerType.BLUR -> {
            BlurSticker(customWidth = data.width, customHeight = data.height).apply {
                val m = Matrix()
                m.setValues(data.matrixValues)
                setMatrix(m)
            }
        }

        data.type == StickerType.AI -> {
            val drawable = withContext(Dispatchers.IO) { stickerAsDrawable(data.imageUrl) }
            AiSticker(
                drawable = drawable,
                url = data.imageUrl,
                customHeight = data.height,
                customWidth = data.width
            ).apply {
                val m = Matrix()
                m.setValues(data.matrixValues)
                setMatrix(m)
            }
        }

        else -> null
    }
    return sticker
}
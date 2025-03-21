package com.minshoki.image_editor.model

import android.graphics.Bitmap
import android.net.Uri
import com.minshoki.image_editor.feature.sticker.Sticker

data class ImageEditorViewerModel(
    val prefix: String,
    val origin: Origin,
    val key: String,
    val originalUri: Uri,
    val rotate: Int,
    val originalRotate: Int,
    val copyOriginalBitmap: Bitmap,
    val originalBitmap: Bitmap,
    val updatedUri: Uri,
    val updatedBitmap: Bitmap,
    val isUpdatedBitmap: Boolean,
    val copyOriginalUri: Uri,
    val stickers: List<Sticker>
) {

    fun getChangeRotate(): Int {
        return if(rotate != originalRotate) rotate else originalRotate
    }

    fun isChangedRotate() = rotate != originalRotate
    sealed class Origin {
        data class Remote(
            val url: String
        ): Origin()

        data object Local: Origin()

        fun remoteImagePath(): String? {
            return when(this) {
                is Remote -> url
                else -> null
            }
        }
    }
    fun getResultUri() = if(updatedUri == Uri.EMPTY) originalUri else updatedUri
    fun canResetImageEdit() = isUpdatedBitmap
}
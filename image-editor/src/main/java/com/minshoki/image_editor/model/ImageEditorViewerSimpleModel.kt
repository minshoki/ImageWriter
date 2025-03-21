package com.minshoki.image_editor.model

import android.net.Uri
import com.minshoki.image_editor.feature.sticker.Sticker
import com.minshoki.image_editor.model.ImageEditorViewerModel

data class ImageEditorViewerSimpleModel(
    val prefix: String,
    val origin: ImageEditorViewerModel.Origin,
    val key: String,
    val originalUri: Uri,
    val updatedUri: Uri,
    val isUpdatedBitmap: Boolean,
    val stickers: List<Sticker>,
    val lastUpdateTimestamp: Long
) {
    fun canResetImageEdit() = isUpdatedBitmap
    fun getResultUri() = if(updatedUri == Uri.EMPTY) originalUri else updatedUri
}
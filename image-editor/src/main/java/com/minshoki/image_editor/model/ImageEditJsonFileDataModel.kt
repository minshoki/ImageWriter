package com.minshoki.image_editor.model

data class ImageEditJsonFileDataModel(
    val stickers: List<StickerJsonFileDataModel>,
    val rotate: Int,
    val originalRotate: Int,
    val originalPathFromUri: String,
    val remoteImagePath: String?,
    val progressPathFromUri: String,
    val isUpdatedBitmap: Boolean = false,
)
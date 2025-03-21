package com.minshoki.image_editor.model

import com.minshoki.image_editor.core.StickerType


data class StickerJsonFileDataModel(
    val type: StickerType,
    val matrixValues: FloatArray,
    val imageUrl: String,
    val width: Int,
    val height: Int,
    val textData: TextStickerJsonFileDataModel? = null,
)
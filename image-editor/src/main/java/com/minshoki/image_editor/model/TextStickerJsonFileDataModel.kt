package com.minshoki.image_editor.model

import com.minshoki.image_editor.core.TextStickerColors

data class TextStickerJsonFileDataModel(
    val text: String,
    val textColor: TextStickerColors.TextColor,
    val backgroundColor: TextStickerColors.BackgroundColor,
)
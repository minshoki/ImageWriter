package com.minshoki.image_editor.model

import android.graphics.drawable.Drawable


sealed class ImageEditorSticker {
    data class StickerModel(
        val url: String,
        val useAi: Boolean,
        val drawable: Drawable,
    ): ImageEditorSticker()

    data object MosaicStickerModel: ImageEditorSticker()

    data object AiStickerModel: ImageEditorSticker()
    data object BlurStickerModel: ImageEditorSticker()
}

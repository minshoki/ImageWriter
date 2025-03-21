package com.minshoki.image_editor.model

data class RemoteConfigStickersValueModel(
    val stickers: List<RemoteConfigStickerValueModel>
) {
    data class RemoteConfigStickerValueModel(
        val url: String,
        val useAi: Boolean
    )
}
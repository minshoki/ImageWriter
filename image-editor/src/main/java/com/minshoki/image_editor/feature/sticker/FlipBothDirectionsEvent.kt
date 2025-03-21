package com.minshoki.image_editor.feature.sticker

import com.minshoki.image_editor.feature.sticker.StickerView.Flip

class FlipBothDirectionsEvent : AbstractFlipEvent() {
    @get:Flip
    protected override val flipDirection: Int
        protected get() = StickerView.FLIP_VERTICALLY or StickerView.FLIP_HORIZONTALLY
}
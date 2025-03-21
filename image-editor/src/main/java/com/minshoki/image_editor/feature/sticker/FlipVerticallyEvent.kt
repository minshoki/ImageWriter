package com.minshoki.image_editor.feature.sticker

import com.minshoki.image_editor.feature.sticker.StickerView.Flip

class FlipVerticallyEvent : AbstractFlipEvent() {
    @get:Flip
    protected override val flipDirection: Int
        protected get() = StickerView.FLIP_VERTICALLY
}
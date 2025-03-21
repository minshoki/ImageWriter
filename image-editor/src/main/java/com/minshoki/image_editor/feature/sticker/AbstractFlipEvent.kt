package com.minshoki.image_editor.feature.sticker

import android.view.MotionEvent
import com.minshoki.image_editor.feature.sticker.StickerView.Flip

abstract class AbstractFlipEvent : StickerIconEvent {
    override fun onActionDown(stickerView: StickerView?, event: MotionEvent?) {}
    override fun onActionMove(stickerView: StickerView, event: MotionEvent) {}
    override fun onActionUp(stickerView: StickerView, event: MotionEvent?) {
        stickerView.flipCurrentSticker(flipDirection)
    }

    @get:Flip
    protected abstract val flipDirection: Int


}
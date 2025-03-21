package com.minshoki.image_editor.feature.sticker

import android.view.MotionEvent

class ZoomIconEvent : StickerIconEvent {
    override fun onActionDown(stickerView: StickerView?, event: MotionEvent?) {}
    override fun onActionMove(stickerView: StickerView, event: MotionEvent) {
        stickerView.zoomAndRotateCurrentSticker(event)
    }

    override fun onActionUp(stickerView: StickerView, event: MotionEvent?) {
        if (stickerView.onStickerOperationListener != null) {
            stickerView.currentSticker?.let {
                stickerView.onStickerOperationListener!!
                    .onStickerZoomFinished(it)
            }
        }
    }
}
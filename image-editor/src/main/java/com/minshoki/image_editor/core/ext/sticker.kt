package com.minshoki.image_editor.core.ext

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val MIN_FACE_SIZE = 100

suspend fun Context.stickerAsDrawable(url: String): Drawable {
    return withContext(Dispatchers.IO) {
        Glide.with(this@stickerAsDrawable)
            .asDrawable()
            .load(url)
            .override(512)
            .submit().get()
    }
}
fun Rect.ifFaceMinSize(): Rect {
    val size = max(width(), height())
    val widthDiff = MIN_FACE_SIZE -size/2
    val heightDiff = MIN_FACE_SIZE -size/2
    if(size < MIN_FACE_SIZE) {
        this.left += widthDiff
        this.right -= widthDiff
        this.top += heightDiff
        this.bottom -= heightDiff
        return this
    } else {
        return this
    }
}
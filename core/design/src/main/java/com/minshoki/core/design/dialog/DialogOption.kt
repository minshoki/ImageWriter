package com.minshoki.core.design.dialog

import android.text.SpannedString

class DialogOption {
    internal var positiveButton: String = "확인"
    internal var negativeButton: String = "취소"
    internal var positiveButtonTextColorResource: Int = 0
    internal var positiveListener: () -> Unit = {}
    internal var negativeListener: () -> Unit = {}
    internal var title: SpannedString = SpannedString("")
    internal var message: SpannedString = SpannedString("")
    internal var useSignleButton: Boolean = false
    internal var isCenterTitle: Boolean = false
    fun title(text: String) = apply { this.title = SpannedString(text) }
    fun message(text: String) = apply { this.message = SpannedString(text) }
    fun messageSpannable(spannable: SpannedString) = apply { this.message = spannable }
    fun titleSpannable(spannable: SpannedString) = apply { this.title = spannable }
    fun centerTitle() = apply { this.isCenterTitle = true }
    fun positiveButton(text: String) = apply { this.positiveButton = text }
    fun negativeButton(text: String) = apply { this.negativeButton = text }
    fun positiveListener(listener: () -> Unit) = apply { this.positiveListener = listener }
    fun negativeListener(listener: () -> Unit) = apply { this.negativeListener = listener }

    fun useSingleButton() = apply { this.useSignleButton = true }

    fun setPositiveButtonTextColor(colorRes: Int) = apply {
        this.positiveButtonTextColorResource = colorRes
    }
}
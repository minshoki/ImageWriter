package com.minshoki.image_editor.core.ext

import android.view.View

fun View.haptic() {
    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
}

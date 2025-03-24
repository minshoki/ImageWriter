package com.minshoki.core.util

import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

fun View.showKeyboard(window: Window) {
    WindowCompat.getInsetsController(window, this)
        .show(WindowInsetsCompat.Type.ime())
}

fun View.hideKeyboard(window: Window) {
    WindowCompat.getInsetsController(window, this)
        .hide(WindowInsetsCompat.Type.ime())
}
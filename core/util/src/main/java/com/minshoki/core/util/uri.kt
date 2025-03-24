package com.minshoki.core.util

import android.content.Context
import android.net.Uri

fun Uri.isGif(context: Context): Boolean {
    return context.contentResolver.getType(this)?.contains("gif") == true
}
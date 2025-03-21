package com.minshoki.image_editor.feature.crop

import android.net.Uri

sealed class CropException(message: String) : Exception(message) {
    class Cancellation :
        CropException("cropping has been cancelled by the user")

    class FailedToLoadBitmap(uri: Uri, message: String?) :
        CropException("Failed to load sampled bitmap: $uri\r\n$message")

    class FailedToDecodeImage(uri: Uri) :
        CropException("Failed to decode image: $uri")
}

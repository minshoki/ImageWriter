package com.minshoki.image_editor.feature.crop

import android.net.Uri

data class CropImageContractOptions(
    val uri: Uri?,
    val cropImageOptions: CropImageOptions,
)

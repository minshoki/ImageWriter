package com.minshoki.image_editor.core.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.minshoki.image_editor.core.EditorMode
import com.minshoki.image_editor.core.ImageEditorHolder
import com.minshoki.image_editor.model.ImageEditorViewerModel
import com.minshoki.image_editor.model.ImageEditorViewerSimpleModel
import com.minshoki.image_editor.ui.editor.ImageEditorActivity

class ImageEditorContract: ActivityResultContract<ImageEditorContract.Request, ImageEditorContract.Result?>() {

    companion object {
        const val EXTRA_INITIAL_EDITOR_MODE = "extra.initial.editor.mode"
        const val EXTRA_VIEWER_MODEL_HOLDER_KEY = "extra.viewer.model.holder.key"
        const val EXTRA_STICKER_INDEX = "extra.sticker.index"
        const val RESULT_EXTRA_VIEWER_MODEL_HOLDER_KEY = "result.extra.viewer.model.holder.key"
    }

    data class Request(
        val editorMode: EditorMode,
        val item: ImageEditorViewerSimpleModel,
        val selectStickerPosition: Int? = null,
    )
    data class Result(
        val item: ImageEditorViewerModel
    )

    override fun createIntent(context: Context, input: Request): Intent {
        val key = ImageEditorHolder.putViewerSimpleModelData(model = input.item)
        return Intent(context, ImageEditorActivity::class.java).apply {
            putExtra(EXTRA_INITIAL_EDITOR_MODE, input.editorMode)
            putExtra(EXTRA_VIEWER_MODEL_HOLDER_KEY, key)
            putExtra(EXTRA_STICKER_INDEX, input.selectStickerPosition)
        }
    }


    override fun parseResult(resultCode: Int, intent: Intent?): Result? {
        return if (resultCode == Activity.RESULT_CANCELED) {
            null
        } else {
            val key = intent?.getStringExtra(RESULT_EXTRA_VIEWER_MODEL_HOLDER_KEY) ?: return null
            return Result(
                item = ImageEditorHolder.getViewerModelData(key = key) ?: return null
            )
        }
    }

}
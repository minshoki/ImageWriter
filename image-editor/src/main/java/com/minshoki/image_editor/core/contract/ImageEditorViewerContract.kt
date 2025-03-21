package com.minshoki.image_editor.core.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import com.minshoki.image_editor.core.ImageEditorHolder
import com.minshoki.image_editor.ui.viewer.ImageEditorViewerActivity

class ImageEditorViewerContract :
    ActivityResultContract<ImageEditorViewerContract.Request, ImageEditorViewerContract.Result?>() {

    companion object {
        const val RESULT_EXTRA_URI_LIST_HOLDER_KEY = "result.extra.uri.list.holder.key"
        const val RESULT_EXTRA_REMOTE_IMAGES_URI_LIST_KEY = "result.extra.remote.images.uri.list.key"
        const val RESULT_EXTRA_URI_AND_ORIGINAL_URI_HOLDER_KEY = "result.extra.uri.and.original.uri.holder.key"
        const val RESULT_EXTRA_SELECT_INDEX = "result.extra.select.index"
        const val RESULT_EXTRA_PREFIX = "result.extra.prefix"
        const val EXTRA_ORIGINAL_URI_LIST_HOLDER_KEY = "extra.original.uri.list.holder.key"
        const val EXTRA_SELECT_INDEX = "extra.select.index"
        const val EXTRA_USE_FORCE_COMPLETE = "extra.use.force.complete"
        const val RESULT_OK_FROM_BACK_KEY = 34833
        const val EXTRA_PREFIX = "extra.prefix"
    }

    data class Request(
        val uris: List<Uri>,
        val selectIndex: Int = 0,
        val prefix: String = "${System.currentTimeMillis()}",
        val useForceComplete: Boolean = false, //완료버튼시 앞에 갤러리도 종료할 경우 true
    )
    data class Result(
        val completed: Boolean,
        val selectIndex: Int,
        val uris: List<Uri>,
        val uriAndOriginalUri: List<Pair<Uri, Uri>>,
        val remoteImageUris: HashMap<Uri, String> = hashMapOf()
    )


    override fun createIntent(context: Context, input: Request): Intent {
        val key = ImageEditorHolder.putUriData(input.uris)
        return Intent(context, ImageEditorViewerActivity::class.java).apply {
            putExtra(EXTRA_ORIGINAL_URI_LIST_HOLDER_KEY, key)
            putExtra(EXTRA_SELECT_INDEX, input.selectIndex)
            putExtra(EXTRA_PREFIX, input.prefix)
            putExtra(EXTRA_USE_FORCE_COMPLETE, input.useForceComplete)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result? {
        return if (resultCode == Activity.RESULT_CANCELED) {
            null
        } else {
            val key = intent?.getStringExtra(RESULT_EXTRA_URI_LIST_HOLDER_KEY) ?: return null
            val key2 = intent.getStringExtra(RESULT_EXTRA_URI_AND_ORIGINAL_URI_HOLDER_KEY) ?: return null
            val selectIndex = intent.getIntExtra(RESULT_EXTRA_SELECT_INDEX, 0)
            val remoteKey = intent.getStringExtra(RESULT_EXTRA_REMOTE_IMAGES_URI_LIST_KEY)
            val remoteData = remoteKey?.let { ImageEditorHolder.getRemoteImagesUrlData(it) } ?: hashMapOf()
            val result = Result(
                uris = ImageEditorHolder.getUriData(key = key) ?: return null,
                uriAndOriginalUri = ImageEditorHolder.getUriAndOriginalUriData(key = key2) ?: return null,
                completed = resultCode != RESULT_OK_FROM_BACK_KEY,
                selectIndex = selectIndex,
                remoteImageUris = remoteData
            )
            Log.i("ImageEditorViewerContract", "remoteImageUris ${result.remoteImageUris}")
            Log.i("ImageEditorViewerContract", "result $result")
            return result
        }
    }

}
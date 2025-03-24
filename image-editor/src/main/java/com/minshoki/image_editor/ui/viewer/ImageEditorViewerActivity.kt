package com.minshoki.image_editor.ui.viewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.bumptech.glide.Glide
import com.minshoki.core.design.dialog.dialog
import com.minshoki.core.design.safePlayAnimation
import com.minshoki.image_compress.ImageCompress
import com.minshoki.image_editor.R
import com.minshoki.image_editor.core.EditorMode
import com.minshoki.image_editor.core.ImageEditorHolder
import com.minshoki.image_editor.core.base.ImageEditorBaseActivity
import com.minshoki.image_editor.core.contract.ImageEditorContract
import com.minshoki.image_editor.core.contract.ImageEditorViewerContract
import com.minshoki.image_editor.core.contract.ImageEditorViewerContract.Companion.RESULT_OK_FROM_BACK_KEY
import com.minshoki.image_editor.core.deleteImageEditorDataFile
import com.minshoki.image_editor.core.ext.imageEditorPrefix
import com.minshoki.image_editor.core.ext.removeEditorSuffix
import com.minshoki.image_editor.core.ext.toBitmap
import com.minshoki.image_editor.core.getOriginalUri
import com.minshoki.image_editor.core.getStickersFromLoadImageEditorData
import com.minshoki.image_editor.core.imageEditorCacheDir
import com.minshoki.image_editor.core.loadImageEditorData
import com.minshoki.image_editor.core.saveImageEditorDataEmptyString
import com.minshoki.image_editor.databinding.ActivityImageEditorViewerBinding
import com.minshoki.image_editor.feature.sticker.AiSticker
import com.minshoki.image_editor.feature.sticker.BlurSticker
import com.minshoki.image_editor.feature.sticker.DrawableSticker
import com.minshoki.image_editor.feature.sticker.MosaicSticker
import com.minshoki.image_editor.feature.sticker.TextSticker
import com.minshoki.image_editor.model.ImageEditorViewerModel
import com.minshoki.image_editor.model.ImageEditorViewerSimpleModel
import com.minshoki.image_editor.viewmodel.ImageEditorViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageEditorViewerActivity : ImageEditorBaseActivity<ActivityImageEditorViewerBinding>() {
    override val layoutId: Int
        get() = R.layout.activity_image_editor_viewer

    private val imageEditorViewModel: ImageEditorViewerViewModel by viewModels()
    private val editorViewerAdapter: ImageEditorViewerPagerAdapter by lazy {
        ImageEditorViewerPagerAdapter { item, selectedSticker, selectedStickerIndex ->
            val mode = when (selectedSticker) {
                is TextSticker -> EditorMode.TEXT_STICKER
                is BlurSticker, is AiSticker, is DrawableSticker, is MosaicSticker -> EditorMode.STICKER
                else -> null
            } ?: return@ImageEditorViewerPagerAdapter
            editorContract.launch(
                ImageEditorContract.Request(
                    editorMode = mode,
                    item = item,
                    selectStickerPosition = selectedStickerIndex
                ),
                ActivityOptionsCompat.makeCustomAnimation(this@ImageEditorViewerActivity, 0, 0)
            )
        }
    }

    private val editorContract = registerForActivityResult(ImageEditorContract()) { result ->
        if (result != null) {
            imageEditorViewModel.sendAction(
                ImageEditorViewerViewModel.ImageEditorViewerAction.OnResultUpdatedFromEditor(
                    result.item
                )
            )
        }
    }

    private val pageChangedCallback = object : OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            imageEditorViewModel.sendAction(
                ImageEditorViewerViewModel.ImageEditorViewerAction.SelectedPage(
                    page = position
                )
            )
        }
    }

    override fun initView() {
        super.initView()
        binding.viewPager.adapter = editorViewerAdapter
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val key = ImageEditorHolder.putUriData(imageEditorViewModel.getUriList())
        outState.putString(ImageEditorViewerContract.EXTRA_ORIGINAL_URI_LIST_HOLDER_KEY, key)
        outState.putInt(ImageEditorViewerContract.EXTRA_SELECT_INDEX, binding.viewPager.currentItem)
        outState.putBoolean(
            ImageEditorViewerContract.EXTRA_USE_FORCE_COMPLETE,
            imageEditorViewModel.getUseForceComplete()
        )
        outState.putString(ImageEditorViewerContract.EXTRA_PREFIX, imageEditorViewModel.getPrefix())
    }

    override fun afterOnCreate(savedInstanceState: Bundle?) {
        super.afterOnCreate(savedInstanceState)
        val key =
            if (savedInstanceState != null) savedInstanceState.getString(ImageEditorViewerContract.EXTRA_ORIGINAL_URI_LIST_HOLDER_KEY)
                ?: return
            else intent.extras?.getString(ImageEditorViewerContract.EXTRA_ORIGINAL_URI_LIST_HOLDER_KEY)
                ?: return

        val pathList = ImageEditorHolder.getUriData(key) ?: emptyList()

        val selectIndex =
            savedInstanceState?.getInt(ImageEditorViewerContract.EXTRA_SELECT_INDEX, 0)
                ?: (intent.extras?.getInt(ImageEditorViewerContract.EXTRA_SELECT_INDEX, 0) ?: 0)

        val useForceComplete =
            savedInstanceState?.getBoolean(
                ImageEditorViewerContract.EXTRA_USE_FORCE_COMPLETE,
                false
            )
                ?: (intent.extras?.getBoolean(
                    ImageEditorViewerContract.EXTRA_USE_FORCE_COMPLETE,
                    false
                ) ?: false)

        val prefix = savedInstanceState?.getString(ImageEditorViewerContract.EXTRA_PREFIX)
            ?: (intent.extras?.getString(ImageEditorViewerContract.EXTRA_PREFIX)) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            binding.includeLoading.clLoading.isVisible = true
            binding.includeLoading.lottieLoading.safePlayAnimation()

            val items = pathList.map { it.toString() }
                .chunked(5)
                .flatMap { chunk ->
                    chunk.map { path ->
                        async(Dispatchers.IO) {
                            return@async if (path.startsWith("http")) {
                                bitmapFromRemote(prefix = prefix, remoteImagePath = path)
                            } else {
                                val uri = path.toUri()
                                if (uri.scheme == "file" && uri.toFile().startsWith(cacheDir)) {
                                    try {
                                        bitmapFromFile(uri)
                                    } catch (e: Exception) {
                                        val originalUri =
                                            uri.getOriginalUri(this@ImageEditorViewerActivity)
                                                ?: return@async null
                                        bitmapFromContent(
                                            prefix = prefix,
                                            uri = Uri.parse(originalUri)
                                        )
                                    }
                                } else {
                                    try {
                                        bitmapFromContent(prefix = prefix, uri = uri)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        null
                                    }
                                }
                            }
                        }
                    }
                        //.filterNotNull()
                        .awaitAll().filterNotNull()
                }


            imageEditorViewModel.sendAction(
                ImageEditorViewerViewModel.ImageEditorViewerAction.InitData(
                    items = items,
                    selectIndex = selectIndex,
                    useForceComplete = useForceComplete,
                    prefix = prefix
                )
            )
        }
    }

    private suspend fun bitmapFromRemote(
        prefix: String,
        remoteImagePath: String
    ): ImageEditorViewerSimpleModel {
//        val bitmapFromRemote = withContext(Dispatchers.IO) {
//            Glide.with(this@ImageEditorViewerActivity)
//                .asBitmap()
//                .load(remoteImagePath)
//                .override(360, 360)
//                .submit().get()
//        }
//        val cacheDir = "${imageEditorCacheDir}${prefix}${File.separator}remote_original"
//        val compressBitmap = HiImageCompress.compress(
//            this@ImageEditorViewerActivity,
//            cacheDir = cacheDir,
//            bitmap = bitmapFromRemote,
//            fileName = "${System.currentTimeMillis()}.jpg"
//        )
//        val uri = compressBitmap.file.toUri()
//        saveImageEditorDataEmptyString(uri = uri, prefix = prefix, remoteImagePath = remoteImagePath)
        return ImageEditorViewerSimpleModel(
            origin = ImageEditorViewerModel.Origin.Remote(url = remoteImagePath),
            originalUri = Uri.EMPTY,
            updatedUri = Uri.EMPTY,
            stickers = emptyList(),
            key = remoteImagePath.toUri().removeEditorSuffix(),
            prefix = prefix,
            isUpdatedBitmap = false,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    private suspend fun bitmapFromFile(uri: Uri): ImageEditorViewerSimpleModel {
        return withContext(Dispatchers.IO) {
            val itemKey = uri.removeEditorSuffix()
            val itemPrefix = uri.imageEditorPrefix()
            var data = loadImageEditorData(key = itemKey, prefix = itemPrefix)
            if (data == null) {
                data = saveImageEditorDataEmptyString(
                    uri = uri,
                    prefix = itemPrefix,
                    remoteImagePath = null
                )
            }
            val originalUri = data.originalPathFromUri.toUri()
            val stickers = getStickersFromLoadImageEditorData(itemKey, itemPrefix)
            ImageEditorViewerSimpleModel(
                originalUri = originalUri,
                origin = if (data.remoteImagePath != null) ImageEditorViewerModel.Origin.Remote(data.remoteImagePath.toString()) else ImageEditorViewerModel.Origin.Local,
                updatedUri = uri,
                stickers = stickers,
                key = itemKey,
                prefix = itemPrefix,
                isUpdatedBitmap = data.isUpdatedBitmap,
                lastUpdateTimestamp = System.currentTimeMillis()
            )
        }
    }

    private suspend fun bitmapFromContent(
        prefix: String,
        uri: Uri
    ): ImageEditorViewerSimpleModel {
//        val bitmap = uri.toBitmap(this@ImageEditorViewerActivity).copy(
//            Bitmap.Config.ARGB_8888, true
//        )
//        val rotate = uri.getExifRotationDegrees(this@ImageEditorViewerActivity)
//        val rotateBitmap = bitmap
//            if (rotate != 0) {
//            bitmap.rotateBitmap(rotate.toFloat())
//        } else {
//            bitmap
//        }

        return ImageEditorViewerSimpleModel(
            originalUri = uri,
            origin = ImageEditorViewerModel.Origin.Local,
            updatedUri = Uri.EMPTY,
            stickers = emptyList(),
            key = uri.removeEditorSuffix(),
            prefix = prefix,
            isUpdatedBitmap = false,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    override fun initListener() {
        super.initListener()
        binding.viewPager.setPageTransformer { _, _ -> }
        binding.viewPager.registerOnPageChangeCallback(pageChangedCallback)
        binding.btnRotate.setOnClickListener {
            imageEditorViewModel.sendAction(
                ImageEditorViewerViewModel.ImageEditorViewerAction.OnClickEditorMode(
                    mode = EditorMode.CROP_AND_ROTATE,
                    position = binding.viewPager.currentItem
                )
            )
        }
        binding.btnSticker.setOnClickListener {
            imageEditorViewModel.sendAction(
                ImageEditorViewerViewModel.ImageEditorViewerAction.OnClickEditorMode(
                    mode = EditorMode.STICKER,
                    position = binding.viewPager.currentItem
                )
            )
        }
        binding.btnTextSticker.setOnClickListener {
            imageEditorViewModel.sendAction(
                ImageEditorViewerViewModel.ImageEditorViewerAction.OnClickEditorMode(
                    mode = EditorMode.TEXT_STICKER,
                    position = binding.viewPager.currentItem
                )
            )
        }

        binding.ivBack.setOnClickListener { onBackPressedCompat() }

        binding.btnResetImageEdit.setOnClickListener {
            imageEditorViewModel.sendAction(
                ImageEditorViewerViewModel.ImageEditorViewerAction.OnClickResetImageEdit(position = binding.viewPager.currentItem)
            )
        }

        binding.btnComplete.setOnClickListener {
            imageEditorViewModel.sendAction(
                ImageEditorViewerViewModel.ImageEditorViewerAction.OnClickComplete
            )
        }
    }

    private fun showLoading() {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.clDim.isVisible = true
            binding.includeLoading.clLoading.isVisible = true
            binding.includeLoading.lottieLoading.safePlayAnimation()
        }
    }

    private fun hideLoading() {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.includeLoading.lottieLoading.cancelAnimation()
            binding.clDim.isVisible = false
            binding.includeLoading.clLoading.isVisible = false
        }
    }

    override fun initViewModel() {
        super.initViewModel()

        imageEditorViewModel.imageEditorViewerUiState
            .onEach { state ->
                if (state.setResult != null) {
                    lifecycleScope.launch {
                        showLoading()
                        val remoteImageUris = hashMapOf<Uri, String>()
                        val uris =
                            state.setResult.second
                                .chunked(10)
                                .flatMap {
                                    it.map {
                                        async(Dispatchers.IO) {
                                            val resultUri = it.getResultUri()
                                            if (it.origin is ImageEditorViewerModel.Origin.Remote) {
                                                if(it.originalUri == Uri.EMPTY) {
                                                    remoteImageUris[it.origin.url.toUri()] = it.origin.url
                                                } else {
                                                    remoteImageUris[resultUri] = it.origin.url
                                                }
                                            }
                                            if(it.originalUri == Uri.EMPTY) {
                                                return@async Uri.EMPTY to Uri.EMPTY
                                            }
                                            if (resultUri.scheme != "file") {
                                                val fileName = it.key + ".jpg"
                                                val cacheDir =
                                                    if (resultUri.pathSegments.contains("my_image")) {
                                                        saveImageEditorDataEmptyString(
                                                            uri = resultUri,
                                                            prefix = it.prefix,
                                                            remoteImagePath = null
                                                        )
                                                        "${imageEditorCacheDir}${it.prefix}${File.separator}original"
                                                    } else {
                                                        saveImageEditorDataEmptyString(
                                                            uri = resultUri,
                                                            prefix = it.prefix,
                                                            remoteImagePath = if (resultUri.toString()
                                                                    .startsWith("http")
                                                            ) resultUri.toString() else null
                                                        )
                                                        "${imageEditorCacheDir}${it.prefix}"
                                                    }
                                                try {
                                                    val result = ImageCompress.compress(
                                                        this@ImageEditorViewerActivity,
                                                        cacheDir = cacheDir,
                                                        bitmap = it.originalUri.toBitmap(this@ImageEditorViewerActivity),
                                                        fileName = fileName
                                                    )
                                                    result.file.toUri() to it.originalUri
                                                } catch (e: Exception) {
                                                    return@async Uri.EMPTY to Uri.EMPTY
                                                }
                                            } else {
                                                val originalUri =
                                                    resultUri.getOriginalUri(this@ImageEditorViewerActivity)
                                                if (originalUri == null) {
                                                    val fileName = it.key + ".jpg"
                                                    val cacheDir =
                                                        if (resultUri.pathSegments.contains("my_image")) {
                                                            saveImageEditorDataEmptyString(
                                                                uri = resultUri,
                                                                prefix = it.prefix,
                                                                remoteImagePath = null
                                                            )
                                                            "${imageEditorCacheDir}${it.prefix}${File.separator}original"
                                                        } else {
                                                            saveImageEditorDataEmptyString(
                                                                uri = resultUri,
                                                                prefix = it.prefix,
                                                                remoteImagePath = if (resultUri.toString()
                                                                        .startsWith("http")
                                                                ) resultUri.toString() else null
                                                            )
                                                            "${imageEditorCacheDir}${it.prefix}"
                                                        }
                                                    val result = ImageCompress.compress(
                                                        this@ImageEditorViewerActivity,
                                                        cacheDir = cacheDir,
                                                        bitmap = it.originalUri.toBitmap(this@ImageEditorViewerActivity),
                                                        fileName = fileName
                                                    )
                                                    result.file.toUri() to it.originalUri
                                                } else {
                                                    resultUri to originalUri.toUri()
                                                }
                                            }
                                        }
                                    }.awaitAll()
                                }


                        hideLoading()
                        val key = ImageEditorHolder.putUriData(uris.map { it.first })
                        val key2 = ImageEditorHolder.putUriAndOriginalUriData(uris)
                        setResult(
                            RESULT_OK,
                            Intent().apply {
                                val remoteImagesKey =
                                    ImageEditorHolder.putRemoteImagesUrlData(data = remoteImageUris)
                                putExtra(
                                    ImageEditorViewerContract.RESULT_EXTRA_REMOTE_IMAGES_URI_LIST_KEY,
                                    remoteImagesKey
                                )
                                putExtra(
                                    ImageEditorViewerContract.RESULT_EXTRA_URI_AND_ORIGINAL_URI_HOLDER_KEY,
                                    key2
                                )
                                putExtra(
                                    ImageEditorViewerContract.RESULT_EXTRA_URI_LIST_HOLDER_KEY,
                                    key
                                )
                                putExtra(
                                    ImageEditorViewerContract.RESULT_EXTRA_SELECT_INDEX,
                                    binding.viewPager.currentItem
                                )
                            }
                        )
                        finish()
                    }
                }

                if (state.setResultFromBackKey != null) {
                    val key =
                        ImageEditorHolder.putUriData(state.setResultFromBackKey.map { it.getResultUri() })
                    val key2 =
                        ImageEditorHolder.putUriAndOriginalUriData(state.setResultFromBackKey.map { it.getResultUri() to it.originalUri })
                    setResult(
                        RESULT_OK_FROM_BACK_KEY,
                        Intent().apply {
                            putExtra(
                                ImageEditorViewerContract.RESULT_EXTRA_URI_AND_ORIGINAL_URI_HOLDER_KEY,
                                key2
                            )
                            putExtra(
                                ImageEditorViewerContract.RESULT_EXTRA_URI_LIST_HOLDER_KEY,
                                key
                            )
                            putExtra(
                                ImageEditorViewerContract.RESULT_EXTRA_SELECT_INDEX,
                                binding.viewPager.currentItem
                            )
                        }
                    )
                    finish()
                }

                if (state.makeData != null) {
                    val path = state.makeData.first.origin.remoteImagePath() ?: return@onEach
                    val bitmapFromRemote = withContext(Dispatchers.IO) {
                        try {
                            Glide.with(this@ImageEditorViewerActivity)
                                .asBitmap()
                                .load(path)
                                .override(360, 360)
                                .submit().get()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if(bitmapFromRemote == null) {
                        dialog {
                            message(getString(R.string.image_editor_failed_load_image))
                            useSingleButton()
                        }
                        return@onEach
                    }
                    val cacheDir =
                        "${imageEditorCacheDir}${state.makeData.first.prefix}${File.separator}remote_original"
                    val compressBitmap = ImageCompress.compress(
                        this@ImageEditorViewerActivity,
                        cacheDir = cacheDir,
                        bitmap = bitmapFromRemote,
                        fileName = "${state.makeData.first.key.removeSuffix(".jpg")?.removeSuffix(".gif").toString()}.jpg"
                    )
                    val uri = compressBitmap.file.toUri()
                    saveImageEditorDataEmptyString(uri = uri, prefix = state.makeData.first.prefix, remoteImagePath = path)

                    imageEditorViewModel.sendAction(
                        ImageEditorViewerViewModel.ImageEditorViewerAction.UpdateEditorItemAfterEditorMode(
                            mode = state.makeData.third,
                            position = state.makeData.second,
                            originalUri = uri
                        )
                    )
                }

                if (state.setDisableResetImageEditButton) {
                    binding.btnResetImageEdit.isEnabled = false
                }

                if (state.setEnableResetImageEditButton) {
                    binding.btnResetImageEdit.isEnabled = true
                }

                if (state.updatedItems != null) {
                    editorViewerAdapter.submitList(state.updatedItems) {
                        if (state.afterUpdateItemsMoveIndex != null) {
                            binding.viewPager.setCurrentItem(state.afterUpdateItemsMoveIndex, false)
                        }

                        lifecycleScope.launch(Dispatchers.Main) {
                            binding.includeLoading.clLoading.isVisible = false
                            binding.includeLoading.lottieLoading.cancelAnimation()
                        }
                    }
                }

                if (state.resetImage != null) {
                    deleteImageEditorDataFile(
                        state.resetImage.key,
                        prefix = state.resetImage.prefix,
                        remoteImagePath = state.resetImage.origin.remoteImagePath(),
                        shouldRemoveBitmapFile = true
                    )
                    val key = state.resetImage.key
                    val fileName = "$key.jpg"
                    ImageCompress.compress(
                        this@ImageEditorViewerActivity,
                        cacheDir = "${imageEditorCacheDir}${state.resetImage.prefix}",
                        bitmap = state.resetImage.originalUri.toBitmap(this@ImageEditorViewerActivity),
                        fileName = fileName
                    )
                }

                if (state.showPopupResetImageEdit != null) {
                    dialog {
                        message(getString(R.string.image_editor_reset_edit_popup_message))
                        positiveListener {
                            imageEditorViewModel.sendAction(
                                ImageEditorViewerViewModel.ImageEditorViewerAction.ResetImageEdit(
                                    position = state.showPopupResetImageEdit
                                )
                            )
                        }
                    }
                }

                if (state.updateIndicator != null) {
                    binding.tvIndicator.text =
                        "${state.updateIndicator.first} / ${state.updateIndicator.second}"
                }

                if (state.openEditorMode != null) {
                    editorContract.launch(
                        ImageEditorContract.Request(
                            editorMode = state.openEditorMode.first,
                            item = state.openEditorMode.second
                        ),
                        ActivityOptionsCompat.makeCustomAnimation(
                            this@ImageEditorViewerActivity,
                            0,
                            0
                        )
                    )
                }

                if(state.shouldMergeBitmapForMoveRotate) {
                    dialog {
                        message(getString(R.string.image_editor_crop_popup_message))
                        positiveListener {
                            imageEditorViewModel.sendAction(
                                ImageEditorViewerViewModel.ImageEditorViewerAction.OnClickEditorMode(
                                    mode = EditorMode.CROP_AND_ROTATE,
                                    position = binding.viewPager.currentItem,
                                    forceCrop = true
                                )
                            )
                        }
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onDestroy() {
        binding.viewPager.unregisterOnPageChangeCallback(pageChangedCallback)
        super.onDestroy()
    }

    override fun handleBackPressed() {
        imageEditorViewModel.sendAction(
            ImageEditorViewerViewModel.ImageEditorViewerAction.OnClickBackKey
        )
    }
}
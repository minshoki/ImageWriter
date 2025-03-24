package com.minshoki.image_editor.ui.editor

import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.Layout
import android.util.Log
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doOnTextChanged
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.transition.Slide
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialElevationScale
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.minshoki.core.design.dialog.dialog
import com.minshoki.core.design.safePlayAnimation
import com.minshoki.core.util.SoftKeyboardWatcher
import com.minshoki.core.util.bitmap.point
import com.minshoki.core.util.bitmap.resizeBitmap
import com.minshoki.core.util.bitmap.rotate
import com.minshoki.core.util.bitmap.rotateBitmap
import com.minshoki.core.util.dp
import com.minshoki.core.util.hideKeyboard
import com.minshoki.core.util.showKeyboard
import com.minshoki.image_compress.ImageCompress
import com.minshoki.image_editor.R
import com.minshoki.image_editor.core.EditorMode
import com.minshoki.image_editor.core.ImageEditorHolder
import com.minshoki.image_editor.core.TextStickerColors
import com.minshoki.image_editor.core.base.ImageEditorBaseActivity
import com.minshoki.image_editor.core.contract.ImageEditorContract
import com.minshoki.image_editor.core.ext.ifFaceMinSize
import com.minshoki.image_editor.core.ext.stickerAsDrawable
import com.minshoki.image_editor.core.ext.toBitmap
import com.minshoki.image_editor.core.imageEditorCacheDir
import com.minshoki.image_editor.core.loadImageEditorData
import com.minshoki.image_editor.core.makeSticker
import com.minshoki.image_editor.core.saveImageEditorDataEmptyString
import com.minshoki.image_editor.core.saveImageEditorDataString
import com.minshoki.image_editor.databinding.ActivityImageEditorBinding
import com.minshoki.image_editor.feature.crop.CropImage
import com.minshoki.image_editor.feature.crop.CropImageOptions
import com.minshoki.image_editor.feature.crop.CropImageView
import com.minshoki.image_editor.feature.facedetection.FaceDetectionProcess
import com.minshoki.image_editor.feature.sticker.AiSticker
import com.minshoki.image_editor.feature.sticker.BlurSticker
import com.minshoki.image_editor.feature.sticker.DrawableSticker
import com.minshoki.image_editor.feature.sticker.MosaicSticker
import com.minshoki.image_editor.feature.sticker.Sticker
import com.minshoki.image_editor.feature.sticker.StickerView
import com.minshoki.image_editor.feature.sticker.TextSticker
import com.minshoki.image_editor.model.AiStickerDataModel
import com.minshoki.image_editor.model.ImageEditorSticker
import com.minshoki.image_editor.model.ImageEditorViewerModel
import com.minshoki.image_editor.model.RemoteConfigStickersValueModel
import com.minshoki.image_editor.viewmodel.ImageEditorViewModel
import com.minshoki.image_editor.viewmodel.ImageTextStickerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File
import kotlin.math.max
import kotlin.math.min


class ImageEditorActivity : ImageEditorBaseActivity<ActivityImageEditorBinding>(),
    CropImageView.OnSetImageUriCompleteListener,
    CropImageView.OnCropImageCompleteListener {
    override val layoutId: Int
        get() = R.layout.activity_image_editor

    private val imageTextStickerViewModel: ImageTextStickerViewModel by viewModels()
    private val imageEditorViewModel: ImageEditorViewModel by viewModels()
    private val stickerRecyclerAdapter: StickerRecyclerAdapter by lazy {
        StickerRecyclerAdapter { sticker ->
            imageEditorViewModel.sendAction(
                ImageEditorViewModel.ImageEditorAction.AddSticker(
                    sticker = sticker
                )
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val item = imageEditorViewModel.getItem() ?: return
        val key = ImageEditorHolder.putViewerModelData(item)
        outState.putString(ImageEditorContract.EXTRA_VIEWER_MODEL_HOLDER_KEY, key)

        val mode = when {
            binding.btnCropAndRotate.isSelected -> EditorMode.CROP_AND_ROTATE
            binding.btnSticker.isSelected -> EditorMode.STICKER
            binding.btnTextSticker.isSelected -> EditorMode.TEXT_STICKER
            else -> EditorMode.NONE
        }
        outState.putSerializable(ImageEditorContract.EXTRA_INITIAL_EDITOR_MODE, mode)
    }

    override fun afterOnCreate(savedInstanceState: Bundle?) {
        super.afterOnCreate(savedInstanceState)
        val key =
            if (savedInstanceState != null) savedInstanceState.getString(ImageEditorContract.EXTRA_VIEWER_MODEL_HOLDER_KEY)
                ?: return
            else intent.extras?.getString(ImageEditorContract.EXTRA_VIEWER_MODEL_HOLDER_KEY)
                ?: return
        val item = ImageEditorHolder.getViewerSimpleModelData(key) ?: return

        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (savedInstanceState != null) {
                savedInstanceState.getSerializable(
                    ImageEditorContract.EXTRA_INITIAL_EDITOR_MODE,
                    EditorMode::class.java
                )
            } else {
                intent?.extras?.getSerializable(
                    ImageEditorContract.EXTRA_INITIAL_EDITOR_MODE,
                    EditorMode::class.java
                )
            }
        } else {
            if (savedInstanceState != null) {
                savedInstanceState.getSerializable(ImageEditorContract.EXTRA_INITIAL_EDITOR_MODE) as? EditorMode
            } else {
                intent?.extras?.getSerializable(ImageEditorContract.EXTRA_INITIAL_EDITOR_MODE) as? EditorMode
            }
        } ?: return

        val selectStickerIndex =
            intent.extras?.getInt(ImageEditorContract.EXTRA_STICKER_INDEX, -1) ?: -1
        var simpleItem = item
        simpleItem = simpleItem.copy(key = simpleItem.key.removeSuffix(".jpg").removeSuffix(".gif"))

        lifecycleScope.launch {
            try {
                Log.i("imageEditorTest", "item ${simpleItem.key} ${simpleItem.prefix}")
                val data = loadImageEditorData(simpleItem.key, simpleItem.prefix)
                    ?: saveImageEditorDataEmptyString(
                        uri = simpleItem.originalUri,
                        prefix = simpleItem.prefix,
                        remoteImagePath = simpleItem.origin.remoteImagePath()
                    )
                Log.i("imageEditorTest", "data $data")
                val stickers = data.stickers.mapNotNull { makeSticker(it) }
                val originBitmap = simpleItem.originalUri.toBitmap(this@ImageEditorActivity)
                val updatedBitmap = if (simpleItem.updatedUri != Uri.EMPTY) {
                    simpleItem.updatedUri.toBitmap(this@ImageEditorActivity)
                } else {
                    originBitmap
                }
                val newItem = ImageEditorViewerModel(
                    stickers = stickers,
                    prefix = simpleItem.prefix,
                    key = simpleItem.key,
                    origin = simpleItem.origin,
                    originalUri = simpleItem.originalUri,
                    rotate = data.rotate,
                    originalRotate = data.originalRotate,
                    copyOriginalBitmap = if (stickers.isEmpty()) updatedBitmap else data.progressPathFromUri.toUri()
                        .toBitmap(this@ImageEditorActivity),
                    updatedUri = simpleItem.updatedUri,
                    originalBitmap = originBitmap,
                    updatedBitmap = updatedBitmap,
                    isUpdatedBitmap = data.isUpdatedBitmap,
                    copyOriginalUri = data.progressPathFromUri.toUri(),
                )
                imageEditorViewModel.sendAction(
                    ImageEditorViewModel.ImageEditorAction.InitData(
                        item = newItem,
                        mode = mode,
                        selectStickerIndex = selectStickerIndex
                    )
                )
            } catch (_: Exception) {
                dialog {
                    useSingleButton()
                    message(getString(R.string.image_editor_failed_load_image))
                    positiveListener {
                        finish()
                    }
                }
            }
        }
    }

    override fun initViewModel() {
        super.initViewModel()
        imageTextStickerViewModel.imageTextStickerUiState
            .onEach { state ->
                if (state.selectBackgroundColor != null) {
                    setTextStickerUiBackgroundColor(state.selectBackgroundColor)
                }
                if (state.selectTextColor != null) {
                    setTextStickerUiTextColor(state.selectTextColor)
                }
                if (state.setText != null) {
                    binding.etTextSickerInput.setText(state.setText)
                    binding.etTextSickerInput.setSelection(binding.etTextSickerInput.text.toString().length)
                }
                if (state.createTextSticker != null) {
                    imageEditorViewModel.sendAction(
                        ImageEditorViewModel.ImageEditorAction.AddTextSticker(
                            state.createTextSticker
                        )
                    )
                }
                if (state.modifyTextSticker != null) {
                    imageEditorViewModel.sendAction(
                        ImageEditorViewModel.ImageEditorAction.ReplaceTextSticker(
                            state.modifyTextSticker
                        )
                    )
                }
            }
            .launchIn(lifecycleScope)

        imageEditorViewModel.imageEditorUiState
            .onEach { state ->
                if (state.initStickers != null) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        state.initStickers.first.forEach { sticker ->
                            binding.sticker.addSticker(sticker, Sticker.Position.Copy)
                        }
                        val selectSticker = state.initStickers.second
                        if (selectSticker != null) {
                            binding.sticker.selectSticker(selectSticker)
                        }
                    }
                }

                if (state.updateBitmap != null) {
                    updateImage(state.updateBitmap.first, state.updateBitmap.second, state.updateBitmapAfterChangeMode)
                }

                if (state.croppedImage) {
                    cropImage()
                }

                if (state.saveFile != null) {
                    saveFile(state.saveFile, rotate = 0, fromCrop = false)
                }

                if (state.saveFileFromCrop != null) {
                    saveFile(state.saveFileFromCrop.first, rotate = state.saveFileFromCrop.second, fromCrop = true)
                }

                if (state.changeMode != null) {
                    when (state.changeMode) {
                        EditorMode.CROP_AND_ROTATE -> {
                            changeModeCropAndRotate()
                        }

                        EditorMode.STICKER -> {
                            changeModeSticker()
                        }

                        EditorMode.TEXT_STICKER -> {
                            changeModeTextSticker()
                        }

                        else -> {
                            binding.sticker.isVisible = true
                            binding.ivCrop.isVisible = false
                        }
                    }
                }

                if (state.initCreateTextSticker) {
                    binding.clTextStickerDim.isVisible = true
                    binding.btnTextStickerTextColor.isSelected = true
                    binding.btnTextStickerBackgroundColor.isSelected = false
                    binding.clTextStickerBottomOptions.isVisible = true
                    binding.llTextStickerBackgroundColors.isVisible = false
                    binding.llTextStickerTextColors.isVisible = true
                    binding.etTextSickerInput.showKeyboard(window)
                    imageTextStickerViewModel.sendAction(ImageTextStickerViewModel.ImageTextStickerAction.InitCreate)
                }

                if (state.initModifyTextSticker != null) {
                    binding.clDeleteSticker.isVisible = false
                    binding.ivDeleteSticker.isVisible = false
                    binding.sticker.hideStickers()

                    binding.clTextStickerDim.isVisible = true
                    binding.btnTextStickerTextColor.isSelected = true
                    binding.btnTextStickerBackgroundColor.isSelected = false
                    binding.clTextStickerBottomOptions.isVisible = true
                    binding.llTextStickerBackgroundColors.isVisible = false
                    binding.llTextStickerTextColors.isVisible = true
                    binding.etTextSickerInput.showKeyboard(window)
                    imageTextStickerViewModel.sendAction(
                        ImageTextStickerViewModel.ImageTextStickerAction.InitModify(
                            state.initModifyTextSticker
                        )
                    )
                }

                if (state.setCropAndRotateBitmap != null) {
                    binding.ivCrop.clearAspectRatio()
                    binding.ivCrop.resetCropRect()
                    binding.ivCrop.clearImage()
                    binding.ivCrop.setImageCropOptions(
                        CropImageOptions(
                            showProgressBar = false,
                            outputCompressFormat = Bitmap.CompressFormat.PNG,
                            borderCornerOffset = 2.dp.toFloat(),
                            borderCornerLength = 20.dp.toFloat(),
                            borderCornerThickness = 4.dp.toFloat(),
                            borderLineThickness = 1f,
                            borderCornerColor = getColor(com.minshoki.core.design.R.color.design_color_primary_blue),
                        )
                    )
                    binding.ivCrop.guidelines = CropImageView.Guidelines.ON
                    binding.ivCrop.setImageBitmap(state.setCropAndRotateBitmap)
                }

                if (state.submitStickers != null) {
                    stickerRecyclerAdapter.submitList(state.submitStickers)
                }

                if (state.addSticker != null) {
                    if (getStickerCount() < MAX_STICKER_COUNT) {
                        binding.sticker.addSticker(state.addSticker, Sticker.Position.Center)
                    } else {
                        showToastLimitSticker()
                    }
                }

                if (state.addMosaicSticker != null) {
                    if (getStickerCount() < MAX_STICKER_COUNT) {
                        binding.sticker.addSticker(state.addMosaicSticker, Sticker.Position.Center)
                    } else {
                        showToastLimitSticker()
                    }
                }

                if (state.addTextSticker != null) {
                    if (getTextStickerCount() < MAX_STICKER_COUNT) {
                        binding.etTextSickerInput.hideKeyboard(window)
                        val textSticker = TextSticker(
                            text = state.addTextSticker.first,
                            drawable = ContextCompat.getDrawable(
                                this@ImageEditorActivity,
                                state.addTextSticker.third.drawableRes
                            )!!,
                            maxWidth = StickerView.LIMIT_STICKER_MAX_SIZE.toInt()
                        )
                            .setTypeface(
                                ResourcesCompat.getFont(
                                    this,
                                    com.minshoki.core.design.R.font.nanumsquare_otf_ac_b
                                )
                            )
                            .setTextColor(this@ImageEditorActivity, state.addTextSticker.second)
                            .setTextBackgroundColor(
                                this@ImageEditorActivity,
                                state.addTextSticker.third
                            )
                            .setTextAlign(Layout.Alignment.ALIGN_CENTER)
                            .resizeText()
                        binding.sticker.addSticker(textSticker, Sticker.Position.Center)

                    } else {
                        showToastLimitSticker()
                    }
                }

                if (state.addAiStickers != null) {
                    val canAddStickerCount = MAX_STICKER_COUNT - getStickerCount()
                    if (canAddStickerCount > 0) {
                        val stickers = state.addAiStickers.take(canAddStickerCount)
                        stickers.forEach { data ->
                            val sticker = data.first
                            val (x, y) = data.second
                            binding.sticker.addSticker(
                                sticker,
                                position = Sticker.Position.Custom(x = x, y = y)
                            )
                        }
                    } else {
                        showToastLimitSticker()
                    }
                }

                if(state.addAiBlurStickers != null) {
                    val canAddStickerCount = MAX_STICKER_COUNT - getStickerCount()
                    if (canAddStickerCount > 0) {
                        val stickers = state.addAiBlurStickers.take(canAddStickerCount)
                        stickers.forEach { data ->
                            val sticker = data.first
                            val (x, y) = data.second
                            binding.sticker.addSticker(
                                sticker,
                                position = Sticker.Position.Custom(x = x, y = y)
                            )
                        }
                    } else {
                        showToastLimitSticker()
                    }
                }

                if(state.startFaceDetectDataFromBlur != null) {
                    val rotate = state.startFaceDetectDataFromBlur.second

                    val (displayedWidth, displayedHeight) = getBitmapWidthAndHeight(bitmap = state.startFaceDetectDataFromBlur.first)

                    var isNormal = false
                    val widthFactor = if (displayedWidth == state.startFaceDetectDataFromBlur.first.width) {
                        isNormal = true
                        displayedWidth / binding.ivImage.width.toFloat()
                    } else state.startFaceDetectDataFromBlur.first.width / binding.ivImage.width.toFloat()
                    val heightFactor =
                        if (displayedWidth == state.startFaceDetectDataFromBlur.first.width) {
                            isNormal = true
                            displayedHeight / binding.ivImage.height.toFloat()
                        } else state.startFaceDetectDataFromBlur.first.height / binding.ivImage.height.toFloat()


                    val faceDetectBitmap = state.startFaceDetectDataFromBlur.first.resizeBitmap(
                        resizeWidth = displayedWidth,
                        resizeHeight = displayedHeight
                    ).rotateBitmap(
                        degress = 360 - rotate.toFloat()
                    )
                    FaceDetectionProcess.detectFace(
                        input = InputImage.fromBitmap(faceDetectBitmap, 0)
                    ) {
                        val newFaces = if (isNormal) it.map { face -> face.ifFaceMinSize() }
                        else it

                        binding.sticker.removeAllBlurSticker()
                        binding.sticker.removeAllAiSticker()
                        val faceStickers = newFaces.map { face ->

                            val constraint = if (isNormal) max(
                                max(
                                    face.width() / widthFactor,
                                    face.height() / heightFactor
                                ), 100f
                            )
                            else max(
                                max(face.width().toFloat(), face.height().toFloat()),
                                StickerView.LIMIT_STICKER_MIN_SIZE
                            )

                            val widthOffset =
                                constraint - if (isNormal) (face.width() / widthFactor) else (face.width()).toFloat()
                            val heightOffset =
                                constraint - if (isNormal) (face.height() / heightFactor) else (face.height()).toFloat()

                            val newFace = face.rotate(
                                degress = rotate,
                                originalPoint = (faceDetectBitmap.width) point (faceDetectBitmap.height)
                            )

                            val offset = max(widthOffset, heightOffset) / 2
                            val x =
                                if (isNormal) (newFace.left.toFloat() / widthFactor) - offset else (newFace.left.toFloat()) - offset
                            val y =
                                if (isNormal) (newFace.top.toFloat() / heightFactor) - offset else (newFace.top.toFloat()) - offset
                            AiStickerDataModel(size = constraint.toInt(), x = x, y = y)
                        }
                        imageEditorViewModel.sendAction(
                            ImageEditorViewModel.ImageEditorAction.LoadedFaceStickers(
                                faceStickersData = faceStickers,
                                type = ImageEditorSticker.BlurStickerModel
                            )
                        )
                    }

                }
                if (state.startFaceDetectData != null) {
                    val rotate = state.startFaceDetectData.second

                    val (displayedWidth, displayedHeight) = getBitmapWidthAndHeight(bitmap = state.startFaceDetectData.first)

                    var isNormal = false
                    val widthFactor = if (displayedWidth == state.startFaceDetectData.first.width) {
                        isNormal = true
                        displayedWidth / binding.ivImage.width.toFloat()
                    } else state.startFaceDetectData.first.width / binding.ivImage.width.toFloat()
                    val heightFactor =
                        if (displayedWidth == state.startFaceDetectData.first.width) {
                            isNormal = true
                            displayedHeight / binding.ivImage.height.toFloat()
                        } else state.startFaceDetectData.first.height / binding.ivImage.height.toFloat()


                    val faceDetectBitmap = state.startFaceDetectData.first.resizeBitmap(
                        resizeWidth = displayedWidth,
                        resizeHeight = displayedHeight
                    ).rotateBitmap(
                        degress = 360 - rotate.toFloat()
                    )

                    binding.viewToast.setMesssage(R.string.image_editor_random_ai_face_toast_message)
                    binding.viewToast.setIcon(R.drawable.ic_edit_sticker_face_ai)
                    binding.viewToast.show()

                    FaceDetectionProcess.detectFace(
                        input = InputImage.fromBitmap(faceDetectBitmap, 0)
                    ) {
                        val newFaces = if (isNormal) it.map { face -> face.ifFaceMinSize() }
                        else it

                        binding.sticker.removeAllBlurSticker()
                        binding.sticker.removeAllAiSticker()

                        val faceStickers = newFaces.map { face ->

                            val constraint = if (isNormal) max(
                                max(
                                    face.width() / widthFactor,
                                    face.height() / heightFactor
                                ), 100f
                            )
                            else max(
                                max(face.width().toFloat(), face.height().toFloat()),
                                StickerView.LIMIT_STICKER_MIN_SIZE
                            )

                            val widthOffset =
                                constraint - if (isNormal) (face.width() / widthFactor) else (face.width()).toFloat()
                            val heightOffset =
                                constraint - if (isNormal) (face.height() / heightFactor) else (face.height()).toFloat()

                            val newFace = face.rotate(
                                degress = rotate,
                                originalPoint = (faceDetectBitmap.width) point (faceDetectBitmap.height)
                            )

                            val offset = max(widthOffset, heightOffset) / 2
                            val x =
                                if (isNormal) (newFace.left.toFloat() / widthFactor) - offset else (newFace.left.toFloat()) - offset
                            val y =
                                if (isNormal) (newFace.top.toFloat() / heightFactor) - offset else (newFace.top.toFloat()) - offset
                            AiStickerDataModel(size = constraint.toInt(), x = x, y = y)
                        }
                        imageEditorViewModel.sendAction(
                            ImageEditorViewModel.ImageEditorAction.LoadedFaceStickers(
                                faceStickersData = faceStickers,
                                type = ImageEditorSticker.AiStickerModel
                            )
                        )
                    }
                }

                if (state.showTextStickerBackgroundColorSet) {
                    binding.clTextStickerBottomOptions.isVisible = true
                    binding.llTextStickerTextColors.isVisible = false
                    binding.llTextStickerBackgroundColors.isVisible = true
                    binding.btnTextStickerTextColor.isSelected = false
                    binding.btnTextStickerBackgroundColor.isSelected = true
                }

                if (state.showTextStickerTextColorSet) {
                    binding.clTextStickerBottomOptions.isVisible = true
                    binding.llTextStickerTextColors.isVisible = true
                    binding.llTextStickerBackgroundColors.isVisible = false
                    binding.btnTextStickerTextColor.isSelected = true
                    binding.btnTextStickerBackgroundColor.isSelected = false
                }

                if (state.replaceTextSticker != null) {
                    val targetTextSticker = state.replaceTextSticker.first
                    val newTextSticker = TextSticker(
                        text = state.replaceTextSticker.second.first,
                        maxWidth = StickerView.LIMIT_STICKER_MAX_SIZE.toInt()
                    )
                    newTextSticker.setMatrix(targetTextSticker.matrix)
                    newTextSticker.setTextColor(
                        this@ImageEditorActivity,
                        state.replaceTextSticker.second.second
                    )
                        .setTextAlign(Layout.Alignment.ALIGN_CENTER)
                        .setTextBackgroundColor(
                            this@ImageEditorActivity,
                            state.replaceTextSticker.second.third
                        )
                        .resizeText()
                    binding.sticker.removeSticker(targetTextSticker)
                    binding.sticker.addSticker(newTextSticker, Sticker.Position.Copy)
                    binding.etTextSickerInput.hideKeyboard(window)
                }

                if (state.showLoading) {
                    showLoading()
                }

                if (state.hideLoading) {
                    hideLoading()
                }

            }
            .launchIn(lifecycleScope)
    }

    override fun onStart() {
        super.onStart()
        binding.ivCrop.setOnSetImageUriCompleteListener(this)
        binding.ivCrop.setOnCropImageCompleteListener(this)
    }

    override fun onStop() {
        super.onStop()
        binding.etTextSickerInput.hideKeyboard(window)
        binding.sticker.showStickers()
        binding.clTextStickerBottomOptions.isVisible = false
        binding.clTextStickerDim.isVisible = false
        binding.ivCrop.setOnSetImageUriCompleteListener(null)
        binding.ivCrop.setOnCropImageCompleteListener(null)
    }

    override fun handleBackPressed() {
        if (binding.clDim.isVisible || binding.includeLoading.clLoading.isVisible || binding.includeLoading.lottieLoading.isAnimating) {
            return
        }
        if (binding.clTextStickerDim.isVisible) {
            binding.sticker.showStickers()
            binding.clTextStickerDim.isVisible = false
            binding.btnTextStickerTextColor.isSelected = false
            binding.btnTextStickerBackgroundColor.isSelected = false
        } else {
            super.handleBackPressed()
        }
    }

    override fun initView() {
        super.initView()
        binding.rvStickers.adapter = stickerRecyclerAdapter
        binding.etTextSickerInput.setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        binding.etTextSickerInput.setSingleLine(false)
        binding.etTextSickerInput.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION)
    }

    override fun initListener() {
        super.initListener()
        OpenCVLoader.initLocal()
        fetchRemoteConfig()

        SoftKeyboardWatcher(window).startWatch(
            activity = this, lifecycleOwner = this,
            callback = object : SoftKeyboardWatcher.WatcherCallback {
                override fun onChanged(
                    imeHeight: Int,
                    navigationBarsHeight: Int,
                    animated: Boolean,
                    completed: Boolean
                ) {
                    val calculate = imeHeight - navigationBarsHeight
                    binding.clTextStickerBottomOptions.updateLayoutParams<MarginLayoutParams> {
                        updateMargins(0, 0, 0, calculate)
                    }
                    if (imeHeight <= 0 && completed) {
                        binding.sticker.showStickers()
                        binding.clTextStickerDim.isVisible = false
                        binding.btnTextStickerTextColor.isSelected = false
                        binding.btnTextStickerBackgroundColor.isSelected = false
                    }
                }

                override fun onChangedKeyboardOptions(imeHeight: Int, navigationBarsHeight: Int) {
                    val calculate = imeHeight - navigationBarsHeight
                    binding.clTextStickerBottomOptions.updateLayoutParams<MarginLayoutParams> {
                        updateMargins(0, 0, 0, calculate)
                    }
                }
            })

        binding.sticker.onStickerOperationListener =
            object : StickerView.OnStickerOperationListener {
                override fun onStickerAdded(sticker: Sticker) {
                    binding.flSubOptionDim.isVisible = false
                }

                override fun onStickerClicked(sticker: Sticker) {
                    binding.flSubOptionDim.isVisible = false
                    if (sticker is TextSticker) {
                        imageEditorViewModel.sendAction(
                            ImageEditorViewModel.ImageEditorAction.ChangeModeModifyTextSticker(
                                sticker = sticker
                            )
                        )
                    } else {
                        binding.clTextStickerDim.isVisible = false
                        hideStickerDeleteArea()
                    }
                }

                override fun onStickerDeleted(sticker: Sticker) {
                    binding.flSubOptionDim.isVisible = false
                    hideStickerDeleteArea()
                }

                override fun onStickerDragFinished(sticker: Sticker) {
                    binding.flSubOptionDim.isVisible = false
                    hideStickerDeleteArea()
                }

                override fun onStickerTouchedDown(sticker: Sticker) {
                    binding.flSubOptionDim.isVisible = true
                }

                override fun onStickerZoomFinished(sticker: Sticker) {
                    binding.flSubOptionDim.isVisible = false
                    hideStickerDeleteArea()
                }

                override fun onStickerFlipped(sticker: Sticker) {
                }

                override fun onStickerDoubleTapped(sticker: Sticker) {
                }

                override fun hasStickerEnteredDeletionArea() {
                    binding.clDeleteStickerBg.setBackgroundResource(R.drawable.bg_sticker_delete)
                }

                override fun notStickerEnterDeletionArea() {
                    binding.clDeleteStickerBg.setBackgroundResource(R.drawable.bg_sticker_delete_not_enter)
                }

                override fun showStickerDeleteArea() {
                    this@ImageEditorActivity.showStickerDeleteArea()
                }

            }
        binding.btnSave.setOnClickListener {
            imageEditorViewModel.sendAction(ImageEditorViewModel.ImageEditorAction.OnClickSaveFile)
        }


        binding.btnCropAndRotate.setOnClickListener {
            if (binding.sticker.getStickers().isNotEmpty()) {
                dialog {
                    message(getString(R.string.image_editor_crop_popup_message))
                    positiveListener {
                        imageEditorViewModel.sendAction(
                            ImageEditorViewModel.ImageEditorAction.OnClickCropAndRotateHasStickers(
                                createBitmap()
                            )
                        )
                    }
                }
            } else {
                imageEditorViewModel.sendAction(ImageEditorViewModel.ImageEditorAction.OnClickCropAndRotate)
            }
        }

        binding.btnRotate.setOnClickListener {
            it.isSelected = true
            binding.ivCrop.rotateImage(90)
            binding.btnFixedCrop.isSelected = false
            binding.btnFreeCrop.isSelected = false
            binding.btnOriginalCrop.isSelected = false
        }

        binding.btnFixedCrop.setOnClickListener {
            it.isSelected = true
            binding.ivCrop.setAspectRatio(1,1)
            binding.ivCrop.setFixedAspectRatio(true)
            binding.btnRotate.isSelected = false
            binding.btnFreeCrop.isSelected = false
            binding.btnOriginalCrop.isSelected = false
        }

        binding.btnFreeCrop.setOnClickListener {
            it.isSelected = true
            binding.ivCrop.setFixedAspectRatio(false)
            binding.btnRotate.isSelected = false
            binding.btnFixedCrop.isSelected = false
            binding.btnOriginalCrop.isSelected = false
        }

        binding.btnOriginalCrop.setOnClickListener {
            it.isSelected = true
            binding.btnRotate.isSelected = false
            binding.btnFixedCrop.isSelected = false
            binding.btnFreeCrop.isSelected = false
            val rect = binding.ivCrop.wholeImageRect ?: return@setOnClickListener

            binding.ivCrop.setAspectRatio(rect.width(), rect.height())
            binding.ivCrop.setFixedAspectRatio(true)
        }


        binding.btnSticker.setOnClickListener {
            imageEditorViewModel.sendAction(ImageEditorViewModel.ImageEditorAction.OnClickSticker)
        }

        binding.btnTextSticker.setOnClickListener {
            if(getTextStickerCount() >= MAX_STICKER_COUNT) {
                binding.viewToast.setMesssage(R.string.image_editor_limit_text_sticker)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    binding.viewToast.setIcon(Resources.ID_NULL)
                } else {
                    binding.viewToast.setIcon(0)
                }
                binding.viewToast.show()
            } else {
                imageEditorViewModel.sendAction(ImageEditorViewModel.ImageEditorAction.OnClickTextSticker)
            }
        }

        binding.btnTextStickerBackgroundColor.setOnClickListener {
            imageEditorViewModel.sendAction(ImageEditorViewModel.ImageEditorAction.OnClickTextStickerBackgroundColor)
        }

        binding.btnTextStickerTextColor.setOnClickListener {
            imageEditorViewModel.sendAction(ImageEditorViewModel.ImageEditorAction.OnClickTextStickerTextColor)
        }

        binding.ivTextStickerTextColorWhite.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectTextColor(
                    TextStickerColors.TextColor.WHITE
                )
            )
        }
        binding.ivTextStickerTextColorBlack.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectTextColor(
                    TextStickerColors.TextColor.BLACK
                )
            )
        }
        binding.ivTextStickerTextColorRed.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectTextColor(
                    TextStickerColors.TextColor.RED
                )
            )
        }
        binding.ivTextStickerTextColorOrange.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectTextColor(
                    TextStickerColors.TextColor.ORANGE
                )
            )
        }
        binding.ivTextStickerTextColorYellow.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectTextColor(
                    TextStickerColors.TextColor.YELLOW
                )
            )
        }
        binding.ivTextStickerTextColorGreen.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectTextColor(
                    TextStickerColors.TextColor.GREEN
                )
            )
        }
        binding.ivTextStickerTextColorBlue.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectTextColor(
                    TextStickerColors.TextColor.BLUE
                )
            )
        }
        binding.ivTextStickerTextColorPurple.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectTextColor(
                    TextStickerColors.TextColor.PURPLE
                )
            )
        }

        binding.ivTextStickerBackgroundColorNone.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectBackgroundColor(
                    TextStickerColors.BackgroundColor.NONE
                )
            )
        }
        binding.ivTextStickerBackgroundColorLightblue.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectBackgroundColor(
                    TextStickerColors.BackgroundColor.LIGHT_BLUE
                )
            )
        }
        binding.ivTextStickerBackgroundColorLightorange.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectBackgroundColor(
                    TextStickerColors.BackgroundColor.LIGHT_ORANGE
                )
            )
        }
        binding.ivTextStickerBackgroundColorYellow.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectBackgroundColor(
                    TextStickerColors.BackgroundColor.YELLOW
                )
            )
        }
        binding.ivTextStickerBackgroundColorLightgreen.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectBackgroundColor(
                    TextStickerColors.BackgroundColor.LIGHT_GREEN
                )
            )
        }
        binding.ivTextStickerBackgroundColorPink.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectBackgroundColor(
                    TextStickerColors.BackgroundColor.PINK
                )
            )
        }
        binding.ivTextStickerBackgroundColorGray.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SelectBackgroundColor(
                    TextStickerColors.BackgroundColor.GRAY
                )
            )
        }
        binding.btnSaveTextSticker.setOnClickListener {
            imageTextStickerViewModel.sendAction(
                ImageTextStickerViewModel.ImageTextStickerAction.SaveTextSticker(
                    getConvertNewLineText()
                )
            )
        }

        binding.btnCloseTextSticker.setOnClickListener {
            binding.etTextSickerInput.hideKeyboard(window)
        }

        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.etTextSickerInput.addTextChangedListener(
            beforeTextChanged = { s, _, _, _ ->

            },
            afterTextChanged = {
            },
            onTextChanged = { s, _, _, _ ->
                if(isFormatting) return@addTextChangedListener
                isFormatting = true
                val editText = binding.etTextSickerInput
                val lineCount = binding.etTextSickerInput.lineCount
                val selectionStart = editText.selectionStart
                if(lineCount > 4) {
                    try {
                        editText.setText(previousText)
                        editText.setSelection(previousText.length)
                        previousText = binding.etTextSickerInput.text.toString()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    previousText = binding.etTextSickerInput.text.toString()
                }
                binding.etTextSickerInputFake.text = binding.etTextSickerInput.text
                isFormatting = false
            }
        )
//        binding.etTextSickerInput.doOnTextChanged { text, _, _, _ ->
//            if (isFormatting) return@doOnTextChanged
//            isFormatting = true
//            formattedText(text.toString())
//            isFormatting = false
//        }
        binding.etTextSickerInput.doOnTextChanged { text, _, _, _ ->
            if (text.toString().isEmpty()) {
                binding.etTextSickerInput.hint =
                    getString(R.string.image_editor_text_sticker_placeholder)
            } else {
                binding.etTextSickerInput.hint = ""
            }
        }
    }
    private var previousText = ""
    private var isFormatting = false
    private var currentLineCount = 0
    private fun formattedText(text: String) {
        val lines = text.split("\n")
        var updatedText = ""
        val selectionStart = binding.etTextSickerInput.selectionStart
        val selectionEnd = binding.etTextSickerInput.selectionEnd
        var newSelection = min(selectionEnd, selectionStart)
        currentLineCount = lines.size
        if (currentLineCount > 4) {
            updatedText = lines.take(4).joinToString("\n")
            binding.etTextSickerInput.setText(updatedText)
            binding.etTextSickerInput.setSelection(min(newSelection, updatedText.length))
        } else {
            for (i in lines.indices) {
                val line = lines[i]
                var isChunk = false
                if (line.length > 15) {
                    val chunks = line.chunked(15)
                    isChunk = true
                    updatedText += chunks.joinToString("\n")
                    if(newSelection +1 == updatedText.length) {
                        newSelection += 1
                    }
                } else {
                    updatedText += line
                }
                if (isChunk.not() && i < lines.size - 1) {
                    updatedText += "\n"
                }
            }

            val newUpdatedText = updatedText.split("\n").take(4).joinToString("\n")
            if (newUpdatedText != text) {
                binding.etTextSickerInput.setText(newUpdatedText)
                if(newSelection > newUpdatedText.length) {
                    binding.etTextSickerInput.setSelection(newUpdatedText.length)
                } else {
                    binding.etTextSickerInput.setSelection(newSelection)
                }
            }
        }
    }

    private fun cropImage() {
        lifecycleScope.launch(Dispatchers.Main) {
            showLoading()
        }
        binding.ivCrop.croppedImageAsync(
            saveCompressFormat = Bitmap.CompressFormat.JPEG,
            saveCompressQuality = 100
        )
    }

    private fun createBitmapFromCrop(cropBitmap: Bitmap): Bitmap {
        val imageDrawableBitmap = binding.ivImage.drawable.toBitmap()
        val (w, h) = getBitmapWidthAndHeight(imageDrawableBitmap)
        return cropBitmap.resizeBitmap(resizeWidth = w, resizeHeight = h)

    }

    private fun createBitmap(withoutSticker: Boolean = false): Bitmap {
        val imageDrawableBitmap = binding.ivImage.drawable.toBitmap()
        val isScaled: Boolean
        val (extractedWidth, extractedHeight) = if (imageDrawableBitmap.height > binding.ivImage.height && imageDrawableBitmap.width > binding.ivImage.width) {
            isScaled = true
            imageDrawableBitmap.width to imageDrawableBitmap.height
        } else {
            isScaled = false
            binding.ivImage.width to binding.ivImage.height
        }
        val (w, h) = getBitmapWidthAndHeight(imageDrawableBitmap)
        val originalBitmap = binding.ivImage.drawable.toBitmap(w, h)

        val offset = (extractedWidth - w) / 2.toFloat()

        if(withoutSticker) {
            return if (isScaled) {
                originalBitmap.resizeBitmap(extractedWidth, extractedHeight)
            } else {
                originalBitmap
            }
        }
        val stickersBitmap = binding.sticker.createBitmap()
        val resizeStickersBitmap = if (isScaled) {
            stickersBitmap.resizeBitmap(extractedWidth, extractedHeight)
        } else {
            stickersBitmap
        }

        val baseBitmap = Bitmap.createBitmap(
            resizeStickersBitmap.width,
            resizeStickersBitmap.height,
            Bitmap.Config.ARGB_8888
        ).copy(Bitmap.Config.ARGB_8888, true)

        val copyOriginalBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val comboImage = Canvas(baseBitmap)

        comboImage.drawBitmap(copyOriginalBitmap, offset, 0f, null)
        comboImage.drawBitmap(resizeStickersBitmap, 0f, 0f, null)

        return baseBitmap
//        val extractedBitmap = Bitmap.createBitmap(
//            extractedWidth,
//            extractedHeight,
//            Bitmap.Config.ARGB_8888
//        ).copy(Bitmap.Config.ARGB_8888, true)
//
//        val newRect = Rect(
//            offset.toInt(),
//            0,
//            (offset + w).toInt(),
//            extractedHeight
//        )
//
//        val extractedCanvas = Canvas(extractedBitmap)
//        extractedCanvas.drawBitmap(
//            baseBitmap, newRect, Rect(
//                0, 0,
//                extractedWidth, extractedHeight
//            ), null
//        )
//        return extractedBitmap
    }

    private fun getBitmapWidthAndHeight(
        bitmap: Bitmap,
        forceBitmapSize: Boolean = true
    ): Pair<Int, Int> {
        val bitmapWidth = bitmap.getWidth()
        val bitmapHeight = bitmap.getHeight()

        if (forceBitmapSize) {
            if (bitmapWidth > binding.ivImage.width && bitmapHeight > binding.ivImage.height) {
                return bitmapWidth to bitmapHeight
            }
        }
        val aspectRatioBitmap = bitmapWidth.toFloat() / bitmapHeight
        val aspectRatioImageView: Float =
            binding.ivImage.width.toFloat() / binding.ivImage.height
        val displayedWidth: Int
        val displayedHeight: Int

        if (aspectRatioImageView > aspectRatioBitmap) {
            displayedHeight = binding.ivImage.height
            displayedWidth = ((displayedHeight * aspectRatioBitmap).toInt())
        } else {
            displayedWidth = binding.ivImage.width
            displayedHeight = ((displayedWidth / aspectRatioBitmap).toInt())
        }
        return displayedWidth to displayedHeight
    }

    private fun saveFile(item: ImageEditorViewerModel, rotate: Int, fromCrop: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            showLoading()
            val bitmap = if (fromCrop) createBitmapFromCrop(item.updatedBitmap) else createBitmap()
            val fileName = item.key + ".jpg"
            try {
                val result = ImageCompress.compress(
                    this@ImageEditorActivity,
                    cacheDir = "${imageEditorCacheDir}${item.prefix}",
                    bitmap = bitmap,
                    fileName = fileName
                )
                val stickers = if(fromCrop) item.stickers else binding.sticker.getStickers()
                val newItem = if (fromCrop) {
                    item.copy(
                        updatedUri = result.file.toUri(),
                        updatedBitmap = result.bitmap,
                        copyOriginalBitmap = result.bitmap,
                        stickers = stickers,
                        rotate = rotate,
                        copyOriginalUri = result.file.toUri(),
                        originalRotate = item.originalRotate,
                        isUpdatedBitmap = if(stickers.isNotEmpty()) true else item.updatedUri != item.originalUri && item.updatedUri != Uri.EMPTY
                    )
                } else {
                    val progress = ImageCompress.compress(
                        this@ImageEditorActivity,
                        cacheDir = "${imageEditorCacheDir}${item.prefix}${File.separator}progress",
                        bitmap = createBitmap(withoutSticker = true),
                        fileName = fileName
                    )
                    item.copy(
                        updatedUri = result.file.toUri(),
                        updatedBitmap = result.bitmap,
                        copyOriginalUri = progress.file.toUri(),
                        copyOriginalBitmap = progress.bitmap,
                        stickers = stickers.map { it },
                        isUpdatedBitmap = if(stickers.isNotEmpty()) true else item.updatedUri != item.originalUri && item.updatedUri != Uri.EMPTY
                    )
                }
                if (stickers.isNotEmpty()) {
                    saveImageEditorDataString(
                        item = newItem,
                        stickers = stickers,
                        remoteImagePath = item.origin.remoteImagePath()
                    )
                } else {
                    saveImageEditorDataString(item = newItem, stickers = emptyList(), remoteImagePath = item.origin.remoteImagePath())
                }
                delay(1_000)

                hideLoading()
                setResult(RESULT_OK, Intent().apply {
                    val key = ImageEditorHolder.putViewerModelData(model = newItem)
                    putExtra(ImageEditorContract.RESULT_EXTRA_VIEWER_MODEL_HOLDER_KEY, key)
                })
                finish()

            } catch (e: Exception) {
                e.printStackTrace()
                hideLoading()
            }
        }

    }

    private fun fetchRemoteConfig() {
//        val remoteConfig = Firebase.remoteConfig
//        val key = "dev_photoStickers"
//
//        remoteConfig.fetchAndActivate()
//            .addOnCompleteListener {
//                if (it.isSuccessful) {
//                    val remoteConfigValue = Firebase.remoteConfig[key].asString()
//                    applyRemoteConfig(remoteConfigValue)
//                }
//            }
//
//        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
//            override fun onUpdate(configUpdate: ConfigUpdate) {
//                if (configUpdate.updatedKeys.contains(key)) {
//                    val remoteConfigValue = Firebase.remoteConfig[key].asString()
//                    applyRemoteConfig(remoteConfigValue)
//                }
//            }
//
//            override fun onError(error: FirebaseRemoteConfigException) {}
//        })
    }

    private fun applyRemoteConfig(remoteConfigValue: String) {
        try {
            val stickers: List<RemoteConfigStickersValueModel.RemoteConfigStickerValueModel> = Gson().fromJson(
                remoteConfigValue,
                RemoteConfigStickersValueModel::class.java
            ).stickers
            lifecycleScope.launch {
                try {
                    val convert = stickers.map {
                        it to withContext(Dispatchers.IO) { stickerAsDrawable(it.url) }
                    }
                    imageEditorViewModel.sendAction(
                        ImageEditorViewModel.ImageEditorAction.LoadedImageEditorStickers(
                            stickers = convert
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    dialog { message(getString(R.string.image_editor_failed_sticker_popup_message)) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDeleteStickerButtonLocationToRect(
        displayedHeight: Int,
        displayedWidth: Int
    ): Rect {
        val deleteImageLocation = IntArray(2)
        val mainImageLocation = IntArray(2)
        val deleteImageRect = Rect()
        val widthOffset = Resources.getSystem().displayMetrics.widthPixels - displayedWidth
        binding.ivImage.getLocationOnScreen(mainImageLocation)
        binding.clDeleteSticker.getLocationOnScreen(deleteImageLocation)
        binding.clDeleteSticker.getGlobalVisibleRect(deleteImageRect)

        if (widthOffset / 2 > 0) {
            deleteImageRect.left -= widthOffset / 2
            deleteImageRect.right -= widthOffset / 2
        }

        val newTop = displayedHeight - binding.clDeleteSticker.height
        val newBottom = newTop + deleteImageRect.height()
        deleteImageRect.top = newTop
        deleteImageRect.bottom = newBottom
        return deleteImageRect
    }

    override fun onSetImageUriComplete(view: CropImageView, uri: Uri, error: Exception?) {
        if (error != null) {
            setResult(null, error, 1)
        }
    }

    override fun onCropImageComplete(view: CropImageView, result: CropImageView.CropResult) {
        imageEditorViewModel.sendAction(
            ImageEditorViewModel.ImageEditorAction.ResultCroppedImage(
                result = result
            )
        )
    }

    private fun showToastLimitSticker() {
        binding.viewToast.setMesssage(R.string.image_editor_limit_stickers_toast_message)
        binding.viewToast.clearIcon()
        binding.viewToast.show()
    }

    private fun setTextStickerUiBackgroundColor(color: TextStickerColors.BackgroundColor) {
        binding.ivTextStickerBackgroundColorNone.isSelected = false
        binding.ivTextStickerBackgroundColorLightblue.isSelected = false
        binding.ivTextStickerBackgroundColorLightorange.isSelected = false
        binding.ivTextStickerBackgroundColorYellow.isSelected = false
        binding.ivTextStickerBackgroundColorLightgreen.isSelected = false
        binding.ivTextStickerBackgroundColorPink.isSelected = false
        binding.ivTextStickerBackgroundColorGray.isSelected = false
        when (color) {
            TextStickerColors.BackgroundColor.NONE -> binding.ivTextStickerBackgroundColorNone.isSelected =
                true

            TextStickerColors.BackgroundColor.LIGHT_BLUE -> binding.ivTextStickerBackgroundColorLightblue.isSelected =
                true

            TextStickerColors.BackgroundColor.LIGHT_ORANGE -> binding.ivTextStickerBackgroundColorLightorange.isSelected =
                true

            TextStickerColors.BackgroundColor.YELLOW -> binding.ivTextStickerBackgroundColorYellow.isSelected =
                true

            TextStickerColors.BackgroundColor.LIGHT_GREEN -> binding.ivTextStickerBackgroundColorLightgreen.isSelected =
                true

            TextStickerColors.BackgroundColor.PINK -> binding.ivTextStickerBackgroundColorPink.isSelected =
                true

            TextStickerColors.BackgroundColor.GRAY -> binding.ivTextStickerBackgroundColorGray.isSelected =
                true
        }
        val backgroundColorId = when (color) {
            TextStickerColors.BackgroundColor.NONE -> android.R.color.transparent
            else -> color.colorRes
        }
        binding.etTextSickerInputFake.setBackgroundColor(
            ContextCompat.getColor(
                this,
                backgroundColorId
            )
        )
    }

    private fun setTextStickerUiTextColor(color: TextStickerColors.TextColor) {
        binding.ivTextStickerTextColorWhite.isSelected = false
        binding.ivTextStickerTextColorBlack.isSelected = false
        binding.ivTextStickerTextColorRed.isSelected = false
        binding.ivTextStickerTextColorOrange.isSelected = false
        binding.ivTextStickerTextColorYellow.isSelected = false
        binding.ivTextStickerTextColorGreen.isSelected = false
        binding.ivTextStickerTextColorBlue.isSelected = false
        binding.ivTextStickerTextColorPurple.isSelected = false

        when (color) {
            TextStickerColors.TextColor.WHITE -> binding.ivTextStickerTextColorWhite.isSelected =
                true

            TextStickerColors.TextColor.BLACK -> binding.ivTextStickerTextColorBlack.isSelected =
                true

            TextStickerColors.TextColor.RED -> binding.ivTextStickerTextColorRed.isSelected = true
            TextStickerColors.TextColor.ORANGE -> binding.ivTextStickerTextColorOrange.isSelected =
                true

            TextStickerColors.TextColor.YELLOW -> binding.ivTextStickerTextColorYellow.isSelected =
                true

            TextStickerColors.TextColor.GREEN -> binding.ivTextStickerTextColorGreen.isSelected =
                true

            TextStickerColors.TextColor.BLUE -> binding.ivTextStickerTextColorBlue.isSelected = true
            TextStickerColors.TextColor.PURPLE -> binding.ivTextStickerTextColorPurple.isSelected =
                true
        }
        val textColorId = color.colorRes
        binding.etTextSickerInput.setTextColor(ContextCompat.getColor(this, textColorId))
    }

    open fun setResult(uri: Uri?, error: Exception?, sampleSize: Int) {
        setResult(
            error?.let { CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE } ?: RESULT_OK,
            getResultIntent(uri, error, sampleSize),
        )
        finish()
    }

    open fun getResultIntent(uri: Uri?, error: Exception?, sampleSize: Int): Intent {
        val result = CropImage.ActivityResult(
            originalUri = binding.ivCrop.imageUri,
            uriContent = uri,
            error = error,
            cropPoints = binding.ivCrop.cropPoints,
            cropRect = binding.ivCrop.cropRect,
            rotation = binding.ivCrop.rotatedDegrees,
            wholeImageRect = binding.ivCrop.wholeImageRect,
            sampleSize = sampleSize,
        )
        val intent = Intent()
        intent.extras?.let(intent::putExtras)
        intent.putExtra(CropImage.CROP_IMAGE_EXTRA_RESULT, result)
        return intent
    }

    private fun getStickerCount(): Int {
        return binding.sticker.getStickers()
            .count { it is DrawableSticker || it is AiSticker || it is MosaicSticker || it is BlurSticker }
    }

    private fun getTextStickerCount(): Int {
        return binding.sticker.getStickers()
            .count { it is TextSticker }
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

    private fun initTransitionManager() {
        TransitionManager.beginDelayedTransition(binding.clSubOptions, Slide().apply {
            setDuration(300L)
            addTarget(binding.clRotateOptions)
            addTarget(binding.clTextStickerOptions)
            addTarget(binding.clStickerOptions)
        })
    }

    private fun changeModeCropAndRotate() {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.btnCropAndRotate.isSelected = true
            binding.btnSticker.isSelected = false
            binding.btnTextSticker.isSelected = false
            binding.sticker.isVisible = false
            binding.ivCrop.isVisible = true
            binding.sticker.removeAllStickers()

            initTransitionManager()
            binding.clRotateOptions.isVisible = true
            binding.clTextStickerOptions.isVisible = false
            binding.clStickerOptions.isVisible = false
        }
    }

    private fun changeModeTextSticker() {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.btnCropAndRotate.isSelected = false
            binding.btnSticker.isSelected = false
            binding.btnTextSticker.isSelected = true
            binding.sticker.isVisible = true
            binding.ivCrop.isVisible = false
            initTransitionManager()
            binding.clRotateOptions.isVisible = false
            binding.clTextStickerOptions.isVisible = true
            binding.clStickerOptions.isVisible = false
        }
    }

    private fun changeModeSticker() {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.sticker.isVisible = true
            binding.btnCropAndRotate.isSelected = false
            binding.btnSticker.isSelected = true
            binding.btnTextSticker.isSelected = false
            binding.ivCrop.isVisible = false
            initTransitionManager()
            binding.clRotateOptions.isVisible = false
            binding.clTextStickerOptions.isVisible = false
            binding.clStickerOptions.isVisible = true
        }
    }

    private fun showStickerDeleteArea() {
        if(binding.clDeleteSticker.isVisible) return
        TransitionManager.endTransitions(binding.clRoot)
        TransitionManager.beginDelayedTransition(
            binding.clRoot, MaterialElevationScale(true).apply {
                setInterpolator(FastOutSlowInInterpolator())
                setDuration(200)
                addTarget(binding.clDeleteSticker)
            }
        )
        binding.clDeleteSticker.isVisible = true
        binding.ivDeleteSticker.isVisible = true
    }

    private fun hideStickerDeleteArea() {
            TransitionManager.endTransitions(binding.clRoot)
            TransitionManager.beginDelayedTransition(
                binding.clRoot, MaterialElevationScale(true).apply {
                    setInterpolator(FastOutSlowInInterpolator())
                    setDuration(200)
                    addTarget(binding.clDeleteSticker)
                }
            )
            binding.clDeleteSticker.isVisible = false
            binding.ivDeleteSticker.isVisible = false
    }

    private fun updateImage(bitmap: Bitmap, rotate: Int, changeMode: EditorMode?) {
        binding.ivImage.setOnMeasureListener { width, height ->
            binding.clDeleteSticker.doOnPreDraw {
                val deleteImageRect =
                    getDeleteStickerButtonLocationToRect(
                        displayedHeight = height,
                        displayedWidth = width
                    )
                Log.i("containsDeleteIcon", "containsDeleteIcon $deleteImageRect")
                binding.sticker.setDefaultBackgroundImage(
                    binding.ivImage.drawable.toBitmap().resizeBitmap(
                        width, height
                    ),
                    customHeight = height,
                    customWidth = width,
                    deleteIconRect = deleteImageRect
                )
            }
            if (changeMode != null) {
                if (changeMode == EditorMode.CROP_AND_ROTATE) {
                    imageEditorViewModel.sendAction(ImageEditorViewModel.ImageEditorAction.ChangeModeCropAndRotate)
                } else if (changeMode == EditorMode.TEXT_STICKER) {
                    imageEditorViewModel.sendAction(ImageEditorViewModel.ImageEditorAction.ChangeModeTextSticker)
                } else if (changeMode == EditorMode.STICKER) {
                    imageEditorViewModel.sendAction(ImageEditorViewModel.ImageEditorAction.ChangeModeSticker)
                }
            }
            binding.ivImage.clearOnMeasureListener()
        }
        binding.ivImage.setImageBitmap(bitmap)
    }

    private fun getConvertNewLineText(): String {
        val editText = binding.etTextSickerInput
        val layout = editText.layout
        val lineCount = editText.lineCount
        val builder = StringBuilder()
        for(i in 0 until lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            val line = editText.text?.substring(start, end)?.replace("\n", "")
            builder.append(line)
            if(i != lineCount-1) {
                builder.append("\n")
            }
        }
        return builder.toString()
    }

    private companion object {
        private const val MAX_STICKER_COUNT = 50
    }
}
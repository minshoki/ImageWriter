package com.minshoki.image_editor.viewmodel

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minshoki.image_editor.core.EditorMode
import com.minshoki.image_editor.core.TextStickerColors
import com.minshoki.image_editor.feature.crop.CropImageView
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update

class ImageEditorViewModel : ViewModel() {

    data class ImageEditorUiState(
        val error: Throwable? = null,

        val showLoading: Boolean = false,
        val hideLoading: Boolean = false,

        val startFaceDetectData: Pair<Bitmap, Int>? = null,
        val startFaceDetectDataFromBlur: Pair<Bitmap, Int>? = null,
        val updateBitmap: Pair<Bitmap, Int>? = null,
        val updateBitmapAfterChangeMode: EditorMode? = null,
        val initStickers: Pair<List<Sticker>, Sticker?>? = null,

        val saveFile: ImageEditorViewerModel? = null,
        val saveFileFromCrop: Pair<ImageEditorViewerModel, Int>? = null,

        val changeMode: EditorMode? = null,
        val croppedImage: Boolean = false,
        val setCropAndRotateBitmap: Bitmap? = null,


        val submitStickers: List<ImageEditorSticker>? = null,

        val addSticker: DrawableSticker? = null,
        val addMosaicSticker: MosaicSticker? = null,
        val addAiStickers: List<Pair<AiSticker, Pair<Float, Float>>>? = null,
        val addAiBlurStickers: List<Pair<BlurSticker, Pair<Float, Float>>>? = null,
        val addTextSticker: Triple<String, TextStickerColors.TextColor, TextStickerColors.BackgroundColor>? = null,
        val replaceTextSticker: Pair<TextSticker, Triple<String, TextStickerColors.TextColor, TextStickerColors.BackgroundColor>>? = null,

        val showTextStickerBackgroundColorSet: Boolean = false,
        val showTextStickerTextColorSet: Boolean = false,

        val initCreateTextSticker: Boolean = false,
        val initModifyTextSticker: TextSticker? = null,
    )

    private val _imageEditorUiState: MutableSharedFlow<ImageEditorUiState> =
        MutableSharedFlow(0, 1, BufferOverflow.SUSPEND)
    val imageEditorUiState = _imageEditorUiState.asSharedFlow()

    sealed class ImageEditorDataState {
        data class ImageEditorData(
            val item: ImageEditorViewerModel,
            val mode: EditorMode = EditorMode.NONE,
            val saveMode: EditorMode? = null,
        ) : ImageEditorDataState()

        data class ImageEditorStickersData(
            val stickers: List<ImageEditorSticker>
        ) : ImageEditorDataState()

        data object Idle : ImageEditorDataState()
    }

    sealed class ImageEditorAction {
        data class InitData(
            val item: ImageEditorViewerModel,
            val mode: EditorMode,
            val selectStickerIndex: Int? = null,
        ) : ImageEditorAction()

        data object OnClickSaveFile : ImageEditorAction()

        data object OnClickSticker : ImageEditorAction()

        data object OnClickTextSticker : ImageEditorAction()

        data object OnClickTextStickerBackgroundColor : ImageEditorAction()
        data object OnClickTextStickerTextColor : ImageEditorAction()

        data class ResultCroppedImage(
            val result: CropImageView.CropResult
        ) : ImageEditorAction()

        data class OnClickCropAndRotateHasStickers(
            val bitmap: Bitmap
        ) : ImageEditorAction()

        data object OnClickCropAndRotate : ImageEditorAction()

        data object ChangeModeCropAndRotate : ImageEditorAction()

        data object ChangeModeSticker : ImageEditorAction()
        data object ChangeModeTextSticker : ImageEditorAction()

        data class ChangeModeModifyTextSticker(val sticker: TextSticker) : ImageEditorAction()


        data class AddSticker(
            val sticker: ImageEditorSticker
        ) : ImageEditorAction()

        data class AddTextSticker(
            val data: Triple<String, TextStickerColors.TextColor, TextStickerColors.BackgroundColor>
        ) : ImageEditorAction()

        data class ReplaceTextSticker(
            val data: Pair<TextSticker, Triple<String, TextStickerColors.TextColor, TextStickerColors.BackgroundColor>>
        ): ImageEditorAction()

        data class LoadedImageEditorStickers(
            val stickers: List<Pair<RemoteConfigStickersValueModel.RemoteConfigStickerValueModel, Drawable>>,
        ) : ImageEditorAction()

        data class LoadedFaceStickers(
            val faceStickersData: List<AiStickerDataModel>,
            val type: ImageEditorSticker
        ): ImageEditorAction()
    }

    private val _imageEditorDataState: MutableStateFlow<ImageEditorDataState> =
        MutableStateFlow(ImageEditorDataState.Idle)
    val imageEditorDataState = _imageEditorDataState.asSharedFlow()

    private val _imageEditorStickersDataState: MutableStateFlow<ImageEditorDataState> =
        MutableStateFlow(ImageEditorDataState.Idle)
    val imageEditorStickersDataState = _imageEditorStickersDataState.asSharedFlow()

    private val _imageEditorAction: MutableSharedFlow<ImageEditorAction> =
        MutableSharedFlow(
            0, 1, BufferOverflow.SUSPEND
        )

    init {
        _imageEditorAction
            .onEach { action -> handleAction(action) }
            .launchIn(viewModelScope)
    }

    private suspend fun handleAction(action: ImageEditorAction) {
        Log.i("shokitest", "handle $action")
        when (action) {
            is ImageEditorAction.InitData -> {
                _imageEditorDataState.emit(
                    ImageEditorDataState.ImageEditorData(item = action.item)
                )
                val selectSticker = action.selectStickerIndex?.let { action.item.stickers.getOrNull(it) }

                if(selectSticker is TextSticker) {
                    _imageEditorUiState.emit(
                        ImageEditorUiState(
//                            showLoading = true,
                            updateBitmap = action.item.copyOriginalBitmap to action.item.getChangeRotate(),
                            initStickers = action.item.stickers to selectSticker,
                        )
                    )
                    sendAction(ImageEditorAction.ChangeModeModifyTextSticker(sticker = selectSticker))
                } else {
                    _imageEditorUiState.emit(
                        ImageEditorUiState(
//                            showLoading = true,
                            updateBitmap = action.item.copyOriginalBitmap to action.item.getChangeRotate(),
                            initStickers = action.item.stickers to selectSticker,
                            updateBitmapAfterChangeMode = action.mode,
                        )
                    )
                }
            }

            ImageEditorAction.OnClickSaveFile -> {
                _imageEditorDataState.update { current ->
                    if (current !is ImageEditorDataState.ImageEditorData) return@update current
                    if (current.mode == EditorMode.CROP_AND_ROTATE) {
                        _imageEditorUiState.emit(ImageEditorUiState(croppedImage = true))
                        current.copy(saveMode = EditorMode.SAVE)
                    } else {
                        _imageEditorUiState.emit(ImageEditorUiState(saveFile = current.item))
                        return@update current
                    }
                }
            }

            is ImageEditorAction.OnClickCropAndRotate -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                if (dataState.mode == EditorMode.CROP_AND_ROTATE) return
                _imageEditorDataState.update { current ->
                    if (current !is ImageEditorDataState.ImageEditorData) return@update current
                    current.copy(mode = EditorMode.CROP_AND_ROTATE)
                }
                _imageEditorUiState.emit(
                    ImageEditorUiState(
                        changeMode = EditorMode.CROP_AND_ROTATE,
                        setCropAndRotateBitmap = dataState.item.copyOriginalBitmap
                    )
                )
            }

            is ImageEditorAction.OnClickCropAndRotateHasStickers -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                if (dataState.mode == EditorMode.CROP_AND_ROTATE) return
                _imageEditorDataState.update { current ->
                    if (current !is ImageEditorDataState.ImageEditorData) return@update current
                    current.copy(mode = EditorMode.CROP_AND_ROTATE)
                }
                _imageEditorUiState.emit(
                    ImageEditorUiState(
                        changeMode = EditorMode.CROP_AND_ROTATE,
                        setCropAndRotateBitmap = action.bitmap
                    )
                )
            }

            ImageEditorAction.OnClickTextSticker -> {
                var hasCroppedImage = false
                _imageEditorDataState.update { current ->
                    if (current !is ImageEditorDataState.ImageEditorData) return@update current
                    if (current.mode == EditorMode.CROP_AND_ROTATE) hasCroppedImage = true
                    if (hasCroppedImage) {
                        current.copy(saveMode = EditorMode.TEXT_STICKER)
                    } else current.copy(mode = EditorMode.TEXT_STICKER)
                }
                if (hasCroppedImage) {
                    _imageEditorUiState.emit(
                        ImageEditorUiState(croppedImage = true)
                    )
                } else {
                    _imageEditorUiState.emit(
                        ImageEditorUiState(changeMode = EditorMode.TEXT_STICKER, initCreateTextSticker = true)
                    )
                }
            }


            ImageEditorAction.OnClickSticker -> {
                var hasCroppedImage = false
                _imageEditorDataState.update { current ->
                    if (current !is ImageEditorDataState.ImageEditorData) return@update current
                    if (current.mode == EditorMode.CROP_AND_ROTATE) hasCroppedImage = true
                    if (hasCroppedImage) {
                        current.copy(saveMode = EditorMode.STICKER)
                    } else current.copy(mode = EditorMode.STICKER)
                }
                if (hasCroppedImage) {
                    _imageEditorUiState.emit(
                        ImageEditorUiState(croppedImage = true)
                    )
                } else {
                    _imageEditorUiState.emit(
                        ImageEditorUiState(changeMode = EditorMode.STICKER)
                    )
                }
            }

            is ImageEditorAction.ResultCroppedImage -> {
                var uiState: ImageEditorUiState? = null
                _imageEditorDataState.update { current ->
                    if (current !is ImageEditorDataState.ImageEditorData) return@update current
                    if (action.result.bitmap == null) return@update current
                    var rotate = current.item.rotate + action.result.rotation
                    if (rotate >= 360) rotate %= 360
                    if (current.saveMode != null) {
                        val newItem = current.item.copy(
                            updatedUri = action.result.uriContent ?: return@update current,
                            updatedBitmap = action.result.bitmap,
                            rotate = rotate,
                            copyOriginalBitmap = action.result.bitmap,
                            stickers = emptyList()
                        )
                        uiState = ImageEditorUiState(
                            changeMode = current.saveMode,
                            updateBitmap = action.result.bitmap to 0,
                            saveFileFromCrop = if (current.saveMode == EditorMode.SAVE) newItem to action.result.rotation else null,
                            hideLoading = true,
                            initCreateTextSticker = current.saveMode == EditorMode.TEXT_STICKER,
                        )
                        current.copy(
                            mode = current.saveMode, saveMode = null,
                            item = newItem
                        )
                    } else {
                        uiState = ImageEditorUiState(
                            changeMode = EditorMode.CROP_AND_ROTATE,
                            updateBitmap = action.result.bitmap to 0,
                            hideLoading = true
                        )
                        current.copy(
                            mode = EditorMode.CROP_AND_ROTATE,
                            item = current.item.copy(
                                rotate = rotate,
                                copyOriginalBitmap = action.result.bitmap,
                                updatedBitmap = action.result.bitmap,
                                updatedUri = action.result.uriContent ?: return@update current,
                                stickers = emptyList()
                            ),
                        )
                    }
                }
                uiState?.let { _imageEditorUiState.emit(it) }
            }

            ImageEditorAction.ChangeModeCropAndRotate -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                delay(500)
                _imageEditorDataState.update { current ->
                    if (current !is ImageEditorDataState.ImageEditorData) return@update current
                    current.copy(mode = EditorMode.CROP_AND_ROTATE)
                }
                _imageEditorUiState.emit(
                    ImageEditorUiState(
                        changeMode = EditorMode.CROP_AND_ROTATE,
                        setCropAndRotateBitmap = if (dataState.item.stickers.isEmpty()) dataState.item.copyOriginalBitmap else dataState.item.updatedBitmap
                    )
                )
            }

            ImageEditorAction.ChangeModeSticker -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                delay(500)
                _imageEditorDataState.update { current ->
                    if (current !is ImageEditorDataState.ImageEditorData) return@update current
                    current.copy(mode = EditorMode.STICKER)
                }
                _imageEditorUiState.emit(
                    ImageEditorUiState(changeMode = EditorMode.STICKER)
                )
            }

            is ImageEditorAction.ChangeModeModifyTextSticker -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                _imageEditorDataState.update { current ->
                    if (current !is ImageEditorDataState.ImageEditorData) return@update current
                    current.copy(mode = EditorMode.TEXT_STICKER)
                }
                _imageEditorUiState.emit(
                    ImageEditorUiState(changeMode = EditorMode.TEXT_STICKER, initModifyTextSticker = action.sticker)
                )
            }

            ImageEditorAction.ChangeModeTextSticker -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                _imageEditorDataState.update { current ->
                    if (current !is ImageEditorDataState.ImageEditorData) return@update current
                    current.copy(mode = EditorMode.TEXT_STICKER)
                }
                _imageEditorUiState.emit(
                    ImageEditorUiState(changeMode = EditorMode.TEXT_STICKER, initCreateTextSticker = true)
                )
            }

            is ImageEditorAction.LoadedImageEditorStickers -> {
                val dataState = _imageEditorStickersDataState.value
                if (dataState is ImageEditorDataState.ImageEditorStickersData) {
                    return
                }
                val newStickers = mutableListOf<ImageEditorSticker>()
                newStickers.add(ImageEditorSticker.AiStickerModel)
                newStickers.add(ImageEditorSticker.BlurStickerModel)
                newStickers.add(ImageEditorSticker.MosaicStickerModel)
                newStickers.addAll(action.stickers.map { ImageEditorSticker.StickerModel(url = it.first.url, drawable = it.second, useAi = it.first.useAi) })
                _imageEditorStickersDataState.emit(
                    ImageEditorDataState.ImageEditorStickersData(
                        stickers = newStickers
                    )
                )

                _imageEditorUiState.emit(ImageEditorUiState(submitStickers = newStickers, hideLoading = true))
            }

            is ImageEditorAction.AddSticker -> {
                when (action.sticker) {
                    is ImageEditorSticker.MosaicStickerModel -> {
                        val dataState = _imageEditorDataState.value
                        if (dataState !is ImageEditorDataState.ImageEditorData) return
                        val mosaicSticker = MosaicSticker(customWidth = StickerView.DEFAULT_STICKER_SIZE, customHeight = StickerView.DEFAULT_STICKER_SIZE)
                        _imageEditorUiState.emit(ImageEditorUiState(addMosaicSticker = mosaicSticker))
                    }

                    is ImageEditorSticker.AiStickerModel -> {
                        val dataState = _imageEditorDataState.value
                        if (dataState !is ImageEditorDataState.ImageEditorData) return
                        _imageEditorUiState.emit(ImageEditorUiState(startFaceDetectData = dataState.item.copyOriginalBitmap to dataState.item.rotate, showLoading = true))
                    }

                    is ImageEditorSticker.StickerModel -> {
                        val sticker = DrawableSticker(action.sticker.drawable, customWidth = StickerView.DEFAULT_STICKER_SIZE, customHeight = StickerView.DEFAULT_STICKER_SIZE, url = action.sticker.url)
                        _imageEditorUiState.emit(ImageEditorUiState(addSticker = sticker))
                    }

                    is ImageEditorSticker.BlurStickerModel -> {
                        val dataState = _imageEditorDataState.value
                        if (dataState !is ImageEditorDataState.ImageEditorData) return
                        _imageEditorUiState.emit(ImageEditorUiState(startFaceDetectDataFromBlur = dataState.item.copyOriginalBitmap to dataState.item.rotate, showLoading = true))
                    }
                }
            }

            is ImageEditorAction.LoadedFaceStickers -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                val stickerDataState = _imageEditorStickersDataState.value
                if(stickerDataState !is ImageEditorDataState.ImageEditorStickersData) return
                if(action.type is ImageEditorSticker.AiStickerModel) {
                    val data = action.faceStickersData.map { data ->
                        val randomSticker = stickerDataState.stickers.filterIsInstance<ImageEditorSticker.StickerModel>()
                            .filter { it.useAi }
                            .random()
                        (AiSticker(drawable = randomSticker.drawable, url = randomSticker.url, customWidth = data.size, customHeight = data.size))to (data.x to data.y)
                    }
                    _imageEditorUiState.emit(ImageEditorUiState(addAiStickers = data, hideLoading = true))
                } else if(action.type is ImageEditorSticker.BlurStickerModel) {
                    val data = action.faceStickersData.map { data ->
                        (BlurSticker(customWidth = data.size, customHeight = data.size))to (data.x to data.y)
                    }
                    _imageEditorUiState.emit(ImageEditorUiState(addAiBlurStickers = data, hideLoading = true))
                }
            }

            ImageEditorAction.OnClickTextStickerBackgroundColor -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                if(dataState.mode == EditorMode.TEXT_STICKER) {
                    _imageEditorUiState.emit(ImageEditorUiState(showTextStickerBackgroundColorSet = true))
                }
            }

            ImageEditorAction.OnClickTextStickerTextColor -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                if(dataState.mode == EditorMode.TEXT_STICKER) {
                    _imageEditorUiState.emit(ImageEditorUiState(showTextStickerTextColorSet = true))
                }
            }

            is ImageEditorAction.AddTextSticker -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                _imageEditorUiState.emit(ImageEditorUiState(addTextSticker = action.data))
            }

            is ImageEditorAction.ReplaceTextSticker -> {
                val dataState = _imageEditorDataState.value
                if (dataState !is ImageEditorDataState.ImageEditorData) return
                _imageEditorUiState.emit(ImageEditorUiState(replaceTextSticker = action.data))
            }

        }
    }

    fun sendAction(action: ImageEditorAction) {
        _imageEditorAction.tryEmit(action)
    }

    fun getItem(): ImageEditorViewerModel? {
        val dataState = _imageEditorDataState.value
        if(dataState !is ImageEditorDataState.ImageEditorData) return null
        return dataState.item
    }
}
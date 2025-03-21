package com.minshoki.image_editor.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope
import com.minshoki.image_editor.core.EditorMode
import com.minshoki.image_editor.model.ImageEditorViewerModel
import com.minshoki.image_editor.model.ImageEditorViewerSimpleModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update

class ImageEditorViewerViewModel : ViewModel() {

    data class ImageEditorViewerUiState(
        val error: Throwable? = null,

        val updatedItems: List<ImageEditorViewerSimpleModel>? = null,
        val makeData: Triple<ImageEditorViewerSimpleModel, Int, EditorMode>? = null,
        val openEditorMode: Pair<EditorMode, ImageEditorViewerSimpleModel>? = null,
        val updateIndicator: Pair<Int, Int>? = null,
        val afterUpdateItemsMoveIndex: Int? = null,
        val setEnableResetImageEditButton: Boolean = false,
        val setDisableResetImageEditButton: Boolean = false,

        val showPopupResetImageEdit: Int? = null,
        val resetImage: ImageEditorViewerSimpleModel? = null,

        val setResult: Pair<String, List<ImageEditorViewerSimpleModel>>? = null,
        val setResultFromBackKey: List<ImageEditorViewerSimpleModel>? = null,

        val shouldMergeBitmapForMoveRotate: Boolean = false,
    )

    private val _imageEditorViewerUiState: MutableSharedFlow<ImageEditorViewerUiState> =
        MutableSharedFlow(0, 1, BufferOverflow.SUSPEND)
    val imageEditorViewerUiState = _imageEditorViewerUiState.asSharedFlow()

    sealed class ImageEditorViewerDataState {
        data class ImageEditorViewerData(
            val prefix: String,
            val items: List<ImageEditorViewerSimpleModel>,
            val useForceComplete: Boolean,
        ) : ImageEditorViewerDataState()

        data object Idle : ImageEditorViewerDataState()
    }

    sealed class ImageEditorViewerAction {
        data class InitData(
            val items: List<ImageEditorViewerSimpleModel>,
            val selectIndex: Int,
            val prefix: String,
            val useForceComplete: Boolean
        ) : ImageEditorViewerAction()

        data class OnClickEditorMode(
            val mode: EditorMode,
            val position: Int,
            val forceCrop: Boolean = false,
        ) : ImageEditorViewerAction()

        data class UpdateEditorItemAfterEditorMode(
            val mode: EditorMode,
            val position: Int,
            val originalUri: Uri
        )  : ImageEditorViewerAction()

        data class OnClickResetImageEdit(
            val position: Int
        ) : ImageEditorViewerAction()

        data class OnResultUpdatedFromEditor(
            val item: ImageEditorViewerModel
        ) : ImageEditorViewerAction()

        data class ResetImageEdit(
            val position: Int
        ) : ImageEditorViewerAction()

        data object OnClickComplete: ImageEditorViewerAction()
        data object OnClickBackKey: ImageEditorViewerAction()

        data class SelectedPage(val page: Int): ImageEditorViewerAction()
    }

    private val _imageEditorViewerDataState: MutableStateFlow<ImageEditorViewerDataState> =
        MutableStateFlow(ImageEditorViewerDataState.Idle)
    val imageEditorViewerDataState = _imageEditorViewerDataState.asSharedFlow()

    private val _imageEditorViewerAction: MutableSharedFlow<ImageEditorViewerAction> =
        MutableSharedFlow(
            0, 1, BufferOverflow.SUSPEND
        )

    init {
        _imageEditorViewerAction
            .onEach { action -> handleAction(action) }
            .launchIn(viewModelScope)
    }

    private suspend fun handleAction(action: ImageEditorViewerAction) {
        when (action) {
            is ImageEditorViewerAction.InitData -> {
                _imageEditorViewerDataState.emit(
                    ImageEditorViewerDataState.ImageEditorViewerData(
                        items = action.items,
                        useForceComplete = action.useForceComplete,
                        prefix = action.prefix
                    )
                )
                try {
                    if(action.items[0].canResetImageEdit()) {
                        _imageEditorViewerUiState.emit(
                            ImageEditorViewerUiState(
                                updatedItems = action.items,
                                updateIndicator = 1 to action.items.size,
                                setEnableResetImageEditButton = true,
                                afterUpdateItemsMoveIndex = if(action.selectIndex == 0) null else action.selectIndex
                            )
                        )
                    } else {
                        _imageEditorViewerUiState.emit(
                            ImageEditorViewerUiState(
                                updatedItems = action.items,
                                updateIndicator = 1 to action.items.size,
                                setDisableResetImageEditButton = true,
                                afterUpdateItemsMoveIndex = if(action.selectIndex == 0) null else action.selectIndex
                            )
                        )
                    }
                } catch (e: Exception) {
                    _imageEditorViewerUiState.emit(ImageEditorViewerUiState(error = e))
                }
            }

            is ImageEditorViewerAction.SelectedPage -> {
                val dataState = _imageEditorViewerDataState.value
                if (dataState !is ImageEditorViewerDataState.ImageEditorViewerData) return
                val item = dataState.items[action.page]
                if(item.canResetImageEdit()) {
                    _imageEditorViewerUiState.emit(
                        ImageEditorViewerUiState(updateIndicator = action.page+1 to dataState.items.size, setEnableResetImageEditButton = true)
                    )
                } else {
                    _imageEditorViewerUiState.emit(
                        ImageEditorViewerUiState(updateIndicator = action.page+1 to dataState.items.size, setDisableResetImageEditButton = true)
                    )
                }
            }

            is ImageEditorViewerAction.UpdateEditorItemAfterEditorMode -> {
                var uiState: ImageEditorViewerUiState? = null
                _imageEditorViewerDataState.update { current ->
                    if(current !is ImageEditorViewerDataState.ImageEditorViewerData) return@update current
                    val data = current.items.toMutableList()
                    val new = data.mapIndexed { index, item ->
                        if(index == action.position) {
                            item.copy(originalUri = action.originalUri)
                        } else item
                    }
                    uiState = ImageEditorViewerUiState(updatedItems = new, openEditorMode = action.mode to (new.getOrNull(action.position) ?: return))
                    current.copy(items = new)
                }
                uiState?.let { _imageEditorViewerUiState.emit(it) }
            }

            is ImageEditorViewerAction.OnClickEditorMode -> {
                val dataState = _imageEditorViewerDataState.value
                if (dataState !is ImageEditorViewerDataState.ImageEditorViewerData) return
                val data = dataState.items.getOrNull(action.position) ?: return

                if(action.mode == EditorMode.CROP_AND_ROTATE) {
                    if(data.stickers.isEmpty() || action.forceCrop) {
                        if(data.originalUri == Uri.EMPTY) {
                            _imageEditorViewerUiState.emit(ImageEditorViewerUiState(makeData = Triple(data, action.position, action.mode)))
                        } else {
                            _imageEditorViewerUiState.emit(ImageEditorViewerUiState(openEditorMode = action.mode to data))
                        }
                    } else {
                        _imageEditorViewerUiState.emit(ImageEditorViewerUiState(shouldMergeBitmapForMoveRotate = true))
                    }
                } else {
                    if(data.originalUri == Uri.EMPTY) {
                        _imageEditorViewerUiState.emit(ImageEditorViewerUiState(makeData = Triple(data, action.position, action.mode)))
                    } else {
                        _imageEditorViewerUiState.emit(ImageEditorViewerUiState(openEditorMode = action.mode to data))
                    }
                }
//                val bitmapFromRemote = withContext(Dispatchers.IO) {
//                    Glide.with(this@ImageEditorViewerActivity)
//                        .asBitmap()
//                        .load(remoteImagePath)
//                        .override(360, 360)
//                        .submit().get()
//                }
//                val cacheDir = "${imageEditorCacheDir}${prefix}${File.separator}remote_original"
//                val compressBitmap = HiImageCompress.compress(
//                    this@ImageEditorViewerActivity,
//                    cacheDir = cacheDir,
//                    bitmap = bitmapFromRemote,
//                    fileName = "${System.currentTimeMillis()}.jpg"
//                )
//                val uri = compressBitmap.file.toUri()

            }

            is ImageEditorViewerAction.OnResultUpdatedFromEditor -> {
                var uiState: ImageEditorViewerUiState? = null
                _imageEditorViewerDataState.update { current ->
                    if (current !is ImageEditorViewerDataState.ImageEditorViewerData) return@update current
                    val newItems = current.items.toMutableList()
                        .map { item ->
                            if (item.key == action.item.key && item.prefix == action.item.prefix) {
                                item.copy(
                                    updatedUri = action.item.updatedUri,
                                    stickers = action.item.stickers,
                                    originalUri = action.item.originalUri,
                                    prefix = action.item.prefix,
                                    isUpdatedBitmap = action.item.isUpdatedBitmap,
                                    lastUpdateTimestamp = System.currentTimeMillis(),
                                )
                            } else item
                        }
                    if(action.item.canResetImageEdit()) {
                        uiState = ImageEditorViewerUiState(updatedItems = newItems, setEnableResetImageEditButton = true)
                    } else {
                        uiState = ImageEditorViewerUiState(updatedItems = newItems, setDisableResetImageEditButton = true)
                    }
                    current.copy(items = newItems)
                }

                uiState?.let { _imageEditorViewerUiState.emit(it) }
            }

            is ImageEditorViewerAction.OnClickResetImageEdit -> {
                _imageEditorViewerUiState.emit(ImageEditorViewerUiState(showPopupResetImageEdit = action.position))
            }

            is ImageEditorViewerAction.ResetImageEdit -> {
                var uiState: ImageEditorViewerUiState? = null
                _imageEditorViewerDataState.update { current ->
                    if (current !is ImageEditorViewerDataState.ImageEditorViewerData) return@update current
                    var newItem: ImageEditorViewerSimpleModel? = null
                    val newItems = current.items
                        .toMutableList()
                        .mapIndexed { index, imageEditorViewerModel ->
                            if (index == action.position) {
                                newItem = imageEditorViewerModel.copy(
                                    stickers = emptyList(),
                                    originalUri = imageEditorViewerModel.originalUri,
                                    updatedUri = Uri.EMPTY,
                                    isUpdatedBitmap = false,
                                    lastUpdateTimestamp = System.currentTimeMillis()
                                )
                                newItem
                            } else imageEditorViewerModel
                        }.filterNotNull()

                    uiState = ImageEditorViewerUiState(updatedItems = newItems, resetImage = newItem, setDisableResetImageEditButton = true)
                    current.copy(items = newItems)
                }
                uiState?.let { _imageEditorViewerUiState.emit(it) }
            }

            ImageEditorViewerAction.OnClickComplete -> {
                val dataState = _imageEditorViewerDataState.value
                if (dataState !is ImageEditorViewerDataState.ImageEditorViewerData) return
                _imageEditorViewerUiState.emit(ImageEditorViewerUiState(setResult = dataState.prefix to dataState.items))
            }

            ImageEditorViewerAction.OnClickBackKey -> {
                val dataState = _imageEditorViewerDataState.value
                if (dataState !is ImageEditorViewerDataState.ImageEditorViewerData) return
                _imageEditorViewerUiState.emit(ImageEditorViewerUiState(setResultFromBackKey = dataState.items))
            }
        }
    }

    fun sendAction(action: ImageEditorViewerAction) {
        _imageEditorViewerAction.tryEmit(action)
    }

    fun getUriList(): List<Uri> {
        val dataState = _imageEditorViewerDataState.value
        if(dataState !is ImageEditorViewerDataState.ImageEditorViewerData) return emptyList()
        return dataState.items.map { if(it.updatedUri == Uri.EMPTY) it.originalUri else it.updatedUri }
    }

    fun getUseForceComplete(): Boolean {
        val dataState = _imageEditorViewerDataState.value
        if(dataState !is ImageEditorViewerDataState.ImageEditorViewerData) return false
        return dataState.useForceComplete
    }

    fun getPrefix(): String {
        val dataState = _imageEditorViewerDataState.value
        if(dataState !is ImageEditorViewerDataState.ImageEditorViewerData) return ""
        return dataState.prefix
    }
}
package com.minshoki.image_editor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minshoki.image_editor.core.TextStickerColors
import com.minshoki.image_editor.feature.sticker.TextSticker
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update

class ImageTextStickerViewModel: ViewModel() {

    data class ImageTextStickerUiState(
        val error: Throwable? = null,

        val selectTextColor: TextStickerColors.TextColor? = null,
        val selectBackgroundColor: TextStickerColors.BackgroundColor? = null,
        val setText: String? = null,


        val createTextSticker: Triple<String, TextStickerColors.TextColor, TextStickerColors.BackgroundColor>? = null,
        val modifyTextSticker: Pair<TextSticker, Triple<String, TextStickerColors.TextColor, TextStickerColors.BackgroundColor>>? = null,
    )

    private val _imageTextStickerUiState: MutableSharedFlow<ImageTextStickerUiState> =
        MutableSharedFlow(0, 1, BufferOverflow.SUSPEND)
    val imageTextStickerUiState = _imageTextStickerUiState.asSharedFlow()

    sealed class ImageTextStickerDataState {
        data class ImageTextStickerData(
            val textColor: TextStickerColors.TextColor,
            val backgroundColor: TextStickerColors.BackgroundColor,
            val text: String,
            val isCreate: Boolean,
            val modifySticker: TextSticker? = null,
        ) : ImageTextStickerDataState()

        data object Idle : ImageTextStickerDataState()
    }

    sealed class ImageTextStickerAction {
        data object InitCreate: ImageTextStickerAction()

        data class InitModify(
            val textSticker: TextSticker
        ): ImageTextStickerAction()

        data class SelectBackgroundColor(
            val color: TextStickerColors.BackgroundColor
        ): ImageTextStickerAction()

        data class SelectTextColor(
            val color: TextStickerColors.TextColor
        ): ImageTextStickerAction()

        data class SaveTextSticker(
            val text: String
        ): ImageTextStickerAction()
    }

    private val _imageTextStickerDataState: MutableStateFlow<ImageTextStickerDataState> =
        MutableStateFlow(ImageTextStickerDataState.Idle)
    val imageTextStickerDataState = _imageTextStickerDataState.asSharedFlow()

    private val _imageTextStickerAction: MutableSharedFlow<ImageTextStickerAction> =
        MutableSharedFlow(
            0, 1, BufferOverflow.SUSPEND
        )

    init {
        _imageTextStickerAction
            .onEach { action -> handleAction(action) }
            .launchIn(viewModelScope)
    }

    private suspend fun handleAction(action: ImageTextStickerAction) {
        when (action) {
            ImageTextStickerAction.InitCreate -> {
                _imageTextStickerDataState.emit(
                    ImageTextStickerDataState.ImageTextStickerData(
                        text = "",
                        textColor = TextStickerColors.TextColor.WHITE,
                        backgroundColor = TextStickerColors.BackgroundColor.NONE,
                        isCreate = true
                    )
                )
                _imageTextStickerUiState.emit(
                    ImageTextStickerUiState(
                    setText = "",
                    selectTextColor = TextStickerColors.TextColor.WHITE,
                    selectBackgroundColor = TextStickerColors.BackgroundColor.NONE
                )
                )
            }
            is ImageTextStickerAction.InitModify -> {
                val sticker = action.textSticker
                _imageTextStickerDataState.emit(
                    ImageTextStickerDataState.ImageTextStickerData(
                        text = sticker.text.toString(),
                        textColor = sticker.getTextColor(),
                        backgroundColor = sticker.getBackgroundColor(),
                        isCreate = false,
                        modifySticker = sticker
                    )
                )

                _imageTextStickerUiState.emit(
                    ImageTextStickerUiState(
                    setText = sticker.text.toString(),
                    selectTextColor = sticker.getTextColor(),
                    selectBackgroundColor = sticker.getBackgroundColor()
                )
                )
            }

            is ImageTextStickerAction.SelectBackgroundColor -> {
                _imageTextStickerDataState.update { current ->
                    if(current !is ImageTextStickerDataState.ImageTextStickerData) return@update current
                    current.copy(backgroundColor = action.color)
                }
                _imageTextStickerUiState.emit(ImageTextStickerUiState(selectBackgroundColor = action.color))
            }
            is ImageTextStickerAction.SelectTextColor -> {
                _imageTextStickerDataState.update { current ->
                    if(current !is ImageTextStickerDataState.ImageTextStickerData) return@update current
                    current.copy(textColor = action.color)
                }
                _imageTextStickerUiState.emit(ImageTextStickerUiState(selectTextColor = action.color))
            }

            is ImageTextStickerAction.SaveTextSticker -> {
                val dataState = _imageTextStickerDataState.value
                if(dataState !is ImageTextStickerDataState.ImageTextStickerData) return
                val stickerText = action.text
                if(stickerText.isBlank()) {

                } else {
                    if(dataState.isCreate) {
                        _imageTextStickerUiState.emit(
                            ImageTextStickerUiState(
                            createTextSticker = Triple(stickerText, dataState.textColor, dataState.backgroundColor)
                        )
                        )
                    } else {
                        val sticker = dataState.modifySticker ?: return
                        _imageTextStickerUiState.emit(
                            ImageTextStickerUiState(
                            modifyTextSticker = sticker to Triple(stickerText, dataState.textColor, dataState.backgroundColor)
                        )
                        )
                    }
                }
            }
        }
    }

    fun sendAction(action: ImageTextStickerAction) {
        _imageTextStickerAction.tryEmit(action)
    }
}
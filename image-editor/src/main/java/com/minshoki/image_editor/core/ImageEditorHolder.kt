package com.minshoki.image_editor.core

import android.net.Uri
import com.minshoki.image_editor.feature.sticker.Sticker
import com.minshoki.image_editor.model.ImageEditorViewerModel
import com.minshoki.image_editor.model.ImageEditorViewerSimpleModel
import java.util.UUID

object ImageEditorHolder {
    private val stickerHolderMap: HashMap<String, Sticker> = hashMapOf()
    private val urisHolderMap: HashMap<String, List<Uri>> = hashMapOf()
    private val uriAndOriginalUriHolderMap: HashMap<String, List<Pair<Uri, Uri>>> = hashMapOf()
    private val viewerModelHolderMap: HashMap<String, ImageEditorViewerModel> = hashMapOf()
    private val remoteImagesUrlHolderMap: HashMap<String, HashMap<Uri, String>> = hashMapOf()
    private val viewerSimpleModelHolderMap: HashMap<String, ImageEditorViewerSimpleModel> = hashMapOf()
    fun putUriData(uris: List<Uri>): String {
        val key = UUID.randomUUID().toString()
        urisHolderMap[key] = uris
        return key
    }

    fun putUriAndOriginalUriData(data: List<Pair<Uri, Uri>>): String {
        val key = UUID.randomUUID().toString()
        uriAndOriginalUriHolderMap[key] = data
        return key
    }

    fun putRemoteImagesUrlData(data: HashMap<Uri, String>): String {
        val key = UUID.randomUUID().toString()
        remoteImagesUrlHolderMap[key] = data
        return key
    }
    fun putViewerModelData(model: ImageEditorViewerModel): String {
        val key = UUID.randomUUID().toString()
        viewerModelHolderMap[key] = model
        return key
    }

    fun putViewerSimpleModelData(model: ImageEditorViewerSimpleModel): String {
        val key = UUID.randomUUID().toString()
        viewerSimpleModelHolderMap[key] = model
        return key
    }

    fun getViewerSimpleModelData(key: String): ImageEditorViewerSimpleModel? {
        return viewerSimpleModelHolderMap.remove(key)
    }

    fun getViewerModelData(key: String): ImageEditorViewerModel? {
        return viewerModelHolderMap.remove(key)
    }

    fun getRemoteImagesUrlData(key: String): HashMap<Uri, String>? {
        return remoteImagesUrlHolderMap.remove(key)
    }

    fun getUriAndOriginalUriData(key: String): List<Pair<Uri, Uri>>? {
        return uriAndOriginalUriHolderMap.remove(key)
    }
    fun getUriData(key: String) = urisHolderMap.remove(key = key)

    fun putSticker(sticker: Sticker): String {
        val key = UUID.randomUUID().toString()
        stickerHolderMap[key] = sticker
        return key
    }

    fun removeData(key: String) {
        stickerHolderMap.remove(key)
    }

    fun getSticker(key: String): Sticker? {
        return stickerHolderMap.remove(key)
    }
}
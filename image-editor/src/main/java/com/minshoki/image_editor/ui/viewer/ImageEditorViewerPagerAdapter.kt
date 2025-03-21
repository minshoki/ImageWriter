package com.minshoki.image_editor.ui.viewer

import android.content.res.Resources
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.minshoki.image_editor.databinding.ItemImageEditorViewerBinding
import com.minshoki.image_editor.feature.sticker.Sticker
import com.minshoki.image_editor.model.ImageEditorViewerModel
import com.minshoki.image_editor.model.ImageEditorViewerSimpleModel

class ImageEditorViewerPagerAdapter(
    private val onClickStickerListener: (item: ImageEditorViewerSimpleModel, selectSticker: Sticker, selectStickerIndex: Int) -> Unit,
) :
    ListAdapter<ImageEditorViewerSimpleModel, ImageEditorViewerPagerAdapter.ViewerViewHolder>(
        object : DiffUtil.ItemCallback<ImageEditorViewerSimpleModel>() {
            override fun areItemsTheSame(
                oldItem: ImageEditorViewerSimpleModel,
                newItem: ImageEditorViewerSimpleModel
            ): Boolean {
                return oldItem.key == newItem.key && oldItem.prefix == newItem.prefix
            }

            override fun areContentsTheSame(
                oldItem: ImageEditorViewerSimpleModel,
                newItem: ImageEditorViewerSimpleModel
            ): Boolean {
                return oldItem.lastUpdateTimestamp == newItem.lastUpdateTimestamp
            }

        }
    ) {

    inner class ViewerViewHolder(
        private val binding: ItemImageEditorViewerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.ivImage.setOnTouchUpListener { array ->
                val item = binding.item ?: return@setOnTouchUpListener false
                for(i in item.stickers.indices.reversed()) {
                    val sticker = item.stickers[i]
                    val drawable = binding.ivImage.drawable ?: return@setOnTouchUpListener false

                    val parentWidth = binding.ivImage.fixedWidth
                    val drawableWidth = drawable.intrinsicWidth

                    val parentHeight = binding.ivImage.fixedHeight
                    val drawableHeight = drawable.intrinsicHeight

                    if(parentWidth != drawableWidth || parentHeight != drawableHeight) {
                        var newX = array[0]
                        var newY = array[1]
                        val widthPercent = parentWidth / drawableWidth.toFloat()
                        val heightPercent = parentHeight / drawableHeight.toFloat()
                        newX /= widthPercent
                        newY /= heightPercent
                        val isContains = sticker.contains(newX, newY)
                        if(isContains) {
                            onClickStickerListener(item, sticker, i)
                            return@setOnTouchUpListener true
                        }
                    } else {
                        val isContains = sticker.contains(array)
                        if(isContains) {
                            onClickStickerListener(item, sticker, i)
                            return@setOnTouchUpListener true
                        }
                    }
                }
                return@setOnTouchUpListener false
            }
        }

        fun onBind(item: ImageEditorViewerSimpleModel) {
            binding.item = item
            if(item.origin is ImageEditorViewerModel.Origin.Remote && item.originalUri == Uri.EMPTY) {
                Glide.with(binding.ivImage)
                    .load(item.origin.url)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .override(Resources.getSystem().displayMetrics.widthPixels)
//                    .override(Target.SIZE_ORIGINAL)
                    .into(binding.ivImage)
            } else {
                Glide.with(binding.ivImage)
                    .load(item.getResultUri())
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .override(Resources.getSystem().displayMetrics.widthPixels)
                    .into(binding.ivImage)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewerViewHolder {
        return ViewerViewHolder(
            binding = ItemImageEditorViewerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewerViewHolder, position: Int) {
        holder.onBind(getItem(position))
    }
}
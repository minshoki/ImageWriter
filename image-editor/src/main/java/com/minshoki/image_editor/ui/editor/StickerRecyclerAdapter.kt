package com.minshoki.image_editor.ui.editor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.minshoki.core.util.dp
import com.minshoki.image_editor.databinding.ItemImageEditorAiStickerBinding
import com.minshoki.image_editor.databinding.ItemImageEditorBlurStickerBinding
import com.minshoki.image_editor.databinding.ItemImageEditorMosaicStikcerBinding
import com.minshoki.image_editor.databinding.ItemImageEditorStickerBinding
import com.minshoki.image_editor.model.ImageEditorSticker
import java.lang.RuntimeException

class StickerRecyclerAdapter(
    private val onClickSticker: (sticker: ImageEditorSticker) -> Unit
) : ListAdapter<ImageEditorSticker, RecyclerView.ViewHolder>(
    object : DiffUtil.ItemCallback<ImageEditorSticker>() {
        override fun areItemsTheSame(
            oldItem: ImageEditorSticker,
            newItem: ImageEditorSticker
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: ImageEditorSticker,
            newItem: ImageEditorSticker
        ): Boolean {
            return oldItem == newItem
        }
    }
) {

    inner class StickerViewHolder(
        private val binding: ItemImageEditorStickerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.clRoot.setOnClickListener {
                val item = binding.item ?: return@setOnClickListener
                onClickSticker(item)
            }
        }
        fun onBind(item: ImageEditorSticker.StickerModel) {
            binding.item = item
            Glide.with(binding.ivSticker)
                .load(item.url)
                .override(40.dp)
                .into(binding.ivSticker)
        }
    }

    inner class AiStickerViewHolder(
        private val binding: ItemImageEditorAiStickerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.clRoot.setOnClickListener {
                onClickSticker(ImageEditorSticker.AiStickerModel)
            }
        }

        fun onBind() {

        }
    }

    inner class BlurStickerViewHolder(
        private val binding: ItemImageEditorBlurStickerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.clRoot.setOnClickListener {
                onClickSticker(ImageEditorSticker.BlurStickerModel)
            }
        }

        fun onBind() {

        }
    }

    inner class MosaicStickerViewHolder(
        private val binding: ItemImageEditorMosaicStikcerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.clRoot.setOnClickListener {
                onClickSticker(ImageEditorSticker.MosaicStickerModel)
            }
        }
        fun onBind() {

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_AI_STICKER -> AiStickerViewHolder(
                ItemImageEditorAiStickerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            VIEW_TYPE_STICKER -> StickerViewHolder(
                ItemImageEditorStickerBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

            VIEW_TYPE_MOSAIC_STICKER -> MosaicStickerViewHolder(
                ItemImageEditorMosaicStikcerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            VIEW_TYPE_BLUR_STICKER -> BlurStickerViewHolder(
                ItemImageEditorBlurStickerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> throw RuntimeException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        if(position == RecyclerView.NO_POSITION) super.getItemViewType(position)
        return when(getItem(position)) {
            is ImageEditorSticker.MosaicStickerModel -> VIEW_TYPE_MOSAIC_STICKER
            is ImageEditorSticker.StickerModel -> VIEW_TYPE_STICKER
            is ImageEditorSticker.AiStickerModel -> VIEW_TYPE_AI_STICKER
            is ImageEditorSticker.BlurStickerModel -> VIEW_TYPE_BLUR_STICKER
            else ->super.getItemViewType(position)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MosaicStickerViewHolder -> holder.onBind()
            is AiStickerViewHolder -> holder.onBind()
            is StickerViewHolder -> holder.onBind(getItem(position) as ImageEditorSticker.StickerModel)
            is BlurStickerViewHolder -> holder.onBind()
        }
    }

    private companion object {
        private const val VIEW_TYPE_STICKER = 3
        private const val VIEW_TYPE_AI_STICKER = 4
        private const val VIEW_TYPE_MOSAIC_STICKER = 5
        private const val VIEW_TYPE_BLUR_STICKER = 6
    }
}
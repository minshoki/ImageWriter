package com.minshoki.image_editor.core

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.minshoki.image_editor.R

class TextStickerColors {
    enum class TextColor(
        @ColorRes val colorRes: Int
    ) {
        WHITE(R.color.image_editor_text_sticker_text_color_white),
        BLACK(R.color.image_editor_text_sticker_text_color_black),
        RED(R.color.image_editor_text_sticker_text_color_red),
        ORANGE(R.color.image_editor_text_sticker_text_color_orange),
        YELLOW(R.color.image_editor_text_sticker_text_color_yellow),
        GREEN(R.color.image_editor_text_sticker_text_color_green),
        BLUE(R.color.image_editor_text_sticker_text_color_blue),
        PURPLE(R.color.image_editor_text_sticker_text_color_purple),
        ;
    }

    enum class BackgroundColor(
        @ColorRes val colorRes: Int,
        @DrawableRes val drawableRes: Int,
    ) {
        NONE(android.R.color.transparent, R.drawable.bg_sticker_transparency),
        LIGHT_BLUE(R.color.image_editor_text_sticker_background_color_light_blue, R.drawable.bg_sticker_lightblue),
        LIGHT_ORANGE(R.color.image_editor_text_sticker_background_color_light_orange, R.drawable.bg_sticker_lightorange),
        YELLOW(R.color.image_editor_text_sticker_background_color_yellow, R.drawable.bg_sticker_yellow),
        LIGHT_GREEN(R.color.image_editor_text_sticker_background_color_light_green, R.drawable.bg_sticker_lightgreen),
        PINK(R.color.image_editor_text_sticker_background_color_pink, R.drawable.bg_sticker_pink),
        GRAY(R.color.image_editor_text_sticker_background_color_gray, R.drawable.bg_sticker_gray),
        ;
    }
}
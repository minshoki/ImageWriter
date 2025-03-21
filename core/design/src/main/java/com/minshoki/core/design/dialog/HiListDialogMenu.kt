package com.minshoki.core.design.dialog

import android.graphics.drawable.Drawable

sealed class HiListDialogMenu(
    open val menu: String
) {
    data class IconTextMenu(
        override val menu: String,
        val icon: Drawable
    ) : HiListDialogMenu(menu)

    data class TextMenu(
        override val menu: String
    ) : HiListDialogMenu(menu)

    data class GenericMenu<T : IHiDialogMenu>(
        val item: T,
        val useDisplayMenu: Boolean = true,
    ) : HiListDialogMenu(menu = if(useDisplayMenu) item.displayMenu else item.originText)
}

interface IHiDialogMenu {
    val displayMenu: String
    val originText: String
}
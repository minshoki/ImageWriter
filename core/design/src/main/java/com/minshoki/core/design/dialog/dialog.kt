package com.minshoki.core.design.dialog

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iscreammedia.app.hiclass.android.design.ui.dialog.HiclassDialogOption

fun AppCompatActivity.dialog(option: HiclassDialogOption.() -> Unit) {
    if (isFinishing || isDestroyed) return
    val newOption = HiclassDialogOption().apply(option)
    val view = HiclassDialogView.Builder(this)
        .setOption(newOption).build()
    val dialog = MaterialAlertDialogBuilder(this)
        .setView(view)
        .setCancelable(false)
        .create()
    view.setDialog(dialog)
    dialog.show()
}

fun Fragment.dialog(option: HiclassDialogOption.() -> Unit) {
    if (isAdded.not()) return
    val newOption = HiclassDialogOption().apply(option)
    val view = HiclassDialogView.Builder(requireContext())
        .setOption(newOption).build()
    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setView(view)
        .setCancelable(false)
        .create()
    view.setDialog(dialog)
    dialog.show()
}
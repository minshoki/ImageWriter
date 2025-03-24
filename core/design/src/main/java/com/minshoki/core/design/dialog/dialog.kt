package com.minshoki.core.design.dialog

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun AppCompatActivity.dialog(option: DialogOption.() -> Unit) {
    if (isFinishing || isDestroyed) return
    val newOption = DialogOption().apply(option)
    val view = DialogView.Builder(this)
        .setOption(newOption).build()
    val dialog = MaterialAlertDialogBuilder(this)
        .setView(view)
        .setCancelable(false)
        .create()
    view.setDialog(dialog)
    dialog.show()
}

fun Fragment.dialog(option: DialogOption.() -> Unit) {
    if (isAdded.not()) return
    val newOption = DialogOption().apply(option)
    val view = DialogView.Builder(requireContext())
        .setOption(newOption).build()
    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setView(view)
        .setCancelable(false)
        .create()
    view.setDialog(dialog)
    dialog.show()
}
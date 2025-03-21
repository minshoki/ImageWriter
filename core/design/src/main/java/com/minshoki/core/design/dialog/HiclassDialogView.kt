package com.minshoki.core.design.dialog

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.iscreammedia.app.hiclass.android.design.ui.dialog.HiclassDialogOption
import com.minshoki.core.design.databinding.ViewHiclassDialogBinding

class HiclassDialogView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private val binding: ViewHiclassDialogBinding =
        ViewHiclassDialogBinding.inflate(LayoutInflater.from(context), this, true)

    private var onPositiveListener: (() -> Unit)? = null
    private var onNegativeListener: (() -> Unit)? = null

    private var dialog: AlertDialog? = null

    fun setDialog(dialog: AlertDialog) {
        this.dialog = dialog
    }

    fun setMessage(message: String) {
        binding.tvMessage.text = message
    }

    fun setPositiveButton(positive: String) {
        binding.btnPositive.text = positive
    }

    fun setNegativeButton(negative: String) {
        binding.btnNegative.text = negative
    }

    internal fun setPositiveListener(listener: () -> Unit) {
        onPositiveListener = listener
    }

    internal fun setNegativeListener(listener: () -> Unit) {
        onNegativeListener = listener
    }

    fun setOption(option: HiclassDialogOption) {
        if(option.positiveButtonTextColorResource != 0) {
            binding.btnPositive.setTextColor(ContextCompat.getColor(context, option.positiveButtonTextColorResource))
        }
        binding.tvTitle.isVisible = option.title.isBlank().not()
        binding.tvTitle.text = option.title
        binding.tvMessage.text = option.message
        binding.btnPositive.text = option.positiveButton
        binding.btnNegative.text = option.negativeButton
        if(option.isCenterTitle) {
            binding.tvTitle.gravity = Gravity.CENTER
        }
        if(option.useSignleButton) {
            binding.btnNegative.isVisible = false
        } else {
            binding.btnNegative.isVisible = true
        }
        binding.btnPositive.setOnClickListener {
            dialog?.dismiss()
            option.positiveListener()
        }
        binding.btnNegative.setOnClickListener {
            dialog?.dismiss()
            option.negativeListener()
        }
    }

    private fun initView(builder: Builder) {
        val option = builder.option
        setOption(option)
    }

    fun build(builder: Builder): HiclassDialogView {
        initView(builder)
        return this
    }


    data class Builder(
        val context: Context
    ) {

        internal var option: HiclassDialogOption = HiclassDialogOption()

        fun setOption(option: HiclassDialogOption) = apply {
            this.option = option
        }

        fun build(): HiclassDialogView {
            return HiclassDialogView(context).build(builder = this)
        }
    }
}
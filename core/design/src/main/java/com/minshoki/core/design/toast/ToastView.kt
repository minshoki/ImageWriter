package com.minshoki.core.design.toast

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.LinearInterpolator
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.minshoki.core.design.R
import com.minshoki.core.design.databinding.ViewToastBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ToastView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private val binding: ViewToastBinding


    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ToastView, 0, 0)
        binding = ViewToastBinding.inflate(LayoutInflater.from(context), this, true)

        try {
            val toastString = a.getString(R.styleable.ToastView_toastString)
            val toastIcon = a.getDrawable(R.styleable.ToastView_toastIcon)
            binding.ivIcon.isVisible = toastIcon != null
            toastIcon?.let { binding.ivIcon.setImageDrawable(it) }
            binding.tvMessage.text = toastString
        } finally {
            a.recycle()
        }
    }

    fun setMesssage(@StringRes messageResId: Int) {
        binding.tvMessage.text = context.getString(messageResId)
    }

    fun clearIcon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setIcon(Resources.ID_NULL)
        } else {
            setIcon(0)
        }
    }

    fun setIcon(@DrawableRes iconResId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (iconResId == Resources.ID_NULL) {
                binding.ivIcon.isVisible = false
            } else {
                binding.ivIcon.isVisible = true
                binding.ivIcon.setImageDrawable(context.getDrawable(iconResId))
            }
        } else {
            if (iconResId == 0) {
                binding.ivIcon.isVisible = false
            } else {
                binding.ivIcon.isVisible = true
                binding.ivIcon.setImageDrawable(context.getDrawable(iconResId))
            }
        }
    }

    fun show() {
        animate().cancel()
        this.alpha = 0f
        this.isVisible = true
        animate().apply {
            interpolator = LinearInterpolator()
            duration = 500
            alpha(1f)
            withEndAction {
                val owner = findViewTreeLifecycleOwner()
                if (owner != null) {
                    owner.lifecycleScope.launch {
                        delay(3_000)
                        hide()
                    }
                } else {
                    hide()
                }
            }
            start()
        }
    }

    private fun hide() {
        animate().apply {
            interpolator = LinearInterpolator()
            duration = 500
            alpha(0f)
            withEndAction {
                this@ToastView.alpha = 0f
                this@ToastView.isVisible = false
            }
            start()
        }
    }
}
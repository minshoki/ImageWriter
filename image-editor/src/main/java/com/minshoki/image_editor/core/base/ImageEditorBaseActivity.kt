package com.minshoki.image_editor.core.base

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding

abstract class ImageEditorBaseActivity<BINDING: ViewDataBinding>: AppCompatActivity() {
    protected lateinit var binding: BINDING
    abstract val layoutId: Int

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            handleBackPressed()
        }
    }

    open fun handleBackPressed() {
        finish()
    }
    fun onBackPressedCompat() {
        onBackPressedDispatcher.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        beforeSetContentView()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, layoutId)
        initView()
        initViewModel()
        initListener()

        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        afterOnCreate(savedInstanceState)
    }

    open fun beforeSetContentView() {}
    open fun initView() {}
    open fun initViewModel() {}
    open fun initListener() {}
    open fun afterOnCreate(savedInstanceState: Bundle? = null) {}
}
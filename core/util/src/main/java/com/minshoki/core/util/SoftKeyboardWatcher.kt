package com.minshoki.core.util

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

class SoftKeyboardWatcher(window: Window) {
    interface WatcherCallback {
        fun onChanged(
            imeHeight: Int,
            navigationBarsHeight: Int,
            animated: Boolean,
            completed: Boolean
        )

        fun onChangedKeyboardOptions(imeHeight: Int, navigationBarsHeight: Int)
    }

    private var callback: WatcherCallback? = null

    private val decorView: View = window.decorView

    fun startWatch(activity: Activity, lifecycleOwner: LifecycleOwner, callback: WatcherCallback) {
        this.callback = callback

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            watchViaWindowInsetsAnimationCallback(lifecycleOwner)
        } else {
            watchViaPopupWindow(activity, lifecycleOwner)
        }
    }

    private var lastImeHeight = 0

    @RequiresApi(Build.VERSION_CODES.R)
    private fun watchViaWindowInsetsAnimationCallback(lifecycleOwner: LifecycleOwner) {
        val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rootInsets = ViewCompat.getRootWindowInsets(decorView)
            if (rootInsets != null) {
                val imeHeight = rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                if (imeHeight != 0 && lastImeHeight != imeHeight) {
                    val navigationBarsHeight =
                        rootInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    callback?.onChangedKeyboardOptions(imeHeight, navigationBarsHeight)
                    lastImeHeight = imeHeight
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_START) {
                    decorView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
                } else if (event == Lifecycle.Event.ON_STOP) {
                    decorView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    lifecycleOwner.lifecycle.removeObserver(this)
                }
            }
        })

        ViewCompat.setWindowInsetsAnimationCallback(
            decorView,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                private var imeVisibleOnPrepare = false
                private var navigationBarsHeight = 0


                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    super.onPrepare(animation)

                    val rootInsets = ViewCompat.getRootWindowInsets(decorView)
                    if (rootInsets != null) {
                        imeVisibleOnPrepare = rootInsets.isVisible(WindowInsetsCompat.Type.ime())
                        navigationBarsHeight =
                            rootInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    }
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    val isComplete = runningAnimations.all { it.fraction == 1f }
                    if (isComplete) lastImeHeight = imeHeight
                    callback?.onChanged(
                        imeHeight,
                        navigationBarsHeight,
                        true,
                        completed = isComplete
                    )
                    return insets
                }
            })
    }

    private fun watchViaPopupWindow(activity: Activity, lifecycleOwner: LifecycleOwner) {
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenRealHeight: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.height()
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(it)
            }.y
        }

        var keyboardHeight = 0

        val popupRect = Rect()

        val popupView = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            popupView.getWindowVisibleDisplayFrame(popupRect)

            var statusBarTop = 0
            var navigationBarBottom = 0
            val rootInsets = ViewCompat.getRootWindowInsets(decorView)
            if (rootInsets != null) {
                statusBarTop = rootInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                navigationBarBottom =
                    rootInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            }

            val heightDiff = decorView.height - popupRect.height()
            // If the heightDiff is greater than 1/5, the soft keyboard is visible
            val imeVisible = heightDiff > screenRealHeight / 5
            // When full screen statusBarTop = 0, No need to consider full screen
            val imeHeight = if (imeVisible) heightDiff - statusBarTop else 0

            // Log.d("SoftKeyboardWatcher", "watchViaPopupWindow: imeHeight = $imeHeight")
            if (keyboardHeight != imeHeight) {
                keyboardHeight = imeHeight
                callback?.onChanged(imeHeight, navigationBarBottom, false, true)
            }
        }

        val popupWindow = PopupWindow(activity).apply {
            contentView = popupView
            // PopupWindow to be resized when the soft keyboard pops up
            @Suppress("DEPRECATION")
            softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED

            width = 0 // Width set to 0 to avoid obscuring the ui
            height = WindowManager.LayoutParams.MATCH_PARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                /**
                 * windowLayoutType default = [WindowManager.LayoutParams.TYPE_APPLICATION_PANEL]
                 */
                windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            setBackgroundDrawable(ColorDrawable(0))
        }

        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_CREATE) {
                    popupView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
                    decorView.post {
                        if (!popupWindow.isShowing && decorView.windowToken != null) {
                            popupWindow.showAtLocation(decorView, Gravity.NO_GRAVITY, 0, 0)
                        }
                    }
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    popupWindow.dismiss()
                    popupView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
                    lifecycleOwner.lifecycle.removeObserver(this)
                }
            }
        })
    }
}
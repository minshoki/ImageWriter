package com.minshoki.core.design

import android.os.Looper
import com.airbnb.lottie.LottieAnimationView

fun LottieAnimationView.safePlayAnimation() {
    if(Looper.myLooper() == Looper.getMainLooper()) {
        setSafeMode(true)
        playAnimation()
    } else {
        post {
            setSafeMode(true)
            playAnimation()
        }
    }
}
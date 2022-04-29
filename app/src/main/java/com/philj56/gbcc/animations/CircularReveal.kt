package com.philj56.gbcc.animations

import android.animation.Animator
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.transition.TransitionValues
import androidx.transition.Visibility
import kotlin.math.max

class CircularReveal : Visibility() {
    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues,
        endValues: TransitionValues
    ): Animator {
        val animator = ViewAnimationUtils.createCircularReveal(
            view,
            view.width / 2,
            view.height / 2,
            0.0f,
            max(view.width, view.height).toFloat()
        )
        view.alpha = 0.0f
        animator.addListener(
            onStart = { view.alpha = 1.0f }
        )
        return animator
    }

    override fun onDisappear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues,
        endValues: TransitionValues
    ): Animator {
        return ViewAnimationUtils.createCircularReveal(
            view,
            view.width / 2,
            view.height / 2,
            max(view.width, view.height).toFloat(),
            0.0f
        )
    }
}
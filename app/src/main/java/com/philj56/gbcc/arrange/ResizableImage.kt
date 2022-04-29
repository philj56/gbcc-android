package com.philj56.gbcc.arrange

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView

class ResizableImage : AppCompatImageView {
    private var floating: Boolean = false

    constructor(context: Context) : super(context) {
        addMotionListener()
        addLongClickListener()
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        addMotionListener()
        addLongClickListener()
    }

    private fun addMotionListener() {
        setOnTouchListener(OnTouchListener { view, motionEvent ->

            if (!floating) {
                return@OnTouchListener view.performClick()
            }

            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    floating = false
                }
            }

            view.x = motionEvent.rawX - view.width / 2
            view.y = motionEvent.rawY - view.height / 2

            return@OnTouchListener true
        })
    }

    private fun addLongClickListener() {
        setOnLongClickListener(OnLongClickListener {
            floating = true
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return@OnLongClickListener false
        })
    }
}


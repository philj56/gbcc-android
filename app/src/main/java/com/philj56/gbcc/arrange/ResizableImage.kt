package com.philj56.gbcc.arrange

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.roundToInt

class ResizableImage : AppCompatImageView {
    var gridSize = 0
    var gridBase = 0
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

            val x = motionEvent.rawX
            val y = motionEvent.rawY

            if (gridSize > 0) {
                view.x = ((x - gridBase) / gridSize).roundToInt() * gridSize.toFloat() + gridBase - view.width / 2
                view.y = ((y - gridBase) / gridSize).roundToInt() * gridSize.toFloat() + gridBase - view.height / 2
            } else {
                view.x = x - view.width / 2
                view.y = y - view.height / 2
            }

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


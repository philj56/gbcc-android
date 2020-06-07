/*
 * Copyright (C) 2019-2020 Philip Jones
 *
 * Licensed under the MIT License.
 * See either the LICENSE file, or:
 *
 * https://opensource.org/licenses/MIT
 *
 */

package com.philj56.gbcc

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.slider.Slider
import kotlinx.android.synthetic.main.activity_arrange.*
import kotlinx.android.synthetic.main.activity_arrange_sliders.*
import kotlin.math.log
import kotlin.math.min
import kotlin.math.pow


class ArrangeActivity : AppCompatActivity() {
    private val sliderListener = SliderListener()
    private val scaleFactorRange : Float = 2f

    private lateinit var prefs : SharedPreferences

    private fun updateLayout(gbc: Boolean) {
        val bgColor = when (gbc) {
            true -> when (prefs.getString("color", "Teal")) {
                "Berry" -> R.color.gbcBerry
                "Dandelion" -> R.color.gbcDandelion
                "Grape" -> R.color.gbcGrape
                "Kiwi" -> R.color.gbcKiwi
                "Teal" -> R.color.gbcTeal
                else -> R.color.gbcTeal
            }
            false -> R.color.dmgBackground
        }
        window.setBackgroundDrawableResource(bgColor)

        abSlider.addOnChangeListener(sliderListener)
        startSelectSlider.addOnChangeListener(sliderListener)
        dpadSlider.addOnChangeListener(sliderListener)

        if (!gbc) {
            val screenBorderColor = ContextCompat.getColor(this, R.color.dmgScreenBorder)
            val borders = arrayOf(
                screenBorderTop,
                screenBorderBottom,
                screenBorderLeft,
                screenBorderRight
            )

            borders.forEach {
                it.setColorFilter(
                    screenBorderColor,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }

            buttonA.setImageResource(R.drawable.ic_button_ab_dmg)
            buttonB.setImageResource(R.drawable.ic_button_ab_dmg)

            buttonStart.setImageResource(R.drawable.ic_button_startselect_dmg)
            buttonSelect.setImageResource(R.drawable.ic_button_startselect_dmg)

            buttonStart.rotation = -45f
            buttonSelect.rotation = -45f

            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                val px = (resources.displayMetrics.density + 0.5f).toInt()

                bottomLeftCorner.layoutParams.apply {
                    width = 16 * px
                    height = width
                }

                bottomRightCorner.layoutParams.apply {
                    width = 64 * px
                    height = width
                }
            }
        }

        buttonA.scaleX = prefs.getFloat(getString(R.string.a_scale_key), 1f)
        buttonA.scaleY = buttonA.scaleX
        buttonB.scaleX = prefs.getFloat(getString(R.string.b_scale_key), 1f)
        buttonB.scaleY = buttonB.scaleX
        buttonStart.scaleX = prefs.getFloat(getString(R.string.start_scale_key), 1f)
        buttonStart.scaleY = buttonStart.scaleX
        buttonSelect.scaleX = prefs.getFloat(getString(R.string.select_scale_key), 1f)
        buttonSelect.scaleY = buttonSelect.scaleX
        dpad.scaleX = prefs.getFloat(getString(R.string.dpad_scale_key), 1f)
        dpad.scaleY = dpad.scaleX

        buttonA.translationX = prefs.getFloat(getString(R.string.a_offset_x_key), 0f)
        buttonA.translationY = prefs.getFloat(getString(R.string.a_offset_y_key), 0f)
        buttonB.translationX = prefs.getFloat(getString(R.string.b_offset_x_key), 0f)
        buttonB.translationY = prefs.getFloat(getString(R.string.b_offset_y_key), 0f)
        buttonStart.translationX = prefs.getFloat(getString(R.string.start_offset_x_key), 0f)
        buttonStart.translationY = prefs.getFloat(getString(R.string.start_offset_y_key), 0f)
        buttonSelect.translationX = prefs.getFloat(getString(R.string.select_offset_x_key), 0f)
        buttonSelect.translationY = prefs.getFloat(getString(R.string.select_offset_y_key), 0f)
        dpad.translationX = prefs.getFloat(getString(R.string.dpad_offset_x_key), 0f)
        dpad.translationY = prefs.getFloat(getString(R.string.dpad_offset_y_key), 0f)

        setSizes()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        super.onCreate(savedInstanceState)

        window.decorView.setOnSystemUiVisibilityChangeListener {
            hideNavigation()
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        requestedOrientation = prefs.getString("orientation", "-1")?.toInt() ?: -1

        setContentView(R.layout.activity_arrange)

        placeholderTouchTarget.setOnTouchListener { v, _ ->
            // This shouldn't be needed, but Android
            // seems to act strangely when the root view is touched
            // and ignores any further touches.
            if (v != placeholderTouchTarget) {
                return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }

        updateLayout(
            when(prefs.getString("skin", "auto")) {
                "dmg" -> false
                "gbc" -> true
                else -> true
            }
        )
    }

    override fun onStop() {
        super.onStop()

        prefs.edit {
            putFloat(getString(R.string.a_scale_key), buttonA.scaleX)
            putFloat(getString(R.string.b_scale_key), buttonB.scaleX)
            putFloat(getString(R.string.start_scale_key), buttonStart.scaleX)
            putFloat(getString(R.string.select_scale_key), buttonSelect.scaleX)
            putFloat(getString(R.string.dpad_scale_key), dpad.scaleX)

            putFloat(getString(R.string.a_offset_x_key), buttonA.translationX)
            putFloat(getString(R.string.a_offset_y_key), buttonA.translationY)
            putFloat(getString(R.string.b_offset_x_key), buttonB.translationX)
            putFloat(getString(R.string.b_offset_y_key), buttonB.translationY)
            putFloat(getString(R.string.start_offset_x_key), buttonStart.translationX)
            putFloat(getString(R.string.start_offset_y_key), buttonStart.translationY)
            putFloat(getString(R.string.select_offset_x_key), buttonSelect.translationX)
            putFloat(getString(R.string.select_offset_y_key), buttonSelect.translationY)
            putFloat(getString(R.string.dpad_offset_x_key), dpad.translationX)
            putFloat(getString(R.string.dpad_offset_y_key), dpad.translationY)
            apply()
        }
    }

    private fun setSizes() {
        abSlider.value = sizeToValue(buttonA.scaleX)
        startSelectSlider.value = sizeToValue(buttonStart.scaleX)
        dpadSlider.value = sizeToValue(dpad.scaleX)
    }

    fun resetSizes(@Suppress("UNUSED_PARAMETER") view: View) {
        abSlider.value = 0.5F
        startSelectSlider.value = 0.5F
        dpadSlider.value = 0.5F
    }

    fun resetLayout(@Suppress("UNUSED_PARAMETER") view: View) {
        buttonA.translationX = 0f
        buttonA.translationY = 0f
        buttonB.translationX = 0f
        buttonB.translationY = 0f
        buttonStart.translationX = 0f
        buttonStart.translationY = 0f
        buttonSelect.translationX = 0f
        buttonSelect.translationY = 0f
        dpad.translationX = 0f
        dpad.translationY = 0f
    }

    private fun valueToSize(value: Float) : Float {
        val t : Float = value * 2 - 1f
        return scaleFactorRange.pow(t)
    }

    private fun sizeToValue(size: Float) : Float {
        val t : Float = log(size, scaleFactorRange)
        return (t + 1f) / 2
    }

    private inner class SliderListener: Slider.OnChangeListener {

        override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
            val scale = valueToSize(value)
            when (slider.id) {
                R.id.abSlider -> {
                    buttonA.scaleX = scale
                    buttonA.scaleY = scale
                    buttonB.scaleX = scale
                    buttonB.scaleY = scale
                }

                R.id.startSelectSlider -> {
                    buttonStart.scaleX = scale
                    buttonStart.scaleY = scale
                    buttonSelect.scaleX = scale
                    buttonSelect.scaleY = scale
                }

                R.id.dpadSlider -> {
                    dpad.scaleX = scale
                    dpad.scaleY = scale
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideNavigation()
        }
    }

    private fun hideNavigation() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }
}

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
            vibrate()
            return@OnLongClickListener false
        })
    }

    private fun vibrate() {
        val milliseconds: Long = 10
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }
    }
}

class ScreenPlaceholder : AppCompatImageView {

    constructor(context: Context) : super(context) {
        setMeasuredDimension(160, 144)
        layoutParams = ViewGroup.LayoutParams(160, 144)
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        setMeasuredDimension(160, 144)
        layoutParams = ViewGroup.LayoutParams(160, 144)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var width = 0
        var height = 0
        val scaleX = widthSize / 160
        val scaleY = heightSize / 144

        when(widthMode) {
            MeasureSpec.EXACTLY -> width = widthSize
            MeasureSpec.AT_MOST -> Unit
            MeasureSpec.UNSPECIFIED -> width = 160
        }

        when(heightMode) {
            MeasureSpec.EXACTLY -> height = heightSize
            MeasureSpec.AT_MOST -> Unit
            MeasureSpec.UNSPECIFIED -> height = 144
        }

        if (width == 0 && height == 0) {
            val scale = min(scaleX, scaleY)
            width = 160 * scale
            height = 144 * scale
        } else if (width == 0) {
            width = (height * 160) / 144
        } else if (height == 0) {
            height = (width * 144) / 160
        }

        setMeasuredDimension(width, height)
    }
}
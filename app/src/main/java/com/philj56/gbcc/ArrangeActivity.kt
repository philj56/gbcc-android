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
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.slider.Slider
import com.philj56.gbcc.databinding.ActivityArrangeBinding
import kotlin.math.log
import kotlin.math.min
import kotlin.math.pow


class ArrangeActivity : AppCompatActivity() {
    private val sliderListener = SliderListener()
    private val scaleFactorRange : Float = 2f

    private lateinit var prefs : SharedPreferences
    private lateinit var binding: ActivityArrangeBinding

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
            false -> when (prefs.getString("dmg_color", "Light")) {
                "Light" -> R.color.dmgLightBackground
                "Dark" -> R.color.dmgDarkBackground
                else -> R.color.dmgLightBackground
            }
        }
        window.setBackgroundDrawableResource(bgColor)

        binding.resetLayout.setOnClickListener { resetLayout() }
        binding.resetSizes.setOnClickListener { resetSizes() }

        binding.sliders.abSlider.addOnChangeListener(sliderListener)
        binding.sliders.startSelectSlider.addOnChangeListener(sliderListener)
        binding.sliders.dpadSlider.addOnChangeListener(sliderListener)

        if (!gbc) {
            val screenBorderColor: Int
            val theme = prefs.getString("dmg_color", "Light")
            if (theme == "Dark") {
                screenBorderColor = ContextCompat.getColor(this, R.color.dmgDarkScreenBorder)
                binding.dpad.dpadBackground.setColorFilter(
                    ContextCompat.getColor(this, R.color.dmgDarkDpad),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )

                binding.buttonA.setImageResource(R.drawable.ic_button_ab_dmg_dark_selector)
                binding.buttonB.setImageResource(R.drawable.ic_button_ab_dmg_dark_selector)

                binding.buttonStart.setImageResource(R.drawable.ic_button_startselect_dmg_dark_selector)
                binding.buttonSelect.setImageResource(R.drawable.ic_button_startselect_dmg_dark_selector)

                binding.turboToggleLayout.turboToggle.visibility = View.INVISIBLE
                binding.turboToggleLayout.turboToggleDark.visibility = View.VISIBLE
            } else {
                screenBorderColor = ContextCompat.getColor(this, R.color.dmgLightScreenBorder)
                binding.buttonA.setImageResource(R.drawable.ic_button_ab_dmg_selector)
                binding.buttonB.setImageResource(R.drawable.ic_button_ab_dmg_selector)

                binding.buttonStart.setImageResource(R.drawable.ic_button_startselect_dmg_selector)
                binding.buttonSelect.setImageResource(R.drawable.ic_button_startselect_dmg_selector)
            }

            binding.buttonStart.rotation = -45f
            binding.buttonSelect.rotation = -45f

            val borders = arrayOf(
                binding.screenBorderTop,
                binding.screenBorderBottom,
                binding.screenBorderLeft,
                binding.screenBorderRight
            )

            borders.forEach {
                it.setColorFilter(
                    screenBorderColor,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }

            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                val px = (resources.displayMetrics.density + 0.5f).toInt()

                binding.bottomLeftCorner.layoutParams.apply {
                    width = 16 * px
                    height = width
                }

                binding.bottomRightCorner.layoutParams.apply {
                    width = 64 * px
                    height = width
                }
            }
        }

        binding.buttonA.scaleX = prefs.getFloat(getString(R.string.a_scale_key), 1f)
        binding.buttonA.scaleY = binding.buttonA.scaleX
        binding.buttonB.scaleX = prefs.getFloat(getString(R.string.b_scale_key), 1f)
        binding.buttonB.scaleY = binding.buttonB.scaleX
        binding.buttonStart.scaleX = prefs.getFloat(getString(R.string.start_scale_key), 1f)
        binding.buttonStart.scaleY = binding.buttonStart.scaleX
        binding.buttonSelect.scaleX = prefs.getFloat(getString(R.string.select_scale_key), 1f)
        binding.buttonSelect.scaleY = binding.buttonSelect.scaleX
        binding.dpad.root.scaleX = prefs.getFloat(getString(R.string.dpad_scale_key), 1f)
        binding.dpad.root.scaleY = binding.dpad.root.scaleX
        binding.turboToggleLayout.root.scaleX = prefs.getFloat(getString(R.string.turbo_scale_key), 1f)
        binding.turboToggleLayout.root.scaleY = binding.turboToggleLayout.root.scaleX

        binding.buttonA.translationX = prefs.getFloat(getString(R.string.a_offset_x_key), 0f)
        binding.buttonA.translationY = prefs.getFloat(getString(R.string.a_offset_y_key), 0f)
        binding.buttonB.translationX = prefs.getFloat(getString(R.string.b_offset_x_key), 0f)
        binding.buttonB.translationY = prefs.getFloat(getString(R.string.b_offset_y_key), 0f)
        binding.buttonStart.translationX = prefs.getFloat(getString(R.string.start_offset_x_key), 0f)
        binding.buttonStart.translationY = prefs.getFloat(getString(R.string.start_offset_y_key), 0f)
        binding.buttonSelect.translationX = prefs.getFloat(getString(R.string.select_offset_x_key), 0f)
        binding.buttonSelect.translationY = prefs.getFloat(getString(R.string.select_offset_y_key), 0f)
        binding.dpad.root.translationX = prefs.getFloat(getString(R.string.dpad_offset_x_key), 0f)
        binding.dpad.root.translationY = prefs.getFloat(getString(R.string.dpad_offset_y_key), 0f)
        binding.turboToggleLayout.root.translationX = prefs.getFloat(getString(R.string.turbo_offset_x_key), 0f)
        binding.turboToggleLayout.root.translationY = prefs.getFloat(getString(R.string.turbo_offset_y_key), 0f)

        if (!prefs.getBoolean("show_turbo", false)) {
            binding.turboToggleLayout.turboToggle.visibility = View.GONE
            binding.turboToggleLayout.turboToggleDark.visibility = View.GONE
        }

        setSizes()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        requestedOrientation = prefs.getString("orientation", "-1")?.toInt() ?: -1

        binding = ActivityArrangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideNavigation()

        binding.placeholderTouchTarget.setOnTouchListener { v, _ ->
            // This shouldn't be needed, but Android
            // seems to act strangely when the root view is touched
            // and ignores any further touches.
            if (v != binding.placeholderTouchTarget) {
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
            putFloat(getString(R.string.a_scale_key), binding.buttonA.scaleX)
            putFloat(getString(R.string.b_scale_key), binding.buttonB.scaleX)
            putFloat(getString(R.string.start_scale_key), binding.buttonStart.scaleX)
            putFloat(getString(R.string.select_scale_key), binding.buttonSelect.scaleX)
            putFloat(getString(R.string.dpad_scale_key), binding.dpad.root.scaleX)
            putFloat(getString(R.string.turbo_scale_key), binding.turboToggleLayout.root.scaleX)

            putFloat(getString(R.string.a_offset_x_key), binding.buttonA.translationX)
            putFloat(getString(R.string.a_offset_y_key), binding.buttonA.translationY)
            putFloat(getString(R.string.b_offset_x_key), binding.buttonB.translationX)
            putFloat(getString(R.string.b_offset_y_key), binding.buttonB.translationY)
            putFloat(getString(R.string.start_offset_x_key), binding.buttonStart.translationX)
            putFloat(getString(R.string.start_offset_y_key), binding.buttonStart.translationY)
            putFloat(getString(R.string.select_offset_x_key), binding.buttonSelect.translationX)
            putFloat(getString(R.string.select_offset_y_key), binding.buttonSelect.translationY)
            putFloat(getString(R.string.dpad_offset_x_key), binding.dpad.root.translationX)
            putFloat(getString(R.string.dpad_offset_y_key), binding.dpad.root.translationY)
            putFloat(getString(R.string.turbo_offset_x_key), binding.turboToggleLayout.root.translationX)
            putFloat(getString(R.string.turbo_offset_y_key), binding.turboToggleLayout.root.translationY)
            apply()
        }
    }

    private fun setSizes() {
        binding.sliders.abSlider.value = sizeToValue(binding.buttonA.scaleX)
        binding.sliders.startSelectSlider.value = sizeToValue(binding.buttonStart.scaleX)
        binding.sliders.dpadSlider.value = sizeToValue(binding.dpad.root.scaleX)
    }

    private fun resetSizes() {
        binding.sliders.abSlider.value = 0.5F
        binding.sliders.startSelectSlider.value = 0.5F
        binding.sliders.dpadSlider.value = 0.5F
        binding.turboToggleLayout.root.scaleX = 1f
        binding.turboToggleLayout.root.scaleY = 1f
    }

    private fun resetLayout() {
        binding.buttonA.translationX = 0f
        binding.buttonA.translationY = 0f
        binding.buttonB.translationX = 0f
        binding.buttonB.translationY = 0f
        binding.buttonStart.translationX = 0f
        binding.buttonStart.translationY = 0f
        binding.buttonSelect.translationX = 0f
        binding.buttonSelect.translationY = 0f
        binding.dpad.root.translationX = 0f
        binding.dpad.root.translationY = 0f
        binding.turboToggleLayout.root.translationX = 0f
        binding.turboToggleLayout.root.translationY = 0f
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
                    binding.buttonA.scaleX = scale
                    binding.buttonA.scaleY = scale
                    binding.buttonB.scaleX = scale
                    binding.buttonB.scaleY = scale
                }

                R.id.startSelectSlider -> {
                    binding.buttonStart.scaleX = scale
                    binding.buttonStart.scaleY = scale
                    binding.buttonSelect.scaleX = scale
                    binding.buttonSelect.scaleY = scale
                }

                R.id.dpadSlider -> {
                    binding.dpad.root.scaleX = scale
                    binding.dpad.root.scaleY = scale
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
        @Suppress("Deprecation")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(
                WindowInsets.Type.statusBars()
                    or WindowInsets.Type.navigationBars())
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
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

class ResizableLayout : FrameLayout {
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
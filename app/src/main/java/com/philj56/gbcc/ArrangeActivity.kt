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
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.google.android.material.slider.Slider
import com.philj56.gbcc.databinding.ActivityArrangeBinding
import kotlin.math.log
import kotlin.math.pow

class ArrangeActivity : BaseActivity() {
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

        binding.sliders.abSlider.addOnChangeListener { slider, value, fromUser -> onValueChange(slider, value, fromUser) }
        binding.sliders.startSelectSlider.addOnChangeListener { slider, value, fromUser -> onValueChange(slider, value, fromUser) }
        binding.sliders.dpadSlider.addOnChangeListener { slider, value, fromUser -> onValueChange(slider, value, fromUser) }

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

                binding.turboToggleLayout.turboToggle.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dmgDarkToggleThumb))
                binding.turboToggleLayout.turboToggle.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dmgDarkToggleTrack))
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
        }

        setSizes()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).let {
            // Hide system bars
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
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

    @Suppress("UNUSED_PARAMETER")
    private fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
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


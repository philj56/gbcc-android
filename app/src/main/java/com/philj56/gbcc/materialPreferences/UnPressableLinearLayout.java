/*
 * This file is an exact copy of the corresponding androidx file,
 * but that's not exported by the library and I need it for the
 * SliderPreference.
 */
/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.philj56.gbcc.materialPreferences;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
/**
 * Custom {@link LinearLayout} that does not propagate the pressed state down to its children.
 * By default, the pressed state is propagated to all the children that are not clickable
 * or long-clickable.
 *
 * Used by Leanback and Car.
 *
 * @hide
 */
public class UnPressableLinearLayout extends LinearLayout {
    public UnPressableLinearLayout(Context context) {
        this(context, null);
    }
    public UnPressableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    @Override
    protected void dispatchSetPressed(boolean pressed) {
        // Skip dispatching the pressed key state to the children so that they don't trigger any
        // pressed state animation on their stateful drawables.
    }
}

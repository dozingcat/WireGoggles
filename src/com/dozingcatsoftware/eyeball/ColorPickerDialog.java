/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.dozingcatsoftware.eyeball;

import org.openintents.widget.ColorCircle;
import org.openintents.widget.ColorSlider;
import org.openintents.widget.OnColorChangedListener;

import com.dozingcatsoftware.WireGoggles.R;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

public class ColorPickerDialog extends Dialog implements OnColorChangedListener {

    public interface OnColorChangedListener {
        void colorChanged(int color);
    }

    private OnColorChangedListener mListener;
    ColorCircle mColorCircle;
    ColorSlider mSaturation;
    ColorSlider mValue;
    int mInitialColor;

	void initializeColor(int color) {

        mColorCircle = (ColorCircle) findViewById(R.id.colorcircle);
        mColorCircle.setOnColorChangedListener(this);
        mColorCircle.setColor(color);

        mSaturation = (ColorSlider) findViewById(R.id.saturation);
        mSaturation.setOnColorChangedListener(this);
        mSaturation.setColors(color, Color.BLACK);

        mValue = (ColorSlider) findViewById(R.id.value);
        mValue.setOnColorChangedListener(this);
        mValue.setColors(Color.WHITE, color);
	}


    public ColorPickerDialog(Context context,
                             OnColorChangedListener listener,
                             int initialColor) {
        super(context);

        mListener = listener;
        mInitialColor = initialColor;
    }

	@Override
    public void onColorChanged(View view, int newColor) {
		if (view == mColorCircle) {
			mValue.setColors(0xFFFFFFFF, newColor);
	        mSaturation.setColors(newColor, 0xff000000);
		} else if (view == mSaturation) {
			mColorCircle.setColor(newColor);
			mValue.setColors(0xFFFFFFFF, newColor);
		} else if (view == mValue) {
			mColorCircle.setColor(newColor);
		}

	}


	@Override
    public void onColorPicked(View view, int newColor) {
        mListener.colorChanged(newColor);
        dismiss();
	}


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.colorpicker);
        setTitle("Pick a Color");
        initializeColor(mInitialColor);
    }
}

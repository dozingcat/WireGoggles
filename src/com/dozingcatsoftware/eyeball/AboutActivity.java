package com.dozingcatsoftware.eyeball;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;

import com.dozingcatsoftware.WireGoggles.R;

public class AboutActivity extends Activity {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.about);
    }
}

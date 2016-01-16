package com.dozingcatsoftware.eyeball;

import com.dozingcatsoftware.WireGoggles.R;
//import com.dozingcatsoftware.WireGogglesFree.R;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class WGPreferences extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		int width = this.getIntent().getIntExtra("width", 0);
		int height = this.getIntent().getIntExtra("height", 0);
		
		RecordingQualityPreference qualityPref = (RecordingQualityPreference)findPreference(getString(R.string.recordingQualityPrefsKey));
		qualityPref.updateDisplaySize(width, height);
	}

	public static Intent startActivity(Context parent, int width, int height) {
		Intent intent = new Intent(parent, WGPreferences.class);
		intent.putExtra("width", width);
		intent.putExtra("height", height);
		// don't return to preferences screen if the user goes home
    	intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		parent.startActivity(intent);
		return intent;
	}
}

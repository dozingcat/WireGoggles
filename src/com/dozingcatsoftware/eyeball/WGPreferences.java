package com.dozingcatsoftware.eyeball;

import com.dozingcatsoftware.WireGoggles.R;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;

public class WGPreferences extends PreferenceActivity {
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		int width = this.getIntent().getIntExtra("width", 0);
		int height = this.getIntent().getIntExtra("height", 0);

		RecordingQualityPreference qualityPref = (RecordingQualityPreference)findPreference(getString(R.string.recordingQualityPrefsKey));
		qualityPref.updateDisplaySize(width, height);

		// Show the description of the selected video export type. Setting android:summary="%s"
		// in preferences.xml does the same thing, but only on post-Gingerbread Android versions.
		final ListPreference videoExportPref = (ListPreference)findPreference(getString(R.string.videoExportTypePrefsKey));
		videoExportPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateDisplayedSummary(videoExportPref, (String) newValue);
                return true;
            }
		});
		updateDisplayedSummary(videoExportPref, videoExportPref.getValue());
	}

	void updateDisplayedSummary(ListPreference pref, String value) {
	    int index = pref.findIndexOfValue(value);
	    if (index == -1) index = 0;
	    pref.setSummary(pref.getEntries()[index]);
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

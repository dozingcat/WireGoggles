package com.dozingcatsoftware.eyeball;

import com.dozingcatsoftware.WireGoggles.R;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
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

		RecordingQualityPreference qualityPref =
                (RecordingQualityPreference)findPreference(getString(R.string.recordingQualityPrefsKey));
		qualityPref.updateDisplaySize(
		        getResources().getStringArray(R.array.recordingQualityLabels), width, height);

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

		Preference autoConvertPref = getPreferenceManager().findPreference(getString(R.string.autoConvertPicturesPrefsKey));
        autoConvertPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference pref, Object value) {
                // Update broadcast receivers immediately so the change takes effect even if the
                // user doesn't go back to the main activity.
                setAutoConvertEnabled(WGPreferences.this, Boolean.TRUE.equals(value));
                return true;
            }
        });
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

    /**
     * Sets whether pictures saved by the camera app should automatically be converted via the
     * NewPictureReceiver broadcast receiver, or the NewPictureJob service in Android N or later.
     */
    public static void setAutoConvertEnabled(Context context, boolean enabled) {
        // For N and above, schedule or cancel a JobService.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (enabled) {
                NewPictureJob.scheduleJob(context);
            }
            else {
                NewPictureJob.cancelJob(context);
            }
        }
        else {
            boolean receiverEnabled = false;
            boolean legacyReceiverEnabled = false;
            if (enabled) {
                try {
                    // Android 4.0 and later have a Camera.ACTION_NEW_PICTURE constant, which camera
                    // apps send after taking a picture. The NewPictureReceiver class listens for
                    // this broadcast. Earlier Android versions send the undocumented
                    // com.android.camera.NEW_PICTURE. This determines which receiver to enable
                    // based on whether the ACTION_NEW_PICTURE field exists.
                    android.hardware.Camera.class.getField("ACTION_NEW_PICTURE");
                    receiverEnabled = true;
                }
                catch(Exception ex) {
                    legacyReceiverEnabled = true;
                }
            }
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(new ComponentName(context, NewPictureReceiver.class),
                    receiverEnabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(
                    new ComponentName(context, NewPictureReceiverLegacyBroadcast.class),
                    legacyReceiverEnabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}

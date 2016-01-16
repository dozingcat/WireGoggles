package com.dozingcatsoftware.eyeball;

import com.dozingcatsoftware.eyeball.video.VideoRecorder;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

/** Subclass of ListPreference to handle setting summary and item display strings
 * according to resolution passed to parent PreferenceActivity.
 */
public class RecordingQualityPreference extends ListPreference {
	
	int width;
	int height;

	public RecordingQualityPreference(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}
	
	public void updateDisplaySize(int newWidth, int newHeight) {
		this.width = newWidth;
		this.height = newHeight;
		updateSummary(this.getValue());
		
		CharSequence[] entries = this.getEntries();
		CharSequence[] entryValues = this.getEntryValues();
		for(int i=0; i<entries.length; i++) {
			VideoRecorder.Quality quality = VideoRecorder.Quality.valueOf(entryValues[i].toString());
			entries[i] = String.format(entries[i].toString(), 
					VideoRecorder.scaledSizeForQuality(this.width, quality),
					VideoRecorder.scaledSizeForQuality(this.height, quality));
		}
		this.setEntries(entries);
	}
	
	void updateSummary(String value) {
		try {
			int index = this.findIndexOfValue(value);
			VideoRecorder.Quality quality = VideoRecorder.Quality.valueOf(value);
			this.setSummary(String.format(this.getEntries()[index].toString(),
					VideoRecorder.scaledSizeForQuality(this.width, quality),
					VideoRecorder.scaledSizeForQuality(this.height, quality)));
		}
		catch(Exception ex) {
			this.setSummary("");
		}
	}
	
	@Override
	protected boolean callChangeListener(Object newValue) {
		updateSummary(newValue.toString());
		return super.callChangeListener(newValue);
	}

}

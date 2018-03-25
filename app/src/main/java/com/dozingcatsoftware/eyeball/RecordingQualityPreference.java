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
	
	public void updateDisplaySize(String[] baseLabels, int newWidth, int newHeight) {
		this.width = newWidth;
		this.height = newHeight;

		CharSequence[] entryValues = this.getEntryValues();
		String[] labelsWithResolution = new String[baseLabels.length];
		for(int i=0; i<baseLabels.length; i++) {
            labelsWithResolution[i] = qualityDescription(entryValues[i].toString(), baseLabels[i]);
		}
		this.setEntries(labelsWithResolution);
        updateSummary(this.getValue());
	}
	
	private void updateSummary(String value) {
		try {
			int index = this.findIndexOfValue(value);
			this.setSummary(this.getEntries()[index]);
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

	private String qualityDescription(String qualityValue, String qualityLabel) {
		VideoRecorder.Quality quality = VideoRecorder.Quality.valueOf(qualityValue);
		String resolutionSuffix = "";
		if (this.width > 0 && this.height > 0) {
			resolutionSuffix = String.format(" (%dx%d)",
					VideoRecorder.scaledSizeForQuality(this.width, quality),
					VideoRecorder.scaledSizeForQuality(this.height, quality));
		}
		return qualityLabel + resolutionSuffix;
	}
}

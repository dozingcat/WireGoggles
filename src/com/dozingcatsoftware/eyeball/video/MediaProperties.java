package com.dozingcatsoftware.eyeball.video;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MediaProperties {
	
	JSONObject properties;
	
	public MediaProperties() {
		this.properties = new JSONObject();
	}
	
	public MediaProperties(JSONObject props) {
		this.properties = props;
	}

	int getIntProperty(String key, int defvalue) {
		return properties.optInt(key, defvalue);
	}
	
	long getLongProperty(String key, long defvalue) {
		return properties.optLong(key, defvalue);
	}

	
	void setProperty(String key, Object value) {
		try {properties.put(key, value);}
		catch(Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public int getVersion() {
		return getIntProperty("version", 1);
	}
	public void setVersion(Integer value) {
		setProperty("version", value);
	}
	
	public int getWidth() {
		return getIntProperty("width", -1);
	}
	public void setWidth(Integer value) {
		setProperty("width", value);
	}
	
	public int getHeight() {
		return getIntProperty("height", -1);
	}
	public void setHeight(Integer value) {
		setProperty("height", value);
	}
	
	public int getNumberOfFrames() {
		return getIntProperty("numFrames", -1);
	}
	public void setNumberOfFrames(Integer value) {
		setProperty("numFrames", value);
	}
	
	public long getStartTime() {
		return getLongProperty("startTime", -1);
	}
	public void setStartTime(Long value) {
		setProperty("startTime", value);
	}

	public long getEndTime() {
		return getLongProperty("endTime", -1);
	}
	public void setEndTime(Long value) {
		setProperty("endTime", value);
	}
	
	public int getColorScheme() {
		return getIntProperty("colorScheme", -1);
	}
	public void setColorScheme(Integer value) {
		setProperty("colorScheme", value);
	}
	
	public boolean isSolidColor() {
		return properties.optBoolean("solidColor", false);
	}
	public void setSolidColor(boolean value) {
		setProperty("solidColor", value);
	}
	
	public boolean useNoiseFilter() {
		return properties.optBoolean("noiseFilter", false);
	}
	public void setUseNoiseFilter(boolean value) {
		setProperty("noiseFilter", value);
	}
	
	public Date dateCreated() {
		long start = getStartTime();
		if (start==-1) {
			// shouldn't happen
			return new Date();
		}
		return new Date(start);
	}
	
	public long durationInMilliseconds() {
		long start = getStartTime();
		long end = getEndTime();
		if (start==-1 || end==-1) return 0;
		return end-start;
	}
	
	public long durationInSeconds() {
		return durationInMilliseconds() / 1000;
	}

	// arrays require annoying conversions to/from JSONArray
	public List<Integer> getFrameDurations() {
		try {
			JSONArray array = properties.getJSONArray("frameDurations");
			List<Integer> result = new ArrayList<Integer>();
			int size = array.length();
			for(int i=0; i<size; i++) {
				result.add(((Number)array.get(i)).intValue());
			}
			return result;
		}
		catch(JSONException ex) {
			return null;
		}
	}
	
	public int[] getFrameDurationArray() {
		List<Integer> durations = getFrameDurations();
		if (durations==null) return null;
		int[] result = new int[durations.size()];
		for(int i=0; i<result.length; i++) {
			result[i] = durations.get(i);
		}
		return result;
	}
	
	/** Returns an array which for each frame contains the number of milliseconds since the video start that the frame ends.
	 * For example, if the frame durations are 20, 30, and 25 milliseconds, the returned array will be [20, 50, 75]. 
	 */
	public int[] getFrameRelativeEndTimes() {
		int[] result = getFrameDurationArray();
		if (result!=null) {
			for(int i=1; i<result.length; i++) {
				result[i] += result[i-1];
			}
		}
		return result;
	}
	
	public void setFrameDurations(List<Integer> durations) {
		JSONArray array = new JSONArray();
		for(Number n : durations) {
			array.put(n);
		}
		setProperty("frameDurations", array);
	}
	

}

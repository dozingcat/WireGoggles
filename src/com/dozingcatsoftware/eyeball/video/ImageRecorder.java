package com.dozingcatsoftware.eyeball.video;

import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;

import com.dozingcatsoftware.eyeball.CameraImageProcessor;
import com.dozingcatsoftware.eyeball.WGUtils;
import com.dozingcatsoftware.util.AndroidUtils;

/** This class handles storing data and metadata for single images. It uses VideoRecorder to record a single-frame "video".
 */

public class ImageRecorder {

	Context context;
	String imageDirectory;
	MediaProperties imageProperties;

	public static int THUMBNAIL_SIZE = 120;

	public ImageRecorder(Context context, String directory) {
		this.context = context;
		this.imageDirectory = directory;
	}

	public void saveImage(byte[] data, Bitmap bitmap, int width, int height,
	        Integer colorScheme, boolean solidColor, boolean noiseFilter, CameraImageProcessor.Orientation orientation)
	                throws IOException {
		VideoRecorder recorder = new VideoRecorder(this.imageDirectory, width, height, VideoRecorder.Quality.HIGH,
				colorScheme, solidColor, noiseFilter, orientation);
		recorder.recordFrame(data);
		recorder.endRecording();
		recorder.storeThumbnailImage(bitmap, THUMBNAIL_SIZE);
		writePNGImage(bitmap);
	}


	public void saveImage(CameraImageProcessor imageProcessor, Integer colorScheme, boolean solidColor, boolean noiseFilter) throws IOException {
		this.saveImage(imageProcessor.getImageData(), imageProcessor.getBitmap(), imageProcessor.getImageWidth(), imageProcessor.getImageHeight(),
				colorScheme, solidColor, noiseFilter, imageProcessor.getOrientation());
	}

	public void writePNGImage(Bitmap bitmap) throws IOException {
		String path = this.imageDirectory + ".png";
		WGUtils.savePicture(bitmap, path);
		AndroidUtils.scanSavedMediaFile(context, path);
	}
}

package com.dozingcatsoftware.eyeball.video;

import com.dozingcatsoftware.eyeball.CameraImageProcessor;

import android.graphics.Bitmap;
import android.util.Log;

public class WebMEncoder {

	MediaDirectory videoDirectory;
	MediaProperties videoProperties;
	VideoReader videoReader;
	CameraImageProcessor imageProcessor;
	String outputPath;
	//int quality;
	int framesEncoded = 0;
	EncodingCallback encodingCallback;

	byte[] frameData;
	int[] frameARGB;
	Thread encodingThread;
	volatile boolean abortEncodingThread;


	public static interface EncodingCallback {
		public void frameEncoded(WebMEncoder encoder, int frameNumber, int totalFrames);
	}

	public WebMEncoder(MediaDirectory vd, String outputPath, CameraImageProcessor imageProcessor) {
		this.videoDirectory = vd;
		this.videoProperties = vd.getVideoProperties();
		this.outputPath = outputPath;
		this.videoReader = new VideoReader(videoDirectory.getPath());
		this.imageProcessor = imageProcessor;
	}

	public void setEncodingCallback(EncodingCallback value) {
		this.encodingCallback = value;
	}

	public String getOutputPath() {
		return outputPath;
	}

	public void startEncoding() {
		this.frameData = new byte[videoProperties.getWidth() * videoProperties.getHeight()];
		this.frameARGB = new int[this.frameData.length];
		float fps = videoProperties.getNumberOfFrames() / (videoProperties.durationInMilliseconds() / 1000.0f);
		int[] frameEndTimes = videoProperties.getFrameRelativeEndTimes();
		int deadline = 1000000; // In microseconds; normally VP8 is much faster than one frame per second.
		this.nativeStartEncoding(outputPath, videoProperties.getWidth(), videoProperties.getHeight(), fps, frameEndTimes, deadline);
	}

	public boolean encodeNextFrame() {
    	try {
        	videoReader.getDataForNextFrame(frameData);
        	imageProcessor.processCameraImage(frameData, videoProperties.getWidth(), videoProperties.getHeight());
    		Bitmap bitmap = imageProcessor.getBitmap();
    		bitmap.getPixels(this.frameARGB, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
    		nativeEncodeFrame(this.frameARGB);
    	}
    	catch(Throwable ex) {
    		Log.e("WG-WEBM", "Error encoding frame", ex);
    		return false;
    	}
		return true;
	}

	public boolean finishEncoding() {
		try {
			nativeFinishEncoding();
		}
		catch(Throwable ex) {
    		Log.e("WG-WEBM", "Error finishing encoding", ex);
    		return false;
		}
		this.frameData = null;
		this.frameARGB = null;
		this.imageProcessor = null;
		return true;
	}

	public int numberOfFrames() {
		return videoProperties.getNumberOfFrames();
	}

	public int currentFrameNumber() {
		return videoReader.currentFrameNumber();
	}

	public boolean allFramesFinished() {
		return videoReader.isAtEnd();
	}

	public void runEncodingInThread() {

	}

	public void cancelEncoding() {
		if (encodingThread!=null) {
			abortEncodingThread = true;
			encodingThread.interrupt();
			try {encodingThread.join(100);}
			catch(InterruptedException ex) {}
		}
		nativeCancelEncoding();
		(new java.io.File(outputPath)).delete();
	}

    public native int nativeStartEncoding(String path, int width, int height, float fps, int[] frameEndTimes, int deadline);
    public native int nativeEncodeFrame(int[] argb);
    public native int nativeFinishEncoding();
    public native int nativeCancelEncoding();

}

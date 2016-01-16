package com.dozingcatsoftware.eyeball.video;

import java.io.IOException;
import java.io.RandomAccessFile;

public class VideoReader {
	MediaDirectory videoDirectory;	
	MediaProperties videoProperties;
	
	RandomAccessFile videoFile;
	int bytesPerFrame;
	int currentFrame;
	
	
	public VideoReader(String directory) {
		this.videoDirectory = new MediaDirectory(directory);
		this.videoProperties = videoDirectory.getVideoProperties();
		this.bytesPerFrame = videoProperties.getWidth() * videoProperties.getHeight();
	}
	
	public MediaProperties getVideoProperties() {
		return videoProperties;
	}
	
	public MediaDirectory getVideoDirectory() {
		return videoDirectory;
	}
	
	// fills data with bytes for next frame, and advances frame number
	public void getDataForNextFrame(byte[] data) throws IOException {
		if (this.videoFile==null) {
			this.videoFile = this.videoDirectory.videoRandomAccessFile();
		}
		
		long offset = currentFrame * bytesPerFrame;
		if (offset!=videoFile.getFilePointer()) {
			videoFile.seek(offset);
		}
		videoFile.readFully(data, 0, bytesPerFrame);
		++currentFrame;
	}
	
	public void moveToFrameNumber(int frameNumber) {
		if (frameNumber<0) frameNumber = 0;
		int maxFrames = this.videoProperties.getNumberOfFrames();
		if (frameNumber>=maxFrames) frameNumber = maxFrames;
		
		this.currentFrame = frameNumber;
	}
	
	// returns the number of the next frame to be displayed. 0-based, so returns 0 for a video that hasn't started yet.
	public int currentFrameNumber() {
		return currentFrame;
	}
	
	public boolean isAtEnd() {
		return this.currentFrame >= this.videoProperties.getNumberOfFrames();
	}
}

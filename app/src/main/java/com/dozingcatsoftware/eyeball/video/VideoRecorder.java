package com.dozingcatsoftware.eyeball.video;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.dozingcatsoftware.eyeball.CameraImageProcessor.Orientation;
import com.dozingcatsoftware.util.AndroidUtils;

import android.graphics.Bitmap;

public class VideoRecorder {

	public enum Quality {
		// string values are stored in preferences, don't change
		LOW,
		MEDIUM,
		HIGH,
	}

	MediaDirectory videoDirectory;

	OutputStream videoOutputStream, audioOutputStream;
	long startTimestamp;
	MediaProperties videoProperties;
	int numFrames = 0;
	int bytesPerFrame;
	Quality quality;
	Orientation orientation;
	long lastFrameTimestamp;
	List<Integer> frameDurations;

	byte[] scaledImageBuffer;
	int fullWidth, fullHeight;
	int scaledWidth, scaledHeight;

	static int THUMBNAIL_SIZE = 80;

	/** Returns the number of pixels for the given dimension that will be recorded for the given quality setting.
	 * Currently 1/2 of input size for medium, 1/3 for low, and unchanged for high quality.
	 */
	public static int scaledSizeForQuality(int size, Quality quality) {
		if (quality==Quality.HIGH) return size;
		// Round up, e.g. a width of 11 would give us 6 samples.
		if (quality==Quality.MEDIUM) return size/2 + (size%2>0 ? 1 : 0);
		return size/3 + (size%3>0 ? 1 : 0);
	}

	public VideoRecorder(String directory, int width, int height, Quality quality, Integer colorScheme,
	        boolean solidColor, boolean noiseFilter, Orientation orientation) {
		this.videoDirectory = new MediaDirectory(directory);
		this.fullWidth = width;
		this.fullHeight = height;
		this.quality = quality;
		this.orientation = orientation;
		this.frameDurations = new ArrayList<Integer>();

		this.scaledWidth = scaledSizeForQuality(this.fullWidth, this.quality);
		this.scaledHeight = scaledSizeForQuality(this.fullHeight, this.quality);
		if (this.scaledWidth!=this.fullWidth || this.scaledHeight!=this.fullHeight) {
			this.scaledImageBuffer = new byte[scaledWidth * scaledHeight];
		}

		videoProperties = new MediaProperties();
		videoProperties.setVersion(1);
		videoProperties.setWidth(this.scaledWidth);
		videoProperties.setHeight(this.scaledHeight);
		videoProperties.setColorScheme(colorScheme);
		videoProperties.setSolidColor(solidColor);
		videoProperties.setUseNoiseFilter(noiseFilter);
	}

	public MediaDirectory getMediaDirectory() {
	    return videoDirectory;
	}

	public void recordFrame(byte[] data) throws IOException {
		recordFrame(data, System.currentTimeMillis());
	}

	public void recordFrame(byte[] data, long timestamp) throws IOException {
		// assuming width/height never changes
		if (numFrames==0) {
			this.startTimestamp = timestamp;
			videoProperties.setStartTime(timestamp);
			videoDirectory.storeVideoProperties(videoProperties);
			// compressing with GZIPOutputStream reduces size by around 50% but causes significant slowdown on N1
			//this.videoOutputStream = new GZIPOutputStream(new FileOutputStream(videoFilePath()));
			this.videoOutputStream = videoDirectory.videoOutputStream();
		}
		else {
			this.frameDurations.add((int)(timestamp - this.lastFrameTimestamp));
		}

		if (this.quality==Quality.HIGH) {
		    if (orientation == Orientation.ROTATED_180) {
		        writeReversed(this.videoOutputStream, data, 0, this.scaledWidth*this.scaledHeight);
		    }
		    else {
	            this.videoOutputStream.write(data, 0, this.scaledWidth*this.scaledHeight);
		    }
		}
		else if (this.quality==Quality.MEDIUM) {
			// Every other row and column, total size 1/4 of original.
            int scaledIndex = 0;
            for(int row=0; row<this.fullHeight; row+=2) {
                int rowIndex = this.fullWidth * row;
                for(int col=0; col<this.fullWidth; col+=2) {
                    scaledImageBuffer[scaledIndex++] = data[rowIndex + col];
                }
            }
			if (orientation == Orientation.ROTATED_180) {
			    reverseByteArray(scaledImageBuffer);
			}
			this.videoOutputStream.write(scaledImageBuffer, 0, scaledImageBuffer.length);
		}
		else {
			// Every third row and column, total size 1/9 of original.
			int scaledIndex = 0;
			for(int row=0; row<this.fullHeight; row+=3) {
				int rowIndex = this.fullWidth * row;
				for(int col=0; col<this.fullWidth; col+=3) {
					scaledImageBuffer[scaledIndex++] = data[rowIndex + col];
				}
			}
            if (orientation == Orientation.ROTATED_180) {
                reverseByteArray(scaledImageBuffer);
            }
			this.videoOutputStream.write(scaledImageBuffer, 0, scaledImageBuffer.length);
		}

		++this.numFrames;
		this.lastFrameTimestamp = timestamp;
	}

	public void recordAudioData(byte[] data, int numBytes) throws IOException {
		if (this.audioOutputStream==null) {
			this.audioOutputStream = this.videoDirectory.audioOutputStream();
		}
		audioOutputStream.write(data, 0, numBytes);
	}

	public boolean hasThumbnailImage() {
		return this.videoDirectory.hasThumbnailImage();
	}

	public void storeThumbnailImage(Bitmap bitmap, int size) {
		Bitmap scaledBitmap = AndroidUtils.createScaledBitmap(bitmap, size);
		this.videoDirectory.storeThumbnailBitmap(scaledBitmap);
	}

	public void storeThumbnailImage(Bitmap bitmap) {
		storeThumbnailImage(bitmap, THUMBNAIL_SIZE);
	}

	public void endRecording() {
		endRecording(System.currentTimeMillis());
	}

	public void endRecording(long timestamp) {
		// record duration of last frame
		this.frameDurations.add((int)(timestamp - this.lastFrameTimestamp));
		// update properties
		videoProperties.setEndTime(timestamp);
		videoProperties.setNumberOfFrames(this.numFrames);
		videoProperties.setFrameDurations(this.frameDurations);

		try {
			this.videoOutputStream.flush();
			this.videoOutputStream.close();
		}
		catch(Exception ignored) {}

		try {
			this.audioOutputStream.flush();
			this.audioOutputStream.close();
		}
		catch(Exception ignored) {}

		try {
			videoDirectory.storeVideoProperties(videoProperties);
		}
		catch(Exception ignored) {}
	}

	public int getNumberOfFrames() {
		return numFrames;
	}

	public long duration() {
		return System.currentTimeMillis() - startTimestamp;
	}

    private static void reverseByteArray(byte[] arr) {
        for (int front=0, back=arr.length-1; front<back; front++, back--) {
            byte tmp = arr[front];
            arr[front] = arr[back];
            arr[back] = tmp;
        }
    }

    private static void writeReversed(OutputStream out, byte[] data, int start, int nbytes) throws IOException {
        byte[] buffer = new byte[65536];
        int position = start + nbytes;
        while (position > start) {
            int remaining = position - start;
            int bytesToWrite = Math.min(buffer.length, remaining);
            for (int i=0; i<bytesToWrite; i++) {
                // Decrement before filling buffer, so the first byte will be data[start+nbytes-1].
                position--;
                buffer[i] = data[position];
            }
            out.write(buffer, 0, bytesToWrite);
        }
    }
}

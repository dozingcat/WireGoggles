package com.dozingcatsoftware.eyeball;

import com.dozingcatsoftware.util.CameraPreviewProcessingQueue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;

public class CameraImageProcessor implements CameraPreviewProcessingQueue.Processor {

    private static final boolean DEBUG = false;

	static class CameraPreviewImage {
		public byte[] data;
		public int width;
		public int height;

		public CameraPreviewImage(byte[] data, int width, int height) {
			this.data = data;
			this.width = width;
			this.height = height;
		}
	}

    public static enum Orientation {
        NORMAL,
        ROTATED_180,
    }

	CameraPreviewProcessingQueue processingQueue = new CameraPreviewProcessingQueue();

	Bitmap bitmap;
	CameraPreviewImage previewImage;
	CameraPreviewImage nextPreviewImage;
	int sampleFactor = 2;
	// false does standard edge detection, true uses brightness values directly.
	boolean useBrightness = false;
	boolean useNoiseFilter = true;
	int noiseThreshold = 48;
	Orientation orientation = Orientation.NORMAL;

	ColorScheme colorScheme;
	ColorScheme[] gridColorSchemes;
	int colorGridRows;

	static int[] identityColorMap = new int[256];
	static {
		for(int i=0; i<256; i++) {
			identityColorMap[i] = i;
		}
	}

	long minFrameDelay = 50;
	long lastFrameTime = 0;

	Handler messageHandler;

	Object LOCK = new Object();

	public void start() {
	    // Unscientific experiments on a 6-core Nexus 5x suggest that using more than 4 threads
	    // doesn't help.
	    edgeDetector.initThreadPool(Math.min(4, Runtime.getRuntime().availableProcessors()));
	    processingQueue.start(this);
	}

	public void stop() {
	    processingQueue.stop();
	    edgeDetector.destroyThreadPool();
	}

	// Call pause() to prevent bitmap ivar from being updated from incoming camera data,
	// e.g. when saving images and PNGs.
	public void pause() {
		processingQueue.pause();
	}
	public void unpause() {
		processingQueue.unpause();
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	public byte[] getImageData() {
		return previewImage.data;
	}
	public int getImageWidth() {
		return previewImage.width;
	}
	public int getImageHeight() {
		return previewImage.height;
	}

	public void setSampleFactor(int value) {
		sampleFactor = value;
	}

	public void setUseBrightness(boolean value) {
		useBrightness = value;
	}

	public void setUseNoiseFilter(boolean value) {
		useNoiseFilter = value;
	}

	public boolean useSolidColor() {
		return useBrightness;
	}
	public boolean useNoiseFilter() {
		return useNoiseFilter;
	}

	public void setMessageHandler(Handler value) {
		messageHandler = value;
	}

	public synchronized void setColorScheme(ColorScheme value) {
		colorScheme = value;
		gridColorSchemes = null;
	}

	public synchronized void setGridColorSchemes(ColorScheme[] value, int rows) {
		gridColorSchemes = value;
		colorGridRows = rows;
	}

	public boolean showingColorSchemeGrid() {
		return (gridColorSchemes!=null);
	}

	public void setOrientation(Orientation value) {
	    orientation = value;
	}
	public Orientation getOrientation() {
	    return orientation;
	}

	/** Called by camera preview callback method when a frame is received.
	 */
	public boolean processImageData(byte[] data, int width, int height) {
	    return processingQueue.processImageData(data, width, height);
	}

	/** Processes an image and generates a bitmap on the caller's thread. */
	@Override public void processCameraImage(byte[] data, int width, int height) {
		this.previewImage = new CameraPreviewImage(data, width, height);
		if (gridColorSchemes!=null) {
			createComboBitmap(gridColorSchemes, colorGridRows, gridColorSchemes.length / colorGridRows);
		}
		else {
			createBitmapFromData();
		}
	}

	// reuse int arrays and Bitmap to reduce memory allocations
	int output[];
	Bitmap edgeBitmap1, edgeBitmap2;
	Bitmap bitmapWithBackground1, bitmapWithBackground2;

	SobelEdgeDetector edgeDetector = new SobelEdgeDetector();

	void createBitmapFromData() {
		// Copy values because they may change while computation is in progress.
		int sample = this.sampleFactor;
		boolean useBackground = colorScheme.useBackground();
		int[] colorMap = colorScheme.getColorMap();
		boolean filterNoise = this.useNoiseFilter;

		// Add extra pixel for "remainder", e.g. if previewWidth=13 and sample=3, there will be 5 pixels.
		int outputWidth = previewImage.width / sample + (previewImage.width%sample==0 ? 0 : 1);
		int outputHeight = previewImage.height / sample + (previewImage.height%sample==0 ? 0 : 1);

		// The first width*height bytes are grayscale values.
		if (output==null || output.length!=outputWidth*outputHeight) {
			output = null;
			output = new int[outputWidth*outputHeight];
		}

		long t1 = System.currentTimeMillis();

		if (!useBrightness) {
			edgeDetector.computeEdgePixels(previewImage.data, previewImage.width, previewImage.height, sample,
					outputWidth, outputHeight, colorMap, (filterNoise) ? noiseThreshold : 0, output);
		}
		else {
			// This will use absolute brightness instead of edges to apply the color map.
			edgeDetector.javaBytesToInts(previewImage.data, previewImage.width, previewImage.height, sample, output);
			edgeDetector.applyColorMap(output, output.length, colorMap, output);
		}
		if (orientation == Orientation.ROTATED_180) {
		    edgeDetector.reverseIntArray(output, 0, output.length);
		}

		if (DEBUG) android.util.Log.i("TIMING", "Got edges: " + (System.currentTimeMillis()-t1));

		// Only allocate edgeBitmap2 if no background image.
		if (edgeBitmap1==null || edgeBitmap1.getWidth()!=outputWidth || edgeBitmap1.getHeight()!=outputHeight) {
			edgeBitmap1 = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
		}
		if (!useBackground && (edgeBitmap2==null || edgeBitmap2.getWidth()!=outputWidth || edgeBitmap2.getHeight()!=outputHeight)) {
			edgeBitmap2 = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
		}
		Bitmap edgeBitmap = (!useBackground && this.bitmap==this.edgeBitmap1) ? this.edgeBitmap2 : this.edgeBitmap1;
		edgeBitmap.setPixels(output, 0, outputWidth, 0, 0, outputWidth, outputHeight);

		if (DEBUG) android.util.Log.i("TIMING", "Set pixels: " + (System.currentTimeMillis()-t1));

		if (!useBackground) {
			bitmapWithBackground1 = bitmapWithBackground2 = null;
			this.bitmap = edgeBitmap;
		}
		else {
			// We don't need edgeBitmap2 anymore, always use edgeBitmap1.
			// Then composite to bitmapWithBackground2 if bitmap==bitmapWithBackground1.
			edgeBitmap2 = null;
			// Create workBitmapWithBackground if needed, and composite background with edgeBitmap.
			if (bitmapWithBackground1==null || bitmapWithBackground1.getWidth()!=outputWidth || bitmapWithBackground1.getHeight()!=outputHeight) {
				bitmapWithBackground1 = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
				bitmapWithBackground2 = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
			}
			Bitmap bitmapWithBackground = (this.bitmap==bitmapWithBackground1) ? bitmapWithBackground2 : bitmapWithBackground1;
			// Draw background, alpha channel from edge pixels will show background where the edges are.
			Canvas c = new Canvas(bitmapWithBackground);
			Rect dstRect = new Rect(0, 0, outputWidth, outputHeight);
			colorScheme.drawBackground(c, dstRect);
			c.drawBitmap(edgeBitmap, null, dstRect, null);
			colorScheme.drawForeground(c, dstRect);
			this.bitmap = bitmapWithBackground;
		}

		long t2 = System.currentTimeMillis();
		// Notify main thread that we have a new image to draw.
		notifyImageDone(t1, t2, previewImage.data);
	}

	void createComboBitmap(ColorScheme[] colorSchemes, int gridRows, int gridCols) {
		long t1 = System.currentTimeMillis();
		int sample = Math.min(gridRows, gridCols);

		// Flip between edgeBitmap1 and edgeBitmap2 for finalBitmap.
		if (edgeBitmap1==null || edgeBitmap1.getWidth()!=previewImage.width || edgeBitmap1.getHeight()!=previewImage.height) {
			edgeBitmap1 = Bitmap.createBitmap(previewImage.width, previewImage.height, Bitmap.Config.ARGB_8888);
		}
		if (edgeBitmap2==null || edgeBitmap2.getWidth()!=previewImage.width || edgeBitmap2.getHeight()!=previewImage.height) {
			edgeBitmap2 = Bitmap.createBitmap(previewImage.width, previewImage.height, Bitmap.Config.ARGB_8888);
		}
		Bitmap finalBitmap = (this.bitmap==this.edgeBitmap1) ? this.edgeBitmap2 : this.edgeBitmap1;
		Canvas canvas = new Canvas(finalBitmap);
		canvas.drawColor(255<<24);

		// Add extra pixel for "remainder", e.g. if previewWidth=13 and sample=3, there will be 5 pixels.
		int subImageWidth = previewImage.width / sample + (previewImage.width%sample==0 ? 0 : 1);
		int subImageHeight = previewImage.height / sample + (previewImage.height%sample==0 ? 0 : 1);

		// The first width*height bytes are grayscale values.
		int subImagePixelCount = subImageWidth*subImageHeight;
		if (output==null || output.length!=subImagePixelCount) {
			output = new int[subImagePixelCount];
		}

		if (!useBrightness) {
			edgeDetector.computeEdgePixels(previewImage.data, previewImage.width, previewImage.height, sample,
					subImageWidth, subImageHeight, identityColorMap, (this.useNoiseFilter) ? noiseThreshold : 0, output);
		}
		else {
			// Use brightness values directly.
			edgeDetector.javaBytesToInts(previewImage.data, previewImage.width, previewImage.height, sample, output);
		}

		Bitmap subImage = Bitmap.createBitmap(subImageWidth, subImageHeight, Bitmap.Config.ARGB_8888);
		int[] subImagePixels = new int[subImagePixelCount];
		// Output now holds 0 for no edge up to 255 for sharp edge.
		// For each color map, update subImage and blit to finalImage.
		for(int i=0; i<colorSchemes.length; i++) {
			int[] cmap = colorSchemes[i].getColorMap();
			edgeDetector.applyColorMap(output, subImagePixelCount, cmap, subImagePixels);
			if (orientation == Orientation.ROTATED_180) {
			    edgeDetector.reverseIntArray(subImagePixels, 0, subImagePixels.length);
			}
			subImage.setPixels(subImagePixels, 0, subImageWidth, 0, 0, subImageWidth, subImageHeight);

			int row = i / gridCols;
			int col = i % gridCols;
			int x = col * (previewImage.width / gridCols);
			int y = row * (previewImage.height / gridRows);
			Rect dstRect = new Rect(x, y, x + (previewImage.width / gridCols), y + (previewImage.height / gridRows));

			if (colorSchemes[i].useBackground()) {
				colorSchemes[i].drawBackground(canvas, dstRect);
			}
			canvas.drawBitmap(subImage, null, dstRect, null);

			// Draw "Custom" label for last color scheme.
			if (i==colorSchemes.length-1) {
				drawCenteredLabel("Custom", dstRect, canvas);
			}
		}
		this.bitmap = finalBitmap;

		long t2 = System.currentTimeMillis();
		notifyImageDone(t1, t2, previewImage.data);
	}

	void drawCenteredLabel(String msg, Rect dstRect, Canvas canvas) {
		Rect textRect = new Rect();
		Paint textPaint = new Paint();
		textPaint.setARGB(255, 128, 128, 128);
		textPaint.setTextSize(30);
		textPaint.setAntiAlias(true);
		textPaint.setFakeBoldText(true);
		textPaint.getTextBounds(msg, 0, msg.length(), textRect);
		// 30 points is too big for HVGA displays.
		if (textRect.width() > dstRect.width()) {
			textPaint.setTextSize(10);
			textPaint.getTextBounds(msg, 0, msg.length(), textRect);
		}
		float startx = dstRect.left + dstRect.width()/2 - textRect.width()/2;
		float starty = dstRect.top + dstRect.height()/2 + textRect.height()/2;
		canvas.drawText(msg, startx, starty, textPaint);
	}

	public static final int IMAGE_DONE = 1;

	void notifyImageDone(long startTime, long endTime, byte[] previewBuffer) {
		if (messageHandler==null) return;

		Message message = messageHandler.obtainMessage();
		message.what = IMAGE_DONE;
		// Include preview buffer so it can be freed for reuse, and render time for performance monitoring.
		message.obj = previewBuffer;
		message.arg1 = (int)(endTime-startTime);
		messageHandler.sendMessage(message);
	}
}

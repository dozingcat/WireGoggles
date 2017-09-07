package com.dozingcatsoftware.eyeball;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.dozingcatsoftware.eyeball.video.ImageRecorder;
import com.dozingcatsoftware.util.AndroidUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

public class ProcessPictureOperation {

    private static final boolean DEBUG = false;

    public String processPicture(Context context, Uri uri) throws IOException, FileNotFoundException {
        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        // use current settings from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int colorIndex = prefs.getInt(EyeballMain.COLOR_PREFS_KEY, 0);
        boolean useSolidColor = (prefs.getInt(EyeballMain.SOLID_COLOR_PREFS_KEY, 0) > 0);
        boolean useNoiseFilter = (prefs.getInt(EyeballMain.NOISE_FILTER_PREFS_KEY, 0) > 0);

        // assume width is always larger
        int width = Math.max(display.getWidth(), display.getHeight());
        int height = Math.min(display.getWidth(), display.getHeight());
        return processPicture(context, uri, width, height,
                colorIndex, useSolidColor, useNoiseFilter);
    }

    public String processPicture(Context context, Uri uri, int maxWidth, int maxHeight,
            int colorIndex, boolean useSolidColor, boolean useNoiseFilter)
            throws IOException, FileNotFoundException {
        BrightnessImage image = brightnessImageFromUri(context, uri, maxWidth, maxHeight);
        long t1 = System.currentTimeMillis();
        CameraImageProcessor imageProcessor = new CameraImageProcessor();
        imageProcessor.setSampleFactor(1);
        imageProcessor.setUseBrightness(useSolidColor);
        imageProcessor.setUseNoiseFilter(useNoiseFilter);
        imageProcessor.setColorScheme(EyeballMain.COLORS[colorIndex]);
        imageProcessor.processCameraImage(image.data, image.width, image.height);
        if (DEBUG) Log.i("PPO", "Computed edges: " + (System.currentTimeMillis() - t1));

        String imageDirectory = WGUtils.pathForNewImageDirectory();
        ImageRecorder recorder = new ImageRecorder(context, imageDirectory);
        recorder.saveImage(imageProcessor, colorIndex, useSolidColor, useNoiseFilter);
        Log.i("PPO", "Saved picture to " + imageDirectory);

        return imageDirectory;
    }

    // Making this larger reduces the number of calls to BitmapRegionDecoder.decodeRegion.
    // Those calls are expensive because it has to mostly reparse the image every time.
    // But it can't be too large or we run out of memory creating the chunk bitmap.
    static int MAX_CHUNK_SIZE = 800;

    static class BrightnessImage {
        public int width, height;
        public byte[] data;
    }

    static BrightnessImage brightnessImageFromUri(Context context, Uri uri, int maxWidth, int maxHeight)
            throws FileNotFoundException, IOException {
        if (DEBUG) Log.i("PPO", "Brightness image start");
        long t1 = System.currentTimeMillis();
        BitmapRegionDecoder regionDecoder = BitmapRegionDecoder.newInstance(context.getContentResolver().openInputStream(uri), true);
        int imageWidth = regionDecoder.getWidth();
        int imageHeight = regionDecoder.getHeight();

        int scaledWidth = imageWidth;
        int scaledHeight = imageHeight;
        // only scale if width or height is too large
        if (scaledWidth>maxWidth || scaledHeight>maxHeight) {
            int[] scaledWH = AndroidUtils.scaledWidthAndHeightToMaximum(scaledWidth, scaledHeight, maxWidth, maxHeight);
            scaledWidth = scaledWH[0];
            scaledHeight = scaledWH[1];
        }
        int sampleFactor = imageWidth / scaledWidth;

        if (DEBUG) Log.i("PPO", String.format("Scaling from (%d,%d) to (%d,%d)", imageWidth, imageHeight, scaledWidth, scaledHeight));
        if (DEBUG) Log.i("PPO", "sampleFactor="+sampleFactor);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleFactor;

        // partition the input into blocks no more than MAX_CHUNK_SIZE width/height
        int wblocks = (int)Math.ceil(1.0*imageWidth/MAX_CHUNK_SIZE);
        int hblocks = (int)Math.ceil(1.0*imageHeight/MAX_CHUNK_SIZE);

        int[] imageWidthPartitions = AndroidUtils.equalIntPartitions(imageWidth, wblocks);
        int[] imageHeightPartitions = AndroidUtils.equalIntPartitions(imageHeight, hblocks);
        int[] scaledWidthPartitions = AndroidUtils.equalIntPartitions(scaledWidth, wblocks);
        int[] scaledHeightPartitions = AndroidUtils.equalIntPartitions(scaledHeight, hblocks);
        int[] tempPixels = null;

        BrightnessImage result = new BrightnessImage();
        result.width = scaledWidth;
        result.height = scaledHeight;
        result.data = new byte[scaledWidth*scaledHeight];

        byte[] data = result.data;
        Rect region = new Rect();
        for(int h=0; h<hblocks; h++) {
            region.top = imageHeightPartitions[h];
            region.bottom = imageHeightPartitions[h+1] - 1;
            int scaledYStart = scaledHeightPartitions[h];
            int scaledBlockHeight = scaledHeightPartitions[h+1] - scaledYStart;
            for(int w=0; w<wblocks; w++) {
                region.left = imageWidthPartitions[w];
                region.right = imageWidthPartitions[w+1] - 1;
                int scaledXStart = scaledWidthPartitions[w];
                int scaledBlockWidth = scaledWidthPartitions[w+1] - scaledXStart;

                long t10 = System.currentTimeMillis();
                Bitmap sourceBitmap = regionDecoder.decodeRegion(region, options);
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, scaledBlockWidth, scaledBlockHeight, false);
                long t11 = System.currentTimeMillis();
                sourceBitmap = null;
                if (DEBUG && w==0) Log.i("PPO", String.format("Block: (%d, %d, %d, %d) to (%d+%d, %d+%d)",
                        region.left, region.top, region.right, region.bottom, scaledXStart, scaledBlockWidth, scaledYStart, scaledBlockHeight));

                if (tempPixels == null || tempPixels.length < scaledBitmap.getWidth()*scaledBitmap.getHeight()) {
                    tempPixels = new int[scaledBitmap.getWidth()*scaledBitmap.getHeight()];
                }
                // Copying the pixels to an array is much faster than calling Bitmap.getPixel.
                scaledBitmap.getPixels(tempPixels, 0, scaledBitmap.getWidth(), 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());
                int bitmapPixelIndex = 0;
                for(int r=0; r<scaledBlockHeight; r++) {
                    int index = (scaledYStart+r) * scaledWidth + scaledXStart;
                    for(int c=0; c<scaledBlockWidth; c++) {
                        int color = tempPixels[bitmapPixelIndex++];
                        // Y = 0.299R + 0.587G + 0.114B
                        data[index++] = (byte)(
                                0.299 * ((color>>16) & 0xff) +
                                0.587 * ((color>>8)  & 0xff) +
                                0.114 * (color & 0xff));
                    }
                }
                long t12 = System.currentTimeMillis();
                if (DEBUG && w==0) Log.i("PPO", "Block processing times: " + (t11-t10) + " " + (t12-t11));
                scaledBitmap = null; // could just allocate once and draw into it
            }
        }
        Log.i("PPO", "BRIGHTNESS IMAGE TIME: " + (System.currentTimeMillis() - t1));

        return result;
    }
}

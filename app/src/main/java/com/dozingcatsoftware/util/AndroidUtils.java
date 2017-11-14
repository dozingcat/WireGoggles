package com.dozingcatsoftware.util;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.view.View;

public class AndroidUtils {

	/** Adds a click listener to the given view which invokes the method named by methodName on the given target.
	 * The method must be public and take no arguments.
	 */
	public static void bindOnClickListener(final Object target, View view, String methodName) {
		final Method method;
		try {
			method = target.getClass().getMethod(methodName);
		}
		catch(Exception ex) {
			throw new IllegalArgumentException(ex);
		}
		view.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				try {
					method.invoke(target);
				}
				catch(Exception ex) {
					throw new RuntimeException(ex);
				}
			}
		});
	}

	public static interface MediaScannerCallback {
		public void mediaScannerCompleted(String scanPath, Uri scanURI);
	}

    /** Notifies the OS to index the new image, so it shows up in Gallery. Allows optional callback method to notify client when
     * the scan is completed, e.g. so it can access the "content" URI that gets assigned.
     */
    public static void scanSavedMediaFile(final Context context, final String path, final MediaScannerCallback callback) {
    	// silly array hack so closure can reference scannerConnection[0] before it's created 
    	final MediaScannerConnection[] scannerConnection = new MediaScannerConnection[1];
		try {
			MediaScannerConnection.MediaScannerConnectionClient scannerClient = new MediaScannerConnection.MediaScannerConnectionClient() {
				public void onMediaScannerConnected() {
					scannerConnection[0].scanFile(path, null);
				}

				public void onScanCompleted(String scanPath, Uri scanURI) {
					scannerConnection[0].disconnect();
					if (callback!=null) {
						callback.mediaScannerCompleted(scanPath, scanURI);
					}
				}
			};
    		scannerConnection[0] = new MediaScannerConnection(context, scannerClient);
    		scannerConnection[0].connect();
		}
		catch(Exception ignored) {}
    }
    
    public static void scanSavedMediaFile(final Context context, final String path) {
    	scanSavedMediaFile(context, path, null);
    }
    
	/** Returns a BitmapFactory.Options object containing the size of the image at the given URI,
	 * without actually loading the image.
	 */
	public static BitmapFactory.Options computeBitmapSizeFromURI(Context context, Uri imageURI) throws FileNotFoundException {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(context.getContentResolver().openInputStream(imageURI), null, options);
		return options;
	}
	
	/** Returns a Bitmap from the given URI that may be scaled by an integer factor to reduce its size,
	 * while staying as least as large as the width and height parameters.
	 */
	public static Bitmap scaledBitmapFromURIWithMinimumSize(Context context, Uri imageURI, int width, int height) throws FileNotFoundException {
		BitmapFactory.Options options = computeBitmapSizeFromURI(context, imageURI);
		options.inJustDecodeBounds = false;
		
		float wratio = 1.0f*options.outWidth / width;
		float hratio = 1.0f*options.outHeight / height;		
		options.inSampleSize = (int)Math.min(wratio, hratio);
		
		return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(imageURI), null, options);		
	}

    /** Returns a Bitmap from the given URI that may be scaled by an integer factor to reduce its size,
     * so that its width and height are no greater than the corresponding parameters. The scale factor
     * will be a power of 2.
     */
    public static Bitmap scaledBitmapFromURIWithMaximumSize(Context context, Uri imageURI, int width, int height) throws FileNotFoundException {
        BitmapFactory.Options options = computeBitmapSizeFromURI(context, imageURI);
        options.inJustDecodeBounds = false;
        
        int wratio = powerOf2GreaterOrEqual(1.0*options.outWidth / width);
        int hratio = powerOf2GreaterOrEqual(1.0*options.outHeight / height);     
        options.inSampleSize = Math.max(wratio, hratio);
        
        return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(imageURI), null, options);       
    }
    
    static int powerOf2GreaterOrEqual(double arg) {
        if (arg<0 && arg>(1<<31)) throw new IllegalArgumentException(arg + " out of range");
        int result = 1;
        while (result < arg) result <<= 1;
        return result;
    }
	
	/** Returns a scaled version of the given Bitmap. One of the returned Bitmap's width and height will be equal to size, and the other
	 * dimension will be equal or less.
	 */
	public static Bitmap createScaledBitmap(Bitmap bitmap, int size) {
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		int scaledWidth=size, scaledHeight=size;
		if (height < width) {
			scaledHeight = (int)(size*1.0f*height/width);
		}
		else if (width < height) {
			scaledWidth = (int)(size*1.0f*width/height); 
		}
		Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false);
		return scaledBitmap;
	}

	/** Given a width and height, fills output array with scaled width and height values 
	 * such that one of the values is exactly equal to the given maximum width or height, 
	 * and the other value is less than or equal to the maximum.
	 */
	public static void getScaledWidthAndHeightToMaximum(
			int width, int height, int maxWidth, int maxHeight, int[] output) {
		output[0] = width;
		output[1] = height;
		// common cases: if one dimension fits exactly and the other is smaller, return unmodified
		if (width==maxWidth && height<=maxHeight) return;
		if (height==maxHeight && width<=maxWidth) return;
		float wratio = ((float)width)/maxWidth;
		float hratio = ((float)height)/maxHeight;
		if (wratio<=hratio) {
			// scale to full height, partial width
			output[0] = (int)(width/hratio);
			output[1] = maxHeight;
		}
		else {
			// scale to full width, partial height
			output[0] = maxWidth;
			output[1] = (int)(height/wratio);
		}
	}
	
	public static int[] scaledWidthAndHeightToMaximum(int width, int height, int maxWidth, int maxHeight) {
		int[] output = new int[2];
		getScaledWidthAndHeightToMaximum(width, height, maxWidth, maxHeight, output);
		return output;
	}
	
	/** Returns an array which partitions the interval [0, max) into n approximately equal integer segments.
	 * The returned array will have n+1 elements, with the first always 0 and the last always max.
	 * If max=100 and n=3, the returned array will be [0, 33, 67, 100] representing the intervals
	 * [0, 33), [33, 67), and [67, 100). 
	 */
	public static int[] equalIntPartitions(int max, int n) {
	    int[] result = new int[n+1];
	    result[0] = 0;
	    for(int i=1; i<n; i++) {
	        float val = 1.0f*i*max / n;
	        result[i] = Math.round(val);
	    }
        result[n] = max;
	    return result;
	}

	/** On API level 14 (ICS) or higher, calls view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE)
     * and returns true. On earlier API versions, does nothing and returns false.
     */
    public static boolean setSystemUiLowProfile(View view) {
        return setSystemUiVisibility(view, "SYSTEM_UI_FLAG_LOW_PROFILE");
    }
    
    static boolean setSystemUiVisibility(View view, String flagName) {
        try {
            Method setUiMethod = View.class.getMethod("setSystemUiVisibility", int.class);
            Field flagField = View.class.getField(flagName);
            setUiMethod.invoke(view, flagField.get(null));
            return true;
        }
        catch(Exception ex) {
            return false;
        }
    }

    /** Returns the estimated memory usage in bytes for a bitmap. Calls bitmap.getByteCount() if that method
     * is available (in API level 12 or higher), otherwise returns 4 times the number of pixels in the bitmap.
     */
    public static int getBitmapByteCount(Bitmap bitmap) {
        try {
            Method byteCountMethod = Bitmap.class.getMethod("getByteCount");
            return (Integer)byteCountMethod.invoke(bitmap);
        }
        catch(Exception ex) {
            return 4 * bitmap.getWidth() * bitmap.getHeight();
        }
    }
    
    /** Enables or disables hardware acceleration for a view, if supported by the API. Returns true if successful */
    public static boolean setViewHardwareAcceleration(View view, boolean enabled) {
        try {
            Method setLayerType = View.class.getMethod("setLayerType", int.class, Paint.class);
            String fieldName = (enabled) ? "LAYER_TYPE_HARDWARE" : "LAYER_TYPE_SOFTWARE";
            int layerType = (Integer)View.class.getField(fieldName).get(null);
            setLayerType.invoke(view, layerType, null);
            return true;
        }
        catch(Exception ignored) {
            return false;
        }
    }
}

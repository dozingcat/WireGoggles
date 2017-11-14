package com.dozingcatsoftware.eyeball;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.graphics.Bitmap;
import android.os.Environment;

public class WGUtils {
	
    static DateFormat filenameDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
    
    static String baseDirectory = Environment.getExternalStorageDirectory() + File.separator + "WireGoggles";
    static String savedVideoDirectory = baseDirectory + File.separator + "video";
    static String savedImageDirectory = baseDirectory + File.separator + "images";

    public static String savePicture(Bitmap bitmap, String path) {
		if (path==null) {
			// use base image directory plus name generated from timestamp
			File dir = new File(savedImageDirectory);
			if (!dir.exists()) {
					dir.mkdirs();
			}
			if (!dir.isDirectory()) {
				//updateStatusTextWithFade("Unable to create directory:"+dir.getPath());
				return null;
			}
			Date now = new Date();
			String filename = filenameDateFormat.format(now) + ".png";
			path = dir.getPath() + File.separator + filename;
		}
		else {
			// path provided directly, all intermediate directories must exist
		}

		try {
			FileOutputStream out = new FileOutputStream(path);
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
			out.close();
			return path;
		}
		catch(Exception ex) {
			return null;
		}
	}
    
    public static String pathForNewVideoRecording() {
    	return savedVideoDirectory + File.separator + filenameDateFormat.format(new Date());
    }

    public static String pathForNewImageDirectory() {
    	return savedImageDirectory + File.separator + filenameDateFormat.format(new Date());
    }

}

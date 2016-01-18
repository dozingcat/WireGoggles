package com.dozingcatsoftware.eyeball;

import java.io.File;
import java.io.IOException;

import com.dozingcatsoftware.WireGoggles.R;
import com.dozingcatsoftware.eyeball.video.AbstractViewMediaActivity;
import com.dozingcatsoftware.eyeball.video.ImageRecorder;
import com.dozingcatsoftware.eyeball.video.MediaDirectory;
import com.dozingcatsoftware.util.AndroidUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class ViewImageActivity extends AbstractViewMediaActivity {

	String imageMimeType = "image/png";
	String imageFilePath;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupView(R.id.imageViewButtonBar);

        imageFilePath = videoReader.getVideoDirectory().getPath() + ".png";

        AndroidUtils.bindOnClickListener(this, findViewById(R.id.imageViewColorsButton), "showColorSchemeGrid"); // superclass method
        AndroidUtils.bindOnClickListener(this, this.findViewById(R.id.imageViewDeleteButton), "deleteImage");
        AndroidUtils.bindOnClickListener(this, this.findViewById(R.id.imageViewSaveButton), "saveImage");
        AndroidUtils.bindOnClickListener(this, this.findViewById(R.id.imageViewShareButton), "shareImage");

        loadImage();
    }

	public static Intent startActivityWithImageDirectory(Activity parent, String path) {
		Intent intent = new Intent(parent, ViewImageActivity.class);
		intent.putExtra("path", path);
		parent.startActivityForResult(intent, 0);
		return intent;
	}

    void loadImage() {
    	try {
        	if (frameData==null) frameData = new byte[videoProperties.getWidth() * videoProperties.getHeight()];
        	try {
        		videoReader.getDataForNextFrame(frameData);
        	}
        	catch(IOException ex) {
        		Log.e("WG-Image", "Error displaying image", ex);
        	}
        	drawCurrentFrame();
    	}
    	catch(OutOfMemoryError oom) {
    		// not good, try to recover
    		this.imageProcessor = null;
    		this.frameData = null;
    		System.gc();
    		Log.e("WG-ViewImageActivity", "Out of memory", oom);
    		Toast.makeText(getApplicationContext(), "Out of memory, try again", Toast.LENGTH_SHORT).show();
    		this.finish();
    	}
    }

    public void deleteImage() {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setMessage("Do you want to permanently delete this picture?").setCancelable(true);
    	builder.setPositiveButton("Delete", performDeleteDialogAction);
    	builder.setNegativeButton("Don't Delete", null);
    	builder.show();
    }

    DialogInterface.OnClickListener performDeleteDialogAction = new DialogInterface.OnClickListener() {
		@Override
        public void onClick(DialogInterface dialog, int which) {
			// delete directory and PNG file
			boolean success = videoReader.getVideoDirectory().delete();
			if (success) {
				Toast.makeText(getApplicationContext(), "Deleted picture", Toast.LENGTH_SHORT).show();
			}
			else {
				Toast.makeText(getApplicationContext(), "Unable to delete picture", Toast.LENGTH_LONG).show();
			}
			(new File(imageFilePath)).delete();
			setResult(DELETE_RESULT);
			finish();
		}
    };

    boolean saveImageAndProperties() {
    	WGUtils.savePicture(imageProcessor.getBitmap(), imageFilePath);
    	// update properties and thumbnail with color scheme
    	videoProperties.setColorScheme(this.colorIndex);
    	videoProperties.setSolidColor(imageProcessor.useSolidColor());
    	videoProperties.setUseNoiseFilter(imageProcessor.useNoiseFilter());
    	MediaDirectory imageDirectory = videoReader.getVideoDirectory();
    	try {
        	imageDirectory.storeVideoProperties(videoProperties);
        	Bitmap thumbnail = AndroidUtils.createScaledBitmap(imageProcessor.getBitmap(), ImageRecorder.THUMBNAIL_SIZE);
        	imageDirectory.storeThumbnailBitmap(thumbnail);
        	this.colorSchemeChanged = false;
        	return true;
    	}
    	catch(IOException ex) {
    		Log.e("WG-ViewImage", "Error saving image", ex);
    		return false;
    	}
    }

    public void saveImage() {
    	boolean result = saveImageAndProperties();
    	String message = (result) ? "Picture saved" : "Error saving picture";
    	Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    public void shareImage() {
    	// save to pick up any color changes
    	if (this.colorSchemeChanged) {
        	if (!saveImageAndProperties()) {
        		Toast.makeText(getApplicationContext(), "Error saving picture", Toast.LENGTH_SHORT).show();
        		return;
        	}
    	}
		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		Uri imageURI = Uri.fromFile(new File(imageFilePath));
		shareIntent.setType(imageMimeType);
		shareIntent.putExtra(Intent.EXTRA_STREAM, imageURI);
		shareIntent.putExtra(Intent.EXTRA_SUBJECT, "WireGoggles Picture");
		shareIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);
		startActivity(Intent.createChooser(shareIntent, "Share Picture Using:"));
    }

    // launch gallery and terminate this activity, so when gallery activity finishes user will go back to main activity
    public void viewImageInGallery() {
    	Intent galleryIntent = new Intent(Intent.ACTION_VIEW);
    	galleryIntent.setDataAndType(Uri.fromFile(new File(imageFilePath)), "image/png");
    	// FLAG_ACTIVITY_NO_HISTORY tells the OS to not return to the gallery if the user goes to the home screen and relaunches the app
    	galleryIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    	this.startActivity(galleryIntent);
    	this.finish();
    }

}

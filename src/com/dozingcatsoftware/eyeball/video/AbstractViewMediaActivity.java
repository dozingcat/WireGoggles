package com.dozingcatsoftware.eyeball.video;

import com.dozingcatsoftware.WireGoggles.R;
//import com.dozingcatsoftware.WireGogglesFree.R;

import com.dozingcatsoftware.eyeball.CameraImageProcessor;
import com.dozingcatsoftware.eyeball.EyeballMain;
import com.dozingcatsoftware.eyeball.OverlayView;
import com.dozingcatsoftware.util.AndroidUtils;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.TextView;

/** Abstract class with common functionality for viewing images and videos. Extended by VideoPlaybackActivity and ViewImageActivity.
 */

public class AbstractViewMediaActivity extends Activity {

	protected VideoReader videoReader;
	protected MediaProperties videoProperties;
	protected CameraImageProcessor imageProcessor;
	protected byte[] frameData;

	OverlayView overlayView;
	TextView statusText;
	View chooseColorControlBar;
	CheckBox solidColorCheckbox;
	CheckBox noiseFilterCheckbox;
	View buttonBar; // different for image vs video, id passed in setupView
	protected int colorIndex;
	protected boolean colorSchemeChanged = false; // set to true if user selects a different color scheme

	public static final int DELETE_RESULT = Activity.RESULT_FIRST_USER;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        imageProcessor = new CameraImageProcessor();
        imageProcessor.setSampleFactor(1);
        
        videoReader = new VideoReader(getIntent().getStringExtra("path"));
        videoProperties = videoReader.getVideoProperties();

        this.colorIndex = this.videoProperties.getColorScheme();
        imageProcessor.setColorScheme(EyeballMain.COLORS[colorIndex]);
        imageProcessor.setUseBrightness(this.videoProperties.isSolidColor());
        imageProcessor.setUseNoiseFilter(this.videoProperties.useNoiseFilter());
        
    }
    
    protected void setupView(int buttonBarID) {
    	this.setContentView(R.layout.playback);
    	
        chooseColorControlBar = findViewById(R.id.chooseColorControlBar);
        
        solidColorCheckbox = (CheckBox)findViewById(R.id.solidColorCheckbox);
        solidColorCheckbox.setChecked(this.videoProperties.isSolidColor());
        AndroidUtils.bindOnClickListener(this, solidColorCheckbox, "solidColorCheckboxChanged");

        noiseFilterCheckbox = (CheckBox)findViewById(R.id.noiseFilterCheckbox);
        noiseFilterCheckbox.setChecked(this.videoProperties.useNoiseFilter());
        AndroidUtils.bindOnClickListener(this, noiseFilterCheckbox, "noiseFilterCheckboxChanged");

        overlayView = (OverlayView)findViewById(R.id.overlayView);
        overlayView.setImageProcessor(imageProcessor);
    	overlayView.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				handleMainViewTouch(event);
				return true;
			}
    	});
        
        statusText = (TextView)findViewById(R.id.statusText);
        
        buttonBar = findViewById(buttonBarID);
        buttonBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroy() {
    	this.imageProcessor = null;
    	this.frameData = null;
    	super.onDestroy();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode==KeyEvent.KEYCODE_BACK) {
			if (imageProcessor.showingColorSchemeGrid()) {
				// if selecting colors, hide selection grid and restore original color scheme
				hideColorChooser();
				return true;
			}
		}
    	return super.onKeyDown(keyCode, event);
    }
    
    public void solidColorCheckboxChanged() {
    	if (imageProcessor!=null) {
    		boolean useSolidColor = solidColorCheckbox.isChecked();
    		imageProcessor.setUseBrightness(useSolidColor);
    		drawCurrentFrame();
    		this.colorSchemeChanged = true;
    	}
    }
    
    public void noiseFilterCheckboxChanged() {
    	if (imageProcessor!=null) {
    		boolean useNoiseFilter = noiseFilterCheckbox.isChecked();
    		imageProcessor.setUseNoiseFilter(useNoiseFilter);
    		drawCurrentFrame();
    		this.colorSchemeChanged = true;
    	}
    }
    
    protected void drawCurrentFrame() {
    	imageProcessor.processCameraImage(frameData, videoProperties.getWidth(), videoProperties.getHeight());
    	overlayView.invalidate();    	
    }
    
    public void showColorSchemeGrid() {
    	imageProcessor.setGridColorSchemes(EyeballMain.COLORS, EyeballMain.COLOR_GRID_ROWS);
		buttonBar.setVisibility(View.INVISIBLE);
		chooseColorControlBar.setVisibility(View.VISIBLE);
		overlayView.setFillScreen(true);
    	drawCurrentFrame();
    }
    
    public void handleMainViewTouch(MotionEvent event) {
    	if (imageProcessor.showingColorSchemeGrid()) {
    		int gridCols = (int)Math.ceil(EyeballMain.COLORS.length / EyeballMain.COLOR_GRID_ROWS);
    		int row = (int)(event.getY() / (overlayView.getHeight() / EyeballMain.COLOR_GRID_ROWS));
    		int col = (int)(event.getX() / (overlayView.getWidth() / gridCols));
    		int color = row * gridCols + col;
    		if (color>=0 && color<EyeballMain.COLORS.length && color!=this.colorIndex) {
        		this.colorIndex = color;
        		this.colorSchemeChanged = true;
    		}
    		hideColorChooser();
    		overlayView.setFillScreen(false);
    	}
    }
    
    void hideColorChooser() {
		imageProcessor.setColorScheme(EyeballMain.COLORS[this.colorIndex]);
		buttonBar.setVisibility(View.VISIBLE);
		chooseColorControlBar.setVisibility(View.GONE);
		drawCurrentFrame();
    }
}

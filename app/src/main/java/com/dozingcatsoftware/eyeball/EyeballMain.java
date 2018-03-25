package com.dozingcatsoftware.eyeball;

import java.util.HashMap;
import java.util.Map;

import com.dozingcatsoftware.WireGoggles.R;
import com.dozingcatsoftware.eyeball.ColorPickerDialog.OnColorChangedListener;
import com.dozingcatsoftware.eyeball.video.ImageRecorder;
import com.dozingcatsoftware.eyeball.video.VideoRecorder;
import com.dozingcatsoftware.util.AndroidUtils;
import com.dozingcatsoftware.util.CameraUtils;
import com.dozingcatsoftware.util.FrameRateManager;
import com.dozingcatsoftware.util.ShutterButton;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class EyeballMain extends Activity implements
        Camera.PreviewCallback,
        SurfaceHolder.Callback,
        OnColorChangedListener,
        ShutterButton.OnShutterButtonListener {

    public static final boolean DEBUG = false;

    // 16 color schemes, left to right and top to bottom in grid view
    public static ColorScheme[] COLORS = new ColorScheme[] {
            new ColorScheme.FixedColorScheme(0,0,0, 255, 255, 255),
            new ColorScheme.FixedColorScheme(0,0,0, 255, 0, 0),
            new ColorScheme.FixedColorScheme(0,0,0, 0, 255, 0),
            new ColorScheme.FixedColorScheme(0,0,0, 0, 0, 255),
            new ColorScheme.FixedColorScheme(0,0,0, 255, 255, 0),
            new ColorScheme.FixedColorScheme(0,0,0, 255, 0, 255),
            new ColorScheme.FixedColorScheme(0,0,0, 0, 255, 255),

            new ColorScheme.GradientColorScheme(0,0,0, 0, 0, 255, 255, 0, 0),
            new ColorScheme.GradientColorScheme(0,0,0, 255, 0, 0, 0, 255, 0),
            new ColorScheme.GradientColorScheme(0,0,0, 0, 255, 0, 0, 0, 255),
            new ColorScheme.GradientColorScheme(0,0,0, 255, 0, 255, 255, 255, 0),

            new ColorScheme.Gradient2DColorScheme(0,0,0, 255,255,255, 255,0,0, 0,255,0, 0,0,255),

            new ColorScheme.FixedColorScheme(255, 255, 255, 0, 0, 0),
            new ColorScheme.FixedColorScheme(255, 255, 255, 255, 0, 0),
            new ColorScheme.FixedColorScheme(255, 255, 255, 0, 0, 255),

            new ColorScheme.AnimatedGradientColorScheme(255,255,255, 255,0,0, 0,192,0, 0,0,255),
            new ColorScheme.Gradient2DColorScheme(255,255,192, 255,0,0, 0,192,0, 0,0,255, 0,0,0),
            new ColorScheme.AnimatedGradientColorScheme(0,0,0, 255,0,0, 0,255,0, 0,0,255),
            new ColorScheme.MatrixColorScheme(0, 255, 0, 400, 240),

            new ColorScheme.Gradient2DColorScheme(
                    0,0,0, 255,255,255, 255,255,0, 255,0,255, 0,255,255),
    };
    public static int COLOR_GRID_ROWS = 4;

    static double PIP_RATIO = 1.0/3;

    Camera camera;
    TextView debugText;
    SurfaceView cameraView;
    OverlayView overlayView;
    Handler handler;

    CameraImageProcessor imageProcessor;

    View buttonBar;
    LinearLayout verticalButtonBar;
    TextView statusText;

    View customColorEditView;
    String customColorEditKey;
    ColorScheme.Gradient2DColorScheme customColorScheme =
            (ColorScheme.Gradient2DColorScheme)COLORS[COLORS.length-1];

    View chooseColorControlBar;
    CheckBox solidColorCheckbox;
    CheckBox noiseFilterCheckbox;

    int color = 0;
    int sampleFactor = 2;
    boolean useSolidColor = false;
    boolean useNoiseFilter = false;

    static String COLOR_PREFS_KEY = "color";
    static String SAMPLE_PREFS_KEY = "sample";
    static String CUSTOM_COLOR_KEY_PREFIX = "customColor.";
    static String SOLID_COLOR_PREFS_KEY = "solidColor";
    static String NOISE_FILTER_PREFS_KEY = "noiseFilter";

    boolean appVisible = false;
    boolean cameraViewReady = false;

    int defaultPreviewFrameRate;
    int currentPreviewFrameRate;

    ImageButton toggleVideoButton, switchCameraButton;
    ShutterButton cameraActionButton;
    boolean videoMode = false;
    VideoRecorder videoRecorder;
    AudioRecord audioRecorder;
    final static long VIDEO_TIME_LIMIT_MS = 60000;

    Runnable cornerImageClickHandler;
    // Fraction of width and height used to show captured image after taking a picture or video.
    final static double CORNER_IMAGE_RATIO = 0.34;
    // First frame of the video being recorded, will be set as the corner image when recording ends.
    Bitmap cornerBitmapForVideo;

    // Set to true after recording video, so library tab will start with videos selected.
    boolean showVideoTab = false;

    boolean showDebugMessages;
    boolean hasToggledDebugForTouch;

    // Helper method to assign an OnClickListener that calls a method with no arguments.
    void setViewClickListener(View view, final String methodName) {
        AndroidUtils.bindOnClickListener(this, view, methodName);
    }

    /** Called when the activity is first created. */
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);

        cameraView = (SurfaceView)findViewById(R.id.cameraView);
        overlayView = (OverlayView)findViewById(R.id.overlayView);
        cameraView.getHolder().addCallback(this);
        cameraView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // Shrink the camera preview so picture-in-picture size is correct, apparently has to be
        // done here.
        Display display = this.getWindowManager().getDefaultDisplay();
        cameraView.setLayoutParams(new FrameLayout.LayoutParams((int)(display.getWidth()*PIP_RATIO),
                (int)(display.getHeight()*PIP_RATIO)));

        handler = new Handler() {
            @Override
            public void handleMessage(Message m) {
                processMessage(m);
            }
        };

        statusText = (TextView)findViewById(R.id.statusText);
        updateStatusTextWithFade(statusText.getText());
        buttonBar = findViewById(R.id.buttonBar);
        verticalButtonBar = (LinearLayout)findViewById(R.id.verticalButtonBar);

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handleMainViewTouch(event);
                return true;
            }
        });

        setViewClickListener(findViewById(R.id.zoomButton), "togglePictureInPicture");
        setViewClickListener(findViewById(R.id.colorButton), "chooseColor");
        setViewClickListener(findViewById(R.id.qualityButton), "cycleQuality");
        setViewClickListener(findViewById(R.id.aboutButton), "doAbout");
        setViewClickListener(findViewById(R.id.convertImageButton), "chooseGalleryImage");

        // The default Material style uppercases button labels, which we don't want for "PinP".
        ((Button) findViewById(R.id.zoomButton)).setTransformationMethod(null);

        // Show "Switch Camera" button if more than one camera.
        switchCameraButton = (ImageButton) findViewById(R.id.switchCameraButton);
        if (CameraUtils.numberOfCameras() > 1) {
            setViewClickListener(switchCameraButton, "switchToNextCamera");
            switchCameraButton.setVisibility(View.VISIBLE);
        }

        customColorEditView = findViewById(R.id.customColorView);

        setViewClickListener(
                findViewById(R.id.customColorDoneButton), "closeCustomColorView");
        setViewClickListener(
                findViewById(R.id.customColorTopLeftButton), "chooseCustomColorTopLeft");
        setViewClickListener(
                findViewById(R.id.customColorTopRightButton), "chooseCustomColorTopRight");
        setViewClickListener(
                findViewById(R.id.customColorBottomLeftButton), "chooseCustomColorBottomLeft");
        setViewClickListener(
                findViewById(R.id.customColorBottomRightButton), "chooseCustomColorBottomRight");
        setViewClickListener(
                findViewById(R.id.customColorBackgroundButton), "chooseCustomColorBackground");

        chooseColorControlBar = findViewById(R.id.chooseColorControlBar);
        solidColorCheckbox = (CheckBox)findViewById(R.id.solidColorCheckbox);
        setViewClickListener(solidColorCheckbox, "solidColorCheckboxChanged");
        noiseFilterCheckbox = (CheckBox)findViewById(R.id.noiseFilterCheckbox);
        setViewClickListener(noiseFilterCheckbox, "noiseFilterCheckboxChanged");

        toggleVideoButton = (ImageButton)findViewById(R.id.toggleVideoButton);
        setViewClickListener(toggleVideoButton, "toggleVideoMode");

        cameraActionButton = (ShutterButton)findViewById(R.id.cameraActionButton);
        cameraActionButton.setOnShutterButtonListener(this);

        setViewClickListener(findViewById(R.id.videoLibraryButton), "gotoVideoLibrary");

        setViewClickListener(findViewById(R.id.preferencesButton), "gotoPreferences");

        updateCameraButtons();
        updateCustomColorScheme();
    }

    @Override public void onPause() {
        appVisible = false;
        if (isRecordingVideo()) {
            stopVideoRecording();
        }
        stopCamera();
        super.onPause();
    }

    @Override public void onResume() {
        super.onResume();
        AndroidUtils.setSystemUiLowProfile(cameraView);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        color = prefs.getInt(COLOR_PREFS_KEY, 0);
        sampleFactor = prefs.getInt(SAMPLE_PREFS_KEY, 2);
        if (sampleFactor>2) sampleFactor = 2;

        useSolidColor = (prefs.getInt(SOLID_COLOR_PREFS_KEY, 0) > 0);
        solidColorCheckbox.setChecked(useSolidColor);

        useNoiseFilter = (prefs.getInt(NOISE_FILTER_PREFS_KEY, 0) > 0);
        noiseFilterCheckbox.setChecked(useNoiseFilter);
        updateControlsPosition();

        appVisible = true;
        // Hide color grid controls because we may have been showing them before.
        chooseColorControlBar.setVisibility(View.GONE);
        if (hasCameraPermission()) {
            startCameraIfVisible();
        }
        else {
            PermissionsChecker.requestCameraAndStoragePermissions(this);
        }
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PermissionsChecker.CAMERA_AND_STORAGE_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCameraIfVisible();
                }
                else {
                    Toast.makeText(this, getString(R.string.cameraPermissionRequired), Toast.LENGTH_LONG).show();
                }
                break;
            case PermissionsChecker.STORAGE_FOR_PHOTO_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Don't actually take the picture.
                }
                else {
                    showPermissionNeededDialog(getString(R.string.storagePermissionRequiredToTakePhoto));
                }
                break;
            case PermissionsChecker.STORAGE_FOR_LIBRARY_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    gotoVideoLibrary();
                }
                else {
                    showPermissionNeededDialog(getString(R.string.storagePermissionRequiredToGoToLibrary));
                }
                break;
            case PermissionsChecker.STORAGE_FOR_CONVERT_PICTURE_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    chooseGalleryImage();
                }
                else {
                    showPermissionNeededDialog(getString(R.string.storagePermissionRequiredToGoToLibrary));
                }
                break;
            case PermissionsChecker.RECORD_AUDIO_FOR_VIDEO_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Don't start recording.
                }
                else {
                    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
                    editor.putBoolean(getString(R.string.recordAudioPrefsKey), false);
                    editor.apply();
                    Toast.makeText(this, getString(R.string.disabledAudioDueToPermissionDenied), Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                PermissionsChecker.hasCameraPermission(this);
    }

    private boolean hasStoragePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                PermissionsChecker.hasStoragePermission(this);
    }

    private boolean hasRecordAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                PermissionsChecker.hasRecordAudioPermission(this);
    }

    private void showPermissionNeededDialog(String msg) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setMessage(msg);
        dialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    /**
     * Droid and possibly other devices have a "camera" button, which will save the picture or
     * start/stop video recording.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode==KeyEvent.KEYCODE_CAMERA ||
                (keyCode==KeyEvent.KEYCODE_DPAD_CENTER) && event.getRepeatCount()==0)) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    handleCameraActionButton();
                }
            });
            return true;
        }
        if (keyCode==KeyEvent.KEYCODE_BACK) {
            // If editing custom colors, dismiss editor, otherwise handle normally.
            if (customColorEditView.getVisibility()==View.VISIBLE) {
                customColorEditView.setVisibility(View.INVISIBLE);
                return true;
            }
            else if (imageProcessor != null && imageProcessor.showingColorSchemeGrid()) {
                // If selecting colors, hide selection grid and restore original color scheme.
                updateColor();
                chooseColorControlBar.setVisibility(View.GONE);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public void doAbout() {
        Intent aboutIntent = new Intent(this, AboutActivity.class);
        this.startActivity(aboutIntent);
    }

    int statusUpdateID = 0;

    void updateStatusTextWithFade(CharSequence text) {
        statusUpdateID++;
        final int updateID = statusUpdateID;
        statusText.setText(text);
        // Schedule an update to clear text, but only if statusUpdateID hasn't changed (which it
        // will if another message is set)
        Runnable clearText = new Runnable() {
            @Override
            public void run() {
                if (updateID==statusUpdateID) {
                    statusText.setText("");
                }
            }
        };
        handler.postDelayed(clearText, 5000);
    }

    void writePreferenceInt(String key, int value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(key, value);
        editor.commit();
    }

    @SuppressLint("RtlHardcoded")
    void updateControlsPosition() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean controlsOnLeft = prefs.getBoolean(getString(R.string.controlsOnLeftPrefsKey), false);
        FrameLayout.LayoutParams controlLayout = (FrameLayout.LayoutParams)verticalButtonBar.getLayoutParams();
        controlLayout.gravity = controlsOnLeft ? Gravity.LEFT : Gravity.RIGHT;
        verticalButtonBar.setLayoutParams(controlLayout);
    }

    public void toggleButtonBarVisibility() {
        if (customColorEditView.getVisibility()==View.VISIBLE) return;
        buttonBar.setVisibility(
                buttonBar.getVisibility()==View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
        if (verticalButtonBar!=null) verticalButtonBar.setVisibility(buttonBar.getVisibility());
    }

    public void togglePictureInPicture() {
        double ratio = overlayView.getPictureInPictureRatio();
        overlayView.setPictureInPictureRatio((ratio<=0) ? PIP_RATIO : 0);
        overlayView.invalidate();
    }

    public void updateColor() {
        if (color<0 || color>=COLORS.length) color = 0;
        if (imageProcessor!=null) {
            imageProcessor.setColorScheme(COLORS[color]);
        }
    }

    public void chooseColor() {
        imageProcessor.setGridColorSchemes(COLORS, COLOR_GRID_ROWS);
        // Hide buttons and picture-in-picture.
        buttonBar.setVisibility(View.INVISIBLE);
        if (verticalButtonBar!=null) verticalButtonBar.setVisibility(View.INVISIBLE);
        overlayView.setPictureInPictureRatio(0);
        overlayView.setCornerImage(null);
        overlayView.invalidate();
        chooseColorControlBar.setVisibility(View.VISIBLE);
        // Tell overlay view to stretch to full screen (no margins).
        overlayView.setFillScreen(true);
    }

    Map<String, Integer> customColorSchemeColors = new HashMap<String, Integer>();

    void updateCustomColorScheme() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        int[] c = new int[15];

        String[] keySuffixes = {"background", "topLeft", "topRight", "bottomLeft", "bottomRight"};
        int[] defaultValues = {0xFF000000, 0xFFFFFFFF, 0xFFFF00FF, 0xFFFFFF00, 0xFF00FFFF};
        for(int i=0; i<5; i++) {
            int cval = prefs.getInt(CUSTOM_COLOR_KEY_PREFIX + keySuffixes[i], defaultValues[i]);
            c[3*i] = (cval>>16) & 0xff;
            c[3*i+1] = (cval>>8) & 0xff;
            c[3*i+2] = cval & 0xff;
            customColorSchemeColors.put(keySuffixes[i], cval);
        }

        customColorScheme.updateColors(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7],
                c[8], c[9], c[10], c[11], c[12], c[13], c[14]);
    }

    public void showCustomColorView() {
        this.color = COLORS.length - 1;
        writePreferenceInt(COLOR_PREFS_KEY, this.color);
        updateColor();
        buttonBar.setVisibility(View.INVISIBLE);
        customColorEditView.setVisibility(View.VISIBLE);
    }

    public void closeCustomColorView() {
        customColorEditView.setVisibility(View.INVISIBLE);
    }

    void chooseCustomColor(String position) {
        int currentColor = customColorSchemeColors.get(position);
        customColorEditKey = position;
        (new ColorPickerDialog(this, this, currentColor)).show();
    }

    public void chooseCustomColorTopLeft() {
        chooseCustomColor("topLeft");
    }
    public void chooseCustomColorTopRight() {
        chooseCustomColor("topRight");
    }
    public void chooseCustomColorBottomLeft() {
        chooseCustomColor("bottomLeft");
    }
    public void chooseCustomColorBottomRight() {
        chooseCustomColor("bottomRight");
    }
    public void chooseCustomColorBackground() {
        chooseCustomColor("background");
    }


    final static int ACTIVITY_SELECT_IMAGE = 1;

    public void chooseGalleryImage() {
        if (!hasStoragePermission()) {
            PermissionsChecker.requestStoragePermissionsToConvertPicture(this);
            return;
        }
        Intent i = new Intent(
                Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(i, ACTIVITY_SELECT_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch(requestCode) {
            case ACTIVITY_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    (new Thread() {
                        @Override public void run() {
                            try {
                                final String imageDirectory = (new ProcessPictureOperation())
                                        .processPicture(EyeballMain.this, intent.getData());
                                handler.post(new Runnable() {
                                    @Override public void run() {
                                        ViewImageActivity.startActivityWithImageDirectory(
                                                EyeballMain.this, imageDirectory);
                                    }
                                });
                            }
                            catch(Exception ex) {
                                Log.e("ConvertImage", "Failed converting image", ex);
                            }
                        }
                    }).start();
                }
                break;
        }
    }

    @Override public void colorChanged(int colorSelected) {
        String key = CUSTOM_COLOR_KEY_PREFIX + customColorEditKey;
        writePreferenceInt(key, colorSelected);
        updateCustomColorScheme();
    }

    int zoomRatio = CameraUtils.DEFAULT_ZOOM_RATIO;
    int ZOOMED_IN_RATIO = 4*CameraUtils.DEFAULT_ZOOM_RATIO;
    Long lastClickTime = null;
    long DOUBLE_CLICK_DELAY = 500;
    Boolean zoomSupported = null;

    void toggleZoom() {
        if (zoomSupported==null) {
            zoomSupported = Boolean.valueOf(CameraUtils.cameraSupportsZoom(camera));
        }
        if (zoomSupported) {
            int targetRatio = (zoomRatio > CameraUtils.DEFAULT_ZOOM_RATIO) ?
                    CameraUtils.DEFAULT_ZOOM_RATIO : ZOOMED_IN_RATIO;
            zoomRatio = CameraUtils.setCameraZoomRatio(camera, targetRatio);
            updateStatusTextWithFade(
                    (zoomRatio<=CameraUtils.DEFAULT_ZOOM_RATIO) ? "Zoom Out" : "Zoom In");
        }
    }

    public void handleMainViewTouch(MotionEvent event) {
        if (event.getAction()==MotionEvent.ACTION_DOWN) {
            hasToggledDebugForTouch = false;
            if (imageProcessor!=null) {
                if (imageProcessor.showingColorSchemeGrid()) {
                    int gridCols = (int)Math.ceil(COLORS.length / COLOR_GRID_ROWS);
                    int row = (int)(event.getY() / (overlayView.getHeight() / COLOR_GRID_ROWS));
                    int col = (int)(event.getX() / (overlayView.getWidth() / gridCols));
                    this.color = row * gridCols + col;
                    writePreferenceInt(COLOR_PREFS_KEY, color);
                    updateColor();
                    if (color==COLORS.length-1) {
                        showCustomColorView();
                    }
                    chooseColorControlBar.setVisibility(View.GONE);
                    overlayView.setFillScreen(false);
                }
                else if (overlayView.isPointInCornerImage(event.getX(), event.getY())) {
                    if (cornerImageClickHandler != null) {
                        cornerImageClickHandler.run();
                    }
                }
                else {
                    toggleButtonBarVisibility();

                    // Check for double click to zoom.
                    long now = System.currentTimeMillis();
                    if (lastClickTime!=null) {
                        if (now - lastClickTime <= DOUBLE_CLICK_DELAY) {
                            toggleZoom();
                            lastClickTime = null;
                        }
                        else {
                            lastClickTime = now;
                        }
                    }
                    else {
                        lastClickTime = now;
                    }
                }
            }
        }
        else {
            // Toggle debug if touch is held for more then 3 seconds.
            if (!hasToggledDebugForTouch && event.getEventTime() - event.getDownTime() >= 3000) {
                hasToggledDebugForTouch = true;
                showDebugMessages = !showDebugMessages;
                String msg = (showDebugMessages) ?
                        "Debug Messages Enabled" : "Debug Messages Disabled";
                updateStatusTextWithFade(msg);
            }
        }
    }

    public void cycleQuality() {
        String msg = null;
        if (sampleFactor==1) {
            sampleFactor = 2;
            msg = "Medium Quality";
        }
        else {
            sampleFactor = 1;
            msg = "High Quality";
        }
        writePreferenceInt(SAMPLE_PREFS_KEY, sampleFactor);
        if (imageProcessor!=null) imageProcessor.setSampleFactor(sampleFactor);
        updateStatusTextWithFade(msg);
    }

    boolean saveInProgress = false;
    boolean saveNextImageReceived = false;
    String lastSavedImageDirectory;

    // This method is called by the shutter button and just sets a flag so that saveCurrentBitmap
    // will be called when the next image is received.
    void saveNextBitmap() {
        if (!hasStoragePermission()) {
            PermissionsChecker.requestStoragePermissionsToTakePhoto(this);
            return;
        }
        if (imageProcessor==null || saveInProgress) return;
        saveInProgress = saveNextImageReceived = true;
        // FIXME: Always save images at highest quality, imageProcessor.setSampleFactor(1) here
        // doesn't work
    }

    void saveCurrentBitmap() {
        // Save image in separate thread so UI stays responsive.
        pauseCamera();
        final Bitmap bitmap = imageProcessor.getBitmap();
        updateStatusTextWithFade("Saving...");
        (new Thread() {
            @Override public void run() {
                final String savedPath = _saveBitmap(bitmap);
                handler.post(new Runnable() {
                    @Override public void run() {
                        resumeCamera();
                        saveInProgress = false;
                        if (savedPath!=null) {
                            updateStatusTextWithFade("Picture saved");
                            lastSavedImageDirectory = savedPath;
                            showCornerImage(createCornerImageFromBitmap(bitmap));
                            // Open ViewImageActivity if the corner image is clicked. Yes, there
                            // are a bit too many nested classes here.
                            cornerImageClickHandler = new Runnable() {
                                @Override public void run() {
                                    ViewImageActivity.startActivityWithImageDirectory(
                                            EyeballMain.this, lastSavedImageDirectory);
                                }
                            };
                        }
                        else {
                            updateStatusTextWithFade("Error saving image");
                        }
                    }
                });
            }
        }).start();
    }

    String _saveBitmap(Bitmap bitmap) {
        String imagePath = WGUtils.pathForNewImageDirectory();
        ImageRecorder ir = new ImageRecorder(this, imagePath);
        try {
            ir.saveImage(imageProcessor, this.color, this.useSolidColor, this.useNoiseFilter);
            showVideoTab = false;
            return imagePath;
        }
        catch(Exception ex) {
            Log.e("WG", "Error saving image", ex);
            return null;
        }
    }

    // The camera action button saves a picture in camera mode, or starts or stops recording in
    // video mode.
    public void handleCameraActionButton() {
        if (videoMode) {
            toggleVideoRecording();
        }
        else {
            saveNextBitmap();
        }
    }

    // Copy to a smaller bitmap, to reduce memory usage.
    Bitmap createCornerImageFromBitmap(Bitmap bitmap) {
        Bitmap smallBitmap = Bitmap.createBitmap(
                (int)(CORNER_IMAGE_RATIO*overlayView.getWidth()),
                (int)(CORNER_IMAGE_RATIO*overlayView.getHeight()),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(smallBitmap);
        Rect dstRect = new Rect(0, 0, smallBitmap.getWidth(), smallBitmap.getHeight());
        canvas.drawBitmap(bitmap, null, dstRect, null);
        return smallBitmap;
    }

    Runnable hideCornerImageCallback = new Runnable() {
        @Override public void run() {
            overlayView.setCornerImage(null);
            overlayView.invalidate();
            cornerImageClickHandler = null;
        }
    };

    void showCornerImage(Bitmap bitmap) {
        overlayView.setCornerImage(bitmap);
        overlayView.setCornerImageRatio(CORNER_IMAGE_RATIO);
        // Hide image after 5 seconds (TODO: reset if user takes another picture).
        handler.postDelayed(hideCornerImageCallback, 5000);
    }

    public void solidColorCheckboxChanged() {
        this.useSolidColor = solidColorCheckbox.isChecked();
        writePreferenceInt(SOLID_COLOR_PREFS_KEY, (this.useSolidColor) ? 1 : 0);
        if (imageProcessor!=null) {
            imageProcessor.setUseBrightness(this.useSolidColor);
        }
    }

    public void noiseFilterCheckboxChanged() {
        this.useNoiseFilter = noiseFilterCheckbox.isChecked();
        writePreferenceInt(NOISE_FILTER_PREFS_KEY, (this.useNoiseFilter) ? 1 : 0);
        if (imageProcessor!=null) {
            imageProcessor.setUseNoiseFilter(this.useNoiseFilter);
        }
    }

    public void updateCameraButtons() {
        switchCameraButton.setImageResource(CameraUtils.cameraIsFrontFacing(cameraId) ?
                R.drawable.ic_camera_front_white_36dp : R.drawable.ic_camera_rear_white_36dp);
        toggleVideoButton.setImageResource(videoMode ?
                R.drawable.ic_videocam_white_36dp : R.drawable.ic_photo_camera_white_36dp);

        int cameraActionId = R.drawable.btn_camera_shutter_holo;
        if (videoMode) {
            cameraActionId = (isRecordingVideo()) ?
                    R.drawable.btn_video_shutter_recording_holo : R.drawable.btn_video_shutter_holo;
        }
        cameraActionButton.setImageResource(cameraActionId);
    }

    public void toggleVideoMode() {
        videoMode = !videoMode;
        if (isRecordingVideo()) stopVideoRecording();
        updateCameraButtons();
    }

    boolean shouldRecordAudio() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        return prefs.getBoolean(getString(R.string.recordAudioPrefsKey),
                getResources().getBoolean(R.bool.recordAudioDefault));
    }

    VideoRecorder.Quality getRecordingQuality() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String qualityString = prefs.getString(getString(R.string.recordingQualityPrefsKey), null);
        try {
            return VideoRecorder.Quality.valueOf(qualityString);
        }
        catch(Exception ex) {
            return VideoRecorder.Quality.LOW;
        }
    }

    public void gotoPreferences() {
        // Pass camera preview size so preferences screen can show video resolutions.
        WGPreferences.startActivity(this,
                previewSize != null ? previewSize.width : 0,
                previewSize != null ? previewSize.height : 0);
    }

    public void toggleVideoRecording() {
        if (isRecordingVideo()) {
            stopVideoRecording();
        }
        else {
            startVideoRecording();
        }
        updateCameraButtons();
    }

    boolean isRecordingVideo() {
        return (videoRecorder!=null);
    }

    void startVideoRecording() {
        if (!hasStoragePermission()) {
            PermissionsChecker.requestStoragePermissionsToTakePhoto(this);
            return;
        }
        if (this.shouldRecordAudio() && !hasRecordAudioPermission()) {
            PermissionsChecker.requestRecordAudioPermissionForVideo(this);
            return;
        }
        String videoPath = WGUtils.pathForNewVideoRecording();
        videoRecorder = new VideoRecorder(videoPath, previewSize.width, previewSize.height,
                getRecordingQuality(), this.color, this.useSolidColor, this.useNoiseFilter,
                imageProcessor.getOrientation());
        cornerBitmapForVideo = null;

        audioRecorder = null;
        int audioSampleSize = 44100;
        if (this.shouldRecordAudio()) {
            int bufferSize = Math.max(audioSampleSize,
                    AudioRecord.getMinBufferSize(
                            audioSampleSize,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT));
            try {
                audioRecorder = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        audioSampleSize,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);
            }
            catch(Exception ex) {
                audioRecorder = null;
            }
        }

        if (audioRecorder!=null) {
            final byte[] audioBuffer = new byte[audioSampleSize];
            Thread audioRecordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (videoRecorder!=null) {
                            int numBytes = audioRecorder.read(audioBuffer, 0, audioBuffer.length);
                            if (videoRecorder!=null) {
                                videoRecorder.recordAudioData(audioBuffer, numBytes);
                            }
                        }
                    }
                    catch(Exception ex) {
                        Log.e("WG-audio", "Error recording audio", ex);
                    }
                    finally {
                        audioRecorder.stop();
                        audioRecorder.release();
                        audioRecorder = null;
                    }
                }
            });

            audioRecorder.startRecording();
            audioRecordingThread.start();
        }
        // TODO: disable most controls while recording in progress?
        updateStatusTextWithFade("Started video recording");
    }

    void stopVideoRecording() {
        updateStatusTextWithFade("Stopped video recording");
        videoRecorder.endRecording();
        final String videoPath = videoRecorder.getMediaDirectory().getPath();
        videoRecorder = null; // This will stop the audio thread created in startVideoRecording.
        showVideoTab = true;

        if (cornerBitmapForVideo != null) {
            showCornerImage(cornerBitmapForVideo);
            // Go to VideoPlaybackActivity if the corner image is clicked.
            cornerImageClickHandler = new Runnable() {
                @Override public void run() {
                    VideoPlaybackActivity.startActivityWithVideoDirectory(
                            EyeballMain.this, videoPath);
                }
            };
            cornerBitmapForVideo = null;
        }
    }

    public void gotoVideoLibrary() {
        if (!hasStoragePermission()) {
            PermissionsChecker.requestStoragePermissionsToGoToLibrary(this);
            return;
        }
        MediaTabActivity.startActivity(this, showVideoTab);
    }

    int totalFrames = 0;
    Size previewSize = null;

    @Override public void onPreviewFrame(byte[] data, Camera _camera) {
        boolean shouldRelease = false;
        if (imageProcessor!=null) {
            if (previewSize==null) previewSize = _camera.getParameters().getPreviewSize();
            // When recording video, only show every third frame to reduce cpu time.
            if (!isRecordingVideo() || (totalFrames % 3 == 0)) {
                long diff = System.currentTimeMillis() - lastTS;
                if (DEBUG) Log.i("WG", "Got preview frame " + diff + "ms after last view update");
                // Make sure we don't replace an existing pending preview image, because then the
                // replaced image's buffer won't get freed and we won't have a spare buffer, which
                // slows down the frame rate significantly.
                boolean queued = imageProcessor.processImageData(
                        data, previewSize.width, previewSize.height);
                if (!queued) {
                    if (DEBUG) Log.i("WG", "Not queued, releasing buffer");
                    shouldRelease = true;
                }
            }
            else {
                shouldRelease = true;
            }
        }
        if (isRecordingVideo()) {
            try {
                videoRecorder.recordFrame(data);
                if (videoRecorder.duration() >= VIDEO_TIME_LIMIT_MS) {
                    toggleVideoRecording();
                    updateStatusTextWithFade("Exceeded time limit, recording stopped");
                }
            }
            catch(Exception ex) {
                toggleVideoRecording();
                Log.e("WireGoggles", "Error recording", ex);
                updateStatusTextWithFade("Recording stopped due to error");
            }
        }
        if (shouldRelease) {
            CameraUtils.addPreviewCallbackBuffer(camera, data);
        }
        totalFrames++;
    }

    int _renderTime1;

    FrameRateManager frameRateManager = new FrameRateManager(15);

    public static long lastTS = 0;

    void processMessage(Message message) {
        if (message.what==CameraImageProcessor.IMAGE_DONE) {

            if (camera!=null) CameraUtils.addPreviewCallbackBuffer(camera, (byte[])message.obj);
            // save image if flag is set
            if (saveNextImageReceived) {
                saveNextImageReceived = false;
                saveCurrentBitmap();
            }

            if (isRecordingVideo()) {
                if (!videoRecorder.hasThumbnailImage()) {
                    // FIXME: This shouldn't be on the main thread.
                    videoRecorder.storeThumbnailImage(imageProcessor.getBitmap());
                }
                if (cornerBitmapForVideo == null) {
                    // Save the image to show in the corner when recording ends.
                    cornerBitmapForVideo = createCornerImageFromBitmap(imageProcessor.getBitmap());
                }
            }

            // trying to touch overlayView when the app is hidden can cause crashes
            if (!appVisible) return;
            overlayView.setFlipHorizontal(
                    CameraUtils.cameraIsFrontFacing(cameraId) &&
                    !imageProcessor.showingColorSchemeGrid());
            overlayView.invalidate();
            if (DEBUG) {
                if (lastTS != 0) {
                    long diff = System.currentTimeMillis() - lastTS;
                    Log.i("WG", "Invalidated frame, time since last: " + diff + "ms" +
                            ", render time: " + message.arg1 + "ms");
                }
                lastTS = System.currentTimeMillis();
            }

            frameRateManager.frameStarted();
            // everything past here is debug output to show frame rates
            if (frameRateManager.getTotalFrames() % 10 == 0) {
                if (showDebugMessages) {
                    long avgRender = _renderTime1 / 10;

                    Camera.Parameters params = camera.getParameters();
                    Camera.Size size = params.getPreviewSize();
                    int previewRate = params.getPreviewFrameRate();

                    String msg = String.format(
                            "%.2f fps, render: %d ms, size: %dx%d, rate: %d, zoom: %d",
                            frameRateManager.currentFramesPerSecond(), avgRender,
                            size.width, size.height, previewRate, zoomRatio);
                    updateStatusTextWithFade(msg);
                }
                _renderTime1 = 0;
            }
            else {
                _renderTime1 += message.arg1;
            }
        }
    }

    int cameraId = 0;

    public void switchToNextCamera() {
        if (camera!=null) {
            stopCamera();
        }
        cameraId = (cameraId + 1) % CameraUtils.numberOfCameras();
        startCamera();
        updateCameraButtons();
    }

    /* The camera preview consumes a lot of CPU time, and there's not much we can do about it
       other than setting the preview frame rate to a lower value. Things that seem like they
       should work but don't:
       - calling stopPreview in onFrameReceived and startPreview in handleMessage: this makes it
         even slower, apparently the camera takes a while to warm up when starting the preview.
       - calling setPreviewCallback(null) in onFrameReceived and restoring in handleMessage:
         no effect, apparently the time is spent filling the data and drawing it to the SurfaceView.
       Caching camera.getParameters().getPreviewSize() helps slightly.
     */
    void startCamera() {
        boolean isRotated180 = CameraUtils.getCameraInfo(this.cameraId).isRotated180Degrees();
        if (camera==null) {
            try {
                camera = CameraUtils.openCamera(this.cameraId);
                Camera.Parameters params = camera.getParameters();

                // If we don't have callback buffers, limit the frame rate to avoid excessive
                // preview callbacks.
                if (!CameraUtils.previewBuffersSupported()) {
                    params.setPreviewFrameRate(15);
                }
                this.defaultPreviewFrameRate = this.currentPreviewFrameRate =
                        params.getPreviewFrameRate();

                Camera.Size bestPreviewSize = CameraUtils.bestCameraSizeForWidthAndHeight(
                        params, overlayView.getWidth(), overlayView.getHeight());
                if (bestPreviewSize!=null) {
                    params.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);
                }
                camera.setParameters(params);

                this.previewSize = null;
                camera.setPreviewDisplay(cameraView.getHolder());

                // If preview buffers aren't available (requires Froyo or later), these methods
                // will use the earlier non-buffer functionality.
                CameraUtils.createPreviewCallbackBuffers(camera, 2);
                CameraUtils.setPreviewCallbackWithBuffer(camera, this);

                if (isRotated180) {
                    camera.setDisplayOrientation(180);
                }

                camera.startPreview();
            }
            catch(Exception ex) {
                camera = null;
            }
        }
        if (camera!=null && imageProcessor==null) {
            imageProcessor = new CameraImageProcessor();
            imageProcessor.setMessageHandler(handler);
            imageProcessor.setSampleFactor(sampleFactor);
            imageProcessor.setUseBrightness(useSolidColor);
            imageProcessor.setUseNoiseFilter(useNoiseFilter);
            // It would be more convenient to immediately transform the camera data as it comes in,
            // but that has various timing problems. Instead we need to tell everything that
            // generates images to rotate them.
            imageProcessor.setOrientation(isRotated180 ?
                    CameraImageProcessor.Orientation.ROTATED_180 :
                    CameraImageProcessor.Orientation.NORMAL);
            updateColor();
            overlayView.setImageProcessor(imageProcessor);
            imageProcessor.start();
        }
    }

    void startCameraIfVisible() {
        if (appVisible && cameraViewReady) {
            startCamera();
        }
    }

    void stopCamera() {
        if (camera!=null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        if (imageProcessor!=null) {
            imageProcessor.stop();
            imageProcessor = null;
        }
    }

    void pauseCamera() {
        // Prevent imageProcessor's bitmap from being modified while paused. This appears to fix
        // a bug that was causing saved images to have horizontal lines through the top, presumably
        // because the bitmap object was being modified during the save process.
        if (imageProcessor!=null) {
            imageProcessor.pause();
        }
        if (camera!=null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
        }
    }

    void resumeCamera() {
        if (imageProcessor!=null) {
            imageProcessor.unpause();
        }
        if (camera!=null) {
            CameraUtils.createPreviewCallbackBuffers(camera, 2);
            CameraUtils.setPreviewCallbackWithBuffer(camera, this);

            camera.startPreview();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        this.cameraViewReady = true;
        startCameraIfVisible();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        // All done in surfaceChanged.
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        this.cameraViewReady = false;
        stopCamera();
    }

    @Override public void onShutterButtonFocus(boolean pressed) {
        int resID;
        if (pressed) {
            resID = R.drawable.btn_camera_shutter_pressed_holo;
            if (videoMode) {
                resID = (isRecordingVideo()) ?
                        R.drawable.btn_video_shutter_recording_pressed_holo :
                        R.drawable.btn_video_shutter_pressed_holo;
            }
        }
        else {
            resID = R.drawable.btn_camera_shutter_holo;
            if (videoMode) {
                resID = (isRecordingVideo()) ?
                        R.drawable.btn_video_shutter_recording_holo :
                        R.drawable.btn_video_shutter_holo;
            }
        }
        cameraActionButton.setImageResource(resID);
    }

    @Override public void onShutterButtonClick() {
        handleCameraActionButton();
    }

}

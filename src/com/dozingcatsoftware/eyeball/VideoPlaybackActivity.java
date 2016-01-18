package com.dozingcatsoftware.eyeball;

import java.io.File;
import java.io.RandomAccessFile;

import com.dozingcatsoftware.WireGoggles.R;
import com.dozingcatsoftware.eyeball.video.AbstractViewMediaActivity;
import com.dozingcatsoftware.eyeball.video.CreateVideoZipFileAsyncTask;
import com.dozingcatsoftware.eyeball.video.CreateWebmAsyncTask;
import com.dozingcatsoftware.eyeball.video.MediaDirectory;
import com.dozingcatsoftware.eyeball.video.ProcessVideoTask;
import com.dozingcatsoftware.util.AndroidUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.widget.ImageButton;
import android.widget.Toast;

/**
 * Handles playing saved videos and exporting them to WebM and ZIP archives.
 * Exporting should be refactored to a separate class, also several strings need to be localized.
 */
public class VideoPlaybackActivity extends AbstractViewMediaActivity {

    // Ways videos can be exported, and the messages shown in the export progress dialog for each.
    enum ExportType {
        WEBM("webm", "video/webm",
                R.string.webmVideoFormatDescription,
                R.string.webmExportDialogTitle,
                R.string.webmExportDialogMessageVideo,
                R.string.webmExportDialogMessageAudio,
                R.string.webmExportConfirmReplaceMessage),
        ZIP("zip", "application/zip",
                R.string.zipVideoFormatDescription,
                R.string.zipExportDialogTitle,
                R.string.zipExportDialogMessageVideo,
                R.string.zipExportDialogMessageAudio,
                R.string.zipExportConfirmReplaceMessage);

        public final String extension;
        public final String mimeType;
        public final int formatDescriptionId;
        public final int exportDialogTitleId;
        public final int exportDialogMessageVideoId;
        public final int exportDialogMessageAudioId;
        public final int exportConfirmReplaceMessageId;

        ExportType(String extension, String mimeType,
                int formatDescriptionId,
                int exportDialogTitleId,
                int exportDialogMessageVideoId,
                int exportDialogMessageAudioId,
                int exportConfirmReplaceMessageId) {
            this.extension = extension;
            this.mimeType = mimeType;
            this.formatDescriptionId = formatDescriptionId;
            this.exportDialogTitleId = exportDialogTitleId;
            this.exportDialogMessageVideoId = exportDialogMessageVideoId;
            this.exportDialogMessageAudioId = exportDialogMessageAudioId;
            this.exportConfirmReplaceMessageId = exportConfirmReplaceMessageId;
        }
    }

    ImageButton playButton;
    View nextFrameButton, previousFrameButton;
    View buttonBar;
    ProgressDialog encodeProgressDialog;

    AudioTrack audioTrack;
    RandomAccessFile audioFile;
    long audioFileSize;
    int audioSamplingFrequency = 44100;
    int audioBufferSize = 88200;
    byte[] audioBuffer;
    int audioFramesWritten;

    // For trying to maintain somewhat accurate frame rate.
    double secondsPerFrame;
    long playbackStartMillis;
    int playbackStartFrame;
    int[] frameEndOffsets; // Stores milliseconds since start of video that each frame ends at.

    boolean isPlaying;
    boolean shownFirstFrame;

    Handler handler = new Handler();
    AsyncTask<ProcessVideoTask.Params, ProcessVideoTask.Progress, ProcessVideoTask.Result> encodeTask;

    public static Intent startActivityWithVideoDirectory(Activity parent, String path) {
        Intent intent = new Intent(parent, VideoPlaybackActivity.class);
        intent.putExtra("path", path);
        parent.startActivityForResult(intent, 0);
        return intent;
    }

    /** Called when the activity is first created. */
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupView(R.id.buttonBar);

        secondsPerFrame = videoProperties.durationInMilliseconds()*1000.0 / videoProperties.getNumberOfFrames();
        frameEndOffsets = videoProperties.getFrameRelativeEndTimes();

        String audioPath = videoReader.getVideoDirectory().audioFilePath();
        if ((new File(audioPath)).isFile()) {
            try {
                audioFile = new RandomAccessFile(audioPath, "r");
                audioFileSize = audioFile.length();
                // NOTE: audioTrack won't play anything until audioBufferSize bytes have been written.
                audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, audioSamplingFrequency,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, audioBufferSize, AudioTrack.MODE_STREAM);
                audioBuffer = new byte[audioBufferSize];
            }
            catch(Exception ex) {
                audioFile = null;
            }
        }

        AndroidUtils.bindOnClickListener(this, playButton = (ImageButton)findViewById(R.id.playbackPlayButton), "togglePlayback");
        AndroidUtils.bindOnClickListener(this, nextFrameButton = findViewById(R.id.playbackNextFrameButton), "showNextFrame");
        AndroidUtils.bindOnClickListener(this, previousFrameButton = findViewById(R.id.playbackPreviousFrameButton), "showPreviousFrame");
        AndroidUtils.bindOnClickListener(this, findViewById(R.id.playbackSavePictureButton), "savePicture");
        AndroidUtils.bindOnClickListener(this, findViewById(R.id.playbackColorsButton), "showColorSchemeGrid"); // superclass method
        AndroidUtils.bindOnClickListener(this, findViewById(R.id.playbackDeleteVideoButton), "deleteVideo");
        AndroidUtils.bindOnClickListener(this, findViewById(R.id.playbackEncodeVideoButton), "encodeVideoWithReplaceCheck");
        AndroidUtils.bindOnClickListener(this, findViewById(R.id.playbackShareVideoButton), "shareVideo");

        // hack: buttons can't fit on single row on smaller screens, "next frame" is least important
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        if (metrics.widthPixels < 800) {
            nextFrameButton.setVisibility(View.GONE);
        }
        // end hack
    }

    String pathForExportedFile(ExportType exportType) {
        return videoReader.getVideoDirectory().getPath() + "." + exportType.extension;
    }

    boolean exportedFileExists(ExportType exportType) {
        return (new File(pathForExportedFile(exportType))).isFile();
    }

    void updateButtons() {
        playButton.setImageResource(isPlaying ? R.drawable.ic_pause_white_48dp : R.drawable.ic_play_arrow_white_48dp);

        nextFrameButton.setEnabled(!isPlaying && !videoReader.isAtEnd());
        setViewAlpha(nextFrameButton, nextFrameButton.isEnabled() ? 1f : 0.3f);

        previousFrameButton.setEnabled(!isPlaying && videoReader.currentFrameNumber()>1);
        setViewAlpha(previousFrameButton, previousFrameButton.isEnabled() ? 1f : 0.3f);
    }

    // Workaround for View.setAlpha not being available on Gingerbread.
    private void setViewAlpha(View view, float alpha) {
        AlphaAnimation animation = new AlphaAnimation(alpha, alpha);
        animation.setDuration(0);
        animation.setFillAfter(true);
        view.startAnimation(animation);
    }

    @Override public void onResume() {
        super.onResume();
        if (!shownFirstFrame) {
            loadNextFrame();
            shownFirstFrame = true;
        }
        updateButtons();
    }

    @Override public void onPause() {
        stopPlayback();
        if (encodeProgressDialog!=null) {
            encodeProgressDialog.cancel();
        }
        allowScreenOff();
        super.onPause();
    }

    void keepScreenOn() {
        getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    void allowScreenOff() {
        getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void moveToStart() {
        videoReader.moveToFrameNumber(0);
        loadNextFrame();
        updateButtons();
    }

    AudioTrack.OnPlaybackPositionUpdateListener audioPeriodicNotificationListener = new AudioTrack.OnPlaybackPositionUpdateListener() {
        @Override public void onMarkerReached(AudioTrack track) {
            refillAudioBuffer();
        }

        @Override public void onPeriodicNotification(AudioTrack track) {
            // not used
        }
    };

    void refillAudioBuffer() {
        try {
            long bytesRemaining = audioFileSize - audioFile.getFilePointer();
            int bytesToRead = (int)Math.min(bytesRemaining, audioBufferSize);
            audioFile.readFully(audioBuffer, 0, bytesToRead);
            audioTrack.write(audioBuffer, 0, bytesToRead);
            // each frame is 16 bits, set notification before we run out
            audioFramesWritten += bytesToRead / 2;
            audioTrack.setNotificationMarkerPosition(audioFramesWritten - 2000);
            audioTrack.setPlaybackPositionUpdateListener(audioPeriodicNotificationListener);
        }
        catch(Exception ex) {
            Log.e("WG-Audio", "Error filling audio buffer", ex);
        }
    }

    void startAudioPlayback() {
        double ratioDone = 1.0*videoReader.currentFrameNumber() / videoReader.getVideoProperties().getNumberOfFrames();
        double timestampSeconds = ratioDone * videoReader.getVideoProperties().durationInMilliseconds() / 1000;
        // convert to position in PCM file (16 byte samples so 1 second is (audioSamplingFrequency*2) bytes
        long position = (long)(timestampSeconds * audioSamplingFrequency * 2);
        if (position % 2 == 1) position += 1;
        try {
            if (position < audioFileSize) {
                audioFile.seek(position);
                audioFramesWritten = 0;
                audioTrack.play();
                refillAudioBuffer();
            }
        }
        catch(Exception ex) {
            Log.e("WG-Audio", "Error starting audio playback", ex);
        }
    }

    public void startPlayback() {
        isPlaying = true;
        if (videoReader.isAtEnd()) {
            videoReader.moveToFrameNumber(0);
        }
        playbackStartMillis = System.currentTimeMillis();
        playbackStartFrame = videoReader.currentFrameNumber();

        startAudioPlayback();
        playbackFrame();
        updateButtons();
    }

    Runnable playbackFrameRunnable = new Runnable() {
        @Override public void run() {
            playbackFrame();
        }
    };

    long delayBeforeNextFrame() {
        long targetMillis = 0; // when we want the next frame to be shown
        if (this.frameEndOffsets==null) {
            // Use average FPS.
            targetMillis = (long)(secondsPerFrame * (videoReader.currentFrameNumber()-playbackStartFrame) / 1000);
        }
        else {
            // Start time is end of frame before starting frame.
            long start = (playbackStartFrame==0) ? 0 : this.frameEndOffsets[playbackStartFrame-1];
            int fnum = videoReader.currentFrameNumber();
            long end = (fnum==0) ? 0 : this.frameEndOffsets[fnum-1];
            targetMillis = end - start;
        }
        long actualMillis = System.currentTimeMillis() - playbackStartMillis;
        return targetMillis - actualMillis;
    }

    void playbackFrame() {
        loadNextFrame();

        // Compute delay to next frame, skipping frames if we're too far behind.
        if (isPlaying && !videoReader.isAtEnd()) {
            long delay = delayBeforeNextFrame();
            // skip frames if we're too far behind
            //int skipped = 0;
            while (delay<-100 && !videoReader.isAtEnd()) {
                videoReader.moveToFrameNumber(1+videoReader.currentFrameNumber());
                //skipped++;
                if (!videoReader.isAtEnd()) {
                    delay = delayBeforeNextFrame();
                }
            }
            //statusText.setText(""+delay+":"+skipped);
            if (delay<5) delay = 5;
            if (!videoReader.isAtEnd()) handler.postDelayed(playbackFrameRunnable, delay);
        }
        if (!isPlaying || videoReader.isAtEnd()) {
            stopPlayback();
            updateButtons();
        }
    }

    public void loadNextFrame() {
        if (videoReader.isAtEnd()) return;
        // For now, runs in the main thread.
        // long t1 = System.currentTimeMillis();
        if (frameData==null) frameData = new byte[videoProperties.getWidth() * videoProperties.getHeight()];
        try {
            videoReader.getDataForNextFrame(frameData);
        }
        catch(Exception ex) {
            return;
        }

        drawCurrentFrame();
    }

    public void showNextFrame() {
        loadNextFrame();
        updateButtons();
    }

    public void showPreviousFrame() {
        videoReader.moveToFrameNumber(videoReader.currentFrameNumber() - 2);
        loadNextFrame();
        updateButtons();
    }


    public void stopPlayback() {
        isPlaying = false;
        if (audioTrack!=null) {
            audioTrack.stop();
            audioTrack.flush();
        }
    }

    public void togglePlayback() {
        if (isPlaying) {
            stopPlayback();
        }
        else {
            startPlayback();
        }
        updateButtons();
    }

    public void savePicture() {
        String savedPath = WGUtils.savePicture(imageProcessor.getBitmap(), null);
        if (savedPath!=null) {
            Toast.makeText(getApplicationContext(), "Saved to "+savedPath, Toast.LENGTH_LONG).show();
            AndroidUtils.scanSavedMediaFile(this, savedPath);
        }
        else {
            Toast.makeText(getApplicationContext(), "Error saving image", Toast.LENGTH_LONG).show();
        }
    }

    public void deleteVideo() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Do you want to permanently delete this recording?").setCancelable(true);
        builder.setPositiveButton("Delete", performDeleteDialogAction);
        builder.setNegativeButton("Don't Delete", null);
        builder.show();
    }

    DialogInterface.OnClickListener performDeleteDialogAction = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            boolean success = videoReader.getVideoDirectory().delete();
            if (success) {
                Toast.makeText(getApplicationContext(), "Deleted recording", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(getApplicationContext(), "Unable to delete recording", Toast.LENGTH_LONG).show();
            }
            setResult(DELETE_RESULT);
            finish();
        }
    };

    void launchShareActivity(ExportType exportType, Uri contentUri) {
        // This is not the best way to share a file, see
        // http://developer.android.com/training/secure-file-sharing/share-file.html
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        // apparently only Android 2.3 and later will get a content URI, earlier versions have to treat as generic file (so no YouTube)
        Uri videoUri = (contentUri!=null) ? contentUri : Uri.fromFile(new File(pathForExportedFile(exportType)));
        String mimeType = (contentUri!=null) ? exportType.mimeType : "application/octet-stream";
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, videoUri);
        // EXTRA_SUBJECT is used as the filename, at least when sharing to Google Drive.
        String filename = String.format("%s.%s",
                videoReader.getVideoDirectory().getDirectoryNameForSharing(), exportType.extension);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, filename);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.videoShareActionTitle)));
        Log.i("VPA", "Sharing: " + mimeType + ": " + videoUri);
    }


    DialogInterface.OnCancelListener cancelEncodingDialogAction = new DialogInterface.OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
            if (encodeTask!=null) encodeTask.cancel(true);
            encodeProgressDialog.dismiss();
            allowScreenOff();
        }
    };

    public void encodeVideo(final ExportType exportType) {
        stopPlayback();
        final MediaDirectory vd = this.videoReader.getVideoDirectory();

        encodeProgressDialog = new ProgressDialog(this);
        encodeProgressDialog.setCancelable(true);
        encodeProgressDialog.setOnCancelListener(cancelEncodingDialogAction);
        encodeProgressDialog.setTitle(getString(exportType.exportDialogTitleId));
        encodeProgressDialog.setMessage(""); // If this isn't called here, future setMessage calls have no effect.
        encodeProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        encodeProgressDialog.setMax(100);
        encodeProgressDialog.show();

        encodeTask = getVideoProcessingTask(exportType);
        encodeTask.execute(new ProcessVideoTask.Params(vd, pathForExportedFile(exportType), imageProcessor));
        keepScreenOn();
    }

    void handleEncodingProgressUpdate(ProcessVideoTask.Progress progress, ExportType exportType) {
        String message = getString(
                progress.mediaType == ProcessVideoTask.MediaType.AUDIO ?
                        exportType.exportDialogMessageAudioId :
                            exportType.exportDialogMessageVideoId);
        encodeProgressDialog.setMessage(message);
        encodeProgressDialog.setProgress((int)(100 * progress.fractionDone));
    }

    void handleEncodingFinished(ProcessVideoTask.Result result, ExportType exportType) {
        encodeProgressDialog.dismiss();
        encodeTask = null;
        if (result == ProcessVideoTask.Result.SUCCEEDED) {
            doEncodingSucceeded(exportType);
        }
        else if (result == ProcessVideoTask.Result.FAILED) {
            doEncodingFailed();
        }
        allowScreenOff();
    }

    private AsyncTask<ProcessVideoTask.Params, ProcessVideoTask.Progress, ProcessVideoTask.Result>
    getVideoProcessingTask(final ExportType exportType) {
        // We have to create a custom subclass that calls our UI methods when the task reports
        // progress updates and completion status. It seems like there should be a cleaner way.
        switch (exportType) {
            case WEBM:
                return new CreateWebmAsyncTask() {
                    @Override protected void onProgressUpdate(final ProcessVideoTask.Progress... progress) {
                        handleEncodingProgressUpdate(progress[0], exportType);
                    }
                    @Override protected void onPostExecute(ProcessVideoTask.Result result) {
                        handleEncodingFinished(result, exportType);
                    }
                };
            case ZIP:
                return new CreateVideoZipFileAsyncTask() {
                    @Override protected void onProgressUpdate(final ProcessVideoTask.Progress... progress) {
                        handleEncodingProgressUpdate(progress[0], exportType);
                    }
                    @Override protected void onPostExecute(ProcessVideoTask.Result result) {
                        handleEncodingFinished(result, exportType);
                    }
                };
            default:
                throw new IllegalArgumentException("Unknown export type: " + exportType);
        }
    }

    void doEncodingSucceeded(final ExportType exportType) {
        // YouTube uploads fail with file-based URI, so we need to wait for the media scanner to give us a "content" URI.
        AndroidUtils.scanSavedMediaFile(this, pathForExportedFile(exportType), new AndroidUtils.MediaScannerCallback() {
            @Override
            public void mediaScannerCompleted(String scanPath, final Uri scanUri) {
                handler.post(new Runnable() {
                    @Override public void run() {
                        showEncodingSucceededDialog(exportType, scanUri);
                    }
                });
            }
        });
    }

    void doEncodingFailed() {
        Toast.makeText(getApplicationContext(), "Encoding failed", Toast.LENGTH_SHORT).show();
    }

    void showEncodingSucceededDialog(final ExportType exportType, final Uri scanUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Successfully exported video to " + pathForExportedFile(exportType)).setCancelable(true);
        builder.setPositiveButton("Share Video", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                launchShareActivity(exportType, scanUri);
            }
        });
        builder.setNegativeButton("Continue", null);
        builder.show();
    }

    // Called from button action.
    public void encodeVideoWithReplaceCheck() {
        final ExportType exportType = exportTypeFromPreferences();
        if (exportedFileExists(exportType)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(exportType.exportConfirmReplaceMessageId)).setCancelable(true);
            builder.setPositiveButton("Replace", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    encodeVideo(exportType);
                }
            });
            builder.setNegativeButton("Don't Replace", null);
            builder.show();
        }
        else {
            encodeVideo(exportType);
        }
    }

    // Called from button action.
    public void shareVideo() {
        final ExportType exportType = exportTypeFromPreferences();
        if (exportedFileExists(exportType)) {
            // We have to get the "content" URI in order for Youtube uploading to work, file URIs cause a weird exception.
            AndroidUtils.scanSavedMediaFile(this, pathForExportedFile(exportType), new AndroidUtils.MediaScannerCallback() {
                @Override public void mediaScannerCompleted(String scanPath, final Uri scanUri) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            launchShareActivity(exportType, scanUri);
                        }
                    });
                }
            });
        }
        else {
            encodeVideo(exportType);
        }
    }

    private ExportType exportTypeFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String val = prefs.getString(getString(R.string.videoExportTypePrefsKey), "WEBM");
        try {
            return ExportType.valueOf(val);
        }
        catch (IllegalArgumentException ex) {
            Log.e("WG-Video", "Unknown export type: " + val, ex);
            return ExportType.WEBM;
        }
    }
}

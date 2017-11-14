package com.dozingcatsoftware.eyeball.video;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.dozingcatsoftware.eyeball.CameraImageProcessor;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Creates a ZIP archive containing a PNG for each frame in a recorded video.
 */
public class CreateVideoZipFileAsyncTask extends
        AsyncTask<ProcessVideoTask.Params, ProcessVideoTask.Progress, ProcessVideoTask.Result> {

    // Passed to execute in the ProcessVideoTask.Params argument.
    private MediaDirectory videoDirectory;
    private String outputPath;
    private CameraImageProcessor imageProcessor;

    @Override protected ProcessVideoTask.Result doInBackground(ProcessVideoTask.Params... params) {
        this.videoDirectory = params[0].videoDirectory;
        this.outputPath = params[0].outputPath;
        this.imageProcessor = params[0].imageProcessor;
        ProcessVideoTask.Result result = ProcessVideoTask.Result.SUCCEEDED;

        String tempZipFile = outputPath + ".tmp.zip";
        ZipOutputStream out = null;
        try {
            out = new ZipOutputStream(new FileOutputStream(tempZipFile));
            VideoReader videoReader = new VideoReader(videoDirectory.getPath());
            MediaProperties videoProperties = videoReader.getVideoProperties();
            int numFrames = videoDirectory.getVideoProperties().getNumberOfFrames();
            int width = videoProperties.getWidth();
            int height = videoProperties.getHeight();
            byte[] frameData = new byte[width * height];

            for (int i=1; i<=numFrames; i++) {
                String filename = String.format("/%s/%05d.png",
                        videoDirectory.getDirectoryNameForSharing(), i);
                out.putNextEntry(new ZipEntry(filename));

                videoReader.getDataForNextFrame(frameData);
                imageProcessor.processCameraImage(frameData, width, height);
                // 0 is "quality" argument, ignored since PNG is lossless.
                imageProcessor.getBitmap().compress(Bitmap.CompressFormat.PNG, 0, out);

                out.closeEntry();
                if (this.isCancelled()) {
                    throw new InterruptedException();
                }
                double fractionDone = 1.0*i / numFrames;
                publishProgress(new ProcessVideoTask.Progress(
                        ProcessVideoTask.MediaType.VIDEO, fractionDone));
            }

            out.close();
            (new File(tempZipFile)).renameTo(new File(outputPath));
        }
        catch (IOException ex) {
            Log.e("WG_ZIP", "Error creating zip file from video", ex);
            result = ProcessVideoTask.Result.FAILED;
        }
        catch(InterruptedException iex) {
            result = ProcessVideoTask.Result.CANCELLED;
        }
        finally {
            try {
                if (out!=null) out.close();
            }
            catch (IOException ignored) {}

            File f = new File(tempZipFile);
            if (f.exists()) f.delete();
        }
        return result;
    }
}

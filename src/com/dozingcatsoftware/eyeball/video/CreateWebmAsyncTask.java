package com.dozingcatsoftware.eyeball.video;

import java.io.File;

import com.dozingcatsoftware.eyeball.CameraImageProcessor;

import android.os.AsyncTask;

public class CreateWebmAsyncTask extends
        AsyncTask<ProcessVideoTask.Params, ProcessVideoTask.Progress, ProcessVideoTask.Result> {

	private WebMEncoder videoEncoder; // to create WebM with video track only

	// Passed to execute in the ProcessVideoTask.Params argument.
	private MediaDirectory videoDirectory;
	private String outputPath;
	private CameraImageProcessor imageProcessor;

	String videoWebmPath;

	@SuppressWarnings("serial")
	static class ExecutionFailedException extends RuntimeException {}

	@Override protected ProcessVideoTask.Result doInBackground(ProcessVideoTask.Params... params) {
		this.videoDirectory = params[0].videoDirectory;
		this.outputPath = params[0].outputPath;
		this.imageProcessor = params[0].imageProcessor;

		ProcessVideoTask.Result result = ProcessVideoTask.Result.SUCCEEDED;

		videoWebmPath = outputPath.endsWith(".webm") ?
				(outputPath.substring(0, outputPath.length()-5) + "-video.webm") :
				(outputPath + "-video.webm");

		try {
			// Create WebM file with video track.
			int numFrames = videoDirectory.getVideoProperties().getNumberOfFrames();
			videoEncoder = new WebMEncoder(this.videoDirectory, videoWebmPath, this.imageProcessor);
			videoEncoder.startEncoding();
			while (!videoEncoder.allFramesFinished()) {
				if (!videoEncoder.encodeNextFrame()) {
					throw new ExecutionFailedException();
				}

				if (this.isCancelled()) {
					throw new InterruptedException();
				}
				double fractionDone = 1.0*videoEncoder.currentFrameNumber() / numFrames;
				publishProgress(new ProcessVideoTask.Progress(ProcessVideoTask.MediaType.VIDEO, fractionDone));
			}
			videoEncoder.finishEncoding();

			// Add audio track by encoding PCM file to Vorbis and inserting into a new WebM file.
			String audioPath = videoDirectory.audioFilePath();
			File audioFile = new File(audioPath);
			if (audioFile.isFile()) {
				final long audioFileSize = (new File(audioPath)).length();

				CombineAudioVideo.EncoderDelegate encoderDelegate = new CombineAudioVideo.EncoderDelegate() {
					@Override
                    public void receivedOggPage(long bytesRead) {
					    double fractionDone = 1.0*bytesRead / audioFileSize;
						publishProgress(new ProcessVideoTask.Progress(ProcessVideoTask.MediaType.AUDIO, fractionDone));
					}
				};
				CombineAudioVideo.insertAudioIntoWebm(videoDirectory.audioFilePath(), videoWebmPath, outputPath, encoderDelegate);
				// Remove temporary video-only WebM file.
				(new File(videoWebmPath)).delete();
			}
			else {
				// No audio, rename video WebM file.
				(new File(videoWebmPath)).renameTo(new File(outputPath));
			}
		}
		catch(InterruptedException iex) {
			result = ProcessVideoTask.Result.CANCELLED;
		}
		catch(Exception ex) {
			result = ProcessVideoTask.Result.FAILED;
		}

		if (result != ProcessVideoTask.Result.SUCCEEDED) {
			cleanupAfterError();
		}

		return result;
	}

	void cleanupAfterError() {
		File f = new File(videoWebmPath);
		if (f.exists()) f.delete();
		f = new File(outputPath);
		if (f.exists()) f.delete();
	}

}

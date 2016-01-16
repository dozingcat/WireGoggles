package com.dozingcatsoftware.eyeball.video;

import java.io.File;

import com.dozingcatsoftware.ebml.EBMLFileWriter;
import com.dozingcatsoftware.ebml.EBMLReader;
import com.dozingcatsoftware.eyeball.CameraImageProcessor;

import android.os.AsyncTask;

public class CreateWebmAsyncTask extends AsyncTask<Object, Integer, CreateWebmAsyncTask.Result> {
	
	public enum Result {
		SUCCEEDED, FAILED, CANCELLED
	}
	
	// parameters passed to execute() (and thus doInBackground)
	WebMEncoder videoEncoder; // to create WebM with video track only	
	VorbisEncoder vorbisEncoder; // to encode PCM audio data to Vorbis
	EBMLReader videoEBMLReader;  // to read from WebM file created by videoEncoder
	EBMLFileWriter finalEBMLWriter; // to write WebM file with video and audio tracks
	
	// passed to execute by caller
	MediaDirectory videoDirectory;
	String outputPath;
	CameraImageProcessor imageProcessor;
	
	String videoWebmPath;
	
	@SuppressWarnings("serial")
	static class ExecutionFailedException extends RuntimeException {}

	@Override
	protected Result doInBackground(Object... params) {
		this.videoDirectory = (MediaDirectory)params[0];
		this.outputPath = (String)params[1];
		this.imageProcessor = (CameraImageProcessor)params[2];
		
		Result result = Result.SUCCEEDED;

		videoWebmPath = outputPath.endsWith(".webm") ? 
				(outputPath.substring(0, outputPath.length()-5) + "-video.webm") : (outputPath + "-video.webm");
		//videoWebmPath = this.outputPath;

		try {
			// create WebM file with video track
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
				publishProgress(100 * videoEncoder.currentFrameNumber() / numFrames);
			}
			videoEncoder.finishEncoding();
			
			// add audio track by encoding PCM file to Vorbis and inserting into a new WebM file
			String audioPath = videoDirectory.audioFilePath();
			File audioFile = new File(audioPath);
			if (audioFile.isFile()) {
				final long audioFileSize = (new File(audioPath)).length();
				
				CombineAudioVideo.EncoderDelegate encoderDelegate = new CombineAudioVideo.EncoderDelegate() {
					public void receivedOggPage(long bytesRead) {
						// update progress with 2 arguments to indicate audio
						publishProgress(0, (int)(100 * bytesRead / audioFileSize));
					}
				};
				CombineAudioVideo.insertAudioIntoWebm(videoDirectory.audioFilePath(), videoWebmPath, outputPath, encoderDelegate);
				// remove temporary video-only WebM file
				(new File(videoWebmPath)).delete();
			}
			else {
				// no audio, rename video WebM file
				(new File(videoWebmPath)).renameTo(new File(outputPath));
			}
		}
		catch(InterruptedException iex) {
			result = Result.CANCELLED;
		}
		catch(Exception ex) {
			result = Result.FAILED;
		}
		
		if (result!=Result.SUCCEEDED) {
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

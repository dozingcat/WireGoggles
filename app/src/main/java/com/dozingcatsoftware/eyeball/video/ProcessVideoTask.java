package com.dozingcatsoftware.eyeball.video;

import com.dozingcatsoftware.eyeball.CameraImageProcessor;

/** Defines parameters, progress, and results for async video processing tasks. */
public class ProcessVideoTask {

    private ProcessVideoTask() {}

    public static class Params {
        public final MediaDirectory videoDirectory;
        public final String outputPath;
        public final CameraImageProcessor imageProcessor;

        public Params(MediaDirectory videoDirectory, String outputPath, CameraImageProcessor imageProcessor) {
            this.videoDirectory = videoDirectory;
            this.outputPath = outputPath;
            this.imageProcessor = imageProcessor;
        }
    }

    public static class Progress {
        public final MediaType mediaType;
        public final double fractionDone;

        public Progress(MediaType mediaType, double fractionDone) {
            this.mediaType = mediaType;
            this.fractionDone = fractionDone;
        }
    }

    public static enum MediaType {
        VIDEO, AUDIO
    };

    public static enum Result {
        SUCCEEDED, FAILED, CANCELLED
    }
}

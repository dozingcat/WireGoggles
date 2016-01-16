// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.util;

/**
 * A variant of a producer-consumer queue where only a single data object is queued behind
 * the data currently being processed. If new data arrives while previous data is still
 * waiting to be processed, we just drop that previous data. Incoming data is added by
 * calling {@code queueData}, and is processed on a secondary thread.
 */
public class SingleItemProcessingQueue<T> {

    private static final boolean DEBUG = false;

    /** Interface defining a method to process incoming data. */
    public static interface Processor<T> {
        /**
         * Called on a secondary thread when data is available to process.
         */
        void processData(T data);
    }

    Thread thread;
    boolean running;
    boolean paused;
    boolean waiting;
    T nextData;

    /**
     * Starts processing data supplied via {@code queueData}, using the specified processor
     * and optionally setting the processing thread name.
     */
    public void start(final Processor<T> processor, String threadName) {
        if (thread==null) {
            thread = new Thread() {
                @Override
                public void run() {
                    threadEntry(processor);
                }
            };
            running = true;
            thread.start();
        }
        if (threadName!=null) thread.setName(threadName);
    }

    /** Calls the two-argument start method with no thread name. */
    public void start(Processor<T> processor) {
        start(processor, null);
    }

    /** Stops the processing thread. */
    public void stop() {
        if (thread!=null) {
            running = false;
            synchronized(this) {
                this.notify();
            }
            try {
                thread.join();
            }
            catch(InterruptedException ie) {}
            thread = null;
        }
    }

    /**
     * Temporarily halts processing input data. The processing thread is not stopped,
     * but no incoming data will be processed until {@code unpause} is called.
     */
    public void pause() {
        paused = true;
    }

    /** Resumes processing data after a call to {@code pause}. */
    public void unpause() {
        paused = false;
    }

    /**
     * Adds data to be processed by the processing thread. Returns false if there is a previous
     * data object queued.
     */
    public boolean queueData(T data) {
        // set nextData and notify processor thread if it's waiting for it
        synchronized(this) {
            if (DEBUG) android.util.Log.i("SingleItemProcessingQueue", "setting nextData");
            if (this.nextData != null) {
                return false;
            }
            this.nextData = data;
            if (waiting) {
                if (DEBUG) android.util.Log.i("SingleItemProcessingQueue", "calling notify()");
                this.notify();
            }
            return true;
        }
    }

    void threadEntry(Processor<T> processor) {
        try {
            while(running) {
                T currentData = null;
                synchronized(this) {
                    // wait until nextData is set in queueData
                    while(this.nextData==null) {
                        if (DEBUG) android.util.Log.e("SingleItemProcessingQueue", "waiting for data");
                        waiting = true;
                        try {this.wait(250);}
                        catch(InterruptedException ie) {}
                        if (!running) return;
                    }
                    currentData = this.nextData;
                    this.nextData = null;
                    waiting = false;
                }
                if (!paused) {
                    if (DEBUG) android.util.Log.i("SingleItemProcessingQueue", "calling processData()");
                    processor.processData(currentData);
                    if (DEBUG) android.util.Log.i("SingleItemProcessingQueue", "processData() done");
                }
            }
        }
        catch(Throwable ex) {
            android.util.Log.e("SingleItemProcessingQueue", "Exception in SingleItemProcessingQueue", ex);
        }
    }

}

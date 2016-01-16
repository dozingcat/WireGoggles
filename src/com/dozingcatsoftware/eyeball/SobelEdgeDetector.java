package com.dozingcatsoftware.eyeball;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SobelEdgeDetector {

    private static boolean DEBUG = false;

	static boolean hasNativeCode = false;

	final static int MID_WEIGHT = 2;
	final static float[] BLUR7_KERNEL = new float[] {0.006f, 0.061f, 0.242f, 0.383f, 0.242f, 0.061f, 0.006f};

	final static int[] BLUR7_KERNEL_INT = new int[] {6, 61, 242, 383, 242, 61, 6};
	final static int BLUR7_KERNEL_INT_SUM = 1001;

	int[] pixelInts; // worth changing to short?

	ExecutorService threadPool = null; //Executors.newFixedThreadPool(2);
	List<Worker> threadPoolWorkers = null;

	static {
		try {
			System.loadLibrary("wiregoggles");
			hasNativeCode = true;
		}
		catch(Throwable ex) {}
	}

	class Worker implements Callable<Long> {

		int[] pixels;
		int width, height;
		int[] colorMap;
		int noiseThreshold;
		int[] output;

		int totalWorkers;
		int workerNumber;

		public Worker(int totalWorkers, int workerNumber) {
			this.totalWorkers = totalWorkers;
			this.workerNumber = workerNumber;
		}

		public void setValues(int[] pixels, int width, int height, int[] colorMap, int noiseThreshold, int[] output) {
			this.pixels = pixels;
			this.width = width;
			this.height = height;
			this.colorMap = colorMap;
			this.noiseThreshold = noiseThreshold;
			this.output = output;
		}

		@Override public Long call() {
		    long t1 = System.nanoTime();
			try {
				int rowStart = height * workerNumber / totalWorkers;
				int rowEnd = height * (workerNumber+1) / totalWorkers;
				detectEdgesSlice(pixels, width, height, colorMap, noiseThreshold, output, rowStart, rowEnd);
			}
			finally {
				this.pixels = null;
				this.colorMap = null;
				this.output = null;
			}
			return System.nanoTime() - t1;
		}
	}

	public void initThreadPool(int ncpus) {
		if (threadPool!=null) {
			destroyThreadPool();
		}
		if (ncpus<=0) ncpus = Runtime.getRuntime().availableProcessors();
		if (ncpus>1) {
	        if (DEBUG) android.util.Log.i("TIMING", "Creating thread pool: " + ncpus);
			threadPool = Executors.newFixedThreadPool(ncpus);
			threadPoolWorkers = new ArrayList<Worker>();
			for(int i=0; i<ncpus; i++) {
				threadPoolWorkers.add(new Worker(ncpus, i));
			}
		}
	}

	public void destroyThreadPool() {
	    if (DEBUG) android.util.Log.i("TIMING", "Destroying thread pool");
		if (threadPool!=null) {
			threadPool.shutdown();
			threadPool = null;
		}
	}

	public void computeEdgePixels(byte[] inputBrightness, int inputWidth, int inputHeight, int sample,
			int outputWidth, int outputHeight, int[] colorMap, int noiseThreshold, int[] outputPixels) {
		// it's ok for pixelInts to be larger than width*height
		if (pixelInts==null || pixelInts.length < outputWidth*outputHeight) {
			pixelInts = new int[outputWidth*outputHeight];
		}

		long t1 = System.currentTimeMillis();
		javaBytesToInts(inputBrightness, inputWidth, inputHeight, sample, pixelInts);
		if (DEBUG) android.util.Log.i("TIMING", "javaBytesToInts: " + (System.currentTimeMillis()-t1));
        t1 = System.currentTimeMillis();
		detectEdges(pixelInts, outputWidth, outputHeight, colorMap, noiseThreshold, outputPixels);
		if (DEBUG) android.util.Log.i("TIMING", "detectEdges: " + (System.currentTimeMillis()-t1));
	}

	/** Fills output array with ints between 0 and 255, where 0 is no edge and 255 is a strong edge.
	 */
	public void detectEdges(final int[] pixels, final int width, final int height,
			final int[] colorMap, final int noiseThreshold, final int[] output) {

		if (threadPool==null) {
		    if (DEBUG) android.util.Log.i("TIMING", "Thread pool is null!");
		    long t1 = System.currentTimeMillis();
			detectEdgesSlice(pixels, width, height, colorMap, noiseThreshold, output, 0, height);
            long elapsed = System.currentTimeMillis() - t1;
            if (DEBUG) android.util.Log.i("TIMING", "Total time: " + elapsed);
		}
		else {
            long t1 = System.currentTimeMillis();
			for(int i=threadPoolWorkers.size()-1; i>=0; i--) {
				threadPoolWorkers.get(i).setValues(pixels, width, height, colorMap, noiseThreshold, output);
			}
			try {
				List<Future<Long>> results = threadPool.invokeAll(threadPoolWorkers);
				if (DEBUG) {
	                StringBuilder sb = new StringBuilder();
	                for (Future<Long> f : results) {
	                    sb.append((long)(f.get()/1e6) + " ");
	                }
	                long elapsed = System.currentTimeMillis() - t1;
	                android.util.Log.i("TIMING", "Total time: " + elapsed + ", Worker times: " + sb);
				}
			}
			catch(ExecutionException ignored) {}
			catch(InterruptedException ignored) {}
		}
	}

	// Fills output array with edge strength values for a given range of rows
	void detectEdgesSlice(int[] pixels, int width, int height, int[] colorMap, int noiseThreshold, int[] output,
			int rowStart, int rowEnd) {
		if (hasNativeCode) {
			detectEdgesNative(pixels, width, height, colorMap, noiseThreshold, output, rowStart, rowEnd);
		}
		else {
			detectEdgesJava(pixels, width, height, colorMap, noiseThreshold, output, rowStart, rowEnd);
		}
	}

	// Java bytes are signed, so convert to unsigned by adding 256 to negative values
	public void javaBytesToInts(byte[] inBytes, int width, int height, int sample, int[] outInts) {
		if (hasNativeCode) {
			try {
				javaBytesToIntsNative(inBytes, width, height, sample, outInts);
				return;
			}
			catch(Throwable ex) {
				ex.printStackTrace();
			}
		}
		else {
			javaBytesToIntsJava(inBytes, width, height, sample, outInts);
		}
	}

	/** Fills output array with the result of indexing colorMap by the corresponding value in input.
	 */
	public void applyColorMap(int[] input, int length, int[] colorMap, int[] output) {
		if (hasNativeCode) {
			try {
				applyColorMapNative(input, length, colorMap, output);
				return;
			}
			catch(Throwable ex) {
				ex.printStackTrace();
			}
		}
		else {
			applyColorMapJava(input, length, colorMap, output);
		}
	}

	public void gaussianBlur(byte[] pixels, int width, int height, int sample) {
		if (hasNativeCode) {
			try {
				//gaussianBlurNative(pixels, width, height, sample, BLUR7_KERNEL, BLUR7_KERNEL.length);
				gaussianBlurIntNative(pixels, width, height, sample, BLUR7_KERNEL_INT, BLUR7_KERNEL.length, BLUR7_KERNEL_INT_SUM);
				return;
			}
			catch(Throwable ex) {
			}
		}
		//gaussianBlurJava(pixels, width, height, sample, BLUR7_KERNEL);
		gaussianBlurIntJava(pixels, width, height, sample, BLUR7_KERNEL_INT, BLUR7_KERNEL_INT_SUM);
	}

	native void detectEdgesNative(int[] pixels, int width, int height, int[] colorMap, int noiseThreshold, int[] output,
			int startRow, int endRow);

	// adapted from http://www.pages.drexel.edu/~weg22/edge.html
    void detectEdgesJava(int[] pixels, int width, int height, int[] colorMap, int noiseThreshold, int[] output,
    		int startRow, int endRow) {
		int[] result = output;

		// Sobel convolution matrices, not needed due to unrolling below
		// int[][] GX = new int[][] {new int[] {-1, 0, 1}, new int[] {-MID_WEIGHT, 0, MID_WEIGHT}, new int[] {-1, 0, 1}};
		// int[][] GY = new int[][] {new int[] {1, MID_WEIGHT, 1}, new int[] {0, 0, 0}, new int[] {-1, -MID_WEIGHT, -1}};

		int p = startRow * width;
		for(int y=startRow; y<endRow; y++) {
			//if (System.currentTimeMillis()>endTime) return;
			for(int x=0; x<width; x++) {
				if (x==0 || y==0 || x==width-1 || y==height-1) {
					result[p] = colorMap[0];
				}
				else {
					int xsum=0, ysum=0;
					/* // standard Sobel algorithm, does redundant multiplications by 0/1/-1 and lots of array indexing
					for(int i=-1; i<=1; i++) {
						for(int j=-1; j<=1; j++) {
							int poff = p + i + j*width;
							xsum += GX[i+1][j+1] * pixels[poff];
							ysum += GY[i+1][j+1] * pixels[poff];
						}
					}
					*/
					// unrolled version, roughly 3x performance improvement on Nexus One
					int poff = p - 1 - width; // -1,-1
					xsum -= pixels[poff];
					ysum += pixels[poff];

					poff++; // -1, 0
					ysum += MID_WEIGHT*pixels[poff];

					poff++; // -1, +1
					xsum += pixels[poff];
					ysum += pixels[poff];

					poff += width; // 0, +1
					xsum += MID_WEIGHT*pixels[poff];

					poff -= 2; // 0, -1
					xsum -= MID_WEIGHT*pixels[poff];

					poff += width; // +1, -1
					xsum -= pixels[poff];
					ysum -= pixels[poff];

					poff++; // +1, 0
					ysum -= MID_WEIGHT*pixels[poff];

					poff++; // +1, +1
					xsum += pixels[poff];
					ysum -= pixels[poff];


					int total = (xsum < 0 ? -xsum : xsum) + (ysum < 0 ? -ysum : ysum);
					if (total<noiseThreshold) total = 0;
					result[p] = colorMap[(total>255) ? 255 : total];
				}
				p++;
			}
		}
	}

    native void javaBytesToIntsNative(byte[] inBytes, int width, int height, int sample, int[] outInts);

    void javaBytesToIntsJava(byte[] inBytes, int width, int height, int sample, int[] outInts) {
		int p = 0;
		int previewIndex = 0;
		for(int y=0; y<height; y+=sample) {
			previewIndex = width * y;
			for(int x=0; x<width; x+=sample) {
				// taking bottom 8 bits converts signed byte to "unsigned" int
				outInts[p++] = 0xff & inBytes[previewIndex];
				previewIndex += sample;
			}
		}
    }

    native void applyColorMapNative(int[] input, int length, int[] colorMap, int[] output);

    void applyColorMapJava(int[] input, int length, int[] colorMap, int[] output) {
    	for(int i=0; i<length; i++) {
    		output[i] = colorMap[input[i]];
    	}
    }

    native void reverseIntArrayNative(int[] arr, int start, int end);

    void reverseIntArray(int[] arr, int start, int end) {
        long t1 = System.currentTimeMillis();
        if (hasNativeCode) {
            reverseIntArrayNative(arr, start, end);
        }
        else {
            for (end-=1; start < end; start++, end--) {
                int tmp = arr[start];
                arr[start] = arr[end];
                arr[end] = tmp;
            }
        }
        if (DEBUG) android.util.Log.i("TIMING", "reverseIntArray: " + (System.currentTimeMillis()-t1));
    }

    native void gaussianBlurNative(byte[] pixels, int width, int height, int sample, float[] kernel, int ksize);

    void gaussianBlurJava(byte[] pixels, int width, int height, int sample, float[] kernel) {
		int border = kernel.length / 2;
		byte[] origPixels = new byte[Math.max(width, height)];

		// blur horizontally
		for(int row=border; row<height-border; row++) {
			int offset = row * width;
			for(int col=0; col<width; col++) {
				origPixels[col] = pixels[offset+col];
			}
			for(int col=border; col<width-border; col++) {
				float sum = 0;
				for(int k=0; k<kernel.length; k++) {
					sum += kernel[k] * (origPixels[col-border+k] & 0xFF);
				}
				pixels[offset+col] = (byte)sum;
			}
		}

		// blur vertically
		for(int col=border; col<width-border; col++) {
			for(int row=0; row<height; row++) {
				origPixels[row] = pixels[col + row*width];
			}
			for(int row=border; row<height-border; row++) {
				float sum = 0;
				for(int k=0; k<kernel.length; k++) {
					sum += kernel[k] * (origPixels[row-border+k] & 0xFF);
				}
				pixels[col + row*width] = (byte)sum;
			}
		}
    }

    native void gaussianBlurIntNative(byte[] pixels, int width, int height, int sample, int[] kernel, int kernelSize, int kernelSum);

    void gaussianBlurIntJava(byte[] pixels, int width, int height, int sample, int[] kernel, int kernelSum) {
		int border = kernel.length / 2;
		byte[] origPixels = new byte[Math.max(width, height)];

		// blur horizontally
		for(int row=border; row<height-border; row++) {
			int offset = row * width;
			for(int col=0; col<width; col++) {
				origPixels[col] = pixels[offset+col];
			}
			for(int col=border; col<width-border; col++) {
				int sum = 0;
				for(int k=0; k<kernel.length; k++) {
					sum += kernel[k] * (origPixels[col-border+k] & 0xFF);
				}
				pixels[offset+col] = (byte)(sum / kernelSum);
			}
		}

		// blur vertically
		for(int col=border; col<width-border; col++) {
			for(int row=0; row<height; row++) {
				origPixels[row] = pixels[col + row*width];
			}
			for(int row=border; row<height-border; row++) {
				int sum = 0;
				for(int k=0; k<kernel.length; k++) {
					sum += kernel[k] * (origPixels[row-border+k] & 0xFF);
				}
				pixels[col + row*width] = (byte)(sum / kernelSum);;
			}
		}
    }
}

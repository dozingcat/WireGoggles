#include <jni.h>
#include <stdlib.h>

// factor for multiplying difference between middle pixels, 2 is standard, higher values make brighter images but with more noise
#define MID_WEIGHT 2

void Java_com_dozingcatsoftware_eyeball_SobelEdgeDetector_detectEdgesNative(JNIEnv* env, jobject thiz, 
        jintArray jpixels, jint width, jint height, jintArray jcolorMap, jint noiseThreshold, jintArray jresult,
        jint startRow, jint endRow) {
    jint *pixels = (*env)->GetIntArrayElements(env, jpixels, 0);
    jint *colorMap = (*env)->GetIntArrayElements(env, jcolorMap, 0);
    jint *result = (*env)->GetIntArrayElements(env, jresult, 0);
    
    int x,y;
    int p = startRow * width;
    for(y=startRow; y<endRow; y++) {
    	if (y==0 || y==height-1) {
    		for(x=0; x<width; x++) {
    			result[p++] = colorMap[0];
    		}
    		continue;
    	}
    	int wm1 = width - 1;

    	result[p++] = colorMap[0];
        for(x=1; x<wm1; x++) {
        	// unrolled Sobel algorithm; see SobelEdgeDetector.java for details
        	int xsum=0, ysum=0;

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

        	//int total = pixels[p];
        	if (total<noiseThreshold) total = 0;
        	result[p++] = colorMap[(total>255) ? 255 : total];
        }
        result[p++] = colorMap[0];
    }
    
    (*env)->ReleaseIntArrayElements(env, jpixels, pixels, 0);
    (*env)->ReleaseIntArrayElements(env, jcolorMap, colorMap, 0);
    (*env)->ReleaseIntArrayElements(env, jresult, result, 0);
}


void Java_com_dozingcatsoftware_eyeball_SobelEdgeDetector_javaBytesToIntsNative(JNIEnv* env, jobject thiz, jbyteArray jinBytes, jint width, jint height, jint sample, jintArray joutInts) {
    jbyte *inBytes = (*env)->GetByteArrayElements(env, jinBytes, 0);
    jint *outInts = (*env)->GetIntArrayElements(env, joutInts, 0);
   
    int x,y;
    int inIndex, outIndex=0;
    
    for(y=0; y<height; y+=sample) {
        inIndex = y * width;
        for(x=0; x<width; x+=sample) {
            // taking bottom 8 bits converts signed byte to "unsigned" int
            outInts[outIndex++] = 0xff & inBytes[inIndex];
            inIndex += sample;
        }
    }
            
    (*env)->ReleaseByteArrayElements(env, jinBytes, inBytes, 0);
    (*env)->ReleaseIntArrayElements(env, joutInts, outInts, 0);
}


void Java_com_dozingcatsoftware_eyeball_SobelEdgeDetector_reverseIntArrayNative(JNIEnv* env, jobject thiz,
		jintArray jvalues, jint start, jint end) {
    jint *values = (*env)->GetIntArrayElements(env, jvalues, 0);

    int front = start;
    int back = end - 1;
    while (front < back) {
        jint tmp = values[front];
        values[front] = values[back];
        values[back] = tmp;
        front++;
        back--;
    }

    (*env)->ReleaseIntArrayElements(env, jvalues, values, 0);
}


void Java_com_dozingcatsoftware_eyeball_SobelEdgeDetector_applyColorMapNative(JNIEnv* env, jobject thiz, jintArray jinput, jint length, jintArray jcolorMap, jintArray joutput) {
    jint *input = (*env)->GetIntArrayElements(env, jinput, 0);
    jint *colorMap = (*env)->GetIntArrayElements(env, jcolorMap, 0);
    jint *output = (*env)->GetIntArrayElements(env, joutput, 0);
    
    int i;
    for(i=0; i<length; i++) {
        output[i] = colorMap[input[i]];
    }
    
    (*env)->ReleaseIntArrayElements(env, jinput, input, 0);
    (*env)->ReleaseIntArrayElements(env, jcolorMap, colorMap, 0);
    (*env)->ReleaseIntArrayElements(env, joutput, output, 0);
}

void Java_com_dozingcatsoftware_eyeball_SobelEdgeDetector_gaussianBlurNative(JNIEnv *env, jobject thiz, jbyteArray jpixels, jint width, jint height, jint sample, jfloatArray jkernel, jint kernelSize) {
	int size = (width>height) ? width : height;
	int *origPixels = malloc(size*sizeof(int));
	jbyte *pixels = (*env)->GetByteArrayElements(env, jpixels, 0);
	jfloat *kernel = (*env)->GetFloatArrayElements(env, jkernel, 0);
	
	int border = kernelSize / 2;
	int row, col, k;
	// blur horizontally
	for(row=border; row<height-border; row++) {
		int offset = row * width;
		for(col=0; col<width; col++) {
			origPixels[col] = pixels[offset+col] & 0xFF;
		}
		for(col=border; col<width-border; col++) {
			float sum = 0;
			for(k=0; k<kernelSize; k++) {
				sum += kernel[k] * origPixels[col-border+k];
			}
			pixels[offset+col] = (jbyte)sum;
		}
	}
	
	// blur vertically
	int pixelIndex;
	for(col=border; col<width-border; col++) {
		for(row=0, pixelIndex=col; row<height; row++, pixelIndex+=width) {
			origPixels[row] = pixels[pixelIndex] & 0xFF;
		}
		for(row=border, pixelIndex=col+border*width; row<height-border; row++, pixelIndex+=width) {
			float sum = 0;
			for(k=0; k<kernelSize; k++) {
				sum += kernel[k] * origPixels[row-border+k];
			}
			pixels[pixelIndex] = (jbyte)sum;
		}
	}
	
	
	(*env)->ReleaseByteArrayElements(env, jpixels, pixels, 0);
	(*env)->ReleaseFloatArrayElements(env, jkernel, kernel, 0);
	free(origPixels);
}


void Java_com_dozingcatsoftware_eyeball_SobelEdgeDetector_gaussianBlurIntNative(JNIEnv *env, jobject thiz, jbyteArray jpixels, jint width, jint height, jint sample, jintArray jkernel, jint kernelSize, jint kernelSum) {
	int size = (width>height) ? width : height;
	int *origPixels = malloc(size*sizeof(int));
	jbyte *pixels = (*env)->GetByteArrayElements(env, jpixels, 0);
	jint *kernel = (*env)->GetIntArrayElements(env, jkernel, 0);
	
	int border = kernelSize / 2;
	int row, col, k;
	// blur horizontally
	for(row=border; row<height-border; row++) {
		int offset = row * width;
		for(col=0; col<width; col++) {
			origPixels[col] = pixels[offset+col] & 0xFF;
		}
		for(col=border; col<width-border; col++) {
			int sum = 0;
			for(k=0; k<kernelSize; k++) {
				sum += kernel[k] * origPixels[col-border+k];
			}
			pixels[offset+col] = (jbyte)(sum / kernelSum);
		}
	}
	
	// blur vertically
	int pixelIndex;
	for(col=border; col<width-border; col++) {
		for(row=0, pixelIndex=col; row<height; row++, pixelIndex+=width) {
			origPixels[row] = pixels[pixelIndex] & 0xFF;
		}
		for(row=border, pixelIndex=col+border*width; row<height-border; row++, pixelIndex+=width) {
			int sum = 0;
			for(k=0; k<kernelSize; k++) {
				sum += kernel[k] * origPixels[row-border+k];
			}
			pixels[pixelIndex] = (jbyte)(sum / kernelSum);
		}
	}
	
	
	(*env)->ReleaseByteArrayElements(env, jpixels, pixels, 0);
	(*env)->ReleaseIntArrayElements(env, jkernel, kernel, 0);
	free(origPixels);
}

package com.dozingcatsoftware.eyeball;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

public interface ColorScheme {
	
	public int[] getColorMap();	
	
	public Bitmap getBackgroundBitmap();
	
	public boolean useBackground();
	
	public void drawBackground(Canvas canvas, Rect dstRect);
	
	public void drawForeground(Canvas canvas, Rect dstRect);
	
	static abstract class AbstractColorScheme implements ColorScheme {
		public static int[] createAlphaMap(int br, int bg, int bb) {
			int[] alphaMap = new int[256];
			// bright=transparent, dark=opaque
			for(int i=0; i<256; i++) {
				alphaMap[i] = ((255-i)<<24) + (br<<16) + (bg<<8) + bb;
			}
			return alphaMap;
		}		
		
		public Bitmap getBackgroundBitmap() {
			return null;
		}
		
		public boolean useBackground() {
			return (this.getBackgroundBitmap()!=null);
		}
		
		public void drawBackground(Canvas canvas, Rect dstRect) {
			canvas.drawBitmap(this.getBackgroundBitmap(), null, dstRect, null);
		}
		
		public void drawForeground(Canvas canvas, Rect dstRect) {
			
		}
	}
	
	
	/** Fixed mapping where 0 and 255 map to colors given in constructor, with intermediate values interpolated.
	 */
	public static class FixedColorScheme extends AbstractColorScheme {
		
		int[] colorMap = new int[256];

		public FixedColorScheme(int r0, int g0, int b0, int r1, int g1, int b1) {
			for(int i=0; i<256; i++) {
				int r = (r0 + (r1-r0)*i/255);
				int g = (g0 + (g1-g0)*i/255);
				int b = (b0 + (b1-b0)*i/255);
				colorMap[i] = (255<<24) + (r<<16) + (g<<8) + b;
			}
		}
		
		public int[] getColorMap() {
			return colorMap;
		}
		
	}
	
	/** Mapping which uses a background image, where 0 maps to opaque and black (hiding the image) 
	 * and 255 is transparent.
	 */
	public static class GradientColorScheme extends AbstractColorScheme {
		Bitmap backgroundBitmap;
		int[] alphaMap = new int[256];
		
		protected GradientColorScheme() {} // for subclasses
		
		public GradientColorScheme(int br, int bg, int bb, int r0, int g0, int b0, int r1, int g1, int b1) {
			backgroundBitmap = Bitmap.createBitmap(1, 256, Bitmap.Config.ARGB_8888);
			
			// ramp to maximum RGB value at midpoint
			int maxr = Math.max(r0, r1);
			int maxg = Math.max(g0, g1);
			int maxb = Math.max(b0, b1);

			for(int i=0; i<128; i++) {
				int r = r0 + (maxr-r0)*i/127;
				int g = g0 + (maxg-g0)*i/127;
				int b = b0 + (maxb-b0)*i/127;
				int color = (255<<24) + (r<<16) + (g<<8) + b;
				backgroundBitmap.setPixel(0, i, color);
			}
			for(int i=0; i<128; i++) {
				int r = maxr + (r1-maxr)*i/127;
				int g = maxg + (g1-maxg)*i/127;
				int b = maxb + (b1-maxb)*i/127;
				int color = (255<<24) + (r<<16) + (g<<8) + b;
				backgroundBitmap.setPixel(0, i+128, color);
			}
			
			alphaMap = createAlphaMap(br, bg, bb);
			backgroundBitmap.prepareToDraw();
		}
		
		public int[] getColorMap() {
			return alphaMap;
		}
		
		public Bitmap getBackgroundBitmap() {
			return backgroundBitmap;
		}

		public void drawBackground(Canvas canvas, Rect dstRect) {
			canvas.drawBitmap(this.getBackgroundBitmap(), null, dstRect, null);
		}
	}
	
	public static class Gradient2DColorScheme extends GradientColorScheme {
		static int SIZE = 80;
		
		public void updateColors(int br, int bg, int bb, 
				int r00, int g00, int b00, int r01, int g01, int b01,
				int r10, int g10, int b10, int r11, int g11, int b11) {
			
			int maxr0 = Math.max(r00, r10);
			int maxr1 = Math.max(r01, r11);
			int maxg0 = Math.max(g00, g10);
			int maxg1 = Math.max(g01, g11);
			int maxb0 = Math.max(b00, b10);
			int maxb1 = Math.max(b01, b11);
			int halfsize = SIZE / 2;
			Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
			for(int row=0; row<SIZE; row++) {
				// interpolate start and end pixels for this row, ramping up to max at midpoint for each component
				int r0, r1, g0, g1, b0, b1;
				if (row < halfsize) {
					r0 = r00 + (maxr0-r00)*row/(halfsize-1);
					r1 = r01 + (maxr1-r01)*row/(halfsize-1);
					g0 = g00 + (maxg0-g00)*row/(halfsize-1);
					g1 = g01 + (maxg1-g01)*row/(halfsize-1);
					b0 = b00 + (maxb0-b00)*row/(halfsize-1);
					b1 = b01 + (maxb1-b01)*row/(halfsize-1);
				}
				else {
					r0 = maxr0 + (r10-maxr0)*(row-halfsize)/(halfsize-1);
					r1 = maxr1 + (r11-maxr1)*(row-halfsize)/(halfsize-1);
					g0 = maxg0 + (g10-maxg0)*(row-halfsize)/(halfsize-1);
					g1 = maxg1 + (g11-maxg1)*(row-halfsize)/(halfsize-1);
					b0 = maxb0 + (b10-maxb0)*(row-halfsize)/(halfsize-1);
					b1 = maxb1 + (b11-maxb1)*(row-halfsize)/(halfsize-1);
				}
				
				int maxr = Math.max(r0, r1);
				int maxg = Math.max(g0, g1);
				int maxb = Math.max(b0, b1);
				int r,g,b;
				for(int col=0; col<SIZE; col++) {
					if (col<halfsize) {
						r = r0 + (maxr-r0)*col/(halfsize-1);
						g = g0 + (maxg-g0)*col/(halfsize-1);
					    b = b0 + (maxb-b0)*col/(halfsize-1);
					}
					else {
						r = maxr + (r1-maxr)*(col-halfsize)/(halfsize-1);
						g = maxg + (g1-maxg)*(col-halfsize)/(halfsize-1);
						b = maxb + (b1-maxb)*(col-halfsize)/(halfsize-1);
					}
					int color = (255<<24) + (r<<16) + (g<<8) + b;
					bitmap.setPixel(col, row, color);
				}
			}
			alphaMap = createAlphaMap(br, bg, bb);
			backgroundBitmap = bitmap;
			backgroundBitmap.prepareToDraw();
		}

		public Gradient2DColorScheme(int br, int bg, int bb, 
				int r00, int g00, int b00, int r01, int g01, int b01,
				int r10, int g10, int b10, int r11, int g11, int b11) {
			super();
			updateColors(br, bg, bb, r00, g00, b00, r01, g01, b01,
					 r10, g10, b10, r11, g11, b11);
		}
	}
	
	public class AnimatedGradientColorScheme extends GradientColorScheme {
		static final int PIXELS_PER_SEGMENT = 128;
		int offset = 0;
		
		Bitmap buildBackgroundBitmap(int[] colors) {
			// create extra segment so that when offset gets to the end, it can wrap around to the beginning and be in the same place
			// RrrrgggGGgggbbbBBbbbrrrRRrrrgggG
			//                        ********
			//                         ********
			//  ********
			int nsegments = colors.length / 3 + 1;
			Bitmap bitmap = Bitmap.createBitmap(1, nsegments * PIXELS_PER_SEGMENT, Bitmap.Config.ARGB_8888);
			int pixnum = 0;
			for(int n=0; n<nsegments; n++) {
				int r0 = colors[(3*n+0) % colors.length];
				int g0 = colors[(3*n+1) % colors.length];
				int b0 = colors[(3*n+2) % colors.length];
				
				int r1 = colors[(3*n+3) % colors.length];
				int g1 = colors[(3*n+4) % colors.length];
				int b1 = colors[(3*n+5) % colors.length];
				
				// ramp to maximum RGB value at midpoint
				int maxr = Math.max(r0, r1);
				int maxg = Math.max(g0, g1);
				int maxb = Math.max(b0, b1);

				for(int i=0; i<PIXELS_PER_SEGMENT/2; i++) {
					int r = r0 + (maxr-r0)*i/(PIXELS_PER_SEGMENT/2-1);
					int g = g0 + (maxg-g0)*i/(PIXELS_PER_SEGMENT/2-1);
					int b = b0 + (maxb-b0)*i/(PIXELS_PER_SEGMENT/2-1);
					int color = (255<<24) + (r<<16) + (g<<8) + b;
					bitmap.setPixel(0, pixnum++, color);
				}
				for(int i=0; i<PIXELS_PER_SEGMENT/2; i++) {
					int r = maxr + (r1-maxr)*i/(PIXELS_PER_SEGMENT/2-1);
					int g = maxg + (g1-maxg)*i/(PIXELS_PER_SEGMENT/2-1);
					int b = maxb + (b1-maxb)*i/(PIXELS_PER_SEGMENT/2-1);
					int color = (255<<24) + (r<<16) + (g<<8) + b;
					bitmap.setPixel(0, pixnum++, color);
				}
			}
			bitmap.prepareToDraw();
			return bitmap;
		}
		
		public void drawBackground(Canvas canvas, Rect dstRect) {
			offset += 3;
			if (offset + PIXELS_PER_SEGMENT > backgroundBitmap.getHeight()) {
				offset %= PIXELS_PER_SEGMENT;
			}
			canvas.drawBitmap(this.getBackgroundBitmap(), new Rect(0, offset, 1, offset+PIXELS_PER_SEGMENT), dstRect, null);
		}

		// colors array is flattened list of r,g,b components
		public AnimatedGradientColorScheme(int br, int bg, int bb, int... colors) {
			super();
			alphaMap = createAlphaMap(br, bg, bb);
			backgroundBitmap = buildBackgroundBitmap(colors);
		}
		
	}
	
	public class MatrixColorScheme extends AbstractColorScheme {
		
		int[] alphaMap;
		int glyphWidth = 9;
		int glyphHeight = 11;
		Paint charPaint = new Paint();
		Paint rainPaint = new Paint();
		Paint rainEndPaint = new Paint();
		String[] glyphChars = new String[] {
		    "1","2","3","4","5","6","7","8","9","0","@","#","$","%","&","*","(",")","{","}",
		};
		Random RAND = new Random();
		int glyphChangeChancePerFrame = 20;
		
		byte[][] glyphGrid;
		
		static class Raindrop {
			public int startRow, startCol, length;
		}
		List<Raindrop> raindrops = new ArrayList<Raindrop>();
		int rainLength = 20;
		int maxRaindrops = 25;
		int rainChancePerFrame = 4;
		
		public MatrixColorScheme(int r, int g, int b, int width, int height) {
			this.alphaMap = createAlphaMap(0,0,0);
			charPaint.setARGB(255, r, g, b);
			charPaint.setTextSize(12);
			
			rainPaint.setARGB(255, 0, 255, 0);
			rainPaint.setTextSize(12);
			rainEndPaint.setARGB(255, 255, 255, 255);
			rainEndPaint.setTextSize(12);
		}
		
		void initGlyphGrid(int rows, int cols) {
			glyphGrid = new byte[rows][cols];
			for(int r=0; r<rows; r++) {
				for(int c=0; c<cols; c++) {
					glyphGrid[r][c] = (byte)RAND.nextInt(glyphChars.length);
				}
			}
			raindrops.clear();
			rainLength = rows / 2;
		}
		
		public boolean useBackground() {
			return true;
		}
		
		public void drawBackground(Canvas canvas, Rect dstRect) {
			processRain();
			updateGlyphs();
			
			canvas.save();
			canvas.clipRect(dstRect);
			canvas.drawRGB(0,0,0);
			
			int rows = dstRect.height() / glyphHeight;
			int cols = dstRect.width() / glyphWidth;
			
			if (glyphGrid==null || rows!=glyphGrid.length || cols!=glyphGrid[0].length) {
				initGlyphGrid(rows, cols);
			}
			
			for(int row=0; row<rows; row++) {
				for(int col=0; col<cols; col++) {
					canvas.drawText(glyphChars[glyphGrid[row][col]], 
							dstRect.left+col*glyphWidth, dstRect.top+(row+1)*glyphHeight, charPaint);
				}
			}
			canvas.restore();
		}
		
		void updateGlyphs() {
			if (glyphGrid!=null) {
				int rows = glyphGrid.length;
				int cols = glyphGrid[0].length;
				for(int r=0; r<rows; r++) {
					for(int c=0; c<cols; c++) {
						if (RAND.nextInt(glyphChangeChancePerFrame)==0) {
							glyphGrid[r][c] = (byte)RAND.nextInt(glyphChars.length);
						}
					}
				}
			}
		}

		void processRain() {
			// possibly create raindrop
			if (raindrops.size()<maxRaindrops && glyphGrid!=null && glyphGrid.length>2*rainLength) {
				if (RAND.nextInt(rainChancePerFrame)==0) {
					Raindrop drop = new Raindrop();
					drop.startRow = RAND.nextInt(glyphGrid.length - rainLength - 1);
					drop.startCol = RAND.nextInt(glyphGrid[0].length);
					drop.length = 0;
					raindrops.add(drop);
				}
			}
			// iterate over raindrops, increase length, update glyphs
			for(int i=raindrops.size()-1; i>=0; i--) {
				Raindrop drop = raindrops.get(i);
				drop.length++;
				if (drop.length > 2*rainLength) {
					raindrops.remove(i);
				}
				// TODO: glyphs
			}
		}
			
		public void drawForeground(Canvas canvas, Rect dstRect) {
			// draw rain
			for(int i=raindrops.size()-1; i>=0; i--) {
				Raindrop drop = raindrops.get(i);
				int numDropsToDraw = Math.min(drop.length, rainLength);
				for(int j=0; j<numDropsToDraw; j++) {
					int distFromEnd = drop.length - 1 - j;
					if (distFromEnd==0) {
						rainPaint.setARGB(255, 255, 255, 255);
					}
					else {
						int g = 255 - 10*(distFromEnd-1);
						if (g<=0) continue;
						rainPaint.setARGB(255, 0, g, 0);
					}
					canvas.drawText(glyphChars[glyphGrid[drop.startRow+j][drop.startCol]],
							dstRect.left+(drop.startCol)*glyphWidth, dstRect.top+(drop.startRow+j+1)*glyphHeight, rainPaint);
				}
			}
		}

		@Override
		public int[] getColorMap() {
			return alphaMap;
		}
		
	}
}

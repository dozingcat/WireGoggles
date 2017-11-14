package com.dozingcatsoftware.eyeball.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;

public class MediaDirectory {

	final String path;
	final String directoryName;
	Boolean hasThumbnail;

	public MediaDirectory(String path) {
		this.path = path;
		this.directoryName = (new File(path)).getName();
	}

	public String getPath() {
		return path;
	}

	public String getDirectoryName() {
	    return directoryName;
	}

	// When naming files that will be sent elsewhere, add a prefix to tell where it came from.
	public String getDirectoryNameForSharing() {
	    return "WireGoggles_" + directoryName;
	}

	void createDirectoryIfNeeded() {
		File dir = new File(path);
		if (!dir.exists()) {
		    dir.mkdirs();
		    // create .nomedia file to prevent thumbnails from being indexed
		    try {
	            (new File(dir.getPath() + File.separator + ".nomedia")).createNewFile();
		    }
		    catch (IOException ignored) {}
		}
	}

	String propertiesFilePath() {
		return this.path + File.separator + "properties.txt";
	}

	// this can be either a video with multiple frames or an image with a single frame
	public String videoFilePath() {
		return this.path + File.separator + "video";
	}

	public String audioFilePath() {
		return this.path + File.separator + "audio.pcm";
	}

	public String thumbnailFilePath() {
		return this.path + File.separator + "thumbnail.png";
	}

	public static List<MediaDirectory> videoDirectoriesInDirectory(String path) {
		String[] contents = (new File(path)).list();
		if (contents==null) return Collections.emptyList();

		// sorting by descending name will sort by date created with newest videos first
		List<String> sortedContents = Arrays.asList(contents);
		Collections.sort(sortedContents);
		Collections.reverse(sortedContents);

		List<MediaDirectory> directories = new ArrayList<MediaDirectory>();
		for(String filename : sortedContents) {
			String filepath = path + File.separator + filename;
			if ((new File(filepath)).isDirectory()) {
				MediaDirectory vd = new MediaDirectory(filepath);
				if (vd.isValid()) {
					directories.add(vd);
				}
			}
		}
		return directories;
	}

	public long videoFileSize() {
		File vf = new File(videoFilePath());
		if (!vf.isFile()) return 0;
		return vf.length();
	}

	public boolean isValid() {
		return ((new File(propertiesFilePath()).isFile() && (new File(videoFilePath())).isFile()));
	}

	public void storeVideoProperties(MediaProperties videoProperties) throws IOException {
		createDirectoryIfNeeded();
		FileWriter writer = new FileWriter(propertiesFilePath());
		try {
			writer.append(videoProperties.properties.toString(4));
		}
		catch(JSONException ex) {
			throw new RuntimeException(ex);
		}
		finally {
			writer.close();
		}
	}

	public MediaProperties getVideoProperties() {
		// ridiculous that there's no API to read a file into a string
	    BufferedReader in = null;
		try {
			StringBuffer buffer = new StringBuffer();
			in = new BufferedReader(new FileReader(propertiesFilePath()));
			String line;
			while ((line=in.readLine())!=null) {
				buffer.append(line).append("\n");
			}
			JSONObject json = new JSONObject(buffer.toString());
			return new MediaProperties(json);
		}
		catch(Exception ignored) {
			return new MediaProperties(new JSONObject());
		}
		finally {
		    if (in != null) {
		        try {in.close();}
		        catch (IOException ignored) {}
		    }
		}
	}

	public boolean hasThumbnailImage() {
		if (hasThumbnail==null) {
			hasThumbnail = (new File(this.thumbnailFilePath())).isFile();
		}
		return hasThumbnail;
	}

	public boolean storeThumbnailBitmap(Bitmap bitmap) {
		try {
			FileOutputStream os = new FileOutputStream(this.thumbnailFilePath());
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
			os.close();
		}
		catch(IOException ex) {
			return false;
		}
		hasThumbnail = true;
		return true;
	}

	public OutputStream videoOutputStream() throws IOException {
		createDirectoryIfNeeded();
		// GZIP compression reduces size by ~50%, but causes slowdowns when writing and makes random access difficult
		return new FileOutputStream(videoFilePath());
	}

	public OutputStream audioOutputStream() throws IOException {
		createDirectoryIfNeeded();
		return new FileOutputStream(audioFilePath());
	}

	public RandomAccessFile videoRandomAccessFile() throws IOException {
		return new RandomAccessFile(videoFilePath(), "r");
	}

	public boolean delete() {
		return _deleteTree(this.getPath());
	}

	// File.delete requires directories to be non-empty, how is this not built-in???
	static boolean _deleteTree(String path) {
		File file = new File(path);
		if(file.isDirectory()) {
			File[] subfiles = file.listFiles();
			for(File subfile: subfiles) {
				_deleteTree(subfile.getAbsolutePath());
			}
		}
		return file.delete();
	}
}

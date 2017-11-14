package com.dozingcatsoftware.eyeball;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dozingcatsoftware.WireGoggles.R;
import com.dozingcatsoftware.eyeball.video.MediaDirectory;
import com.dozingcatsoftware.util.AsyncImageLoader;
import com.dozingcatsoftware.util.ScaledBitmapCache;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

public class ImageListActivity extends Activity {

    static int CELL_WIDTH = 90;
    static int CELL_HEIGHT = 60;

    List<MediaDirectory> imageDirectories;
	List<Map<String, Object>> imageDirectoryMaps;

	GridView gridView;
	int selectedGridIndex;

    // A cache of scaled Bitmaps for the image files, so we can avoid reloading them as the user scrolls.
    ScaledBitmapCache bitmapCache;
    AsyncImageLoader imageLoader = new AsyncImageLoader();

    @Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		setContentView(R.layout.imagegrid);

		bitmapCache = new ScaledBitmapCache(this, ScaledBitmapCache.createIdentityLocator());

		gridView = (GridView) findViewById(R.id.gridview);
		gridView.setOnItemClickListener(new OnItemClickListener() {
			@Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				selectedGridIndex = position;
				ViewImageActivity.startActivityWithImageDirectory(ImageListActivity.this, imageDirectories.get(position).getPath());
			}
		});
		readImageThumbnails();
		displayGrid();
    }

	void readImageThumbnails() {
		imageDirectories = MediaDirectory.videoDirectoriesInDirectory(WGUtils.savedImageDirectory);
		imageDirectoryMaps = new ArrayList<Map<String, Object>>();
		for(MediaDirectory vd : imageDirectories) {
			Map<String, Object> dmap = new HashMap<String, Object>();
			dmap.put("thumbnailURI", Uri.fromFile(new File(vd.thumbnailFilePath())));
			imageDirectoryMaps.add(dmap);
		}
	}

	void displayGrid() {
		SimpleAdapter adapter = new SimpleAdapter(this, imageDirectoryMaps,
				R.layout.imagegrid_cell,
				new String[] {"thumbnailURI"},
				new int[] {R.id.grid_image});
        adapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object data, String textRepresentation) {
                Uri imageUri = (Uri)data;
                imageLoader.loadImageIntoViewAsync(bitmapCache, imageUri, (ImageView)view, CELL_WIDTH, CELL_HEIGHT, getResources());
                return true;
            }
        });
		gridView.setAdapter(adapter);

		// show text message if no images available
		View noImagesView = findViewById(R.id.noImagesTextView);
		noImagesView.setVisibility(imageDirectoryMaps.size()>0 ? View.GONE : View.VISIBLE);

		System.gc(); // seems to avoid OutOfMemoryErrors when selecting image after deleting earlier image
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode==ViewImageActivity.DELETE_RESULT) {
			imageDirectories.remove(selectedGridIndex);
			imageDirectoryMaps.remove(selectedGridIndex);
			displayGrid();
		}
	}

}

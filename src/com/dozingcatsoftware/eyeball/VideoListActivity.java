package com.dozingcatsoftware.eyeball;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dozingcatsoftware.WireGoggles.R;
//import com.dozingcatsoftware.WireGogglesFree.R;
import com.dozingcatsoftware.eyeball.video.MediaDirectory;
import com.dozingcatsoftware.eyeball.video.MediaProperties;

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class VideoListActivity extends ListActivity {

	List<MediaDirectory> videoDirectories;
	List<Map<String, Object>> videoDirectoryMaps;

	static SimpleDateFormat VIDEO_DATE_FORMAT = new SimpleDateFormat("MMM dd yyyy, HH:mm");

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.videolist);

		buildListView();
	}

	void buildListView() {
		videoDirectories = MediaDirectory.videoDirectoriesInDirectory(WGUtils.savedVideoDirectory);
		videoDirectoryMaps = new ArrayList<Map<String, Object>>();
		for(MediaDirectory vd : videoDirectories) {
			MediaProperties props = vd.getVideoProperties();

			Map<String, Object> dmap = new HashMap<String, Object>();
			dmap.put("title", VIDEO_DATE_FORMAT.format(props.dateCreated()));
			String mbString = String.format("%.1f", vd.videoFileSize()/1000000.0);
			dmap.put("info", props.durationInSeconds() + " seconds, " + mbString + "MB");
			dmap.put("thumbnailURI", Uri.fromFile(new File(vd.thumbnailFilePath())).toString());

			videoDirectoryMaps.add(dmap);
		}

		ListAdapter adapter = new SimpleAdapter(this, videoDirectoryMaps,
				R.layout.videolist_row,
				new String[] {"title", "info", "thumbnailURI"},
				new int[] {R.id.listrow_text1, R.id.listrow_text2, R.id.listrow_image});
		setListAdapter(adapter);

		System.gc(); // seems to avoid OutOfMemoryErrors
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		VideoPlaybackActivity.startActivityWithVideoDirectory(this, videoDirectories.get(position).getPath());
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode==VideoPlaybackActivity.DELETE_RESULT) {
			buildListView();
		}
	}
}

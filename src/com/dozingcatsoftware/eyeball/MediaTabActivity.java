package com.dozingcatsoftware.eyeball;

import com.dozingcatsoftware.WireGoggles.R;
//import com.dozingcatsoftware.WireGogglesFree.R;

import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TabHost;
import android.widget.TextView;

public class MediaTabActivity extends TabActivity {
	
	public static Intent startActivity(Context parent, boolean showVideo) {
		Intent intent = new Intent(parent, MediaTabActivity.class);
		if (showVideo) {
			intent.putExtra("video", true);
		}
		parent.startActivity(intent);
		return intent;
	}
	

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
	    TabHost tabHost = getTabHost();
	    addTab(tabHost, "Pictures", ImageListActivity.class);
	    addTab(tabHost, "Videos", VideoListActivity.class);

	    // start on video tab if requested
	    if (this.getIntent().getBooleanExtra("video", false)) {
		    tabHost.setCurrentTab(1);
	    }
	}
	
	TabHost.TabSpec addTab(TabHost tabHost, String label, Class activityClass) {
		Intent tabIntent = (new Intent()).setClass(this, activityClass);
		View tabView = createTabView(label);
		TabHost.TabSpec tabSpec = tabHost.newTabSpec(label).setIndicator(tabView).setContent(tabIntent);
		tabHost.addTab(tabSpec);
		return tabSpec;
	}
	
	View createTabView(String label) {
		View view = LayoutInflater.from(this).inflate(R.layout.mediatab_bg, null);
	    TextView tv = (TextView) view.findViewById(R.id.tabsText);
	    tv.setText(label);
	    return view;
	}
	
}

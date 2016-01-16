package com.dozingcatsoftware.eyeball;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class NewPictureReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        Log.i("NewPictureReceiver", "Got picture: " + intent.getData());
        try {
            String resultDir = (new ProcessPictureOperation()).processPicture(context, intent.getData());
            Toast.makeText(context, "Saved WG image: " + resultDir, Toast.LENGTH_LONG).show();
        }
        catch(Exception ex) {
            Log.e("NewPictureReceiver", "Error saving picture", ex);
            Toast.makeText(context, "Error saving WG image: " + ex, Toast.LENGTH_LONG).show();
        }
    }

}

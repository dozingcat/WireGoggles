package com.dozingcatsoftware.eyeball;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NewPictureReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        Log.i("NewPictureReceiver", "Got picture: " + intent.getData());
        try {
            (new ProcessPictureOperation()).processPicture(context, intent.getData());
        }
        catch(Exception ex) {
            Log.e("NewPictureReceiver", "Error saving picture", ex);
        }
    }

}

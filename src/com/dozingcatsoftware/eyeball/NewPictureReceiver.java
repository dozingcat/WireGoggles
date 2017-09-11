package com.dozingcatsoftware.eyeball;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class NewPictureReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        // On Android N and later we use a JobService and shouldn't get this notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return;
        }
        Log.i("NewPictureReceiver", "Got picture: " + intent.getData());
        try {
            (new ProcessPictureOperation()).processPicture(context, intent.getData());
        }
        catch(Exception ex) {
            Log.e("NewPictureReceiver", "Error saving picture", ex);
        }
    }

}

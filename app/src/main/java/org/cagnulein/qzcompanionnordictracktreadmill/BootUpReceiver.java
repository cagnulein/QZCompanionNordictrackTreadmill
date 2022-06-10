package org.cagnulein.qzcompanionnordictracktreadmill;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootUpReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(new Intent(context, QZService.class));
        } else {
            context.startService(new Intent(context, QZService.class));
        }
    }
}
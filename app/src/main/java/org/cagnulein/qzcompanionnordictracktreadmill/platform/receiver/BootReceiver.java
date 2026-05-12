package org.cagnulein.qzcompanionnordictracktreadmill.platform.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.cagnulein.qzcompanionnordictracktreadmill.ui.MainActivity;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
}

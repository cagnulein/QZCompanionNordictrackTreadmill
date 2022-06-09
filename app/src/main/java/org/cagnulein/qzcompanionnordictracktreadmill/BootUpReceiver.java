package org.cagnulein.qzcompanionnordictracktreadmill;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootUpReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i= new Intent(context, QZService.class);
        // potentially add data to the intent
        i.putExtra("KEY1", "Value to be used by the service");
        context.startService(i);
    }
}
package org.cagnulein.qzcompanionnordictracktreadmill;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        //Intent inServer = new Intent(context, UDPListenerService.class);
        //context.startService(inServer);
        Intent in = new Intent(context, QZService.class);
        context.startService(in);
        setAlarm(context);
    }

    public void setAlarm(Context context)
    {
        AlarmManager am =( AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        assert am != null;
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, (System.currentTimeMillis() + 10000L), pi); //Next alarm in 10s
    }
}
package org.cagnulein.qzcompanionnordictracktreadmill.platform.crash;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private Context context;

    public CrashHandler(Context context) {
        this.context = context;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String stackTraceString = sw.toString();

        Log.e("QZ:Crash", "Uncaught exception on thread " + thread.getName() + ": " + stackTraceString);

        SharedPreferences prefs = context.getSharedPreferences("CrashPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("last_crash", stackTraceString).apply();

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }
}

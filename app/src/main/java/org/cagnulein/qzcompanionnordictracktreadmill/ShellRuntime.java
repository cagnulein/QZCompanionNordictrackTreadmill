package org.cagnulein.qzcompanionnordictracktreadmill;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

public class ShellRuntime {
    private static final String LOG_TAG = "QZ:Shell";

    private final Runtime runtime = Runtime.getRuntime();
    private String sh = null;

    public InputStream execAndGetOutput(String command) throws IOException {
        Process proc = exec(command);
        return proc.getInputStream();
    }

    public Process exec(String command) throws IOException {
        String[] cmd = {getSh(), "-c", " " + command + " 2>&1"};
        return runtime.exec(cmd);
    }

    @NonNull
    private String getSh() {
        if(sh == null) {
            sh = "/bin/sh";

            try {
                execAndGetOutput("ls");
            } catch (final Exception ex) {
                Log.w(LOG_TAG, "Calling " + sh + " failed", ex);
                sh = "/system/bin/sh";
            }

            Log.d(LOG_TAG, "Using sh: " + sh);
        }

        return sh;
    }
}

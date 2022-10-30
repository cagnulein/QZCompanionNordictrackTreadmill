package org.cagnulein.qzcompanionnordictracktreadmill;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

public class QZService extends Service {
    private static final String LOG_TAG = "QZ:Service";
    int startMode;       // indicates how to behave if the service is killed
    IBinder binder;      // interface for clients that bind
    boolean allowRebind; // indicates whether onRebind should be used
    String path = "/sdcard/.wolflogs/";
    int clientPort = 8002;
    Handler handler = new Handler();
    Runnable runnable = null;
    DatagramSocket socket = null;

    byte[] lmessage = new byte[1024];
    DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);

    AtomicLong filePointer = new AtomicLong();
    String fileName = "";
    RandomAccessFile bufferedReader = null;
    boolean firstTime = false;
    String lastSpeed = "";
    String lastInclination = "";
    String lastWattage = "";
    String lastCadence = "";
    String lastResistance = "";
    String lastGear = "";

    int counterTruncate = 0;

    private final ShellRuntime shellRuntime = new ShellRuntime();

    @Override
    public void onCreate() {
        // The service is being created
        //Toast.makeText(this, "Service created!", Toast.LENGTH_LONG).show();
        try {
            runnable = new Runnable() {
                @Override
                public void run() {
                    parse();
                }
            };
        } finally {
            if(socket != null){
                socket.close();
                Log.e(LOG_TAG, "socket.close()");
            }
        }

        if(runnable != null)
            handler.postDelayed(runnable, 500);
    }

    private boolean speed(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            System.out.println(line);
            lastSpeed = line;
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private boolean incline(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            lastInclination = line;
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private boolean watt(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            lastWattage = line;
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private boolean cadence(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            lastCadence = line;
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private boolean gear(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            lastGear = line;
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private boolean resistance(InputStream in) throws IOException {
        BufferedReader is = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = is.readLine()) != null) {
            lastResistance = line;
            sendBroadcast(line);
            return true;
        }
        return  false;
    }

    private void parse() {

        String file = pickLatestFileFromDownloads();
        Log.d(LOG_TAG,"Parsing " + file);

        if(!file.equals("")) {
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);

                InputStream speedInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed KPH\" | tail -n1");
                if(!speed(speedInputStream)) {
                    InputStream speed2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed KPH\" " + file + "  | tail -n1");
                    if(!speed(speed2InputStream)) {
                        sendBroadcast(lastSpeed);
                    }
                }
                InputStream inclineInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed Grade\" | tail -n1");
                if(!incline(inclineInputStream)) {
                    InputStream incline2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed Grade\" " + file + "  | tail -n1");
                    if(!incline(incline2InputStream)) {
                        sendBroadcast(lastInclination);
                    }
                }
                InputStream procWattInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed Watts\" | tail -n1");
                if(!watt(procWattInputStream)) {
                    InputStream watt2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed Watts\" " + file + "  | tail -n1");
                    if(!watt(watt2InputStream)) {
                        sendBroadcast(lastWattage);
                    }
                }
                InputStream cadenceInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed RPM\" | tail -n1");
                if(!cadence(cadenceInputStream)) {
                    InputStream cadence2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed RPM\" " + file + "  | tail -n1");
                    if(!cadence(cadence2InputStream)) {
                        sendBroadcast(lastCadence);
                    }
                }
                InputStream gearInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed CurrentGear\" | tail -n1");
                if(!gear(gearInputStream)) {
                    InputStream gear2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed CurrentGear\" " + file + "  | tail -n1");
                    if(!gear(gear2InputStream)) {
                        sendBroadcast(lastGear);
                    }
                }
                InputStream resistanceInputStream = shellRuntime.execAndGetOutput("tail -n500 " + file + " | grep -a \"Changed Resistance\" | tail -n1");
                if(!resistance(resistanceInputStream)) {
                    InputStream resistance2InputStream = shellRuntime.execAndGetOutput("grep -a \"Changed Resistance\" " + file + "  | tail -n1");
                    if(!resistance(resistance2InputStream)) {
                        sendBroadcast(lastResistance);
                    }
                }

                if(counterTruncate++ > 1200) {
                    Log.d(LOG_TAG, "Truncating file...");
                    counterTruncate = 0;
                    shellRuntime.exec("truncate -s0 " + file);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
            socket.close();
        }

        handler.postDelayed(runnable, 500);
    }

    public void sendBroadcast(String messageStr) {
        StrictMode.ThreadPolicy policy = new   StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        try {

            byte[] sendData = messageStr.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, getBroadcastAddress(), this.clientPort);
            socket.send(sendPacket);
            System.out.println(messageStr);
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException: " + e.getMessage());
        }
    }
    InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager)    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = null;
        try {
            dhcp = wifi.getDhcpInfo();
            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++)
                quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
            return InetAddress.getByAddress(quads);
        } catch (Exception e) {
            Log.e(LOG_TAG, "IOException: " + e.getMessage());
        }
        byte[] quads = new byte[4];
        return InetAddress.getByAddress(quads);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service is starting, due to a call to startService()
        return START_STICKY;
    }
    @Override
    public IBinder onBind(Intent intent) {
        // A client is binding to the service with bindService()
        return binder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        return allowRebind;
    }
    @Override
    public void onRebind(Intent intent) {
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }
    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
    }

    public String pickLatestFileFromDownloads() {

        File dir = new File(path);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            Log.i(LOG_TAG,"There is no file in the folder");
            return "";
        }

        File lastModifiedFile = files[0];
        for (int i = 1; i < files.length; i++) {
            if (lastModifiedFile.lastModified() < files[i].lastModified()) {
                lastModifiedFile = files[i];
            }
        }
        String k = lastModifiedFile.toString();

        Log.i(LOG_TAG, "lastModifiedFile " + lastModifiedFile);
        Log.i(LOG_TAG, "string: " + k);
        return k;

    }
}

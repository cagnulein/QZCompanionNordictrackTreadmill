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
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

import static android.content.ContentValues.TAG;

public class QZService extends Service {
    int startMode;       // indicates how to behave if the service is killed
    IBinder binder;      // interface for clients that bind
    boolean allowRebind; // indicates whether onRebind should be used
    String path = "/sdcard/.wolflogs/";
    int serverPort = 8002;
    Handler handler = new Handler();
    Runnable runnable = null;
    DatagramSocket socket = null;
    AtomicLong filePointer = new AtomicLong();
    String fileName = "";
    RandomAccessFile bufferedReader = null;
    boolean firstTime = false;
    String lastSpeed = "";
    String lastInclination = "";

    @Override
    public void onCreate() {
        // The service is being created
        //Toast.makeText(this, "Service created!", Toast.LENGTH_LONG).show();

        try {
            socket = new DatagramSocket(serverPort);
            runnable = new Runnable() {
                @Override
                public void run() {
                    parse();
                }
            };
        } catch (SocketException e) {
            e.printStackTrace();
        } finally {
            if(socket != null){
                socket.close();
                Log.e(TAG, "socket.close()");
            }
        }

        if(runnable != null)
            handler.postDelayed(runnable, 500);
    }

    private void parse() {
        if(fileName.compareTo(path + pickLatestFileFromDownloads()) != 0) {
            fileName = path + pickLatestFileFromDownloads();
            filePointer.set(0);
            try {
                bufferedReader = new RandomAccessFile(fileName, "r");
            } catch ( IOException  e ) {
                e.printStackTrace();
            }
        }
        //Toast.makeText(getApplicationContext(), "Service is still running", Toast.LENGTH_LONG).show();

        try {
            boolean speedSent = false;
            boolean inclinationSent = false;
            if (bufferedReader != null) {
                while (true) {
                    final String string = bufferedReader.readLine();

                    boolean speedPresent = false;
                    boolean inclinationPresent = false;

                    if (string != null) {
                        speedPresent = string.contains("Changed KPH");
                        inclinationPresent = string.contains("Changed Grade");
                    }

                    if (speedPresent) {
                        speedSent = true;
                        lastSpeed = string;
                    } else if (inclinationPresent) {
                        inclinationSent = true;
                        lastInclination = string;
                    }

                    if (string != null && (firstTime == true || speedPresent || inclinationPresent)) {
                        //System.out.println(string);
                    } else {
                        if (lastSpeed.length() > 0)
                            sendBroadcast(lastSpeed);
                        if (lastInclination.length() > 0)
                            sendBroadcast(lastInclination);
                        firstTime = true;
                        filePointer.set(bufferedReader.getFilePointer());
                        bufferedReader.close();
                        bufferedReader = new RandomAccessFile(fileName, "r");
                        bufferedReader.seek(filePointer.get());
                        break;
                    }
                }
            }
        } catch(IOException e ){
            e.printStackTrace();
        }

        handler.postDelayed(runnable, 500);
    }

    public void sendBroadcast(String messageStr) {
        StrictMode.ThreadPolicy policy = new   StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        try {

            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);
            byte[] sendData = messageStr.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, getBroadcastAddress(), this.serverPort);
            socket.send(sendPacket);
            System.out.println(messageStr);
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
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
            Log.e(TAG, "IOException: " + e.getMessage());
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
            Log.i(TAG,"There is no file in the folder");
            return "";
        }

        File lastModifiedFile = files[0];
        for (int i = 1; i < files.length; i++) {
            if (lastModifiedFile.lastModified() < files[i].lastModified()) {
                lastModifiedFile = files[i];
            }
        }
        String k = lastModifiedFile.toString();

        System.out.println(lastModifiedFile);
        Path p = Paths.get(k);
        String file = p.getFileName().toString();
        return file;

    }
}

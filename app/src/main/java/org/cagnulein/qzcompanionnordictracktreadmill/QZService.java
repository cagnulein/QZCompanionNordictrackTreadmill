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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static android.content.ContentValues.TAG;

public class QZService extends Service {
    int startMode;       // indicates how to behave if the service is killed
    IBinder binder;      // interface for clients that bind
    boolean allowRebind; // indicates whether onRebind should be used
    String path = "/sdcard/.wolflogs/";
    int clientPort = 8002;
    int serverPort = 8003;
    Handler handler = new Handler();
    Runnable runnable = null;
    DatagramSocket socket = null;
    DatagramSocket socketServer = null;

    byte[] lmessage = new byte[1024];
    DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);

    AtomicLong filePointer = new AtomicLong();
    String fileName = "";
    RandomAccessFile bufferedReader = null;
    boolean firstTime = false;
    String lastSpeed = "";
    String lastInclination = "";
    float lastReqSpeed = 2;
    int y1Speed = 782;      //vertical position of slider at 2.0
    float lastReqInclination = -1;
    int y1Inclination = 722;    //vertical position of slider at 0.0
    int counterTruncate = 0;

    @Override
    public void onCreate() {
        // The service is being created
        //Toast.makeText(this, "Service created!", Toast.LENGTH_LONG).show();

        try {
            socket = new DatagramSocket(clientPort);
            socketServer = new DatagramSocket(serverPort);
            socketServer.setSoTimeout(100);
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

    private void parse() {

        String file = pickLatestFileFromDownloads();
        if(file != "") {
            try {
                Runtime rt = Runtime.getRuntime();
                String[] cmd = {"/bin/sh", "-c", " tail -n500 " + path + file + " | grep -a \"Changed KPH\" | tail -n1"};
                Process proc = rt.exec(cmd);
                if(!speed(proc.getInputStream())) {
                    String[] cmd2 = {"/bin/sh", "-c", " grep -a \"Changed KPH\" " + path + file + "  | tail -n1"};
                    Process proc2 = rt.exec(cmd2);
                    if(!speed(proc2.getInputStream())) {
                        sendBroadcast(lastSpeed);
                    }
                }
                String[] cmdIncline = {"/bin/sh", "-c", " tail -n500 " + path + file + " | grep -a \"Changed Grade\" | tail -n1"};
                Process procIncline = rt.exec(cmdIncline);
                if(!incline(procIncline.getInputStream())) {
                    String[] cmdIncline2 = {"/bin/sh", "-c", " grep -a \"Changed Grade\" " + path + file + "  | tail -n1"};
                    Process procIncline2 = rt.exec(cmdIncline2);
                    if(!incline(procIncline2.getInputStream())) {
                        sendBroadcast(lastInclination);
                    }
                }
                if(counterTruncate++ > 1200) {
                    counterTruncate = 0;
                    String[] cmdTruncate = {"/bin/sh", "-c", " truncate -s0 " + path + file};
                    Process procTruncate = rt.exec(cmdTruncate);
                    Log.d(TAG, "Truncating file...");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        new Thread(new Runnable() {

            @Override
            public void run() {
                try  {
                    try {
                        socketServer.receive(packet);
                        String message = new String(lmessage, 0, packet.getLength());
                        String[] amessage = message.split(";");
                        Log.d(TAG, message);
                        if(amessage.length == 2) {
                            String rSpeed = amessage[0];
                            String rInclination = amessage[1];
                            float reqSpeed = Float.parseFloat(rSpeed);
                            float reqInclination = Float.parseFloat(rInclination);

                            Log.d(TAG, "requestSpeed: " + reqSpeed);
                            Log.d(TAG, "requestInclination: " + reqInclination);
                            if(reqSpeed != -1 && lastReqSpeed != reqSpeed) {
                                int x1 = 1845;     //middle of slider
                                //set speed slider to target position
                                int y2 = (int) (y1Speed - (int)((lastReqSpeed - reqSpeed) * 29.78)); //calculate vertical pixel position for new speed

                                Runtime rt = Runtime.getRuntime();
                                String[] cmdReqSpeed = {"/bin/sh", "-c", " input swipe " + x1 + " " + y1Speed + " " + x1 + " " + y2 + " 200"};
                                Process procReqSpeed = rt.exec(cmdReqSpeed);
                                Log.d(TAG, cmdReqSpeed[2]);

                                y1Speed = y2;  //set new vertical position of speed slider
                                lastReqSpeed = reqSpeed;
                            }
                            if(reqInclination != -100 && lastReqInclination != reqInclination) {
                                int x1 = 75;     //middle of slider
                                y1Inclination = 722;    //vertical position of slider at 0.0
                                int y2 = y1Inclination - (int)((lastReqInclination - reqInclination) * 29.9);  //calculate vertical pixel position for new incline

                                Runtime rt = Runtime.getRuntime();
                                String[] cmdReqInclination = {"/bin/sh", "-c", " input swipe " + x1 + " " + y1Speed + " " + x1 + " " + y2 + " 200"};
                                Process procReqInclination = rt.exec(cmdReqInclination);
                                Log.d(TAG, cmdReqInclination[2]);

                                y1Inclination = y2;  //set new vertical position of speed slider
                                lastReqInclination = reqInclination;
                            }
                        }
                    } catch (SocketTimeoutException e){

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        handler.postDelayed(runnable, 500);
    }

    public void sendBroadcast(String messageStr) {
        StrictMode.ThreadPolicy policy = new   StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        try {

            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);
            byte[] sendData = messageStr.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, getBroadcastAddress(), this.clientPort);
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

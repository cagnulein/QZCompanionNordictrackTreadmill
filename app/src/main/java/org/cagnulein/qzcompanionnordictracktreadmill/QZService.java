package org.cagnulein.qzcompanionnordictracktreadmill;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.StrictMode;
import android.provider.SyncStateContract;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import static android.content.ContentValues.TAG;

public class QZService extends Service {
    int startMode;       // indicates how to behave if the service is killed
    IBinder binder;      // interface for clients that bind
    boolean allowRebind; // indicates whether onRebind should be used

    @Override
    public void onCreate() {
        // The service is being created
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service is starting, due to a call to startService()
        UdpServerThread server = new UdpServerThread(8002);
        server.start();
        return startMode;
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

    private class UdpServerThread extends Thread{

        int serverPort;
        DatagramSocket socket;

        boolean running;

        public UdpServerThread(int serverPort) {
            super();
            this.serverPort = serverPort;
        }

        public void setRunning(boolean running){
            this.running = running;
        }

        @Override
        public void run() {

            running = true;

            try {
                socket = new DatagramSocket(serverPort);

                Log.e(TAG, "UDP Server is running");

                while(running){
                    byte[] buf = new byte[256];

                    String fileName = "/sdcard.wolflogs/" + pickLatestFileFromDownloads();
                    try {
                        RandomAccessFile bufferedReader = new RandomAccessFile( fileName, "r"
                        );

                        long filePointer;
                        while ( true ) {
                            final String string = bufferedReader.readLine();

                            if(string != null) {
                                sendBroadcast(string);
                            }

                            if ( string != null )
                                System.out.println( string );
                            else {
                                filePointer = bufferedReader.getFilePointer();
                                bufferedReader.close();
                                bufferedReader = new RandomAccessFile( fileName, "r" );
                                bufferedReader.seek( filePointer );
                            }

                        }
                    } catch ( IOException  e ) {
                        e.printStackTrace();
                    }
                }

                Log.e(TAG, "UDP Server ended");

            } catch (SocketException e) {
                e.printStackTrace();
            } finally {
                if(socket != null){
                    socket.close();
                    Log.e(TAG, "socket.close()");
                }
            }
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
                System.out.println(getClass().getName() + "Broadcast packet sent to: " + getBroadcastAddress().getHostAddress());
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
}
    public String pickLatestFileFromDownloads() {

        File dir = new File("/sdcard.wolflogs");
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            Log.i(TAG,"There is no file in the folder");
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

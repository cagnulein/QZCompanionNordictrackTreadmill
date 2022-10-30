package org.cagnulein.qzcompanionnordictracktreadmill;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/*
 * Linux command to send UDP:
 * #socat - UDP-DATAGRAM:192.168.1.255:8002,broadcast,sp=8002
 */
public class UDPListenerService extends Service {
    private static final String LOG_TAG = "QZ:UDPListenerService";

    static String UDP_BROADCAST = "UDPBroadcast";

    //Boolean shouldListenForUDPBroadcast = false;
    static DatagramSocket socket;

    float lastReqSpeed = 2;
    int y1Speed = 782;      //vertical position of slider at 2.0
    float lastReqInclination = -1;
    int y1Inclination = 722;    //vertical position of slider at 0.0

    private final ShellRuntime shellRuntime = new ShellRuntime();

    private void listenAndWaitAndThrowIntent(InetAddress broadcastIP, Integer port) throws Exception {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");
        wakeLock.acquire();

        byte[] recvBuf = new byte[15000];
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
        }
        //socket.setSoTimeout(1000);
        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        Log.i(LOG_TAG, "Waiting for UDP broadcast");
        socket.receive(packet);

        String senderIP = packet.getAddress().getHostAddress();
        String message = new String(packet.getData()).trim();

        Log.i(LOG_TAG, "Got UDB broadcast from " + senderIP + ", message: " + message);

        Log.d(LOG_TAG, message);
        String[] amessage = message.split(";");
        if(amessage.length > 0) {
            String rSpeed = amessage[0];
            float reqSpeed = Float.parseFloat(rSpeed);
            Log.d(LOG_TAG, "requestSpeed: " + reqSpeed);

            if (reqSpeed != -1 && lastReqSpeed != reqSpeed) {
                int x1 = 1845;     //middle of slider
                //set speed slider to target position
                int y2 = (int) (y1Speed - (int) ((lastReqSpeed - reqSpeed) * 29.78)); //calculate vertical pixel position for new speed

                String command = "input swipe " + x1 + " " + y1Speed + " " + x1 + " " + y2 + " 200";
                shellRuntime.exec(command);
                Log.d(LOG_TAG, command);

                y1Speed = y2;  //set new vertical position of speed slider
                lastReqSpeed = reqSpeed;
            }
        }

        if(amessage.length > 1) {
            String rInclination = amessage[1];
            float reqInclination = Float.parseFloat(rInclination);
            Log.d(LOG_TAG, "requestInclination: " + reqInclination);
            if(reqInclination != -100 && lastReqInclination != reqInclination) {
                int x1 = 75;     //middle of slider
                y1Inclination = 722;    //vertical position of slider at 0.0
                int y2 = y1Inclination - (int)((lastReqInclination - reqInclination) * 29.9);  //calculate vertical pixel position for new incline

                String command = " input swipe " + x1 + " " + y1Speed + " " + x1 + " " + y2 + " 200";
                shellRuntime.exec(command);
                Log.d(LOG_TAG, command);

                y1Inclination = y2;  //set new vertical position of speed slider
                lastReqInclination = reqInclination;
            }
        }

        broadcastIntent(senderIP, message);
        socket.close();
    }

    private void broadcastIntent(String senderIP, String message) {
        Intent intent = new Intent(UDPListenerService.UDP_BROADCAST);
        intent.putExtra("sender", senderIP);
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    Thread UDPBroadcastThread;

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

    void startListenForUDPBroadcast() {
        UDPBroadcastThread = new Thread(new Runnable() {
            public void run() {
                try {
                    InetAddress broadcastIP = getBroadcastAddress();
                    Integer port = 8003;
                    while (shouldRestartSocketListen) {
                        listenAndWaitAndThrowIntent(broadcastIP, port);
                    }
                    //if (!shouldListenForUDPBroadcast) throw new ThreadDeath();
                } catch (Exception e) {
                    Log.i(LOG_TAG, "no longer listening for UDP broadcasts cause of error " + e.getMessage());
                }
            }
        });
        UDPBroadcastThread.start();
    }

    private Boolean shouldRestartSocketListen=true;

    void stopListen() {
        shouldRestartSocketListen = false;
        socket.close();
    }

    @Override
    public void onCreate() {
    }

    @Override
    public void onDestroy() {
        stopListen();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        shouldRestartSocketListen = true;
        startListenForUDPBroadcast();
        Log.i(LOG_TAG, "Service started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}

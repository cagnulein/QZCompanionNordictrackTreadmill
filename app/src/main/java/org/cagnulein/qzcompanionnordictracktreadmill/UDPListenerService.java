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

    public enum _device {
        x11i,
        nordictrack_2950,
        other
    }

    static _device device;

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

        Log.i(LOG_TAG, "Got UDP broadcast from " + senderIP + ", message: " + message);

        Log.i(LOG_TAG, message);
        String[] amessage = message.split(";");
        if(amessage.length > 0) {
            String rSpeed = amessage[0];
            float reqSpeed = Float.parseFloat(rSpeed);
            Log.i(LOG_TAG, "requestSpeed: " + reqSpeed + " " + lastReqSpeed);

            if (reqSpeed != -1 && lastReqSpeed != reqSpeed) {
                int x1 = 0;
                int y2 = 0;
                if(device == _device.x11i) {
                    x1 = 1207;
                    y2 = (int) (621.997 - (21.785 * reqSpeed));
                } else if(device == _device.nordictrack_2950) {
                    x1 = 1845;     //middle of slider
                    y1Speed = 807 - (int)((Float.parseFloat(QZService.lastSpeed) - 1) * 29.78);
                    //set speed slider to target position
                    y2 = y1Speed - (int)((reqSpeed - Float.parseFloat(QZService.lastSpeed)) * 29.78);
                }

                String command = "input swipe " + x1 + " " + y1Speed + " " + x1 + " " + y2 + " 200";
                MainActivity.sendCommand(command);
                Log.i(LOG_TAG, command);

                if(device == _device.x11i)
                    y1Speed = y2;  //set new vertical position of speed slider
                lastReqSpeed = reqSpeed;
            }
        }

        if(amessage.length > 1) {
            String rInclination = amessage[1];
            float reqInclination = Float.parseFloat(rInclination);
            Log.i(LOG_TAG, "requestInclination: " + reqInclination + " " + lastReqInclination);
            if(reqInclination != -100 && lastReqInclination != reqInclination) {
                int x1 = 0;
                int y2 = 0;
                if(device == _device.x11i) {
                    x1 = 75;
                    y2 = (int) (565.491 - (8.44 * reqInclination));
                } else if(device == _device.nordictrack_2950) {
                    x1 = 75;     //middle of slider
                    y1Inclination = 807 - (int)((Float.parseFloat(QZService.lastInclination) - 1) * 29.78);
                    //set speed slider to target position
                    y2 = y1Inclination - (int)((reqInclination - Float.parseFloat(QZService.lastInclination)) * 29.9);
                }

                String command = " input swipe " + x1 + " " + y1Inclination + " " + x1 + " " + y2 + " 200";
                MainActivity.sendCommand(command);
                Log.i(LOG_TAG, command);

                if(device == _device.x11i)
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
        device = _device.x11i;
        if(device == _device.x11i) {
            lastReqSpeed = -1;
            y1Speed = 600;      //vertical position of slider at 2.0
            lastReqInclination = -1;
            y1Inclination = 557;    //vertical position of slider at 0.0
        } else if(device == _device.nordictrack_2950) {
            lastReqSpeed = -1;
            y1Speed = 807;      //vertical position of slider at 2.0
            lastReqInclination = -1;
            y1Inclination = 807;    //vertical position of slider at 0.0
        }
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

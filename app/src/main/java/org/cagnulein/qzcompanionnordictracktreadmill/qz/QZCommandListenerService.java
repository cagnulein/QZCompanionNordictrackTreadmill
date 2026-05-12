package org.cagnulein.qzcompanionnordictracktreadmill.qz;

import org.cagnulein.qzcompanionnordictracktreadmill.BuildConfig;
import org.cagnulein.qzcompanionnordictracktreadmill.ui.MainActivity;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/*
 * Linux command to send UDP:
 * #socat - UDP-DATAGRAM:192.168.1.255:8002,broadcast,sp=8002
 */
public class QZCommandListenerService extends Service {
    private static final String LOG_TAG = "QZ:CmdListener";

    /** Wall-clock ms of the last -100;N heartbeat packet from QZ. 0 = never received. */
    public static volatile long lastQzHeartbeatMs = 0;

    /** Source IP of the last QZ heartbeat; null until first heartbeat received. */
    public static volatile java.net.InetAddress qzAddress = null;

    /** Staleness threshold: QZ is considered disconnected after this many ms without a heartbeat. */
    public static final long QZ_HEARTBEAT_TIMEOUT_MS = 30_000;

    /** UDP port this service listens on for incoming QZ commands. */
    public static final int LISTEN_PORT = 8003;

    private static final int UDP_BUFFER_SIZE = 15_000;

    static DatagramSocket socket;

    /** Singleton pointer — set in onCreate, cleared in onDestroy. */
    private static volatile QZCommandListenerService instance;

    /** Subscriber that receives parsed command packets. */
    private volatile QZCommandSubscriber subscriber = null;

    /**
     * Holds a subscriber set before the service instance exists.
     * MainActivity calls setSubscriber() during its own onCreate(), which runs before
     * the service's onCreate() fires — this field bridges that gap.
     */
    private static volatile QZCommandSubscriber pendingSubscriber = null;

    private PowerManager.WakeLock wakeLock;

    public static void setSubscriber(QZCommandSubscriber s) {
        pendingSubscriber = s;
        if (instance != null) instance.subscriber = s;
    }

    private void writeLog(String command) {
        if (MainActivity.isDebugLog()) {
            MainActivity.writeLog(command);
            Log.i(LOG_TAG, command);
            QZTelemetryUnicastingService.sendUnicast(command);
        }
    }

    private void listenAndWaitAndThrowIntent(Integer port) throws Exception {
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
        }

        wakeLock.acquire(10_000L); // 10-second timeout — auto-releases if receive hangs
        try {
            byte[] buf = new byte[UDP_BUFFER_SIZE];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            socket.receive(pkt);
            String msg = new String(pkt.getData(), 0, pkt.getLength()).trim();
            Log.d(LOG_TAG, "rx: " + msg);

            if (subscriber == null) {
                writeLog("Packet discarded: no subscriber");
                return;
            }

            if (msg.equals(QZCommandPacket.END_OF_RIDE)) {
                lastQzHeartbeatMs = 0;
                qzAddress = null;
            } else if (!msg.startsWith(QZCommandPacket.CALSWIPE_PREFIX)) {
                lastQzHeartbeatMs = System.currentTimeMillis();
                qzAddress = pkt.getAddress();
            } else {
                Log.i(LOG_TAG, "CALSWIPE routed to command subscriber");
            }
            subscriber.onPacket(QZCommandPacket.parse(msg));
        } finally {
            if (wakeLock.isHeld()) wakeLock.release();
        }
    }

    void startListenForUDP() {
        new Thread(() -> {
            int port = LISTEN_PORT;
            while (shouldRestartSocketListen) {
                try {
                    listenAndWaitAndThrowIntent(port);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "UDP receive error, continuing: " + e.getMessage());
                }
            }
        }).start();
    }

    private volatile boolean shouldRestartSocketListen = true;

    void stopListen() {
        shouldRestartSocketListen = false;
        socket.close();
    }

    @Override
    public void onCreate() {
        instance = this;
        if (pendingSubscriber != null) subscriber = pendingSubscriber;
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QZCompanion::UDPListener");
        Log.i(LOG_TAG, "QZCompanion v" + BuildConfig.VERSION_NAME
                + " (" + BuildConfig.VERSION_CODE + ") starting");
        Log.i(LOG_TAG, "Listening on UDP port " + LISTEN_PORT);
    }

    @Override
    public void onDestroy() {
        stopListen();
        instance = null;
        Log.i(LOG_TAG, "UDP listener stopped");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        shouldRestartSocketListen = true;
        startListenForUDP();
        Log.i(LOG_TAG, "UDP listener started on port " + LISTEN_PORT);
        writeLog("Service started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

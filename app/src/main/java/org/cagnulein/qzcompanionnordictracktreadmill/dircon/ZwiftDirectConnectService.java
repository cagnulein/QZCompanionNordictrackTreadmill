package org.cagnulein.qzcompanionnordictracktreadmill.dircon;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.IBinder;
import android.util.Log;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.ui.MainActivity;
import org.cagnulein.qzcompanionnordictracktreadmill.dircon.DirectConnectPacket;
import org.cagnulein.qzcompanionnordictracktreadmill.dircon.DirectConnectProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.dircon.DirectConnectServiceInfo;
import org.cagnulein.qzcompanionnordictracktreadmill.dircon.DirectConnectTrainerState;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.TelemetryHub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ZwiftDirectConnectService extends Service {
    private static final String LOG_TAG = "QZ:DirCon";

    public static final String PREF_ENABLED = "directConnectEnabled";

    private static volatile boolean running = false;
    private static volatile boolean advertising = false;
    private static volatile String connectedClient = null;
    private static volatile String lastError = null;

    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean shouldRun = false;
    private volatile ClientSession clientSession = null;
    private TelemetryHub.Subscription telemetrySubscription = null;
    private final DirectConnectTrainerState trainerState = new DirectConnectTrainerState();
    private ScheduledExecutorService heartbeatExecutor = null;

    public static boolean isRunning() { return running; }
    public static boolean isAdvertising() { return advertising; }
    public static String connectedClient() { return connectedClient; }
    public static String lastError() { return lastError; }

    @Override
    public void onCreate() {
        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startDirectConnect();
        return START_STICKY;
    }

    private synchronized void startDirectConnect() {
        if (running) return;
        shouldRun = true;
        running = true;
        lastError = null;
        subscribeTelemetry();
        registerService();
        startTcpServer();
        startHeartbeat();
        Log.i(LOG_TAG, "Direct Connect service started");
    }

    private void subscribeTelemetry() {
        try {
            telemetrySubscription = TelemetryHub.shared().subscribe(this::publishTelemetry);
        } catch (IOException e) {
            lastError = "telemetry unavailable: " + e.getMessage();
            Log.e(LOG_TAG, lastError, e);
        }
    }

    private void publishTelemetry(Telemetry telemetry) {
        trainerState.apply(telemetry);
        ClientSession session = clientSession;
        if (session != null) session.publishIndoorBikeData();
    }

    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "QZ:DirConHeartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            ClientSession session = clientSession;
            if (session != null) session.publishMeasurements();
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void registerService() {
        DirectConnectServiceInfo info = DirectConnectServiceInfo.defaultInfo();
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(info.name);
        serviceInfo.setServiceType(info.type);
        serviceInfo.setPort(info.port);
        for (Map.Entry<String, String> entry : info.txtRecords.entrySet()) {
            serviceInfo.setAttribute(entry.getKey(), entry.getValue());
        }

        registrationListener = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                advertising = true;
                lastError = null;
                Log.i(LOG_TAG, "mDNS registered: " + nsdServiceInfo.getServiceName());
            }

            @Override public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                advertising = false;
                lastError = "mDNS registration failed: " + errorCode;
                Log.e(LOG_TAG, lastError);
            }

            @Override public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                advertising = false;
                Log.i(LOG_TAG, "mDNS unregistered");
            }

            @Override public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                lastError = "mDNS unregistration failed: " + errorCode;
                Log.e(LOG_TAG, lastError);
            }
        };

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        } catch (RuntimeException e) {
            advertising = false;
            lastError = "mDNS registration error: " + e.getMessage();
            Log.e(LOG_TAG, lastError, e);
        }
    }

    private void startTcpServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(DirectConnectServiceInfo.DEFAULT_PORT);
                Log.i(LOG_TAG, "TCP listening on " + DirectConnectServiceInfo.DEFAULT_PORT);
                while (shouldRun) {
                    Socket socket = serverSocket.accept();
                    if (clientSession != null && clientSession.isOpen()) {
                        Log.w(LOG_TAG, "rejecting second Direct Connect client: "
                                + socket.getInetAddress().getHostAddress());
                        socket.close();
                        continue;
                    }
                    clientSession = new ClientSession(socket);
                    clientSession.start();
                }
            } catch (IOException e) {
                if (shouldRun) {
                    lastError = "TCP server error: " + e.getMessage();
                    Log.e(LOG_TAG, lastError, e);
                }
            }
        }, "QZ:DirConTcp");
        serverThread.start();
    }

    private synchronized void stopDirectConnect() {
        shouldRun = false;
        running = false;
        advertising = false;
        connectedClient = null;

        if (telemetrySubscription != null) {
            telemetrySubscription.close();
            telemetrySubscription = null;
        }
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
        if (registrationListener != null && nsdManager != null) {
            try { nsdManager.unregisterService(registrationListener); }
            catch (IllegalArgumentException ignored) {}
            registrationListener = null;
        }
        ClientSession session = clientSession;
        if (session != null) session.close();
        clientSession = null;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        serverSocket = null;
        Log.i(LOG_TAG, "Direct Connect service stopped");
    }

    @Override
    public void onDestroy() {
        stopDirectConnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class ClientSession {
        private final Socket socket;
        private final DirectConnectProfile profile = new DirectConnectProfile();
        private final Set<Integer> enabledNotifications = new HashSet<>();
        private OutputStream output;
        private int sequenceNumber = 0;
        private volatile boolean open = true;

        ClientSession(Socket socket) {
            this.socket = socket;
        }

        boolean isOpen() {
            return open && !socket.isClosed();
        }

        void start() {
            new Thread(this::run, "QZ:DirConClient").start();
        }

        private void run() {
            connectedClient = socket.getInetAddress().getHostAddress();
            Log.i(LOG_TAG, "client connected: " + connectedClient);
            try {
                InputStream input = socket.getInputStream();
                output = socket.getOutputStream();
                byte[] readBuffer = new byte[4096];
                ByteArrayOutputStream pending = new ByteArrayOutputStream();
                int read;
                while (shouldRun && (read = input.read(readBuffer)) >= 0) {
                    pending.write(readBuffer, 0, read);
                    processPending(pending);
                }
            } catch (IOException e) {
                if (shouldRun) Log.w(LOG_TAG, "client session ended: " + e.getMessage());
            } finally {
                close();
                if (clientSession == this) clientSession = null;
                connectedClient = null;
                Log.i(LOG_TAG, "client disconnected");
            }
        }

        private void processPending(ByteArrayOutputStream pending) throws IOException {
            byte[] bytes = pending.toByteArray();
            int offset = 0;
            while (offset < bytes.length) {
                byte[] remaining = java.util.Arrays.copyOfRange(bytes, offset, bytes.length);
                DirectConnectPacket.ParseResult parsed =
                        DirectConnectPacket.parse(remaining, sequenceNumber);
                if (parsed.status == DirectConnectPacket.ParseResult.WAIT) break;
                int consumed = parsed.consumed > 0 ? parsed.consumed : remaining.length;
                offset += consumed;
                if (parsed.status == DirectConnectPacket.ParseResult.ERROR) {
                    sendUnexpectedError(parsed.packet);
                    continue;
                }
                handlePacket(parsed.packet);
            }
            pending.reset();
            if (offset < bytes.length) pending.write(bytes, offset, bytes.length - offset);
        }

        private void handlePacket(DirectConnectPacket packet) {
            if (MainActivity.isDebugLog()) {
                Log.d(LOG_TAG, "packet id=" + packet.identifier + " uuid="
                        + Integer.toHexString(packet.uuid) + " req=" + packet.request);
            }
            if (packet.request) sequenceNumber = packet.sequenceNumber;
            else if (packet.identifier != DirectConnectPacket.MSG_NOTIFICATION) sequenceNumber += 1;

            DirectConnectProfile.Result result = profile.process(packet, enabledNotifications);
            if (result.response.identifier != DirectConnectPacket.MSG_ERROR) {
                send(result.response.encode(packet.sequenceNumber));
            }
            List<Command> commands = result.commands;
            for (Command command : commands) {
                Log.i(LOG_TAG, "control: " + command);
                if (!DirectConnectCommandBridge.submit(command)) {
                    lastError = "No active device controller for Direct Connect command";
                    Log.w(LOG_TAG, lastError);
                }
            }
            byte[] controlPointAnswer = profile.controlPointAnswer();
            if (controlPointAnswer.length > 0) {
                send(profile.notification(DirectConnectProfile.CHAR_FTMS_CONTROL_POINT, controlPointAnswer));
            }
        }

        private void sendUnexpectedError(DirectConnectPacket packet) {
            DirectConnectPacket response = new DirectConnectPacket();
            response.request = false;
            response.identifier = packet != null ? packet.identifier : DirectConnectPacket.MSG_ERROR;
            response.sequenceNumber = packet != null ? packet.sequenceNumber : 0;
            response.responseCode = DirectConnectPacket.RESP_UNEXPECTED_ERROR;
            send(response.encode(response.sequenceNumber));
        }

        void publishIndoorBikeData() {
            if (!isOpen()) return;
            if (!enabledNotifications.contains(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA)) return;
            send(profile.notification(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA,
                    profile.indoorBikeData(trainerState)));
        }

        void publishMeasurements() {
            if (!isOpen()) return;
            long now = System.currentTimeMillis();
            publishIndoorBikeData();
            if (enabledNotifications.contains(DirectConnectProfile.CHAR_CYCLING_POWER_MEASUREMENT)) {
                send(profile.notification(DirectConnectProfile.CHAR_CYCLING_POWER_MEASUREMENT,
                        profile.cyclingPowerMeasurement(trainerState, now)));
            }
            if (enabledNotifications.contains(DirectConnectProfile.CHAR_CSC_MEASUREMENT)) {
                send(profile.notification(DirectConnectProfile.CHAR_CSC_MEASUREMENT,
                        profile.cscMeasurement(trainerState, now)));
            }
        }

        synchronized void send(byte[] frame) {
            if (output == null || !isOpen() || frame.length == 0) return;
            try {
                output.write(frame);
                output.flush();
            } catch (IOException e) {
                Log.w(LOG_TAG, "send failed: " + e.getMessage());
                close();
            }
        }

        void close() {
            open = false;
            try { socket.close(); }
            catch (IOException ignored) {}
        }
    }
}

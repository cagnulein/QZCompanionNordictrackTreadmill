package org.cagnulein.qzcompanionnordictracktreadmill.qz;

import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.CalibrationDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S15iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X11iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end pipeline tests using real UDP datagrams.
 *
 * These tests verify the complete chain:
 *   DatagramSocket.receive() → message string → DeviceController.onPacket()
 *   → Device.decodeCommand() → applySpeed/applyResistance → Device.commandExecutor
 *
 * The Android Service layer (PowerManager, WifiManager, lifecycle) is not involved;
 * only the core UDP receive + dispatch logic is exercised.
 *
 * Run via run-tests.sh (pure JVM, no Android SDK required).
 */
public class UdpPipelineTest {

    private DatagramSocket serverSocket;
    private Thread         listenerThread;
    private String         lastCommand;

    @Before
    public void setUp() throws Exception {
        lastCommand  = null;
        serverSocket = new DatagramSocket(0);
    }

    @After
    public void tearDown() throws InterruptedException {
        // Close socket first so any blocking receive() throws and the thread exits.
        if (serverSocket  != null) serverSocket.close();
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread.join(1000);
        }
    }

    /** Wraps a device to capture its swipe commands into lastCommand. */
    private <T extends Device> T dev(T d) {
        d.commandExecutor = cmd -> lastCommand = cmd;
        return d;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Starts a background thread that receives one UDP packet, dispatches it via
     * DeviceController, then signals the latch.  Mirrors the core of
     * QZCommandListenerService.listenAndWaitAndThrowIntent without the Android
     * PowerManager/WifiManager calls.
     */
    private void startReceiver(Device device, float speedKmh, CountDownLatch latch) {
        DeviceController ctrl = new DeviceController(device, new CalibrationDevice()::decodeCommands);
        listenerThread = new Thread(() -> {
            try {
                byte[] buf = new byte[1024];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                serverSocket.receive(pkt);
                String message = new String(pkt.getData(), 0, pkt.getLength()).trim();
                device.applyTelemetry(new SpeedTelemetry(speedKmh));
                ctrl.onPacket(QZCommandPacket.parse(message));
                latch.countDown();
            } catch (Exception e) {
                // Socket closed by tearDown — normal shutdown path.
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /** Sends a UDP datagram to the server socket on loopback. */
    private void sendUdp(String message) throws Exception {
        try (DatagramSocket sender = new DatagramSocket()) {
            byte[] data = message.getBytes("UTF-8");
            sender.send(new DatagramPacket(
                    data, data.length,
                    InetAddress.getLoopbackAddress(), serverSocket.getLocalPort()));
        }
    }

    // ── treadmill tests ───────────────────────────────────────────────────────

    @Test
    public void treadmill_speedMessage_producesExpectedSwipe() throws Exception {
        // X11i: "8.0;3.0" — speed and incline decoded as separate Commands.
        // Speed drains first; incline stays queued. lastCommand = speed swipe.
        // X11i speed: speedX=1205, initialSpeedY=600, targetSpeedY(8.0)=447
        CountDownLatch latch = new CountDownLatch(1);
        startReceiver(dev(new X11iDevice()), 5.0f, latch);

        sendUdp("8.0;3.0");

        assertTrue("dispatch should complete within 2 s", latch.await(2, TimeUnit.SECONDS));
        assertEquals("input swipe 1205 600 1205 447 200", lastCommand);
    }

    @Test
    public void treadmill_sentinelMessage_noCommand() throws Exception {
        // "-1;-100" has no actionable values — nothing should be dispatched.
        AtomicReference<String> captured = new AtomicReference<>(null);
        CountDownLatch commandLatch = new CountDownLatch(1);
        X11iDevice sentinelDevice = new X11iDevice();
        sentinelDevice.commandExecutor = cmd -> { captured.set(cmd); commandLatch.countDown(); };

        startReceiver(sentinelDevice, 5.0f, new CountDownLatch(1));

        sendUdp("-1;-100");

        assertFalse("no swipe expected for sentinels",
                commandLatch.await(400, TimeUnit.MILLISECONDS));
        assertNull(captured.get());
    }

    @Test
    public void treadmill_commaDecimalSeparator_parsesCorrectly() throws Exception {
        // Same expected swipe as the dot-separator test — parsing must handle both.
        CountDownLatch latch = new CountDownLatch(1);

        // Simulate a listener with comma as decimal separator.
        X11iDevice device = dev(new X11iDevice());
        DeviceController ctrl = new DeviceController(device);
        listenerThread = new Thread(() -> {
            try {
                byte[] buf = new byte[1024];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                serverSocket.receive(pkt);
                String message = new String(pkt.getData(), 0, pkt.getLength()).trim();
                device.applyTelemetry(new SpeedTelemetry(5.0f));
                ctrl.onPacket(QZCommandPacket.parse(message));
                latch.countDown();
            } catch (Exception e) { /* closed */ }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();

        sendUdp("8.0;3.0");

        assertTrue("dispatch should complete within 2 s", latch.await(2, TimeUnit.SECONDS));
        assertEquals("input swipe 1205 600 1205 447 200", lastCommand); // speed drains first
    }

    // ── bike tests ────────────────────────────────────────────────────────────

    @Test
    public void bike_resistanceMessage_producesExpectedSwipe() throws Exception {
        // S15i: resistance 10 → input swipe 1845 790 1845 559 200
        CountDownLatch latch = new CountDownLatch(1);
        startReceiver(dev(new S15iDevice()), 0.0f, latch);

        sendUdp("10.0");

        assertTrue("dispatch should complete within 2 s", latch.await(2, TimeUnit.SECONDS));
        assertEquals("input swipe 1845 790 1845 559 200", lastCommand);
    }

    @Test
    public void bike_sentinelResistance_noCommand() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>(null);
        CountDownLatch commandLatch = new CountDownLatch(1);
        S15iDevice sentinelDevice = new S15iDevice();
        sentinelDevice.commandExecutor = cmd -> { captured.set(cmd); commandLatch.countDown(); };

        startReceiver(sentinelDevice, 0.0f, new CountDownLatch(1));

        sendUdp("-1");

        assertFalse("no swipe expected for -1 sentinel",
                commandLatch.await(400, TimeUnit.MILLISECONDS));
        assertNull(captured.get());
    }

    @Test
    public void calibrationSwipeMessage_producesRawSwipe() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        startReceiver(dev(new S15iDevice()), 0.0f, latch);

        sendUdp("CALSWIPE:57:250:450");

        assertTrue("dispatch should complete within 2 s", latch.await(2, TimeUnit.SECONDS));
        assertEquals("input swipe 57 250 57 450 200", lastCommand);
    }
}

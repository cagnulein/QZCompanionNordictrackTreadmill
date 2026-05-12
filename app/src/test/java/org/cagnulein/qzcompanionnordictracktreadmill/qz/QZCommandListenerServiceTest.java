package org.cagnulein.qzcompanionnordictracktreadmill.qz;

import android.content.Intent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.CalibrationDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S15iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X11iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;

import static org.junit.Assert.*;

/**
 * End-to-end Robolectric tests for QZCommandListenerService.
 *
 * These tests exercise the full pipeline:
 *   real UDP datagram → service receive loop → DeviceController → Device.execute()
 *
 * Android framework classes (PowerManager, WifiManager, SharedPreferences) are
 * provided by Robolectric shadows — no physical device or emulator needed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class QZCommandListenerServiceTest {

    private ServiceController<QZCommandListenerService> controller;
    private String lastCommand;

    @Before
    public void setUp() {
        lastCommand = null;
    }

    @After
    public void tearDown() {
        QZCommandListenerService.setSubscriber(null);
        if (controller != null) {
            try { controller.destroy(); } catch (Exception ignored) {}
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    public void serviceCreates_withoutThrowingExceptions() {
        controller = Robolectric.buildService(QZCommandListenerService.class);
        assertNotNull(controller.create().get());
    }

    @Test
    public void serviceStarts_withoutThrowingExceptions() {
        controller = Robolectric.buildService(QZCommandListenerService.class);
        QZCommandListenerService svc = controller.create().startCommand(0, 1).get();
        assertNotNull(svc);
    }

    @Test
    public void serviceDestroysCleanly() {
        controller = Robolectric.buildService(QZCommandListenerService.class);
        controller.create().startCommand(0, 1).destroy();
        // No exception = pass
    }

    // ── Full UDP pipeline ─────────────────────────────────────────────────────

    @Test
    public void treadmill_udpMessage_producesExpectedSwipe() throws Exception {
        // X11i: "8.0;3.0" — speed and incline decoded as separate Commands.
        // Speed drains first; incline stays queued. Latch fires on the speed swipe.
        // X11i speed: speedX=1205, initialSpeedY=600, targetSpeedY(8.0)=447
        X11iDevice x11i = new X11iDevice();
        x11i.applyTelemetry(new SpeedTelemetry(5.0f));
        CountDownLatch latch = new CountDownLatch(1);
        x11i.commandExecutor = cmd -> { lastCommand = cmd; latch.countDown(); };

        controller = Robolectric.buildService(QZCommandListenerService.class);
        controller.create().startCommand(0, 1);
        QZCommandListenerService.setSubscriber(new DeviceController(x11i));

        sendUdp("8.0;3.0");

        assertTrue("swipe command should arrive within 3 s", latch.await(3, TimeUnit.SECONDS));
        assertEquals("input swipe 1205 600 1205 447 200", lastCommand);
    }

    @Test
    public void bike_udpMessage_producesExpectedSwipe() throws Exception {
        // S15i: resistance level 10 from a stopped device
        // Expected: input swipe 1845 790 1845 559 200
        S15iDevice s15i = new S15iDevice();
        CountDownLatch latch = new CountDownLatch(1);
        s15i.commandExecutor = cmd -> { lastCommand = cmd; latch.countDown(); };

        controller = Robolectric.buildService(QZCommandListenerService.class);
        controller.create().startCommand(0, 1);
        QZCommandListenerService.setSubscriber(new DeviceController(s15i));

        sendUdp("10.0");

        assertTrue("swipe command should arrive within 3 s", latch.await(3, TimeUnit.SECONDS));
        assertEquals("input swipe 1845 790 1845 559 200", lastCommand);
    }

    @Test
    public void noDevice_selected_noCommandProduced() throws Exception {
        // When no subscriber is set the service should silently drop the message.
        CountDownLatch latch = new CountDownLatch(1);

        controller = Robolectric.buildService(QZCommandListenerService.class);
        controller.create().startCommand(0, 1);
        // No setSubscriber() call — subscriber remains null

        sendUdp("8.0;3.0");

        // Give the service a moment to process (or not).
        assertFalse("no command should be generated when subscriber is null",
                    latch.await(500, TimeUnit.MILLISECONDS));
        assertNull(lastCommand);
    }

    @Test
    public void sentinel_message_noCommandProduced() throws Exception {
        // "-1;-100" is the all-sentinel message — no values to apply.
        X11iDevice x11i2 = new X11iDevice();
        x11i2.applyTelemetry(new SpeedTelemetry(5.0f));
        CountDownLatch latch = new CountDownLatch(1);
        x11i2.commandExecutor = cmd -> { lastCommand = cmd; latch.countDown(); };

        controller = Robolectric.buildService(QZCommandListenerService.class);
        controller.create().startCommand(0, 1);
        QZCommandListenerService.setSubscriber(new DeviceController(x11i2));

        sendUdp("-1;-100");

        assertFalse("no command should be generated for sentinels",
                    latch.await(500, TimeUnit.MILLISECONDS));
        assertNull(lastCommand);
    }

    @Test
    public void calibrationSwipe_udpMessage_producesRawSwipeWithoutHeartbeat() throws Exception {
        S15iDevice s15i = new S15iDevice();
        CountDownLatch latch = new CountDownLatch(1);
        s15i.commandExecutor = cmd -> { lastCommand = cmd; latch.countDown(); };
        QZCommandListenerService.qzAddress = null;
        QZCommandListenerService.lastQzHeartbeatMs = 0;

        controller = Robolectric.buildService(QZCommandListenerService.class);
        controller.create().startCommand(0, 1);
        QZCommandListenerService.setSubscriber(new DeviceController(s15i, new CalibrationDevice()::decodeCommands));

        sendUdp("CALSWIPE:57:250:450");

        assertTrue("raw swipe command should arrive within 3 s", latch.await(3, TimeUnit.SECONDS));
        assertEquals("input swipe 57 250 57 450 200", lastCommand);
        assertNull(QZCommandListenerService.qzAddress);
        assertEquals(0, QZCommandListenerService.lastQzHeartbeatMs);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Sends a single UDP datagram to the service's listen port (8003) on loopback.
     * A brief sleep before sending gives the service thread time to bind its socket.
     */
    private static void sendUdp(String message) throws Exception {
        // Wait for the service's background thread to open its DatagramSocket.
        Thread.sleep(200);

        try (DatagramSocket sender = new DatagramSocket()) {
            byte[] data = message.getBytes("UTF-8");
            sender.send(new DatagramPacket(
                    data, data.length,
                    InetAddress.getLoopbackAddress(), 8003));
        }
    }
}

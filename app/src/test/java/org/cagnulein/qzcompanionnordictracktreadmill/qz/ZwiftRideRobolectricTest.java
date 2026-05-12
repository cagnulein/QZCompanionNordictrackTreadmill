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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S22iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X11iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;

import static org.junit.Assert.*;

/**
 * Robolectric integration tests for multi-grade Zwift ride sequences.
 *
 * These tests send multiple UDP messages through the live QZCommandListenerService
 * socket, verifying that the full pipeline — service receive loop →
 * CommandDispatcher → iFit1 command handler → Device.swipe() — produces
 * the correct swipe chain across throttle window boundaries.
 *
 * Unlike ZwiftRideSimulationTest (which injects time), these tests use real
 * wall-clock time and real UDP sockets, so throttle behavior is exercised as
 * it will run on the device.  Messages are spaced 600ms apart — just outside
 * the 500ms throttle window — so every grade change should produce a swipe.
 *
 * S22i formula: x=75, piecewise — v≤0: (int)(622-10*v); v>0: (int)(622-18.57*v). Calibrated 2026-04-19.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ZwiftRideRobolectricTest {

    private ServiceController<QZCommandListenerService> controller;
    private final List<String> commands = new ArrayList<>();

    @Before
    public void setUp() { commands.clear(); }

    @After
    public void tearDown() {
        QZCommandListenerService.setSubscriber(null);
        if (controller != null) {
            try { controller.destroy(); } catch (Exception ignored) {}
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static int targetThumbY(float grade) {
        return grade <= 0.0f ? (int) (622.0 - 10.0 * grade)
                             : (int) (622.0 - 18.57 * grade);
    }

    private static String swipe(int y1, int y2) {
        return "input swipe 75 " + y1 + " 75 " + y2 + " 200";
    }

    private static int dispatchY(int fromY, int toY) {
        return toY;
    }

    /**
     * Sends a UDP grade change to port 8003 on loopback and waits 600ms.
     * The 600ms gap ensures each message arrives outside the 500ms throttle window.
     */
    private static void sendGrade(float grade) throws Exception {
        try (DatagramSocket sender = new DatagramSocket()) {
            String msg = grade + ";0";
            byte[] data = msg.getBytes("UTF-8");
            sender.send(new DatagramPacket(
                    data, data.length,
                    InetAddress.getLoopbackAddress(), 8003));
        }
        Thread.sleep(600);
    }

    /** Starts the service and waits for its socket to open. */
    private void startService() throws InterruptedException {
        controller = Robolectric.buildService(QZCommandListenerService.class);
        controller.create().startCommand(0, 1);
        Thread.sleep(200);  // allow background thread to bind its DatagramSocket
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    /**
     * Three grade changes spaced 600ms apart (outside the 500ms throttle window).
     * Each should produce exactly one swipe, and y1 of each swipe must match y2
     * of the previous — confirming the Slider's thumbY state is maintained end-to-end
     * through the service socket layer.
     */
    @Test
    public void s22i_threeGradeChanges_correctSwipeChain() throws Exception {
        S22iDevice device = new S22iDevice();
        CountDownLatch latch = new CountDownLatch(3);
        device.commandExecutor = cmd -> { commands.add(cmd); latch.countDown(); };

        startService();
        QZCommandListenerService.setSubscriber(new DeviceController(device));

        float[] grades = {5f, 10f, 0f};
        for (float g : grades) {
            sendGrade(g);
        }

        assertTrue("all 3 swipes should arrive within 5s", latch.await(5, TimeUnit.SECONDS));
        assertEquals(3, commands.size());

        int logicalY = 622;
        for (int i = 0; i < grades.length; i++) {
            int toY = targetThumbY(grades[i]);
            assertEquals("swipe " + i + " (grade " + grades[i] + "%)",
                    swipe(logicalY, dispatchY(logicalY, toY)), commands.get(i));
            logicalY = toY;
        }
    }

    /**
     * Sends the same grade twice through the service.  The second message should
     * be suppressed by de-dup (same value as lastApplied), producing only one swipe.
     */
    @Test
    public void s22i_duplicateGrade_onlyOneSwipeFires() throws Exception {
        S22iDevice device = new S22iDevice();
        CountDownLatch firstSwipe = new CountDownLatch(1);
        device.commandExecutor = cmd -> { commands.add(cmd); firstSwipe.countDown(); };

        startService();
        QZCommandListenerService.setSubscriber(new DeviceController(device));

        sendGrade(7f);
        assertTrue("first swipe should arrive", firstSwipe.await(3, TimeUnit.SECONDS));

        // Send the same grade again — should be de-duped
        CountDownLatch secondSwipe = new CountDownLatch(1);
        device.commandExecutor = cmd -> { commands.add(cmd); secondSwipe.countDown(); };
        sendGrade(7f);

        assertFalse("duplicate grade should be suppressed by de-dup",
                secondSwipe.await(400, TimeUnit.MILLISECONDS));
        assertEquals("exactly one swipe expected", 1, commands.size());
    }

    /**
     * Sends a "-1;-100" sentinel through the real service socket.
     * No swipe should be produced.
     */
    @Test
    public void s22i_sentinelMessage_noSwipeFires() throws Exception {
        S22iDevice device = new S22iDevice();
        CountDownLatch latch = new CountDownLatch(1);
        device.commandExecutor = cmd -> { commands.add(cmd); latch.countDown(); };

        startService();
        QZCommandListenerService.setSubscriber(new DeviceController(device));
        Thread.sleep(200);

        try (DatagramSocket sender = new DatagramSocket()) {
            byte[] data = "-1;-100".getBytes("UTF-8");
            sender.send(new DatagramPacket(data, data.length,
                    InetAddress.getLoopbackAddress(), 8003));
        }

        assertFalse("sentinel should produce no swipe", latch.await(500, TimeUnit.MILLISECONDS));
        assertTrue(commands.isEmpty());
    }

    /**
     * No device selected — service should silently discard the packet.
     */
    @Test
    public void noDevice_messageDiscarded() throws Exception {
        // No setSubscriber() call — subscriber remains null
        CountDownLatch latch = new CountDownLatch(1);

        startService();
        Thread.sleep(200);

        try (DatagramSocket sender = new DatagramSocket()) {
            byte[] data = "7.0;0".getBytes("UTF-8");
            sender.send(new DatagramPacket(data, data.length,
                    InetAddress.getLoopbackAddress(), 8003));
        }

        assertFalse("no swipe expected when subscriber is null", latch.await(500, TimeUnit.MILLISECONDS));
        assertTrue(commands.isEmpty());
    }

    /**
     * Treadmill (X11i) through the service: speed+incline message decoded as two Commands.
     * Speed drains first; incline stays queued. Only the speed swipe fires here.
     * X11i speed: speedX=1205, initialSpeedY=600, targetSpeedY(8.0)=447
     */
    @Test
    public void x11i_speedMessage_producesExpectedSwipe() throws Exception {
        X11iDevice x11i = new X11iDevice();
        x11i.applyTelemetry(new SpeedTelemetry(5.0f));
        CountDownLatch latch = new CountDownLatch(1);
        x11i.commandExecutor = cmd -> { commands.add(cmd); latch.countDown(); };

        startService();
        QZCommandListenerService.setSubscriber(new DeviceController(x11i));
        Thread.sleep(200);

        try (DatagramSocket sender = new DatagramSocket()) {
            byte[] data = "8.0;3.0".getBytes("UTF-8");
            sender.send(new DatagramPacket(data, data.length,
                    InetAddress.getLoopbackAddress(), 8003));
        }

        assertTrue("swipe should arrive within 3s", latch.await(3, TimeUnit.SECONDS));
        assertEquals(1, commands.size());
        assertEquals("input swipe 1205 600 1205 447 200", commands.get(0)); // speed
    }
}

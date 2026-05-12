package org.cagnulein.qzcompanionnordictracktreadmill.perf;

import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.bike.S22iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandListenerService;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * API 25 connected smoke test for the runtime service path.
 *
 * This intentionally mocks iFit at the QZ command boundary: the emulator does not need the iFit APK
 * or NordicTrack firmware. The test proves the app service can receive UDP packets on Android 7.1,
 * dispatch through the selected device, and produce swipe commands under a short replay.
 */
@RunWith(AndroidJUnit4.class)
public class Api25ServicePerformanceSmokeTest {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @After
    public void tearDown() {
        QZCommandListenerService.setSubscriber(null);
        try {
            context.stopService(new Intent(context, QZCommandListenerService.class));
        } catch (Exception ignored) {}
    }

    @Test
    public void commandListenerService_replaysUdpPacketsOnLoopback() throws Exception {
        List<String> commands = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(4);

        S22iDevice device = new S22iDevice();
        device.commandExecutor = cmd -> {
            commands.add(cmd);
            latch.countDown();
        };

        QZCommandListenerService.setSubscriber(new DeviceController(device));
        context.startService(new Intent(context, QZCommandListenerService.class));
        Thread.sleep(500);

        sendUdp("0.0;0");
        Thread.sleep(650);
        sendUdp("5.0;0");
        Thread.sleep(650);
        sendUdp("10.0;0");
        Thread.sleep(650);
        sendUdp("0.0;0");

        assertTrue("expected four replay commands within 8s", latch.await(8, TimeUnit.SECONDS));
        assertEquals(4, commands.size());
        assertEquals("input swipe 75 622 75 622 200", commands.get(0));
        assertEquals("input swipe 75 622 75 529 200", commands.get(1));
        assertEquals("input swipe 75 529 75 436 200", commands.get(2));
        assertEquals("input swipe 75 436 75 622 200", commands.get(3));
    }

    private static void sendUdp(String message) throws Exception {
        try (DatagramSocket sender = new DatagramSocket()) {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            sender.send(new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName("127.0.0.1"),
                    QZCommandListenerService.LISTEN_PORT));
        }
    }
}

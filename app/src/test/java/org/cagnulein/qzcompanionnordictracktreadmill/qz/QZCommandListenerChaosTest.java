package org.cagnulein.qzcompanionnordictracktreadmill.qz;

import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S22iDevice;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Destructive Robolectric coverage for UDP service lifecycle edges.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class QZCommandListenerChaosTest {

    private ServiceController<QZCommandListenerService> controller;

    @After
    public void tearDown() {
        QZCommandListenerService.setSubscriber(null);
        if (controller != null) {
            try { controller.destroy(); } catch (Exception ignored) {}
        }
    }

    @Test
    public void repeatedStartStop_rebindsUdpSocketAndStillDispatches() throws Exception {
        for (int i = 0; i < 3; i++) {
            CountDownLatch latch = new CountDownLatch(1);
            S22iDevice device = new S22iDevice();
            device.commandExecutor = cmd -> latch.countDown();

            controller = Robolectric.buildService(QZCommandListenerService.class);
            controller.create().startCommand(0, i + 1);
            QZCommandListenerService.setSubscriber(new DeviceController(device));

            sendUdp("5.0;0");
            assertTrue("service should dispatch after restart " + i,
                    latch.await(3, TimeUnit.SECONDS));

            controller.destroy();
            controller = null;
            QZCommandListenerService.setSubscriber(null);
            Thread.sleep(100);
        }
    }

    @Test
    public void rapidPacketsWithoutSubscriber_doNotCrashService() throws Exception {
        controller = Robolectric.buildService(QZCommandListenerService.class);
        controller.create().startCommand(0, 1);

        for (int i = 0; i < 50; i++) {
            sendUdp((i % 12) + ".0;0");
        }

        controller.destroy();
        controller = null;
    }

    private static void sendUdp(String message) throws Exception {
        Thread.sleep(50);
        try (DatagramSocket sender = new DatagramSocket()) {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            sender.send(new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getLoopbackAddress(),
                    QZCommandListenerService.LISTEN_PORT));
        }
    }
}

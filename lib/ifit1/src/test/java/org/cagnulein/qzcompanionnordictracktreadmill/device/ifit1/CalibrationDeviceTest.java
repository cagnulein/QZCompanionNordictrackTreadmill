package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S15iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CalibrationDeviceTest {

    @Test
    public void decodeCommands_validCalSwipe_returnsRawSwipeCommand() {
        CalibrationDevice device = new CalibrationDevice();

        List<Command> commands = device.decodeCommands(
                QZCommandPacket.parse("CALSWIPE:57:250:450"));

        assertEquals(1, commands.size());
        assertEquals("rawSwipe(57,250->450)", commands.get(0).toString());
    }

    @Test
    public void decodeCommands_malformedCalSwipe_returnsEmpty() {
        CalibrationDevice device = new CalibrationDevice();

        assertTrue(device.decodeCommands(QZCommandPacket.parse("CALSWIPE:57:250")).isEmpty());
        assertTrue(device.decodeCommands(QZCommandPacket.parse("CALSWIPE:57:nope:450")).isEmpty());
    }

    @Test
    public void decodeCommands_nonCalibrationPacket_returnsEmpty() {
        CalibrationDevice device = new CalibrationDevice();

        assertTrue(device.decodeCommands(QZCommandPacket.parse("10.0")).isEmpty());
    }

    @Test
    public void deviceController_calSwipeDispatchesRawSwipeCommand() {
        String[] lastCommand = { null };
        S15iDevice device = new S15iDevice();
        device.commandExecutor = cmd -> lastCommand[0] = cmd;

        DeviceController controller = new DeviceController(device, new CalibrationDevice()::decodeCommands, () -> 1000);
        controller.onPacket(QZCommandPacket.parse("CALSWIPE:57:250:450"));

        assertEquals("input swipe 57 250 57 450 200", lastCommand[0]);
    }

    @Test
    public void deviceController_nonCalibrationPacketFallsBackToSelectedDevice() {
        String[] lastCommand = { null };
        S15iDevice device = new S15iDevice();
        device.commandExecutor = cmd -> lastCommand[0] = cmd;

        DeviceController controller = new DeviceController(device, new CalibrationDevice()::decodeCommands, () -> 1000);
        controller.onPacket(QZCommandPacket.parse("10.0"));

        assertEquals("input swipe 1845 790 1845 559 200", lastCommand[0]);
    }
}

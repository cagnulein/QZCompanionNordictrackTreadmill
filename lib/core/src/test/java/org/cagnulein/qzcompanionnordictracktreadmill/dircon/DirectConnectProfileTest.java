package org.cagnulein.qzcompanionnordictracktreadmill.dircon;

import org.cagnulein.qzcompanionnordictracktreadmill.command.InclineCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.command.ResistanceCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.CadenceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.WattsTelemetry;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectConnectProfileTest {
    @Test
    public void writeResistanceDecodesFtmsCommand() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = writeControlPoint(new byte[] { 0x04, 120 });

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(1, result.commands.size());
        assertTrue(result.commands.get(0) instanceof ResistanceCommand);
        assertEquals(12.0f, result.commands.get(0).value, 0.001f);
        assertEquals(DirectConnectPacket.RESP_SUCCESS, result.response.responseCode);
    }

    @Test
    public void writeSimulationGradeDecodesFtmsCommand() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = writeControlPoint(new byte[] { 0x11, 0, 0, (byte) 0xD4, (byte) 0xFE, 0x28, 0x19 });

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertEquals(1, result.commands.size());
        assertTrue(result.commands.get(0) instanceof InclineCommand);
        assertEquals(-3.0f, result.commands.get(0).value, 0.001f);
    }

    @Test
    public void cyclingPowerMeasurementContainsWattsAndCrankData() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new WattsTelemetry(225));
        state.apply(new CadenceTelemetry(60));

        profile.cyclingPowerMeasurement(state, 1_000);
        byte[] data = profile.cyclingPowerMeasurement(state, 2_000);

        assertEquals(0x30, data[0] & 0xFF);
        assertEquals(225, (data[2] & 0xFF) | ((data[3] & 0xFF) << 8));
        assertEquals(1, (data[10] & 0xFF) | ((data[11] & 0xFF) << 8));
    }

    @Test
    public void cscMeasurementContainsCrankData() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectTrainerState state = new DirectConnectTrainerState();
        state.apply(new CadenceTelemetry(120));

        profile.cscMeasurement(state, 1_000);
        byte[] data = profile.cscMeasurement(state, 2_000);

        assertEquals(0x02, data[0] & 0xFF);
        assertEquals(2, (data[1] & 0xFF) | ((data[2] & 0xFF) << 8));
    }

    @Test
    public void targetPowerIsAcknowledgedAndRemembered() {
        DirectConnectProfile profile = new DirectConnectProfile();
        DirectConnectPacket packet = writeControlPoint(new byte[] { 0x05, (byte) 0xC8, 0x00 });

        DirectConnectProfile.Result result = profile.process(packet, new HashSet<>());

        assertTrue(result.commands.isEmpty());
        assertEquals(200.0f, profile.lastTargetPowerWatts(), 0.001f);
        assertEquals(0x80, profile.controlPointAnswer()[0] & 0xFF);
        assertEquals(0x05, profile.controlPointAnswer()[1] & 0xFF);
        assertEquals(0x01, profile.controlPointAnswer()[2] & 0xFF);
    }

    private static DirectConnectPacket writeControlPoint(byte[] data) {
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = true;
        packet.identifier = DirectConnectPacket.MSG_WRITE_CHARACTERISTIC;
        packet.uuid = DirectConnectProfile.CHAR_FTMS_CONTROL_POINT;
        packet.additionalData = data;
        return packet;
    }
}

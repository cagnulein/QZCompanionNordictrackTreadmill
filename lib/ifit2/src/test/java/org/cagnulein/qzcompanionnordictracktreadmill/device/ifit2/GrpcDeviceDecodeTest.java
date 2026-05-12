package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit2;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.InclineCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.command.ResistanceCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.command.SpeedCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit2.CommandTransport;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Pure-JVM decode tests — no gRPC channel, no Android framework.
 * Verifies that QZCommandPacket strings map to the correct Command list
 * for each GrpcDevice subtype.
 */
public class GrpcDeviceDecodeTest {

    private static final CommandTransport NULL_TRANSPORT = (cmd, logger) -> true;

    private final GrpcBikeDevice bike = new GrpcBikeDevice(NULL_TRANSPORT);
    private final GrpcTreadmillDevice treadmill = new GrpcTreadmillDevice(NULL_TRANSPORT);

    // ── GrpcBikeDevice — single-field (resistance) ────────────────────────────

    @Test
    public void bike_singleField_resistance_decodesResistanceCommand() {
        List<Command> cmds = bike.decodeCommands(QZCommandPacket.parse("10.0"));
        assertEquals(1, cmds.size());
        assertCommand(ResistanceCommand.class, 10.0f, cmds.get(0));
    }

    @Test
    public void bike_singleField_noResistanceSentinel_returnsEmpty() {
        assertTrue(bike.decodeCommands(QZCommandPacket.parse("-1")).isEmpty());
    }

    @Test
    public void bike_singleField_noCommandSentinel_returnsEmpty() {
        assertTrue(bike.decodeCommands(QZCommandPacket.parse("-100")).isEmpty());
    }

    // ── GrpcBikeDevice — two-field (incline ; resistance) ────────────────────

    @Test
    public void bike_twoFields_decodesInclineAndResistance() {
        List<Command> cmds = bike.decodeCommands(QZCommandPacket.parse("3.5;2.0"));
        assertEquals(2, cmds.size());
        assertCommand(InclineCommand.class, 3.5f, cmds.get(0));
        assertCommand(ResistanceCommand.class, 2.0f, cmds.get(1));
    }

    @Test
    public void bike_twoFields_endOfRide_returnsEmpty() {
        assertTrue(bike.decodeCommands(QZCommandPacket.parse("-1;-100")).isEmpty());
    }

    @Test
    public void bike_twoFields_inclineOnlySentinelResistance_returnsInclineOnly() {
        List<Command> cmds = bike.decodeCommands(QZCommandPacket.parse("3.5;-100"));
        assertEquals(1, cmds.size());
        assertCommand(InclineCommand.class, 3.5f, cmds.get(0));
    }

    @Test
    public void bike_twoFields_sentinelInclineResistanceOnly_returnsResistanceOnly() {
        List<Command> cmds = bike.decodeCommands(QZCommandPacket.parse("-100;5.0"));
        assertEquals(1, cmds.size());
        assertCommand(ResistanceCommand.class, 5.0f, cmds.get(0));
    }

    // ── GrpcTreadmillDevice — two-field (speed ; incline) ────────────────────

    @Test
    public void treadmill_twoFields_decodesSpeedAndIncline() {
        List<Command> cmds = treadmill.decodeCommands(QZCommandPacket.parse("8.0;3.0"));
        assertEquals(2, cmds.size());
        assertCommand(SpeedCommand.class, 8.0f, cmds.get(0));
        assertCommand(InclineCommand.class, 3.0f, cmds.get(1));
    }

    @Test
    public void treadmill_twoFields_endOfRide_returnsEmpty() {
        assertTrue(treadmill.decodeCommands(QZCommandPacket.parse("-1;-100")).isEmpty());
    }

    @Test
    public void treadmill_singleField_returnsEmpty() {
        assertTrue(treadmill.decodeCommands(QZCommandPacket.parse("8.0")).isEmpty());
    }

    @Test
    public void treadmill_twoFields_sentinelSpeed_returnsInclineOnly() {
        List<Command> cmds = treadmill.decodeCommands(QZCommandPacket.parse("-100;3.0"));
        assertEquals(1, cmds.size());
        assertCommand(InclineCommand.class, 3.0f, cmds.get(0));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void assertCommand(Class<?> expectedType, float expectedValue, Command actual) {
        assertSame(expectedType, actual.getClass());
        assertEquals(expectedValue, actual.value, 0.001f);
    }
}

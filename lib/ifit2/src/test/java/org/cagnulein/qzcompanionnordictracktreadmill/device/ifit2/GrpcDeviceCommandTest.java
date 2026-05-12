package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit2;

import org.cagnulein.qzcompanionnordictracktreadmill.command.InclineCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.command.ResistanceCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.command.SpeedCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit2.CommandTransport;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Verifies that GrpcDevice.applyCommand() delegates every command to the
 * transport layer. No gRPC server or Android framework required — a
 * capturing CommandTransport fake is injected instead.
 */
public class GrpcDeviceCommandTest {

    private List<Object> captured;
    private CommandTransport transport;

    @Before
    public void setUp() {
        captured = new ArrayList<>();
        transport = (cmd, logger) -> { captured.add(cmd); return true; };
    }

    @Test
    public void bike_applyResistance_delegatesToTransport() {
        GrpcBikeDevice bike = new GrpcBikeDevice(transport);
        bike.applyCommand(new ResistanceCommand(10.0f));
        assertEquals(1, captured.size());
        assertSame(ResistanceCommand.class, captured.get(0).getClass());
    }

    @Test
    public void bike_applyIncline_delegatesToTransport() {
        GrpcBikeDevice bike = new GrpcBikeDevice(transport);
        bike.applyCommand(new InclineCommand(5.0f));
        assertEquals(1, captured.size());
        assertSame(InclineCommand.class, captured.get(0).getClass());
    }

    @Test
    public void treadmill_applySpeed_delegatesToTransport() {
        GrpcTreadmillDevice treadmill = new GrpcTreadmillDevice(transport);
        treadmill.applyCommand(new SpeedCommand(8.0f));
        assertEquals(1, captured.size());
        assertSame(SpeedCommand.class, captured.get(0).getClass());
    }

    @Test
    public void treadmill_applyIncline_delegatesToTransport() {
        GrpcTreadmillDevice treadmill = new GrpcTreadmillDevice(transport);
        treadmill.applyCommand(new InclineCommand(3.0f));
        assertEquals(1, captured.size());
        assertSame(InclineCommand.class, captured.get(0).getClass());
    }

    @Test
    public void transport_rejected_doesNotThrow() {
        CommandTransport rejectingTransport = (cmd, logger) -> false;
        GrpcBikeDevice bike = new GrpcBikeDevice(rejectingTransport);
        // GrpcDevice logs a warning on rejection — must not throw
        bike.applyCommand(new ResistanceCommand(10.0f));
    }
}

package org.cagnulein.qzcompanionnordictracktreadmill.device;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;

import java.util.List;

public abstract class Device {
    /** Functional interface so the executor can be set without Android imports. */
    public interface CommandExecutor { void send(String command); }

    /** Executes shell commands on this device. No-op by default; set by MainActivity. */
    public CommandExecutor commandExecutor = command -> {};

    /** Functional interface for log output. Level constants match android.util.Log values. */
    public interface Logger {
        int VERBOSE = 2;
        int DEBUG   = 3;
        int INFO    = 4;
        int WARN    = 5;
        int ERROR   = 6;
        void log(int level, String tag, String msg);
    }

    /** Logger for this device. No-op by default; set by MainActivity. */
    public Logger logger = (level, tag, msg) -> {};

    public abstract String displayName();

    /** Interprets a parsed QZ UDP packet and returns one Command per actionable field. */
    public abstract List<Command> decodeCommands(QZCommandPacket pkt);

    /** Returns a log label for confirmed telemetry this device handles, or null to suppress logging. */
    public String telemetryLabel(Telemetry t) { return null; }

    /** Handles a live telemetry update. Devices without telemetry-driven controls can ignore it. */
    public void applyTelemetry(Telemetry telemetry) {}

    /** Applies a command to this device. Routes to sliders (iFit1) or gRPC (iFit2). */
    public void applyCommand(Command cmd) {}

    /** Releases any resources held by this device (e.g. gRPC channel). */
    public void shutdown() {}
}

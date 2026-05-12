package org.cagnulein.qzcompanionnordictracktreadmill.device;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.CommandDecoder;
import org.cagnulein.qzcompanionnordictracktreadmill.command.CommandDispatcher;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.TelemetryHub;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandSubscriber;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class DeviceController implements QZCommandSubscriber {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    private final Device device;
    private final CommandDecoder preDecoder;
    private final CommandDispatcher dispatcher;
    private final TelemetryHub.Subscription telemetrySubscription;

    public DeviceController(Device device) {
        this(device, (CommandDecoder) null);
    }

    /** ifit1 path: pass {@code CalibrationDevice::decodeCommands} as preDecoder. */
    public DeviceController(Device device, CommandDecoder preDecoder) {
        this.device      = device;
        this.preDecoder  = preDecoder;
        this.dispatcher  = new CommandDispatcher(this::executeCommand);
        this.telemetrySubscription = subscribeTelemetry();
    }

    /** Test constructor: injectable clock, no background drain thread. */
    public DeviceController(Device device, CommandDispatcher.Clock clock) {
        this(device, null, clock);
    }

    /** Test constructor: injectable pre-decoder and clock, no background drain thread. */
    public DeviceController(Device device, CommandDecoder preDecoder, CommandDispatcher.Clock clock) {
        this.device      = device;
        this.preDecoder  = preDecoder;
        this.dispatcher  = new CommandDispatcher(this::executeCommand, clock);
        this.telemetrySubscription = null;
    }

    private void executeCommand(Command cmd) {
        device.logger.log(Device.Logger.DEBUG, LOG_TAG, "drain: " + cmd);
        device.applyCommand(cmd);
    }

    /** Allows non-QZ ingress adapters, such as Direct Connect, to reuse this controller. */
    public int enqueueCommand(Command cmd) {
        int depth = dispatcher.enqueue(cmd);
        if (depth >= 0)
            device.logger.log(Device.Logger.DEBUG, LOG_TAG,
                    "enqueue: " + cmd + " depth=" + depth + "/" + CommandDispatcher.QUEUE_CAPACITY);
        else
            device.logger.log(Device.Logger.WARN, LOG_TAG,
                    "drop: " + cmd + " (queue full at " + CommandDispatcher.QUEUE_CAPACITY + ")");
        return depth;
    }

    public void onTelemetry(Telemetry telemetry) {
        String label = device.telemetryLabel(telemetry);
        if (label != null) device.logger.log(Device.Logger.DEBUG, LOG_TAG, "telemetry: " + label);
        device.applyTelemetry(telemetry);
    }

    @Override
    public void onPacket(QZCommandPacket packet) {
        List<Command> commands = preDecoder != null ? preDecoder.decode(packet) : Collections.emptyList();
        if (commands.isEmpty()) commands = device.decodeCommands(packet);
        for (Command cmd : commands) {
            enqueueCommand(cmd);
        }
        // Sentinel packets (empty command list) still act as passive drain drivers.
        if (commands.isEmpty()) dispatcher.drain();
    }

    public void shutdown() {
        if (telemetrySubscription != null) telemetrySubscription.close();
        device.shutdown();
        dispatcher.shutdown();
    }

    public Device device() { return device; }

    private TelemetryHub.Subscription subscribeTelemetry() {
        try {
            return TelemetryHub.shared().subscribe(this::onTelemetry);
        } catch (IOException e) {
            device.logger.log(Device.Logger.ERROR, LOG_TAG,
                    "telemetry subscription failed: " + e.getMessage());
            return null;
        }
    }
}

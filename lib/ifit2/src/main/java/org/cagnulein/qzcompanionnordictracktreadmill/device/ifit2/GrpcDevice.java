package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit2;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit2.CommandTransport;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceLogTags;

public abstract class GrpcDevice extends Device {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    protected final CommandTransport transport;

    protected GrpcDevice(CommandTransport transport) { this.transport = transport; }

    @Override
    public void applyCommand(Command cmd) {
        if (!transport.apply(cmd, logger)) logger.log(Logger.WARN, LOG_TAG, "command rejected: " + cmd);
    }

    @Override public void shutdown() { transport.shutdown(); }
}

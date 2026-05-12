package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit2;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;

public interface CommandTransport {
    boolean apply(Command command, Device.Logger logger);
    default void shutdown() {}
}

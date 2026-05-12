package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceLogTags;
import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.RawSwipeCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.Slider;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;

import java.util.Collections;
import java.util.List;

/**
 * Internal decoder for calibration-only UDP commands.
 *
 * This is not a registry device. It exists so CALSWIPE uses the same
 * decodeCommands -> CommandDispatcher -> Command path as regular device control.
 */
public final class CalibrationDevice extends GestureDevice {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    @Override
    public List<Slider> sliders() {
        return Collections.emptyList();
    }

    @Override
    public String displayName() {
        return "Calibration";
    }

    @Override
    public List<Command> decodeCommands(QZCommandPacket pkt) {
        String raw = pkt.raw();
        if (!raw.startsWith(QZCommandPacket.CALSWIPE_PREFIX)) {
            return Collections.emptyList();
        }

        String[] parts = raw.split(":", -1);
        if (parts.length != 4) {
            logger.log(Logger.ERROR, LOG_TAG, "CALSWIPE parse error: expected 3 coordinates");
            return Collections.emptyList();
        }

        Float x = QZCommandPacket.parseField(parts[1]);
        Float fromY = QZCommandPacket.parseField(parts[2]);
        Float toY = QZCommandPacket.parseField(parts[3]);
        if (x == null || fromY == null || toY == null) {
            logger.log(Logger.ERROR, LOG_TAG, "CALSWIPE parse error: invalid coordinate");
            return Collections.emptyList();
        }

        return Collections.singletonList(new RawSwipeCommand(x, fromY, toY));
    }
}

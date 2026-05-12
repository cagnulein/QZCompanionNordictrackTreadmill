package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.SnapToOriginCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.Slider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.InclineTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class GestureTreadmillDevice extends GestureDevice {

    protected static final double KMH_TO_MPH = 0.621371;

    private final InclineSlider incline;
    private final SpeedSlider speed;

    protected GestureTreadmillDevice(InclineSlider incline, SpeedSlider speed) {
        this.incline = incline;
        this.speed   = speed;
    }

    @Override
    public List<Slider> sliders() { return Arrays.asList(incline, speed); }

    @Override
    public List<Command> decodeCommands(QZCommandPacket pkt) {
        if (pkt.fieldCount() == 1 && QZCommandPacket.SNAP_ORIGIN.equals(pkt.rawField(0))) {
            List<Command> snaps = new ArrayList<>();
            for (Slider s : sliders()) snaps.add(new SnapToOriginCommand(s));
            return snaps;
        }
        List<Command> cmds = new ArrayList<>();
        if (pkt.fieldCount() == 2) {
            Float s = QZCommandPacket.parseField(pkt.rawField(0));
            Float i = QZCommandPacket.parseField(pkt.rawField(1));
            if (s != null && s != QZCommandPacket.NO_COMMAND && s != QZCommandPacket.NO_RESISTANCE) {
                cmds.add(speed.commandFor(QZCommandPacket.roundToOneDecimal(s)));
            }
            if (i != null && i != QZCommandPacket.NO_COMMAND) {
                cmds.add(incline.commandFor(QZCommandPacket.roundToOneDecimal(i)));
            }
        }
        return cmds;
    }

    @Override
    public String telemetryLabel(Telemetry t) {
        if (t instanceof InclineTelemetry) return "grade=" + t.value;
        if (t instanceof SpeedTelemetry)   return "speed=" + t.value;
        return null;
    }
}

package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider;

import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceLogTags;
import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.ResistanceCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.ResistanceTelemetry;

public class ResistanceSlider extends Slider {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    public ResistanceSlider(int trackX, int initialThumbY, ThumbYFormula formula) {
        super(trackX, initialThumbY, formula, ResistanceTelemetry.class, ResistanceCommand.class);
    }

    protected ResistanceSlider(int initialThumbY) {
        super(0, initialThumbY, null, ResistanceTelemetry.class, ResistanceCommand.class);
    }

    @Override
    protected int currentThumbY() { return thumbY(); }

    public static ResistanceSlider live(int trackX, int initialThumbY, ThumbYFormula formula) {
        return new ResistanceSlider(trackX, initialThumbY, formula) {
            @Override
            protected int currentThumbY() {
                if (liveValue != null) return targetThumbY(liveValue);
                return targetThumbY(0.0);
            }
        };
    }

    @Override
    public void handle(double lvl, Device device) {
        Float last = lastApplied();
        device.logger.log(Device.Logger.VERBOSE, LOG_TAG, "requestResistance: " + lvl + " last=" + last);
        if (last == null || !Float.valueOf((float) lvl).equals(last)) {
            moveTo(lvl, device);
            device.logger.log(Device.Logger.DEBUG, LOG_TAG, "applyResistance: " + lvl);
        } else {
            device.logger.log(Device.Logger.VERBOSE, LOG_TAG, "de-dup: skipping resistance " + lvl + " (already at " + last + ")");
        }
    }

    @Override
    public Command commandFor(double lvl) { return new ResistanceCommand((float) lvl); }

    @Override protected float originValue() { return 1f; }
}

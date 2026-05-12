package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider;

import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceLogTags;
import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.InclineCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.InclineTelemetry;

public class InclineSlider extends Slider {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    public InclineSlider(int trackX, int initialThumbY, ThumbYFormula formula) {
        super(trackX, initialThumbY, formula, InclineTelemetry.class, InclineCommand.class);
    }

    protected InclineSlider(int initialThumbY) {
        super(0, initialThumbY, null, InclineTelemetry.class, InclineCommand.class);
    }

    @Override
    protected int currentThumbY() { return thumbY(); }

    public static InclineSlider live(int trackX, int initialThumbY, ThumbYFormula formula) {
        return new InclineSlider(trackX, initialThumbY, formula) {
            @Override
            protected int currentThumbY() {
                if (liveValue != null) return targetThumbY(liveValue);
                return targetThumbY(0.0);
            }
        };
    }

    @Override
    public void handle(double pct, Device device) {
        float quantized = quantize((float) pct);
        Float last = lastApplied();
        device.logger.log(Device.Logger.VERBOSE, LOG_TAG, "requestIncline: " + pct + " quantized=" + quantized + " last=" + last);
        if (last == null || quantized != last) {
            moveTo(quantized, device);
            device.logger.log(Device.Logger.DEBUG, LOG_TAG, "applyIncline: " + quantized);
        } else {
            device.logger.log(Device.Logger.VERBOSE, LOG_TAG, "de-dup: skipping incline " + pct + " (quantized=" + quantized + " already at " + last + ")");
        }
    }

    @Override
    public Command commandFor(double pct) { return new InclineCommand((float) pct); }

    @Override protected float originValue() { return 0f; }
}

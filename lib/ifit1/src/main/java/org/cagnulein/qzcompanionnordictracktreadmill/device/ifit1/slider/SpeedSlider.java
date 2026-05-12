package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider;

import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceLogTags;
import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.SpeedCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;

public class SpeedSlider extends Slider {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    private volatile Float cachedKmh = null;

    public SpeedSlider(int trackX, int initialThumbY, ThumbYFormula formula) {
        super(trackX, initialThumbY, formula, SpeedTelemetry.class, SpeedCommand.class);
    }

    @Override
    protected int currentThumbY() { return thumbY(); }

    public static SpeedSlider live(int trackX, int initialThumbY, ThumbYFormula formula) {
        return new SpeedSlider(trackX, initialThumbY, formula) {
            @Override
            protected int currentThumbY() {
                if (liveValue != null) return targetThumbY(liveValue);
                return targetThumbY(0.0);
            }
        };
    }

    @Override
    public void handle(double kmh, Device device) {
        synchronized (this) {
            device.logger.log(Device.Logger.VERBOSE, LOG_TAG, "requestSpeed: " + kmh + " beltKph=" + liveValueOrZero() + " cached=" + cachedKmh);
            if (liveValueOrZero() <= 0) {
                device.logger.log(Device.Logger.DEBUG, LOG_TAG, "speed gate: held " + kmh + " (belt stopped)");
                cachedKmh = (float) kmh;
                return;
            }
            cachedKmh = null;
        }
        device.logger.log(Device.Logger.DEBUG, LOG_TAG, "applySpeed: " + kmh);
        moveTo(kmh, device);
    }

    @Override
    public void applyTelemetry(Telemetry telemetry, Device device) {
        super.applyTelemetry(telemetry, device);
        if (!(telemetry instanceof SpeedTelemetry) || telemetry.value <= 0) return;
        float cached;
        synchronized (this) {
            if (cachedKmh == null) return;
            cached = cachedKmh;
            cachedKmh = null;
        }
        device.logger.log(Device.Logger.INFO, LOG_TAG, "belt-gate flush: applySpeed " + cached);
        moveTo(cached, device);
    }

    @Override
    public Command commandFor(double kmh) { return new SpeedCommand((float) kmh); }

    @Override protected float originValue() { return 0f; }
}

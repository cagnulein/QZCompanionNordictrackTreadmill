package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider;

import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.GearCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.GearTelemetry;

public class GearSlider extends Slider {

    public GearSlider(int trackX, int initialThumbY, ThumbYFormula formula) {
        super(trackX, initialThumbY, formula, GearTelemetry.class, GearCommand.class);
    }

    @Override
    protected int currentThumbY() { return thumbY(); }

    public static GearSlider live(int trackX, int initialThumbY, ThumbYFormula formula) {
        return new GearSlider(trackX, initialThumbY, formula) {
            @Override
            protected int currentThumbY() {
                if (liveValue != null) return targetThumbY(liveValue);
                return targetThumbY(0.0);
            }
        };
    }

    @Override
    public void handle(double gear, Device device) {
        Float last = lastApplied();
        if (last == null || (float) gear != last) moveTo(gear, device);
    }

    @Override
    public Command commandFor(double gear) { return new GearCommand((float) gear); }

    @Override protected float originValue() { return 1f; }
}

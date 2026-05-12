package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.Slider;

public final class SnapToOriginCommand extends Command {
    private final Slider slider;

    public SnapToOriginCommand(Slider slider) { super(0); this.slider = slider; }

    public Slider slider() { return slider; }

    @Override public String toString() { return "snapToOrigin"; }
}

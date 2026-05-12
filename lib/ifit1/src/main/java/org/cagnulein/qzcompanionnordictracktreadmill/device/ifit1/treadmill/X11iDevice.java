package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class X11iDevice extends GestureTreadmillDevice {

    public X11iDevice() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            new InclineSlider(ScreenProfile.W1280.leftTrackX,  557, v -> (int)(565.491 - 8.44 * v)),
            new SpeedSlider(  ScreenProfile.W1280.rightTrackX, 600, v -> (int)(621.997 - 21.785 * v))
        );
    }

    @Override public String displayName() { return "X11i Treadmill"; }
}

package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class S40Device extends GestureTreadmillDevice {

    public S40Device() {
        // Screen: 1024px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            new InclineSlider(ScreenProfile.W1024.leftTrackX,  490, v -> (int)(490 - 21.4 * v)),
            new SpeedSlider(  ScreenProfile.W1024.rightTrackX, 507, v -> (int)(507 - 12.5 * v))
        );
    }

    @Override public String displayName() { return "S40 Treadmill"; }
}

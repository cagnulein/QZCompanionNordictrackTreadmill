package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class C1750_2021Device extends GestureTreadmillDevice {

    public C1750_2021Device() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        // Incline trackX=79 is 4.5px off APK-expected 74.5; matches hardware calibration.
        super(
            new InclineSlider(ScreenProfile.W1280.leftTrackX,  553, v -> (int)(553 - 22 * v)),
            SpeedSlider.live(  ScreenProfile.W1280.rightTrackX, 620, v -> 620 - (int)((v - 1.0) * 20.73))
        );
    }

    @Override public String displayName() { return "C1750 Treadmill (2021)"; }
}

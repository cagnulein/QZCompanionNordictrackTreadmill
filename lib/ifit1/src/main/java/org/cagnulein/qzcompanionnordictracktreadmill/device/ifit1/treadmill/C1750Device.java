package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class C1750Device extends GestureTreadmillDevice {

    public C1750Device() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        // Incline trackX=79 is 4.5px off APK-expected 74.5; matches hardware calibration.
        super(
            new InclineSlider(ScreenProfile.W1920.leftTrackX,  700, v -> (int)(700 - 34.9 * v)),
            SpeedSlider.live( ScreenProfile.W1920.rightTrackX, 785, v -> 785 - (int)((v - 1.0) * 31.42))
        );
    }

    @Override public String displayName() { return "C1750 Treadmill"; }
}

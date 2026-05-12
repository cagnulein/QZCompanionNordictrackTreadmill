package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class Proform2000Device extends GestureTreadmillDevice {

    public Proform2000Device() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        // Incline trackX=79 is 4.5px off APK-expected 74.5; matches hardware calibration.
        super(
            InclineSlider.live(ScreenProfile.W1280.leftTrackX,  520, v -> 520 - (int)((v + 3) * 21.804)),
            new SpeedSlider(   ScreenProfile.W1280.rightTrackX, 598, v -> (int)(631.03 - 19.921 * v))
        );
    }

    @Override public String displayName() { return "ProForm 2000 Treadmill"; }
}

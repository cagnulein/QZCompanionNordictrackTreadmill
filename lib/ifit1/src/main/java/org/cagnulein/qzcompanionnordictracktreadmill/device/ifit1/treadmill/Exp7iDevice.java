package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class Exp7iDevice extends GestureTreadmillDevice {

    public Exp7iDevice() {
        // Screen: 1024px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1024.leftTrackX,  442, v -> 442 - (int)(v * 21.802)),
            SpeedSlider.live(  ScreenProfile.W1024.rightTrackX, 453, v -> 453 - (int)((v * KMH_TO_MPH - 1.0) * 22.702))
        );
    }

    @Override public String displayName() { return "EXP 7i Treadmill"; }
}

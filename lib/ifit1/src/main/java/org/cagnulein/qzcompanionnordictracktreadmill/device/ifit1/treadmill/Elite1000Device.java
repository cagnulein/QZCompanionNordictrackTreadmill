package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;
public class Elite1000Device extends GestureTreadmillDevice {

    public Elite1000Device() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1280.leftTrackX,  589, v -> 589 - (int)(v * 32.8)),
            SpeedSlider.live(  ScreenProfile.W1280.rightTrackX, 600, v -> 600 - (int)(v * KMH_TO_MPH * 31.33))
        );
    }

    @Override public String displayName() { return "Elite 1000 Treadmill"; }
}

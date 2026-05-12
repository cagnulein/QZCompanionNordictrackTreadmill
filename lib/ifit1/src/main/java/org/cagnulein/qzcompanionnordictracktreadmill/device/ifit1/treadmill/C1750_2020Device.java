package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class C1750_2020Device extends GestureTreadmillDevice {

    public C1750_2020Device() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1280.leftTrackX,  520, v -> 520 - (int)(v * 20)),
            SpeedSlider.live(  ScreenProfile.W1280.rightTrackX, 575, v -> 575 - (int)((v * KMH_TO_MPH - 1.0) * 28.91))
        );
    }

    @Override public String displayName() { return "C1750 Treadmill (2020)"; }
}

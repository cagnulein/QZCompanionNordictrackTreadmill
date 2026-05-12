package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class X32iNtl39221Device extends GestureTreadmillDevice {

    public X32iNtl39221Device() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1920.leftTrackX,  750, v -> 750 - (int)(v * 12.05)),
            SpeedSlider.live(  ScreenProfile.W1920.rightTrackX, 579, v -> (int)(900.26 - 46.63 * v * KMH_TO_MPH))
        );
    }

    @Override public String displayName() { return "X32i Treadmill (NTL39221)"; }
}

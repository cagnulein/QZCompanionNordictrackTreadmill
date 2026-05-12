package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class X32iNtl39019Device extends GestureTreadmillDevice {

    public X32iNtl39019Device() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            new InclineSlider(ScreenProfile.W1920.leftTrackX,  749, v -> (int)(749 - 11.8424 * v)),
            new SpeedSlider(  ScreenProfile.W1920.rightTrackX, 779, v -> (int)(817.5 - 42.5 * v * KMH_TO_MPH))
        );
    }

    @Override public String displayName() { return "X32i Treadmill (NTL39019)"; }
}

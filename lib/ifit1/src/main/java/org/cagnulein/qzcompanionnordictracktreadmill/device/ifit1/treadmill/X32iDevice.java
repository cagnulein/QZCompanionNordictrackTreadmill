package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class X32iDevice extends GestureTreadmillDevice {

    public X32iDevice() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        // initialThumbYRight=927 starts below targetThumbY(0)≈835; conservative position ensures the first speed swipe always travels upward.
        super(
            new InclineSlider(ScreenProfile.W1920.leftTrackX,  881, v -> (int)(734.07 - 12.297 * v)),
            new SpeedSlider(  ScreenProfile.W1920.rightTrackX, 927, v -> (int)(834.85 - 26.946 * v))
        );
    }

    @Override public String displayName() { return "X32i Treadmill"; }
}

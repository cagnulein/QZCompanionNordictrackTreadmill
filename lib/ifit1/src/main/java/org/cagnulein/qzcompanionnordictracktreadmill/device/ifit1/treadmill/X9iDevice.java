package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class X9iDevice extends GestureTreadmillDevice {

    public X9iDevice() {
        // Screen: 800px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            new InclineSlider(ScreenProfile.W800.leftTrackX,  332, v -> (int)(311.91 - 3.3478 * v)),
            new SpeedSlider(  ScreenProfile.W800.rightTrackX, 332, v -> (int)(345.6315 - 13.6315 * v))
        );
    }

    @Override public String displayName() { return "X9i Treadmill"; }
}

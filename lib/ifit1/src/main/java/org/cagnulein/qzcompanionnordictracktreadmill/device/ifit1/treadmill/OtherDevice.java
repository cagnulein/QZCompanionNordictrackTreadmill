package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class OtherDevice extends GestureTreadmillDevice {

    public OtherDevice() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        // Fallback device. initialThumbY=0 for both sliders — no initial position assumed; first swipe starts from Y=0.
        super(
            new InclineSlider(ScreenProfile.W1280.leftTrackX,  0, v -> (int)(520.11 - 21.804 * v)),
            new SpeedSlider(  ScreenProfile.W1280.rightTrackX, 0, v -> (int)(631.03 - 19.921 * v))
        );
    }

    @Override public String displayName() { return "Other Treadmill"; }
}

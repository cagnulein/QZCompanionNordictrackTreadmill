package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class T65sDevice extends GestureTreadmillDevice {

    public T65sDevice() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            new InclineSlider(ScreenProfile.W1280.leftTrackX,  585, v -> (int)(576.91 - 34.182 * v)),
            new SpeedSlider(  ScreenProfile.W1280.rightTrackX, 495, v -> (int)(578.36 - 35.866 * v * KMH_TO_MPH))
        );
    }

    @Override public String displayName() { return "T6.5s Treadmill"; }
}

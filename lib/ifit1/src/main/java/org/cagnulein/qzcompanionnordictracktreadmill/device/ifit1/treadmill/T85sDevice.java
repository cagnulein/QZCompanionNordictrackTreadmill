package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class T85sDevice extends GestureTreadmillDevice {

    public T85sDevice() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1280.leftTrackX,  609, v -> (int)(609 - 36.417 * v)),
            SpeedSlider.live(  ScreenProfile.W1280.rightTrackX, 609, v -> (int)(629.81 - 20.81 * v))
        );
    }

    @Override public String displayName() { return "T8.5s Treadmill"; }
}

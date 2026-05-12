package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class Nordictrack2950Device extends GestureTreadmillDevice {

    public Nordictrack2950Device() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1920.leftTrackX,  807, v -> 807 - (int)((v + 3) * 31.1)),
            SpeedSlider.live(  ScreenProfile.W1920.rightTrackX, 807, v -> 807 - (int)((v - 1.0) * 31))
        );
    }

    @Override public String displayName() { return "NordicTrack 2950 Treadmill"; }
}

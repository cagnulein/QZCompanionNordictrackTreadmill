package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class Nordictrack2950MaxSpeed22Device extends GestureTreadmillDevice {

    public Nordictrack2950MaxSpeed22Device() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1920.leftTrackX,  807, v -> 807 - (int)((v + 3) * 31.1)),
            SpeedSlider.live(  ScreenProfile.W1920.rightTrackX, 682, v -> 682 - (int)((v - 1.0) * 26.5))
        );
    }

    @Override public String displayName() { return "NordicTrack 2950 Treadmill (Max Speed 22)"; }
}

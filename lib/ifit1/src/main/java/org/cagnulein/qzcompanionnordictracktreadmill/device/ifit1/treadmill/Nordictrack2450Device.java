package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class Nordictrack2450Device extends GestureTreadmillDevice {

    public Nordictrack2450Device() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1920.leftTrackX,  715, v -> 715 - (int)((v + 3) * 29.26)),
            SpeedSlider.live(  ScreenProfile.W1920.rightTrackX, 807, v -> (int)(831.39 - 26.33 * v * KMH_TO_MPH))
        );
    }

    @Override public String displayName() { return "NordicTrack 2450 Treadmill"; }
}

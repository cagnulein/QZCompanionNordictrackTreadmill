package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class ProformCarbonT14Device extends GestureTreadmillDevice {

    public ProformCarbonT14Device() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            new InclineSlider(ScreenProfile.W1920.leftTrackX,  844, v -> (int)(844 - 46.833 * v)),
            new SpeedSlider(  ScreenProfile.W1920.rightTrackX, 810, v -> (int)(810 - 52.8 * KMH_TO_MPH * v))
        );
    }

    @Override public String displayName() { return "ProForm Carbon T14 Treadmill"; }
}

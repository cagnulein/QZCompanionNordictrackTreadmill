package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.ResistanceSlider;

public class S27iDevice extends GestureBikeDevice {

    public S27iDevice() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(   ScreenProfile.W1920.leftTrackX,  803, v -> 803 - (int)((v + 10) * (803.0 - 248.0) / 30.0)),
            ResistanceSlider.live(ScreenProfile.W1920.rightTrackX, 803, v -> 803 - (int)((v -  1) * (803.0 - 248.0) / 23.0))
        );
    }

    @Override public String displayName() { return "S27i Bike"; }
}

package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.GearSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;

public class S15iDevice extends GestureBikeDevice {

    public S15iDevice() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1920.leftTrackX,  616, v -> 616 - (int)(v * 17.65)),
            GearSlider.live(   ScreenProfile.W1920.rightTrackX, 790, v -> 790 - (int)(v * 23.16))
        );
    }

    @Override public String displayName() { return "S15i Bike"; }
}

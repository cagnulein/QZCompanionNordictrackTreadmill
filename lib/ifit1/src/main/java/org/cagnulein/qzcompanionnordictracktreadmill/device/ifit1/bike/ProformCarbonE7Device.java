package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.ResistanceSlider;

public class ProformCarbonE7Device extends GestureBikeDevice {

    public ProformCarbonE7Device() {
        // Screen: 1024px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(   ScreenProfile.W1024.leftTrackX,  440, v -> 440 - (int)(v * 11)),
            ResistanceSlider.live(ScreenProfile.W1024.rightTrackX, 440, v -> 440 - (int)(v * 9.16))
        );
    }

    @Override public String displayName() { return "ProForm Carbon E7 Bike"; }
}

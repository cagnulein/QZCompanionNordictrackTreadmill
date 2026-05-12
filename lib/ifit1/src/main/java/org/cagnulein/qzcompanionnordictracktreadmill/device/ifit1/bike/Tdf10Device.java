package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;

public class Tdf10Device extends GestureBikeDevice {

    public Tdf10Device() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(new InclineSlider(ScreenProfile.W1280.rightTrackX, 604, v -> (int)(619.91 - 15.913 * v)), null);
    }

    @Override public String displayName() { return "TDF10 Bike"; }
}

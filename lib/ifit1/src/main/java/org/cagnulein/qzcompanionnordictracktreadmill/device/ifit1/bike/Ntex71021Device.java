package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;

public class Ntex71021Device extends GestureBikeDevice {

    public Ntex71021Device() {
        // Screen: 1024px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(new InclineSlider(ScreenProfile.W1024.rightTrackX, 493, v -> (int)(493 - 13.57 * v)), null);
    }

    @Override public String displayName() { return "NTEX71021 Bike"; }
}

package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;

public class Tdf10InclinationDevice extends GestureBikeDevice {

    public Tdf10InclinationDevice() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(new InclineSlider(ScreenProfile.W1280.leftTrackX, 482, v -> (int)(482.2 - 12.499 * v)), null);
    }

    @Override public String displayName() { return "TDF10 Bike (Inclination)"; }
}

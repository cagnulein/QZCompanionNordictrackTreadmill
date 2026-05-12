package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;

public class S22iNtex02121Device extends GestureBikeDevice {

    public S22iNtex02121Device() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(InclineSlider.live(ScreenProfile.W1920.leftTrackX, 800, v -> 800 - (int)((v + 10) * 19)), null);
    }

    @Override public String displayName() { return "S22i Bike (NTEX02121.5)"; }
}

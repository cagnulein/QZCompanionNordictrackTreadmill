package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class C1750Ntl14122Device extends GestureTreadmillDevice {

    public C1750Ntl14122Device() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1920.leftTrackX,  787, v -> 787 - (int)((v + 3) * 29)),
            SpeedSlider.live(  ScreenProfile.W1920.rightTrackX, 787, v -> 787 - (int)(v * 43.5))
        );
    }

    @Override public String displayName() { return "C1750 Treadmill (NTL14122.2 MPH)"; }
}

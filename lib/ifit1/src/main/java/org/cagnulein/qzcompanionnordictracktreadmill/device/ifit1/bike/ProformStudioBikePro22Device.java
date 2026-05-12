package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;

public class ProformStudioBikePro22Device extends GestureBikeDevice {

    public ProformStudioBikePro22Device() {
        // Screen: 1920px wide — updated to iFit APK 2.6.90 standard
        //   (original hardware calibration: right=1828).
        super(new InclineSlider(ScreenProfile.W1920.rightTrackX, 805, v -> (int)(826.25 - 21.25 * v)), null);
    }

    @Override public String displayName() { return "ProForm Studio Bike Pro 2.2"; }
}

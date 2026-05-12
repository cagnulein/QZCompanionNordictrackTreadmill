package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;

public class C1750MphMinus3GradeDevice extends GestureTreadmillDevice {

    public C1750MphMinus3GradeDevice() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1280.leftTrackX,  603, v -> 603 - (int)((v + 3.0) * 21.7222222)),
            SpeedSlider.live(  ScreenProfile.W1280.rightTrackX, 603, C1750MphMinus3GradeDevice::clampedSpeedY)
        );
    }

    @Override public String displayName() { return "C1750 Treadmill (MPH -3% Grade)"; }

    private static int clampedSpeedY(double v) {
        double mph = Math.max(0.5, Math.min(12.0, v * KMH_TO_MPH));
        return 603 - (int)((mph - 0.5) * 34.0);
    }
}

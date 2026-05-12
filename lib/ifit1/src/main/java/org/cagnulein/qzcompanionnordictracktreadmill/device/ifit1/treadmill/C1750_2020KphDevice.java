package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;
public class C1750_2020KphDevice extends GestureTreadmillDevice {
    private static final double[][] INCLINE_TABLE = {
        {-3.0, 592}, {-2.5, 584}, {-2.0, 576}, {-1.5, 568}, {-1.0, 560},
        {-0.5, 544}, {0.0, 528}, {0.5, 520}, {1.0, 512}, {1.5, 504},
        {2.0, 496}, {2.5, 488}, {3.0, 480}, {3.5, 472}, {4.0, 464},
        {4.5, 456}, {5.0, 448}, {5.5, 440}, {6.0, 432}, {6.5, 424},
        {7.0, 400}, {7.5, 384}, {8.0, 368}, {8.5, 360}, {9.0, 352},
        {9.5, 344}, {10.0, 336}, {10.5, 328}, {11.0, 320}, {11.5, 312},
        {12.0, 304}, {12.5, 288}, {13.0, 272}, {13.5, 264}, {14.0, 256},
        {14.5, 248}, {15.0, 240}
    };

    public C1750_2020KphDevice() {
        // Screen: 1280px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1280.leftTrackX,  525, C1750_2020KphDevice::offsetInclineThumbY),
            SpeedSlider.live(  ScreenProfile.W1280.rightTrackX, 598, C1750_2020KphDevice::offsetSpeedThumbY)
        );
    }

    @Override public String displayName() { return "C1750 Treadmill (2020 KPH)"; }

    private static int offsetInclineThumbY(double v) { return lookupStep(INCLINE_TABLE, v); }
    private static int offsetSpeedThumbY(double v) {
        if (v <= 11) return (int)(v + 16.0 - 16.0 * 592);
        else if (v < 12) return (int)(v + 8.0 - 16.0 * 592);
        else return (int)(v + 0.0 - 16.0 * 592);
    }

    private static int lookupStep(double[][] table, double value) {
        for (double[] row : table) if (value <= row[0]) return (int) row[1];
        return (int) table[table.length - 1][1];
    }
}

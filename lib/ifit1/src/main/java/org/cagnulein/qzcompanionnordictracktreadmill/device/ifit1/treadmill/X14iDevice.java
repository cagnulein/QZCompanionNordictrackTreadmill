package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureTreadmillDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.SpeedSlider;
public class X14iDevice extends GestureTreadmillDevice {
    private static final double[][] INCLINE_TABLE = {
        {-6.0, 856}, {-5.5, 850}, {-5.0, 844}, {-4.5, 838}, {-4.0, 832},
        {-3.5, 826}, {-3.0, 820}, {-2.5, 814}, {-2.0, 808}, {-1.5, 802},
        {-1.0, 796}, {-0.5, 785}, {0.0, 783}, {0.5, 778}, {1.0, 774},
        {1.5, 768}, {2.0, 763}, {2.5, 757}, {3.0, 751}, {3.5, 745},
        {4.0, 738}, {4.5, 731}, {5.0, 724}, {5.5, 717}, {6.0, 710},
        {6.5, 703}, {7.0, 696}, {7.5, 691}, {8.0, 687}, {8.5, 683},
        {9.0, 677}, {9.5, 671}, {10.0, 665}, {10.5, 658}, {11.0, 651},
        {11.5, 645}, {12.0, 638}, {12.5, 631}, {13.0, 624}, {13.5, 617},
        {14.0, 610}, {14.5, 605}, {15.0, 598}, {15.5, 593}, {16.0, 587},
        {16.5, 581}, {17.0, 575}, {17.5, 569}, {18.0, 563}, {18.5, 557},
        {19.0, 551}, {19.5, 545}, {20.0, 539}, {20.5, 533}, {21.0, 527},
        {21.5, 521}, {22.0, 515}, {22.5, 509}, {23.0, 503}, {23.5, 497},
        {24.0, 491}, {24.5, 485}, {25.0, 479}, {25.5, 473}, {26.0, 467},
        {26.5, 461}, {27.0, 455}, {27.5, 449}, {28.0, 443}, {28.5, 437},
        {29.0, 431}, {29.5, 425}, {30.0, 418}, {30.5, 412}, {31.0, 406},
        {31.5, 400}, {32.0, 394}, {32.5, 388}, {33.0, 382}, {33.5, 375},
        {34.0, 369}, {34.5, 363}, {35.0, 357}, {35.5, 351}, {36.0, 345},
        {36.5, 338}, {37.0, 332}, {37.5, 326}, {38.0, 320}, {38.5, 314},
        {39.0, 308}, {39.5, 302}, {40.0, 295}
    };

    public X14iDevice() {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            InclineSlider.live(ScreenProfile.W1920.leftTrackX,  645, X14iDevice::offsetInclineThumbY),
            SpeedSlider.live(  ScreenProfile.W1920.rightTrackX, 807, v -> 807 - (int)((v - 1.0) * 31))
        );
    }

    @Override public String displayName() { return "X14i Treadmill"; }

    private static int offsetInclineThumbY(double v) { return lookupStep(INCLINE_TABLE, v); }

    private static int lookupStep(double[][] table, double value) {
        for (double[] row : table) if (value <= row[0]) return (int) row[1];
        return (int) table[table.length - 1][1];
    }
}

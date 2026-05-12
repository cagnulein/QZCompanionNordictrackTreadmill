package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.ResistanceSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.GearTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;

public class S22iDevice extends GestureBikeDevice {
    private static final int ORIGIN_INCLINE_THUMBY    = 622;
    private static final int ORIGIN_RESISTANCE_THUMBY = 802;

    public S22iDevice() { this(0, 0); }

    protected S22iDevice(int hystLong, int hystShort) {
        // Screen: 1920px wide — trackX confirmed against iFit APK layout XML (tools/discover-device/validate_swipe_targets.py).
        super(
            new InclineSlider(ScreenProfile.W1920.leftTrackX, ORIGIN_INCLINE_THUMBY, S22iDevice::offsetInclineThumbY) {
                // Calibration 2026-04-19 (positive) + 2026-04-22 (negative):
                // Single linear fit — 18.57 px/% across the full range.
                // Intercept 622 = device-reported neutral (0% grade in iFit log).
                public float quantize(float v) {
                    // iFit slider snaps to 0.5% increments.
                    return Math.round(v * 2) / 2.0f;
                }
                @Override
                protected int currentThumbY() {
                    return liveValue != null ? targetThumbY(liveValue) : thumbY();
                }
                @Override
                protected int hysteresisPixels(int fromY, int toY) {
                    return Math.abs(toY - fromY) >= 40 ? hystLong : hystShort;
                }
            },
            new ResistanceSlider(ScreenProfile.W1920.rightTrackX, ORIGIN_RESISTANCE_THUMBY, S22iDevice::offsetResistanceThumbY) {
                // Recalibrated 2026-05-05: resistance=1 → Y=802, resistance=5 → Y=697 (direct swipe test).
                // Scale = (802−697) / (5−1) = 105/4 = 26.25 px per level.
                public float quantize(float v) { return Math.round(v); }
                @Override
                protected int currentThumbY() {
                    // Guard against the "Changed Resistance to: 0" noise readings emitted
                    // during coast/reset — 0 is not a valid level and would swipe off-range.
                    return (liveValue != null && liveValue >= 1)
                            ? targetThumbY(liveValue) : thumbY();
                }
                @Override
                public void applyTelemetry(Telemetry telemetry, Device device) {
                    // S22i reports resistance via CURRENT_GEAR, not RESISTANCE
                    if (telemetry instanceof GearTelemetry && telemetry.value >= 1) liveValue = telemetry.value;
                    else super.applyTelemetry(telemetry, device);
                }
            }
        );
    }

    @Override public String displayName() { return "S22i Bike"; }

    private static int offsetInclineThumbY(double v)    { return (int) (ORIGIN_INCLINE_THUMBY - 18.57 * v); }
    private static int offsetResistanceThumbY(double v) { return (int) (ORIGIN_RESISTANCE_THUMBY - 26.25 * (v - 1)); }
}

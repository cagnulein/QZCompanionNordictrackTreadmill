package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.CalibratedBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.ResistanceSlider;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.GearTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.ResistanceTelemetry;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Regression test: DeviceCalibration formula math matches the S22i hardcoded formula
 * within ±1px across the full operating range, and CalibratedBikeDevice correctly wires
 * up DeviceCalibration.current for both incline and resistance sliders.
 *
 * Fixture: S22i calibration produced by tools/discover-device.py (2026-05-01, R²=1.0000).
 * Tolerance: ±1px — well inside the 9px budget (one 0.5% quantize step × 18.57 px/%).
 */
public class DeviceCalibrationRegressionTest {

    // S22i calibration constants from tools/discover-device.py (2026-05-01, R²=1.0000)
    private static final double S22I_INCLINE_ORIGIN  = 622.0;
    private static final double S22I_INCLINE_SCALE   = 18.57;       // px per 1% grade
    private static final int    S22I_INCLINE_TRACK_X = 75;
    private static final double S22I_RES_ORIGIN      = 724.0;
    private static final double S22I_RES_SCALE       = 401.0 / 23;  // ~17.43 px per level
    private static final int    S22I_RES_TRACK_X     = 1845;
    private static final int    S22I_RES_MIN_LEVEL   = 1;

    private String lastCommand;

    private <T extends Device> T capture(T d) {
        d.commandExecutor = cmd -> lastCommand = cmd;
        return d;
    }

    @After
    public void clearCalibration() {
        DeviceCalibration.current = null;
    }

    // ── formula unit tests ────────────────────────────────────────────────────

    @Test
    public void inclineFormula_atZero_returnsOrigin() {
        assertEquals(622, s22iCalibration().targetThumbY(0.0f));
    }

    @Test
    public void inclineFormula_atPositiveTen_returnsCorrectY() {
        // (int)(622 - 18.57 * 10) = (int)(436.3) = 436
        assertEquals(436, s22iCalibration().targetThumbY(10.0f));
    }

    @Test
    public void inclineFormula_atNegativeTen_returnsCorrectY() {
        // (int)(622 - 18.57 * (-10)) = (int)(807.7) = 807
        assertEquals(807, s22iCalibration().targetThumbY(-10.0f));
    }

    @Test
    public void inclineFormula_matchesS22iHardcoded_acrossFullRange() {
        DeviceCalibration cal = s22iCalibration();
        for (float grade = -15.0f; grade <= 15.0f; grade += 0.5f) {
            int expected = (int)(S22I_INCLINE_ORIGIN - S22I_INCLINE_SCALE * grade);
            assertEquals("grade=" + grade, expected, cal.targetThumbY(grade));
        }
    }

    @Test
    public void resistanceFormula_matchesS22iHardcoded_acrossFullRange() {
        DeviceCalibration cal = s22iCalibration();
        for (int level = 1; level <= 24; level++) {
            // S22iDevice: (int)(724 - 401.0/23 * (level - 1))
            int expected = (int)(S22I_RES_ORIGIN - S22I_RES_SCALE * (level - S22I_RES_MIN_LEVEL));
            int actual   = (int)(cal.resistanceOrigin - cal.resistanceScale * (level - cal.resistanceMinLevel));
            assertEquals("level=" + level, expected, actual);
        }
    }

    // ── CalibratedBikeDevice wiring tests ─────────────────────────────────────

    @Test
    public void calibratedBikeDevice_incline_atZero_dispatchesCorrectSwipe() {
        DeviceCalibration.current = s22iCalibration();
        CalibratedBikeDevice dev = capture(new CalibratedBikeDevice());
        // toY=622=fromY=622 (initial) → no travel
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        assertEquals("input swipe 75 622 75 622 200", lastCommand);
    }

    @Test
    public void calibratedBikeDevice_incline_atPositiveTen_dispatchesCorrectSwipe() {
        DeviceCalibration.current = s22iCalibration();
        CalibratedBikeDevice dev = capture(new CalibratedBikeDevice());
        // toY=436; fromY=622; h=0 → dispatchY=436
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev);
        assertEquals("input swipe 75 622 75 436 200", lastCommand);
    }

    @Test
    public void calibratedBikeDevice_incline_atNegativeTen_dispatchesCorrectSwipe() {
        DeviceCalibration.current = s22iCalibration();
        CalibratedBikeDevice dev = capture(new CalibratedBikeDevice());
        // toY=807; fromY=622; h=0 → dispatchY=807
        dev.sliderOf(InclineSlider.class).moveTo(-10.0, dev);
        assertEquals("input swipe 75 622 75 807 200", lastCommand);
    }

    @Test
    public void calibratedBikeDevice_incline_updatesThumbY_onSequentialCommands() {
        DeviceCalibration.current = s22iCalibration();
        CalibratedBikeDevice dev = capture(new CalibratedBikeDevice());
        dev.sliderOf(InclineSlider.class).moveTo(10.0, dev);  // thumbY becomes 436
        dev.sliderOf(InclineSlider.class).moveTo(5.0, dev);   // fromY=436; toY=(int)(622-18.57*5)=529; h=0 → dispatchY=529
        assertEquals("input swipe 75 436 75 529 200", lastCommand);
    }

    @Test
    public void calibratedBikeDevice_resistance_atMinLevel_dispatchesCorrectSwipe() {
        DeviceCalibration.current = s22iCalibration();
        CalibratedBikeDevice dev = capture(new CalibratedBikeDevice());
        // level=1; toY=(int)(724 - 401/23 * 0)=724; fromY=724 (initial); h=0
        dev.sliderOf(ResistanceSlider.class).moveTo(1.0, dev);
        assertEquals("input swipe 1845 724 1845 724 200", lastCommand);
    }

    @Test
    public void calibratedBikeDevice_resistance_atMaxLevel_dispatchesCorrectSwipe() {
        DeviceCalibration.current = s22iCalibration();
        CalibratedBikeDevice dev = capture(new CalibratedBikeDevice());
        // level=24; toY=(int)(724 - 401/23 * 23)=(int)(724-401)=323; fromY=724; h=0
        dev.sliderOf(ResistanceSlider.class).moveTo(24.0, dev);
        assertEquals("input swipe 1845 724 1845 323 200", lastCommand);
    }

    @Test
    public void calibratedBikeDevice_resistance_updatesThumbY_onSequentialCommands() {
        DeviceCalibration.current = s22iCalibration();
        CalibratedBikeDevice dev = capture(new CalibratedBikeDevice());
        dev.sliderOf(ResistanceSlider.class).moveTo(24.0, dev);  // thumbY becomes 323
        // level=12; toY=(int)(724 - 401/23 * 11)=(int)(724-191.78...)=(int)(532.17)=532
        dev.sliderOf(ResistanceSlider.class).moveTo(12.0, dev);
        assertEquals("input swipe 1845 323 1845 532 200", lastCommand);
    }

    @Test
    public void calibratedBikeDevice_resistance_prefersCurrentGearOverResistanceNoise() {
        DeviceCalibration.current = s22iCalibration();
        CalibratedBikeDevice dev = capture(new CalibratedBikeDevice());
        dev.applyTelemetry(new GearTelemetry(8.0f));
        dev.applyTelemetry(new ResistanceTelemetry(10.0f));

        dev.sliderOf(ResistanceSlider.class).moveTo(12.0, dev);

        assertEquals("input swipe 1845 601 1845 532 200", lastCommand);
    }

    @Test
    public void calibratedBikeDevice_fallback_whenCalibrationNull_usesS22iDefaultTrackX() {
        DeviceCalibration.current = null;
        CalibratedBikeDevice dev = capture(new CalibratedBikeDevice());
        // No travel when grade=0 so hysteresis does not fire; confirms x=75 and neutralY=622
        dev.sliderOf(InclineSlider.class).moveTo(0.0, dev);
        assertEquals("input swipe 75 622 75 622 200", lastCommand);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static DeviceCalibration s22iCalibration() {
        return new DeviceCalibration(
                S22I_INCLINE_ORIGIN, S22I_INCLINE_SCALE, S22I_INCLINE_TRACK_X, (int) S22I_INCLINE_ORIGIN,
                0, 0, 0,  // zero hysteresis so dispatchY == targetThumbY in all tests
                S22I_RES_ORIGIN, S22I_RES_SCALE, S22I_RES_TRACK_X, S22I_RES_MIN_LEVEL);
    }
}

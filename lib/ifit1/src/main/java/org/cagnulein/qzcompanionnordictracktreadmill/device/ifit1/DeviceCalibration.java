package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Holds calibration coefficients for incline and (optionally) resistance sliders.
 *
 * Loaded at startup either from /sdcard/qz-calibration.json (written by the
 * in-app guided calibration flow or tools/discover-device.py) or from
 * SharedPreferences (written by the legacy in-app calibration flow). JSON takes priority.
 *
 * Resistance fields are null when absent from the source — CalibratedBikeDevice
 * skips the resistance slider in that case.
 */
public class DeviceCalibration {

    private static final String KEY_A            = "cal_a";
    private static final String KEY_B            = "cal_b";
    private static final String KEY_X            = "cal_x";
    private static final String KEY_NEUTRAL_Y    = "cal_neutral_y";
    private static final String KEY_HYST_THRESH  = "cal_hyst_thresh";
    private static final String KEY_HYST_SMALL   = "cal_hyst_small";
    private static final String KEY_HYST_LARGE   = "cal_hyst_large";

    /** Incline swipe formula: Y = a − b × grade */
    public final double a, b;
    /** Incline horizontal track coordinate and neutral (grade=0) Y position. */
    public final int x, neutralY;
    /** Hysteresis: travel threshold and overshoot pixels for short/long swipes. */
    public final int hystThresholdPx, hystSmallPx, hystLargePx;

    /** Resistance slider — null when not present in the calibration source. */
    public final Double  resistanceOrigin;   // Y pixel at minLevel
    public final Double  resistanceScale;    // px per resistance level step
    public final Integer resistanceTrackX;
    public final int     resistanceMinLevel; // lowest valid resistance level (typically 1)

    /** In-memory singleton loaded by MainActivity on startup. */
    public static volatile DeviceCalibration current;

    /** Legacy constructor (no resistance data). Used by SharedPreferences load path. */
    public DeviceCalibration(double a, double b, int x, int neutralY,
                             int hystThresholdPx, int hystSmallPx, int hystLargePx) {
        this(a, b, x, neutralY, hystThresholdPx, hystSmallPx, hystLargePx,
             null, null, null, 1);
    }

    /** Full constructor including resistance. Used by JSON load path. */
    public DeviceCalibration(double a, double b, int x, int neutralY,
                             int hystThresholdPx, int hystSmallPx, int hystLargePx,
                             Double resistanceOrigin, Double resistanceScale,
                             Integer resistanceTrackX, int resistanceMinLevel) {
        this.a = a; this.b = b;
        this.x = x; this.neutralY = neutralY;
        this.hystThresholdPx  = hystThresholdPx;
        this.hystSmallPx      = hystSmallPx;
        this.hystLargePx      = hystLargePx;
        this.resistanceOrigin   = resistanceOrigin;
        this.resistanceScale    = resistanceScale;
        this.resistanceTrackX   = resistanceTrackX;
        this.resistanceMinLevel = resistanceMinLevel;
    }

    public int targetThumbY(float grade) { return (int) (a - b * grade); }

    // ── JSON (discover-device.py output) ─────────────────────────────────────

    /**
     * Loads calibration from a JSON file written by the in-app calibration flow
     * or tools/discover-device.py.
     * Format: { "incline": { "trackX", "origin", "scale" },
     *           "resistance": { "trackX", "origin", "scale", "minLevel" } }
     * The resistance section is optional.
     */
    public static DeviceCalibration loadFromJson(File f) throws IOException, JSONException {
        byte[] bytes = new byte[(int) f.length()];
        try (DataInputStream dis = new DataInputStream(new FileInputStream(f))) {
            dis.readFully(bytes);
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(text);
        JSONObject inc  = root.getJSONObject("incline");
        double a = inc.getDouble("origin");
        double b = inc.getDouble("scale");
        int    x = inc.getInt("trackX");

        Double  resOrigin = null;
        Double  resScale  = null;
        Integer resTrackX = null;
        int     resMin    = 1;
        if (root.has("resistance")) {
            JSONObject res = root.getJSONObject("resistance");
            resOrigin = res.getDouble("origin");
            resScale  = res.getDouble("scale");
            resTrackX = res.getInt("trackX");
            resMin    = res.optInt("minLevel", 1);
        }
        // Hysteresis not stored in JSON — use empirical S22i defaults
        return new DeviceCalibration(a, b, x, (int) a, 40, 10, 15,
                                     resOrigin, resScale, resTrackX, resMin);
    }

    // ── SharedPreferences (legacy in-app flow) ────────────────────────────────

    public void save(SharedPreferences prefs) {
        prefs.edit()
            .putLong(KEY_A,           Double.doubleToLongBits(a))
            .putLong(KEY_B,           Double.doubleToLongBits(b))
            .putInt (KEY_X,           x)
            .putInt (KEY_NEUTRAL_Y,   neutralY)
            .putInt (KEY_HYST_THRESH, hystThresholdPx)
            .putInt (KEY_HYST_SMALL,  hystSmallPx)
            .putInt (KEY_HYST_LARGE,  hystLargePx)
            .apply();
    }

    /** Returns null if no calibration has been saved yet. */
    public static DeviceCalibration load(SharedPreferences prefs) {
        if (!prefs.contains(KEY_A)) return null;
        return new DeviceCalibration(
            Double.longBitsToDouble(prefs.getLong(KEY_A, 0)),
            Double.longBitsToDouble(prefs.getLong(KEY_B, 0)),
            prefs.getInt(KEY_X,           75),
            prefs.getInt(KEY_NEUTRAL_Y,   622),
            prefs.getInt(KEY_HYST_THRESH, 40),
            prefs.getInt(KEY_HYST_SMALL,  10),
            prefs.getInt(KEY_HYST_LARGE,  15)
        );
    }
}

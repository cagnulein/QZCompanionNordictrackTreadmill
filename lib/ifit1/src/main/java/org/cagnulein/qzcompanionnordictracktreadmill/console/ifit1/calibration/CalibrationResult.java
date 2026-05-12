package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration;

import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.DeviceCalibration;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** In-memory qz-calibration.json model shared by in-app and external calibration. */
public final class CalibrationResult {

    public final SliderCalibration incline;
    public final SliderCalibration resistance;

    public CalibrationResult(SliderCalibration incline, SliderCalibration resistance) {
        if (incline == null) throw new IllegalArgumentException("incline calibration is required");
        this.incline = incline;
        this.resistance = resistance;
    }

    public JSONObject toJsonObject() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("incline", incline.toJsonObject(false));
        if (resistance != null) root.put("resistance", resistance.toJsonObject(true));
        return root;
    }

    public String toJsonString() throws JSONException {
        return toJsonObject().toString(2);
    }

    public void writeTo(File file) throws IOException, JSONException {
        byte[] bytes = toJsonString().getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
            out.write('\n');
        }
    }

    public DeviceCalibration toDeviceCalibration() {
        Double resistanceOrigin = resistance != null ? resistance.origin : null;
        Double resistanceScale = resistance != null ? resistance.scale : null;
        Integer resistanceTrackX = resistance != null ? resistance.trackX : null;
        int resistanceMinLevel = resistance != null ? resistance.minLevel : 1;
        return new DeviceCalibration(
                incline.origin, incline.scale, incline.trackX, (int) incline.origin,
                40, 10, 15,
                resistanceOrigin, resistanceScale, resistanceTrackX, resistanceMinLevel);
    }

    public static final class SliderCalibration {
        public final int trackX;
        public final double origin;
        public final double scale;
        public final int minLevel;

        public SliderCalibration(int trackX, double origin, double scale) {
            this(trackX, origin, scale, 1);
        }

        public SliderCalibration(int trackX, double origin, double scale, int minLevel) {
            this.trackX = trackX;
            this.origin = origin;
            this.scale = scale;
            this.minLevel = minLevel;
        }

        private JSONObject toJsonObject(boolean includeMinLevel) throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("trackX", trackX);
            obj.put("origin", round(origin, 2));
            obj.put("scale", round(scale, 4));
            if (includeMinLevel) obj.put("minLevel", minLevel);
            return obj;
        }

        private static double round(double value, int digits) {
            double factor = Math.pow(10.0, digits);
            return Math.round(value * factor) / factor;
        }
    }
}

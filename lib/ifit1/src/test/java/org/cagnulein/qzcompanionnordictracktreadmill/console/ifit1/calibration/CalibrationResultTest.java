package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration;

import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.DeviceCalibration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CalibrationResultTest {

    @Test
    public void writeTo_roundTripsThroughDeviceCalibrationJsonLoader() throws Exception {
        CalibrationResult result = new CalibrationResult(
                new CalibrationResult.SliderCalibration(57, 621.77777, 18.57777),
                new CalibrationResult.SliderCalibration(1845, 724.0, 17.43478, 1)
        );
        File file = File.createTempFile("qz-calibration", ".json");
        try {
            result.writeTo(file);

            DeviceCalibration loaded = DeviceCalibration.loadFromJson(file);

            assertEquals(57, loaded.x);
            assertEquals(621.78, loaded.a, 0.0001);
            assertEquals(18.5778, loaded.b, 0.0001);
            assertEquals(Integer.valueOf(1845), loaded.resistanceTrackX);
            assertEquals(724.0, loaded.resistanceOrigin, 0.0001);
            assertEquals(17.4348, loaded.resistanceScale, 0.0001);
            assertEquals(1, loaded.resistanceMinLevel);
        } finally {
            file.delete();
        }
    }

    @Test
    public void toDeviceCalibration_appliesImmediatelyWithoutJsonReload() {
        CalibrationResult result = new CalibrationResult(
                new CalibrationResult.SliderCalibration(57, 622.0, 18.57),
                null
        );

        DeviceCalibration calibration = result.toDeviceCalibration();

        assertEquals(57, calibration.x);
        assertEquals(622, calibration.targetThumbY(0.0f));
        assertNull(calibration.resistanceTrackX);
    }
}

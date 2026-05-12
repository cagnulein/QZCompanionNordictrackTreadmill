package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class CalibrationFitTest {

    @Test
    public void fitLinear_returnsOriginScaleAndR2() {
        List<CalibrationFit.Point> points = Arrays.asList(
                new CalibrationFit.Point(807, -10.0),
                new CalibrationFit.Point(622, 0.0),
                new CalibrationFit.Point(436, 10.0),
                new CalibrationFit.Point(343, 15.0)
        );

        CalibrationFit.FitResult fit = CalibrationFit.fitLinear(points);

        assertEquals(621.6101, fit.origin, 0.001);
        assertEquals(18.5627, fit.scale, 0.001);
        assertTrue(fit.r2 > 0.9999);
        assertTrue(fit.isValid());
        assertFalse(fit.isWarningQuality());
    }

    @Test
    public void fitLinear_rejectsSingleLargeOutlier() {
        List<CalibrationFit.Point> points = Arrays.asList(
                new CalibrationFit.Point(807, -10.0),
                new CalibrationFit.Point(622, 0.0),
                new CalibrationFit.Point(100, 5.0),
                new CalibrationFit.Point(436, 10.0),
                new CalibrationFit.Point(343, 15.0)
        );

        CalibrationFit.FitResult fit = CalibrationFit.fitLinear(points);

        assertNotNull(fit.rejectedOutlier);
        assertEquals(100, fit.rejectedOutlier.y);
        assertEquals(4, fit.points.size());
        assertTrue(fit.r2 > 0.9999);
    }

    @Test
    public void filterMonotonicDescending_dropsUpwardSnapArtefacts() {
        List<CalibrationFit.Point> raw = Arrays.asList(
                new CalibrationFit.Point(300, 15.0),
                new CalibrationFit.Point(325, 14.0),
                new CalibrationFit.Point(350, 17.0),
                new CalibrationFit.Point(375, 13.0)
        );

        List<CalibrationFit.Point> filtered = CalibrationFit.filterMonotonicDescending(raw, 1.0);

        assertEquals(3, filtered.size());
        assertEquals(300, filtered.get(0).y);
        assertEquals(325, filtered.get(1).y);
        assertEquals(375, filtered.get(2).y);
    }

    @Test
    public void findActiveRange_expandsOneCoarseStep() {
        List<CalibrationFit.Point> points = Arrays.asList(
                new CalibrationFit.Point(300, 15.0),
                new CalibrationFit.Point(350, 12.0),
                new CalibrationFit.Point(400, 9.0)
        );

        int[] range = CalibrationFit.findActiveRange(points, 1000);

        assertArrayEquals(new int[]{250, 450}, range);
    }

    @Test
    public void trackProfileForWidth_matchesDiscoverDeviceTable() {
        assertEquals(57, CalibrationFit.trackProfileForWidth(1920).inclineTrackX);
        assertEquals(1845, CalibrationFit.trackProfileForWidth(1920).resistanceTrackX);
        assertEquals(1205, CalibrationFit.trackProfileForWidth(1280).resistanceTrackX);
        assertEquals(950, CalibrationFit.trackProfileForWidth(1024).resistanceTrackX);
        assertEquals(725, CalibrationFit.trackProfileForWidth(800).resistanceTrackX);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fitLinear_rejectsDegenerateMetricValues() {
        CalibrationFit.fitLinear(Arrays.asList(
                new CalibrationFit.Point(100, 1.0),
                new CalibrationFit.Point(200, 1.0),
                new CalibrationFit.Point(300, 1.0)
        ));
    }
}

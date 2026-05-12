package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration;

import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.GearTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.InclineTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.ResistanceTelemetry;
import org.junit.Test;

import static org.junit.Assert.*;

public class CalibrationTelemetryCollectorTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void waitFreshGrade_returnsOnlyEventsAfterTimestamp() {
        ManualClock clock = new ManualClock(1000);
        CalibrationTelemetryCollector collector = new CalibrationTelemetryCollector(clock, clock::advance);
        collector.accept(new InclineTelemetry(3.5f), 1000);

        assertNull(collector.waitFreshGrade(1000, 300));

        collector.accept(new InclineTelemetry(4.0f), clock.nowMs());

        assertEquals(4.0f, collector.waitFreshGrade(1000, 300), DELTA);
    }

    @Test
    public void waitStableResistance_returnsLastValueAfterSettleWindow() {
        ManualClock clock = new ManualClock(1000);
        CalibrationTelemetryCollector collector = new CalibrationTelemetryCollector(clock, clock::advance);
        collector.accept(new ResistanceTelemetry(12.0f), 1100);
        clock.now = 1100;

        assertEquals(12.0f, collector.waitStableResistance(1000, 250, 1000), DELTA);
    }

    @Test
    public void resistance_zeroNoiseIsIgnored() {
        ManualClock clock = new ManualClock(1000);
        CalibrationTelemetryCollector collector = new CalibrationTelemetryCollector(clock, clock::advance);
        collector.accept(new ResistanceTelemetry(0.0f), 1100);

        assertNull(collector.waitFreshResistance(1000, 300));
        assertEquals(0L, collector.anyEventTimestampMs());
    }

    @Test
    public void currentGearFeedsResistanceCollector() {
        ManualClock clock = new ManualClock(1000);
        CalibrationTelemetryCollector collector = new CalibrationTelemetryCollector(clock, clock::advance);
        collector.accept(new GearTelemetry(7.0f), 1100);

        assertEquals(7.0f, collector.waitFreshResistance(1000, 300), DELTA);
    }

    @Test
    public void currentGearWinsWhenResistanceNoiseArrivesLater() {
        ManualClock clock = new ManualClock(1000);
        CalibrationTelemetryCollector collector = new CalibrationTelemetryCollector(clock, clock::advance);
        collector.accept(new GearTelemetry(24.0f), 1100);
        collector.accept(new ResistanceTelemetry(4.0f), 1200);

        assertEquals(24.0f, collector.waitFreshResistance(1000, 300), DELTA);
    }

    private static final class ManualClock implements CalibrationTelemetryCollector.Clock {
        long now;

        ManualClock(long now) {
            this.now = now;
        }

        @Override
        public long nowMs() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }
    }
}

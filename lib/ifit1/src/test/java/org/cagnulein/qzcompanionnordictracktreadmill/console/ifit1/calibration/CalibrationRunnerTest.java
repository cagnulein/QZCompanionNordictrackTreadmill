package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration;

import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.TelemetryHub;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.DeviceCalibration;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.GearTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.InclineTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.ResistanceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CalibrationRunnerTest {

    @Test
    public void inclineOnlyRunner_savesAndAppliesCalibration() throws Exception {
        ManualClock clock = new ManualClock();
        CalibrationTelemetryCollector collector =
                new CalibrationTelemetryCollector(clock::nowMs, clock::advance);
        FakeSubscriptionSource source = new FakeSubscriptionSource();
        ScriptedGestures gestures = new ScriptedGestures();
        File output = File.createTempFile("qz-calibration-runner", ".json");
        CalibrationRunner.Config config = fastConfig(output);

        CalibrationRunner runner = new CalibrationRunner(
                1920, 1000, config, gestures, collector, source,
                ms -> {
                    if (gestures.pendingY != null && source.subscriber != null) {
                        int y = gestures.pendingY;
                        int x = gestures.pendingX;
                        gestures.pendingY = null;
                        gestures.pendingX = null;
                        clock.advance(1);
                        if (x == 57) {
                            float grade = (float) ((622.0 - y) / 18.57);
                            source.subscriber.accept(new InclineTelemetry(grade));
                        }
                    }
                    clock.advance(ms);
                },
                clock);

        CapturingListener listener = new CapturingListener();
        runner.run(listener);

        assertNull(listener.failure);
        assertNotNull(listener.result);
        assertTrue(output.exists());
        assertEquals(57, listener.result.incline.trackX);
        assertEquals(622.0, listener.fit.origin, 0.01);
        assertEquals(18.57, listener.fit.scale, 0.01);
        assertTrue(listener.fit.r2 > 0.999);
        assertNull(listener.result.resistance);
        assertNull(listener.resistanceFit);
        assertSame(listener.calibration, DeviceCalibration.current);
        assertTrue(source.closed);
        assertFalse(gestures.swipes.isEmpty());

        output.delete();
    }

    @Test
    public void inclineOnlyRunner_failsWhenNotEnoughReadings() throws Exception {
        ManualClock clock = new ManualClock();
        CalibrationTelemetryCollector collector =
                new CalibrationTelemetryCollector(clock::nowMs, clock::advance);
        FakeSubscriptionSource source = new FakeSubscriptionSource();
        ScriptedGestures gestures = new ScriptedGestures();
        File output = File.createTempFile("qz-calibration-runner", ".json");
        output.delete();
        CalibrationRunner.Config config = fastConfig(output);

        CalibrationRunner runner = new CalibrationRunner(
                1920, 1000, config, gestures, collector, source,
                clock::advance,
                clock);

        CapturingListener listener = new CapturingListener();
        runner.run(listener);

        assertNull(listener.result);
        assertNotNull(listener.failure);
        assertTrue(listener.failure.contains("at least 3 readings"));
        assertFalse(output.exists());
        assertTrue(source.closed);
    }

    @Test
    public void runner_savesOptionalResistanceCalibrationWhenReadingsExist() throws Exception {
        ManualClock clock = new ManualClock();
        CalibrationTelemetryCollector collector =
                new CalibrationTelemetryCollector(clock::nowMs, clock::advance);
        FakeSubscriptionSource source = new FakeSubscriptionSource();
        ScriptedGestures gestures = new ScriptedGestures();
        File output = File.createTempFile("qz-calibration-runner", ".json");
        CalibrationRunner.Config config = fastConfig(output);

        CalibrationRunner runner = new CalibrationRunner(
                1920, 1000, config, gestures, collector, source,
                ms -> {
                    if (gestures.pendingY != null && source.subscriber != null) {
                        int y = gestures.pendingY;
                        int x = gestures.pendingX;
                        gestures.pendingY = null;
                        gestures.pendingX = null;
                        clock.advance(1);
                        if (x == 57) {
                            float grade = (float) ((622.0 - y) / 18.57);
                            source.subscriber.accept(new InclineTelemetry(grade));
                        } else if (x == 1845) {
                            float level = (float) (1.0 + (802.0 - y) / 26.25);
                            if (level >= 1.0f) {
                                source.subscriber.accept(new GearTelemetry(level));
                            }
                        }
                    }
                    clock.advance(ms);
                },
                clock);

        CapturingListener listener = new CapturingListener();
        runner.run(listener);

        assertNull(listener.failure);
        assertNotNull(listener.result.resistance);
        assertEquals(1845, listener.result.resistance.trackX);
        assertEquals(1, listener.result.resistance.minLevel);
        assertEquals(802.0, listener.resistanceFit.origin, 0.1);
        assertEquals(26.25, listener.resistanceFit.scale, 0.1);
        assertTrue(listener.resistanceFit.r2 > 0.999);
        assertEquals(Integer.valueOf(1845), DeviceCalibration.current.resistanceTrackX);

        output.delete();
    }

    @Test
    public void runner_skipsResistanceWhenFitQualityIsLow() throws Exception {
        ManualClock clock = new ManualClock();
        CalibrationTelemetryCollector collector =
                new CalibrationTelemetryCollector(clock::nowMs, clock::advance);
        FakeSubscriptionSource source = new FakeSubscriptionSource();
        ScriptedGestures gestures = new ScriptedGestures();
        File output = File.createTempFile("qz-calibration-runner", ".json");
        CalibrationRunner.Config config = fastConfig(output);

        CalibrationRunner runner = new CalibrationRunner(
                1920, 1000, config, gestures, collector, source,
                ms -> {
                    if (gestures.pendingY != null && source.subscriber != null) {
                        int y = gestures.pendingY;
                        int x = gestures.pendingX;
                        gestures.pendingY = null;
                        gestures.pendingX = null;
                        clock.advance(1);
                        if (x == 57) {
                            float grade = (float) ((622.0 - y) / 18.57);
                            source.subscriber.accept(new InclineTelemetry(grade));
                        } else if (x == 1845) {
                            float level;
                            if (y <= 250) level = 24.0f;
                            else if (y <= 325) level = 23.0f;
                            else if (y <= 375) level = 1.0f;
                            else level = 19.0f;
                            source.subscriber.accept(new ResistanceTelemetry(level));
                        }
                    }
                    clock.advance(ms);
                },
                clock);

        CapturingListener listener = new CapturingListener();
        runner.run(listener);

        assertNull(listener.failure);
        assertNotNull(listener.result);
        assertNull(listener.result.resistance);
        assertNull(listener.resistanceFit);
        assertNull(DeviceCalibration.current.resistanceTrackX);

        output.delete();
    }

    private static CalibrationRunner.Config fastConfig(File output) {
        CalibrationRunner.Config config = new CalibrationRunner.Config();
        config.outputFile = output;
        config.coarseSettleMs = 1;
        config.coarseTimeoutMs = 1;
        config.fineSettleMs = 1;
        config.fineTimeoutMs = 1;
        config.stableSettleMs = 1;
        return config;
    }

    private static final class CapturingListener implements CalibrationRunner.Listener {
        CalibrationResult result;
        DeviceCalibration calibration;
        CalibrationFit.FitResult fit;
        CalibrationFit.FitResult resistanceFit;
        String failure;

        @Override
        public void onState(CalibrationRunner.State state, String detail) {}

        @Override
        public void onComplete(CalibrationResult result, DeviceCalibration calibration,
                               CalibrationFit.FitResult inclineFit,
                               CalibrationFit.FitResult resistanceFit) {
            this.result = result;
            this.calibration = calibration;
            this.fit = inclineFit;
            this.resistanceFit = resistanceFit;
        }

        @Override
        public void onFailed(String message, Throwable error) {
            this.failure = message;
        }
    }

    private static final class FakeSubscriptionSource
            implements CalibrationRunner.MetricSubscriptionSource {
        Consumer<Telemetry> subscriber;
        boolean closed = false;

        @Override
        public TelemetryHub.Subscription subscribe(Consumer<Telemetry> subscriber) {
            this.subscriber = subscriber;
            return () -> closed = true;
        }
    }

    private static final class ScriptedGestures implements CalibrationRunner.Gestures {
        final List<Integer> swipes = new ArrayList<>();
        Integer pendingY;
        Integer pendingX;

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean tap(int x, int y) {
            swipes.add(y);
            pendingX = x;
            pendingY = y;
            return true;
        }

        @Override
        public boolean swipe(int x, int fromY, int toY) {
            swipes.add(toY);
            pendingX = x;
            pendingY = toY;
            return true;
        }
    }

    private static final class ManualClock implements LongSupplier {
        long now = 1000;

        long nowMs() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }
}

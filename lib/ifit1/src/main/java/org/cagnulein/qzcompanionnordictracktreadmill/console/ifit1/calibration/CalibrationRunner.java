package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration;

import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;

import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.TelemetryHub;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.DeviceCalibration;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Guided incline + optional resistance calibration runner.
 *
 * Runs off the UI thread, drives the incline slider with Accessibility gestures,
 * consumes the shared mono-stdout telemetry stream, fits Y = origin - scale * metric,
 * writes qz-calibration.json, and updates DeviceCalibration.current immediately.
 */
public final class CalibrationRunner {
    private static final String TAG = "QZ:Calibration";

    public enum State {
        IDLE,
        PREFLIGHT,
        INCLINE_COARSE,
        INCLINE_FINE,
        RESISTANCE_PROBE,
        RESISTANCE_COARSE,
        RESISTANCE_FINE,
        FIT,
        SAVE,
        COMPLETE,
        FAILED,
        CANCELLED
    }

    public interface Gestures {
        boolean isReady();
        boolean tap(int x, int y);
        boolean swipe(int x, int fromY, int toY);
    }

    public interface Listener {
        void onState(State state, String detail);
        void onComplete(CalibrationResult result, DeviceCalibration calibration,
                        CalibrationFit.FitResult inclineFit,
                        CalibrationFit.FitResult resistanceFit);
        void onFailed(String message, Throwable error);
    }

    public static final class Config {
        public int coarseSettleMs = 2500;
        public int coarseTimeoutMs = 4000;
        public int fineSettleMs = 2000;
        public int fineTimeoutMs = 6000;
        public int stableSettleMs = 1000;
        public int resistanceProbeTimeoutMs = 3000;
        public int resistanceHomeSettleMs = 800;
        public int resistanceHomeDoneMs = 2000;
        public boolean useTapSweeps = true;
        public int minY = CalibrationFit.COARSE_MARGIN;
        public File outputFile = new File(Environment.getExternalStorageDirectory(),
                "qz-calibration.json");
    }

    private final int screenWidth;
    private final int screenHeight;
    private final Config config;
    private final Gestures gestures;
    private final CalibrationTelemetryCollector metrics;
    private final MetricSubscriptionSource subscriptionSource;
    private final Sleeper sleeper;
    private final LongSupplier clock;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private Thread thread;

    public CalibrationRunner(DisplayMetrics displayMetrics) {
        this(displayMetrics.widthPixels, displayMetrics.heightPixels,
                new Config(), new CalibrationGestureDriver(), new CalibrationTelemetryCollector(),
                subscriber -> TelemetryHub.shared().subscribe(subscriber),
                Thread::sleep, System::currentTimeMillis);
    }

    public CalibrationRunner(int screenWidth, int screenHeight, Config config,
                             Gestures gestures, CalibrationTelemetryCollector metrics,
                             MetricSubscriptionSource subscriptionSource, Sleeper sleeper,
                             LongSupplier clock) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.config = config;
        this.gestures = gestures;
        this.metrics = metrics;
        this.subscriptionSource = subscriptionSource;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    public synchronized void start(Listener listener) {
        if (thread != null && thread.isAlive()) {
            throw new IllegalStateException("Calibration already running");
        }
        cancelled.set(false);
        thread = new Thread(() -> run(listener), "QZ-DeviceCalibration");
        thread.setDaemon(true);
        thread.start();
    }

    public void cancel() {
        cancelled.set(true);
        Thread t = thread;
        if (t != null) t.interrupt();
    }

    void run(Listener listener) {
        TelemetryHub.Subscription subscription = null;
        try {
            state(listener, State.PREFLIGHT, "Checking Accessibility gestures and metric stream");
            if (!gestures.isReady()) {
                throw new IllegalStateException("AccessibilityService is not connected");
            }
            subscription = subscriptionSource.subscribe(metrics);

            CalibrationFit.TrackProfile profile = CalibrationFit.trackProfileForWidth(screenWidth);
            int inclineTrackX = profile.inclineTrackX;
            int resistanceTrackX = profile.resistanceTrackX;
            int bottomY = screenHeight - config.minY;

            state(listener, State.INCLINE_COARSE, "Coarse incline sweep at x=" + inclineTrackX);
            List<CalibrationFit.Point> inclineCoarse =
                    coarseSweep(inclineTrackX, config.minY, bottomY, config.minY, MetricTarget.INCLINE);
            if (inclineCoarse.size() < 3) {
                throw new IllegalStateException("Incline calibration requires at least 3 readings; got "
                        + inclineCoarse.size());
            }

            state(listener, State.FIT, "Fitting coarse incline readings");
            CalibrationFit.FitResult inclineFit = CalibrationFit.fitLinear(inclineCoarse);
            if (inclineFit.r2 < 0.99) {
                int[] range = CalibrationFit.findActiveRange(inclineCoarse, screenHeight);
                if (range != null) {
                    state(listener, State.INCLINE_FINE, "Fine incline sweep from y="
                            + range[0] + " to y=" + range[1]);
                    List<CalibrationFit.Point> fine =
                            fineSweep(inclineTrackX, range[0], range[1], MetricTarget.INCLINE);
                    if (fine.size() >= 3) {
                        state(listener, State.FIT, "Fitting fine incline readings");
                        inclineFit = CalibrationFit.fitLinear(fine);
                    }
                }
            }
            if (!inclineFit.isValid()) throw new IllegalStateException("Incline fit is invalid");
            if (inclineFit.isWarningQuality()) {
                Log.w(TAG, "Incline fit quality warning r2=" + inclineFit.r2);
            }

            ResistanceFit resistance = runResistance(resistanceTrackX, bottomY, listener);

            state(listener, State.SAVE, "Writing qz-calibration.json");
            CalibrationResult result = new CalibrationResult(
                    new CalibrationResult.SliderCalibration(
                            inclineTrackX, inclineFit.origin, inclineFit.scale),
                    resistance != null
                            ? new CalibrationResult.SliderCalibration(
                                    resistanceTrackX,
                                    resistance.fit.origin,
                                    resistance.fit.scale,
                                    resistance.minLevel)
                            : null);
            result.writeTo(config.outputFile);
            DeviceCalibration calibration = result.toDeviceCalibration();
            DeviceCalibration.current = calibration;

            state(listener, State.COMPLETE, resistance != null
                    ? "Incline and resistance calibration saved"
                    : "Incline calibration saved; resistance skipped");
            listener.onComplete(result, calibration, inclineFit,
                    resistance != null ? resistance.fit : null);
        } catch (CancelledException e) {
            state(listener, State.CANCELLED, "Calibration cancelled");
        } catch (Throwable t) {
            state(listener, State.FAILED, t.getMessage());
            listener.onFailed(t.getMessage(), t);
        } finally {
            if (subscription != null) subscription.close();
        }
    }

    private ResistanceFit runResistance(int trackX, int bottomY, Listener listener)
            throws InterruptedException, CancelledException {
        state(listener, State.RESISTANCE_PROBE, "Probing resistance slider at x=" + trackX);
        List<CalibrationFit.Point> coarse;
        if (config.useTapSweeps) {
            state(listener, State.RESISTANCE_COARSE, "Tapped resistance sweep at x=" + trackX);
            coarse = coarseSweep(trackX, config.minY, bottomY, config.minY, MetricTarget.RESISTANCE);
        } else {
            Integer thumbY = probeResistanceThumb(trackX);
            if (thumbY != null) {
                state(listener, State.RESISTANCE_COARSE, "Resistance sweep from y=" + thumbY);
                coarse = coarseSweep(trackX, thumbY + CalibrationFit.COARSE_STEP, bottomY,
                        thumbY, MetricTarget.RESISTANCE);
            } else {
                Log.w(TAG, "Resistance thumb probe failed; falling back to full sweep");
                state(listener, State.RESISTANCE_COARSE, "Fallback resistance sweep at x=" + trackX);
                coarse = coarseSweep(trackX, config.minY, bottomY, config.minY, MetricTarget.RESISTANCE);
            }
        }
        if (coarse.size() < 3) {
            Log.w(TAG, "Only " + coarse.size() + " resistance readings; skipping resistance");
            return null;
        }

        int[] range = CalibrationFit.findActiveRange(coarse, screenHeight);
        if (range == null) {
            Log.w(TAG, "Resistance active range not found; skipping resistance");
            return null;
        }

        ResistanceFit coarseFit = fitResistance(coarse);
        if (coarseFit.fit.r2 >= 0.99) return coarseFit;

        state(listener, State.RESISTANCE_FINE, "Fine resistance sweep from y="
                + range[0] + " to y=" + range[1]);
        List<CalibrationFit.Point> fine =
                fineSweep(trackX, range[0], range[1], MetricTarget.RESISTANCE);
        if (fine.size() < 3) {
            Log.w(TAG, "Only " + fine.size() + " fine resistance readings; skipping resistance");
            return null;
        }
        ResistanceFit fineFit = fitResistance(fine);
        if (!fineFit.fit.isValid() || fineFit.fit.isWarningQuality()) {
            Log.w(TAG, "Resistance fit below quality threshold; skipping resistance r2="
                    + fineFit.fit.r2);
            return null;
        }
        return fineFit;
    }

    private Integer probeResistanceThumb(int trackX)
            throws InterruptedException, CancelledException {
        int[] probeYs = {800, 700, 600, 500, 400, 300};
        for (int probeY : probeYs) {
            checkCancelled();
            long ts = clock.getAsLong();
            gestures.swipe(trackX, probeY, probeY - 100);
            sleep(config.resistanceHomeSettleMs);
            Float level = metrics.waitFreshResistance(ts, config.resistanceProbeTimeoutMs);
            if (level != null) {
                int thumbY = probeY - 100;
                Log.i(TAG, "resistance probe y=" + probeY + " level=" + level
                        + " thumbY=" + thumbY);
                while (thumbY > 250) {
                    checkCancelled();
                    int toY = Math.max(200, thumbY - 100);
                    gestures.swipe(trackX, thumbY, toY);
                    thumbY = toY;
                    sleep(config.resistanceHomeSettleMs);
                }
                sleep(config.resistanceHomeDoneMs);
                return thumbY;
            }
        }
        return null;
    }

    private ResistanceFit fitResistance(List<CalibrationFit.Point> points) {
        int minLevel = Integer.MAX_VALUE;
        for (CalibrationFit.Point point : points) {
            minLevel = Math.min(minLevel, (int) Math.round(point.metric));
        }
        List<CalibrationFit.Point> adjusted = new ArrayList<>();
        for (CalibrationFit.Point point : points) {
            adjusted.add(new CalibrationFit.Point(point.y, point.metric - minLevel));
        }
        CalibrationFit.FitResult fit = CalibrationFit.fitLinear(adjusted);
        if (fit.isWarningQuality()) {
            Log.w(TAG, "Resistance fit quality warning r2=" + fit.r2);
        }
        return new ResistanceFit(fit, minLevel);
    }

    private List<CalibrationFit.Point> coarseSweep(int trackX, int startY, int bottomY,
                                                   int previousY, MetricTarget metric)
            throws InterruptedException, CancelledException {
        List<CalibrationFit.Point> readings = new ArrayList<>();
        for (int y = startY; y <= bottomY; y += CalibrationFit.COARSE_STEP) {
            checkCancelled();
            long ts = clock.getAsLong();
            if (config.useTapSweeps) gestures.tap(trackX, y);
            else gestures.swipe(trackX, previousY, y);
            previousY = y;
            sleep(config.coarseSettleMs);
            Float value = metric.waitFresh(metrics, ts, config.coarseTimeoutMs);
            if (value != null) {
                readings.add(new CalibrationFit.Point(y, value));
                Log.i(TAG, "coarse y=" + y + " " + metric.logName + "=" + value);
                if (metric == MetricTarget.RESISTANCE && value <= 1.0f) break;
            } else {
                Log.i(TAG, "coarse y=" + y + " no " + metric.logName + " change");
            }
        }
        return readings;
    }

    private List<CalibrationFit.Point> fineSweep(int trackX, int topY, int bottomY,
                                                 MetricTarget metric)
            throws InterruptedException, CancelledException {
        if (config.useTapSweeps) gestures.tap(trackX, topY);
        else gestures.swipe(trackX, bottomY, topY);
        sleep(config.fineSettleMs * 2L);
        int previousY = topY;
        List<CalibrationFit.Point> raw = new ArrayList<>();
        for (int y = topY; y <= bottomY; y += CalibrationFit.FINE_STEP) {
            checkCancelled();
            long ts = clock.getAsLong();
            if (config.useTapSweeps) gestures.tap(trackX, y);
            else gestures.swipe(trackX, previousY, y);
            previousY = y;
            sleep(config.fineSettleMs);
            Float value = metric.waitStable(metrics, ts, config.stableSettleMs, config.fineTimeoutMs);
            if (value != null) {
                raw.add(new CalibrationFit.Point(y, value));
                Log.i(TAG, "fine y=" + y + " " + metric.logName + "=" + value);
            } else {
                Log.i(TAG, "fine y=" + y + " timeout");
            }
        }
        return CalibrationFit.filterMonotonicDescending(raw, 1.0);
    }

    private void state(Listener listener, State state, String detail) {
        Log.i(TAG, state + ": " + detail);
        listener.onState(state, detail);
    }

    private void checkCancelled() throws CancelledException {
        if (cancelled.get()) throw new CancelledException();
    }

    private void sleep(long ms) throws InterruptedException {
        sleeper.sleepMs(ms);
    }

    @FunctionalInterface
    public interface MetricSubscriptionSource {
        TelemetryHub.Subscription subscribe(Consumer<Telemetry> subscriber)
                throws IOException;
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleepMs(long ms) throws InterruptedException;
    }

    private static final class CancelledException extends Exception {}

    private static final class ResistanceFit {
        final CalibrationFit.FitResult fit;
        final int minLevel;

        ResistanceFit(CalibrationFit.FitResult fit, int minLevel) {
            this.fit = fit;
            this.minLevel = minLevel;
        }
    }

    private enum MetricTarget {
        INCLINE("grade") {
            @Override Float waitFresh(CalibrationTelemetryCollector metrics, long ts, long timeoutMs) {
                return metrics.waitFreshGrade(ts, timeoutMs);
            }
            @Override Float waitStable(CalibrationTelemetryCollector metrics, long ts,
                                       long settleMs, long timeoutMs) {
                return metrics.waitStableGrade(ts, settleMs, timeoutMs);
            }
        },
        RESISTANCE("resistance") {
            @Override Float waitFresh(CalibrationTelemetryCollector metrics, long ts, long timeoutMs) {
                return metrics.waitFreshResistance(ts, timeoutMs);
            }
            @Override Float waitStable(CalibrationTelemetryCollector metrics, long ts,
                                       long settleMs, long timeoutMs) {
                return metrics.waitStableResistance(ts, settleMs, timeoutMs);
            }
        };

        final String logName;

        MetricTarget(String logName) {
            this.logName = logName;
        }

        abstract Float waitFresh(CalibrationTelemetryCollector metrics, long ts, long timeoutMs);
        abstract Float waitStable(CalibrationTelemetryCollector metrics, long ts,
                                  long settleMs, long timeoutMs);
    }
}

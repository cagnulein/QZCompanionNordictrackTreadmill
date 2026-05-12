package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration;

import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.GearTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.InclineTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.ResistanceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;

import java.util.function.Consumer;

/** Caches recent iFit telemetry and exposes fresh/stable waits for calibration sweeps. */
public final class CalibrationTelemetryCollector implements Consumer<Telemetry> {

    public interface Clock { long nowMs(); }
    public interface Sleeper { void sleepMs(long ms) throws InterruptedException; }

    private static final long POLL_MS = 100L;

    private final Clock clock;
    private final Sleeper sleeper;
    private Sample grade;
    private Sample resistance;
    private Sample currentGear;

    public CalibrationTelemetryCollector() {
        this(System::currentTimeMillis, Thread::sleep);
    }

    public CalibrationTelemetryCollector(Clock clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Override
    public void accept(Telemetry telemetry) {
        accept(telemetry, clock.nowMs());
    }

    public synchronized void accept(Telemetry telemetry, long timestampMs) {
        if (telemetry instanceof InclineTelemetry) {
            grade = new Sample(telemetry.value, timestampMs);
        } else if (telemetry instanceof ResistanceTelemetry) {
            if (telemetry.value >= 1.0f) resistance = new Sample(telemetry.value, timestampMs);
        } else if (telemetry instanceof GearTelemetry) {
            if (telemetry.value >= 1.0f) currentGear = new Sample(telemetry.value, timestampMs);
        }
        notifyAll();
    }

    public synchronized long anyEventTimestampMs() {
        return Math.max(grade != null ? grade.timestampMs : 0L,
                Math.max(resistance != null ? resistance.timestampMs : 0L,
                        currentGear != null ? currentGear.timestampMs : 0L));
    }

    public Float waitFreshGrade(long afterTimestampMs, long timeoutMs) {
        return waitFresh(Target.GRADE, afterTimestampMs, timeoutMs);
    }

    public Float waitFreshResistance(long afterTimestampMs, long timeoutMs) {
        return waitFresh(Target.RESISTANCE, afterTimestampMs, timeoutMs);
    }

    public Float waitStableGrade(long afterTimestampMs, long settleMs, long timeoutMs) {
        return waitStable(Target.GRADE, afterTimestampMs, settleMs, timeoutMs);
    }

    public Float waitStableResistance(long afterTimestampMs, long settleMs, long timeoutMs) {
        return waitStable(Target.RESISTANCE, afterTimestampMs, settleMs, timeoutMs);
    }

    private Float waitFresh(Target target, long afterTimestampMs, long timeoutMs) {
        long deadline = clock.nowMs() + timeoutMs;
        while (clock.nowMs() < deadline) {
            Sample sample = sample(target, afterTimestampMs);
            if (sample != null && sample.timestampMs > afterTimestampMs) return sample.value;
            sleep(POLL_MS);
        }
        Sample sample = sample(target, afterTimestampMs);
        return sample != null && sample.timestampMs > afterTimestampMs ? sample.value : null;
    }

    private Float waitStable(Target target, long afterTimestampMs,
                             long settleMs, long timeoutMs) {
        long deadline = clock.nowMs() + timeoutMs;
        Float lastValue = null;
        long lastChangeMs = 0L;
        while (clock.nowMs() < deadline) {
            Sample sample = sample(target, afterTimestampMs);
            if (sample != null && sample.timestampMs > afterTimestampMs) {
                if (lastValue == null || Float.compare(sample.value, lastValue) != 0) {
                    lastValue = sample.value;
                    lastChangeMs = clock.nowMs();
                } else if (clock.nowMs() - lastChangeMs >= settleMs) {
                    return lastValue;
                }
            }
            sleep(POLL_MS);
        }
        return lastValue;
    }

    private synchronized Sample sample(Target target) {
        if (target == Target.GRADE) return grade;
        if (currentGear != null) return currentGear;
        return resistance;
    }

    private synchronized Sample sample(Target target, long afterTimestampMs) {
        if (target == Target.GRADE) return grade;
        if (currentGear != null && currentGear.timestampMs > afterTimestampMs) return currentGear;
        return resistance;
    }

    private enum Target { GRADE, RESISTANCE }

    private void sleep(long ms) {
        try {
            sleeper.sleepMs(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Sample {
        final float value;
        final long timestampMs;

        Sample(float value, long timestampMs) {
            this.value = value;
            this.timestampMs = timestampMs;
        }
    }
}

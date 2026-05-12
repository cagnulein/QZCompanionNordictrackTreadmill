package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.GestureService;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceLogTags;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;

/**
 * Represents one physical slider on the fitness device's touch screen.
 *
 * A slider has:
 *   - a fixed horizontal track position ({@link #trackX})
 *   - a movable thumb whose current vertical position is tracked in {@link #thumbY}
 *   - a formula mapping a metric value to a target Y coordinate ({@link #targetThumbY})
 *   - an optional live telemetry value ({@link #liveValue}) updated via {@link #applyTelemetry}
 *
 * Concrete subclasses ({@code InclineSlider}, {@code SpeedSlider}, {@code ResistanceSlider},
 * {@code GearSlider}) implement {@link #handle} and {@link #commandFor}, encapsulating
 * dedup, belt-gate, and command-creation logic for their slider type.
 */
public abstract class Slider {

    private static final String LOG_TAG = DeviceLogTags.SLIDER;

    @FunctionalInterface
    public interface ThumbYFormula {
        int apply(double v);
    }

    private final int trackX;
    private final int originThumbY;
    private final ThumbYFormula formula;
    private int thumbY;
    private Float lastApplied = null;
    private Class<? extends Telemetry> telemetryType;
    private Class<? extends Command> commandType;
    protected volatile Float liveValue = null;

    protected Slider(int trackX, int originThumbY, ThumbYFormula formula,
                     Class<? extends Telemetry> telemetryType,
                     Class<? extends Command> commandType) {
        this.trackX        = trackX;
        this.originThumbY  = originThumbY;
        this.thumbY        = originThumbY;
        this.formula       = formula;
        this.telemetryType = telemetryType;
        this.commandType   = commandType;
    }

    // ── Abstract API ───────────────────────────────────────────────────────────

    /** Apply a command value to this slider, handling dedup, belt-gate, and swipe. */
    public abstract void handle(double value, Device device);

    /** Create the matching Command type for this slider with the given value. */
    public abstract Command commandFor(double value);

    /** The telemetry value corresponding to this slider's physical origin (e.g. 0f for incline, 1f for resistance). */
    protected abstract float originValue();

    // ── Live telemetry ownership ───────────────────────────────────────────────

    /** Called by {@link Device#applyTelemetry}; subclasses may override for custom routing. */
    public void applyTelemetry(Telemetry telemetry, Device device) {
        if (telemetryType != null && telemetryType.isInstance(telemetry)) {
            liveValue = telemetry.value;
        }
    }

    /** Called by {@link GestureDevice#applyCommand}; symmetric with {@link #applyTelemetry}. */
    public void applyCommand(Command cmd, Device device) {
        if (commandType != null && commandType.isInstance(cmd)) handle(cmd.value, device);
    }

    public float liveValueOrZero() { return liveValue != null ? liveValue : 0f; }

    // ── Instance API ───────────────────────────────────────────────────────────

    /** Fixed horizontal pixel coordinate of this slider's track on screen. */
    public int trackX() { return trackX; }

    /** Pixel Y coordinate the thumb should move to for the given telemetry {@code value}. */
    public int targetThumbY(double v) { return formula.apply(v); }

    /**
     * Current pixel Y of the slider thumb.
     * Override in typed subclasses to choose live or dead mode:
     *   - Live: {@code liveValue != null ? targetThumbY(liveValue) : targetThumbY(0.0)}
     *   - Dead: {@code thumbY()}
     */
    protected abstract int currentThumbY();

    /**
     * Snap {@code value} to the nearest position this slider can physically reach.
     * Default: identity (no snapping). Override for sliders with fixed increments.
     */
    public float quantize(float value) { return value; }

    /**
     * Pixels of directional overshoot applied to compensate for physical slider hysteresis.
     * The thumb is swiped this many pixels past {@code toY} in the direction of travel,
     * while {@link #thumbY} still tracks the logical target so de-dup and state-tracking remain
     * consistent. Override in device-specific subclasses; default is 0 (no overshoot).
     *
     * @param fromY current thumb pixel Y (starting position)
     * @param toY   logical target pixel Y
     */
    protected int hysteresisPixels(int fromY, int toY) { return 0; }

    /**
     * Swipe the slider thumb from its current position to the position for {@code value}.
     */
    public void moveTo(double value, Device device) {
        int fromY  = currentThumbY();
        int toY    = targetThumbY(value);
        int h      = hysteresisPixels(fromY, toY);
        int swipeY = (h > 0 && toY != fromY) ? (toY < fromY ? toY - h : toY + h) : toY;
        String cmd = "input swipe " + trackX() + " " + fromY + " " + trackX() + " " + swipeY
                   + " " + GestureService.SWIPE_DURATION_MS;
        device.logger.log(Device.Logger.DEBUG, LOG_TAG, "swipe -> " + cmd);
        if (GestureService.isConnected()) {
            GestureService.performSwipe(trackX(), fromY, trackX(), swipeY, GestureService.SWIPE_DURATION_MS);
        } else {
            device.logger.log(Device.Logger.ERROR, LOG_TAG, "swipe dropped: AccessibilityService not connected");
        }
        device.commandExecutor.send(cmd);
        thumbY      = toY;
        lastApplied = (float) value;
    }

    public void snapToOrigin(Device device) {
        device.logger.log(Device.Logger.DEBUG, LOG_TAG, "snapToOrigin: tap at y=" + originThumbY);
        if (GestureService.isConnected()) {
            GestureService.performTap(trackX(), originThumbY);
        } else {
            device.logger.log(Device.Logger.ERROR, LOG_TAG, "snapToOrigin dropped: AccessibilityService not connected");
        }
        thumbY      = originThumbY;
        liveValue   = originValue();
        lastApplied = null;
    }

    public int   thumbY()        { return thumbY; }
    public int   originThumbY()  { return originThumbY; }
    public Float lastApplied()   { return lastApplied; }
}

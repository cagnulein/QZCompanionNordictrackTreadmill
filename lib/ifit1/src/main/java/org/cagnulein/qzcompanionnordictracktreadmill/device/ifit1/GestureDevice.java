package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.GestureService;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceLogTags;
import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.RawSwipeCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.SnapToOriginCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.Slider;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;

import java.util.List;

public abstract class GestureDevice extends Device {

    private static final String LOG_TAG = DeviceLogTags.DISPATCH;

    /** All sliders on this device, in declaration order. Used for metric routing and command dispatch. */
    public abstract List<Slider> sliders();

    /** Returns the first slider of the given type, or null if none. */
    public final <S extends Slider> S sliderOf(Class<S> type) {
        for (Slider s : sliders()) {
            if (type.isInstance(s)) return type.cast(s);
        }
        return null;
    }

    /** Routes a live telemetry update to all matching sliders on this device. */
    @Override
    public final void applyTelemetry(Telemetry telemetry) {
        for (Slider s : sliders()) s.applyTelemetry(telemetry, this);
    }

    /** Routes a command to the matching slider, or handles calibration commands directly. */
    @Override
    public final void applyCommand(Command cmd) {
        if (cmd instanceof SnapToOriginCommand) {
            ((SnapToOriginCommand) cmd).slider().snapToOrigin(this);
            return;
        }
        if (cmd instanceof RawSwipeCommand) {
            applyRawSwipe((RawSwipeCommand) cmd);
            return;
        }
        for (Slider s : sliders()) s.applyCommand(cmd, this);
    }

    private void applyRawSwipe(RawSwipeCommand cmd) {
        String shell = "input swipe " + fmt(cmd.x()) + " " + fmt(cmd.fromY()) + " "
                + fmt(cmd.x()) + " " + fmt(cmd.toY()) + " "
                + GestureService.SWIPE_DURATION_MS;
        logger.log(Logger.INFO, LOG_TAG,
                "CALSWIPE x=" + fmt(cmd.x()) + " " + fmt(cmd.fromY()) + "->" + fmt(cmd.toY()));
        logger.log(Logger.DEBUG, LOG_TAG, "raw swipe -> " + shell);
        if (GestureService.isConnected()) {
            GestureService.performSwipe(cmd.x(), cmd.fromY(), cmd.x(), cmd.toY(),
                    GestureService.SWIPE_DURATION_MS);
        } else {
            logger.log(Logger.ERROR, LOG_TAG, "raw swipe dropped: AccessibilityService not connected");
        }
        commandExecutor.send(shell);
    }

    private static String fmt(float v) {
        return v == Math.rint(v) ? Integer.toString((int) v) : Float.toString(v);
    }
}

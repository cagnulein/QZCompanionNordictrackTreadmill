package org.cagnulein.qzcompanionnordictracktreadmill.command;

import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S15iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S22iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X11iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;


/**
 * Integration tests for CommandDispatcher via DeviceController.
 *
 * Each test exercises the full pipeline from a raw UDP message string through
 * decodeCommand, queue/throttle logic, and apply*, down to the final "input swipe"
 * shell command — using real device instances with a capturing CommandExecutor.
 *
 * Time is injected via a mutable long[] so throttle behaviour can be tested
 * without sleeping.
 */
public class CommandDispatcherTest {

    private String lastCommand;
    private final long[] time = {1_000L};

    /** Creates a DeviceController with an injectable clock backed by {@code time}. */
    private <T extends Device> DeviceController ctrl(T device) {
        return new DeviceController(device, () -> time[0]);
    }

    /** Pre-populate a device's live speed so the treadmill speed gate passes. */
    private static void setMoving(Device device) {
        device.applyTelemetry(new SpeedTelemetry(5.0f));
    }

    @Before
    public void reset() { lastCommand = null; }

    /** Wraps a device to capture its swipe commands into lastCommand. */
    private <T extends Device> T dev(T d) {
        d.commandExecutor = cmd -> lastCommand = cmd;
        return d;
    }


    // ── Treadmill: basic dispatch ─────────────────────────────────────────────

    @Test
    public void treadmill_speedAndIncline_appliesSpeedSwipe() {
        // Speed and incline are decoded as two separate Commands and enqueued.
        // Only the first (speed) is drained immediately; incline stays queued.
        // X11i speed: speedX=1205, initialSpeedY=600, targetSpeedY(8.0)=447
        X11iDevice device = dev(new X11iDevice());
        setMoving(device);
        DeviceController ctrl = ctrl(device);
        ctrl.onPacket(QZCommandPacket.parse("8.0;3.0"));
        assertEquals("input swipe 1205 600 1205 447 200", lastCommand);
    }

    @Test
    public void treadmill_secondDispatchWithinWindow_isQueued() {
        // First dispatch: speed drained, incline queued.
        // A second dispatch within the throttle window enqueues both its Commands — no additional drain.
        int[] count = {0};
        X11iDevice device = new X11iDevice();
        device.commandExecutor = cmd -> { lastCommand = cmd; count[0]++; };
        setMoving(device);
        DeviceController ctrl = ctrl(device);
        ctrl.onPacket(QZCommandPacket.parse("8.0;3.0")); // speed drained (count=1), incline queued

        int countAfterFirst = count[0]; // 1
        time[0] += 200;                 // still within window
        ctrl.onPacket(QZCommandPacket.parse("9.0;4.0")); // queued — no additional drain
        assertEquals("second dispatch within window must not produce additional swipes",
                countAfterFirst, count[0]);
    }

    @Test
    public void treadmill_queuedMessage_drainedAfterThrottleWindow() {
        // A message queued during the throttle window executes once the window re-opens.
        // X11i targetSpeedY(9.0) = (int)(621.997 - 21.785*9.0) = 425; fromY=447 (after 8.0)
        X11iDevice device = dev(new X11iDevice());
        setMoving(device);
        DeviceController ctrl = ctrl(device);
        ctrl.onPacket(QZCommandPacket.parse("8.0;-100")); // speed 8.0 applied (y: 600→447)

        time[0] += 200;                              // within window
        ctrl.onPacket(QZCommandPacket.parse("9.0;-100")); // queued

        time[0] += CommandDispatcher.SWIPE_THROTTLE_MS;
        ctrl.onPacket(QZCommandPacket.parse("-1;-100")); // window open: drains "9.0;-100"
        assertEquals("input swipe 1205 447 1205 425 200", lastCommand);
    }

    @Test
    public void treadmill_speedGate_stopped_cachesSpeed() {
        // Speed must not be applied when current speed is 0 (device not yet running).
        // Use sentinel incline (-100) so that path also produces no command.
        X11iDevice device = dev(new X11iDevice());
        ctrl(device).onPacket(QZCommandPacket.parse("8.0;-100"));
        assertNull(lastCommand);
    }

    @Test
    public void treadmill_cachedSpeed_appliedWhenBeltStarts() {
        // Speed cached while stopped. Fires immediately when applyTelemetry(SpeedTelemetry > 0) is called —
        // no sentinel or subsequent dispatch() needed.
        // X11i targetSpeedY(8.0) = 447; fromY = 600 (initialSpeedY)
        X11iDevice device = dev(new X11iDevice());
        DeviceController ctrl = ctrl(device);
        ctrl.onPacket(QZCommandPacket.parse("8.0;-100")); // cached — belt stopped, no swipe
        assertNull(lastCommand);

        setMoving(device); // fires cached 8.0 immediately via applyTelemetry self-flush
        assertEquals("input swipe 1205 600 1205 447 200", lastCommand);
    }

    @Test
    public void treadmill_throttle_secondMessageWithinWindowIsCached() {
        X11iDevice device = dev(new X11iDevice());
        setMoving(device);
        DeviceController ctrl = ctrl(device);
        ctrl.onPacket(QZCommandPacket.parse("8.0;3.0")); // applied at t=1000
        lastCommand = null;

        time[0] += 200; // still within 500 ms window
        ctrl.onPacket(QZCommandPacket.parse("9.0;3.0")); // throttled — same device instance
        assertNull(lastCommand);
    }

    @Test
    public void treadmill_cachedSpeed_appliedAfterThrottleWindow() {
        // X11i targetSpeedY(9.0) = (int)(621.997 - 21.785*9.0) = (int)(425.932) = 425
        // Reuse the same device so initialSpeedY carries over correctly: 600→447 after first dispatch.
        X11iDevice device = dev(new X11iDevice());
        setMoving(device);
        DeviceController ctrl = ctrl(device);
        ctrl.onPacket(QZCommandPacket.parse("8.0;-100")); // speed 8.0 applied, y 600→447

        time[0] += 200;
        ctrl.onPacket(QZCommandPacket.parse("9.0;-100")); // throttled → queued

        time[0] = 1000 + CommandDispatcher.SWIPE_THROTTLE_MS + 100;
        ctrl.onPacket(QZCommandPacket.parse("-1;-100")); // flush queued 9.0, y 447→425
        assertEquals("input swipe 1205 447 1205 425 200", lastCommand);
    }

    @Test
    public void treadmill_sentinelMessage_noCommandGenerated() {
        X11iDevice device = dev(new X11iDevice());
        ctrl(device).onPacket(QZCommandPacket.parse("-1;-100"));
        assertNull(lastCommand);
    }

    @Test
    public void treadmill_commaLocale_parsesCorrectly() {
        // "8.0;3.0" — dot separator: speed field parses correctly and speed swipe fires first.
        X11iDevice device = dev(new X11iDevice());
        setMoving(device);
        ctrl(device).onPacket(QZCommandPacket.parse("8.0;3.0"));
        assertEquals("input swipe 1205 600 1205 447 200", lastCommand);
    }

    // ── Bike: basic dispatch ──────────────────────────────────────────────────

    @Test
    public void bike_resistance_appliesResistanceSwipe() {
        // S15i: resistanceX=1848, initialResistanceY=790
        // targetResistanceY(10.0) = 790 - (int)(23.16*10) = 790 - 231 = 559
        S15iDevice device = dev(new S15iDevice());
        ctrl(device).onPacket(QZCommandPacket.parse("10.0"));
        assertEquals("input swipe 1845 790 1845 559 200", lastCommand);
    }

    @Test
    public void bike_incline_appliesInclineSwipe() {
        // S22i targetInclineY(5.0)=(int)(622-18.57*5.0)=529; h=0 → dispatch=529
        S22iDevice device = dev(new S22iDevice());
        ctrl(device).onPacket(QZCommandPacket.parse("5.0;0"));
        assertEquals("input swipe 75 622 75 529 200", lastCommand);
    }

    @Test
    public void bike_duplicateResistance_notReapplied() {
        S15iDevice device = dev(new S15iDevice());
        DeviceController ctrl = ctrl(device);
        ctrl.onPacket(QZCommandPacket.parse("10.0")); // applied
        lastCommand = null;

        time[0] += CommandDispatcher.SWIPE_THROTTLE_MS + 100;
        ctrl.onPacket(QZCommandPacket.parse("10.0")); // same value — skipped
        assertNull(lastCommand);
    }

    @Test
    public void bike_throttle_cachesResistance() {
        S15iDevice device = dev(new S15iDevice());
        DeviceController ctrl = ctrl(device);
        ctrl.onPacket(QZCommandPacket.parse("10.0")); // applied at t=1000
        lastCommand = null;

        time[0] += 200; // within throttle window
        ctrl.onPacket(QZCommandPacket.parse("12.0")); // cached
        assertNull(lastCommand);
    }

    @Test
    public void bike_throttledResistance_cachedAndAppliedAfterWindow() {
        // S15i targetResistanceY(12.0) = 790 - (int)(23.16*12) = 790 - 277 = 513
        S15iDevice device = dev(new S15iDevice());
        DeviceController ctrl = ctrl(device);
        ctrl.onPacket(QZCommandPacket.parse("10.0")); // applied at t=1000

        time[0] += 200;
        lastCommand = null;
        ctrl.onPacket(QZCommandPacket.parse("12.0")); // throttled → queued
        assertNull(lastCommand);

        time[0] = 1000 + CommandDispatcher.SWIPE_THROTTLE_MS + 100;
        ctrl.onPacket(QZCommandPacket.parse("-1")); // sentinel: flush queued 12.0
        assertEquals("input swipe 1845 790 1845 513 200", lastCommand);
    }

    @Test
    public void bike_sentinelResistance_noCommandGenerated() {
        S15iDevice device = dev(new S15iDevice());
        ctrl(device).onPacket(QZCommandPacket.parse("-1"));
        assertNull(lastCommand);
    }

    // ── Locale mismatch ───────────────────────────────────────────────────────

    @Test
    public void treadmill_commaInValueWithDotSeparator_fallbackParsesCorrectly() {
        // "8,0;3,0" — parseField fallback replaces ',' with '.'; speed field parses and swipe fires.
        X11iDevice device = dev(new X11iDevice());
        setMoving(device);
        ctrl(device).onPacket(QZCommandPacket.parse("8,0;3,0"));
        assertEquals("input swipe 1205 600 1205 447 200", lastCommand);
    }

    // ── Sentinel as passive drain driver ──────────────────────────────────────

    /**
     * Verifies FIFO ordering and per-window drain count.
     *
     * In test mode (Clock-injected constructor, no background scheduler), onPacket() is the
     * only drain path — sentinel packets act as passive drain drivers, one Command per call.
     * In production the background thread drains independently; sentinel packets still drive
     * an immediate drain attempt for any Commands that arrive alongside them.
     *
     * S15i: resistanceY(12)=513, resistanceY(14)=466, resistanceY(16)=420
     */
    @Test
    public void sentinel_drainsOneCommandPerCall() {
        List<String> commands = new ArrayList<>();
        S15iDevice device = new S15iDevice();
        device.commandExecutor = commands::add;
        DeviceController ctrl = ctrl(device);

        ctrl.onPacket(QZCommandPacket.parse("10.0"));                      // t=1000: window open → drained immediately
        time[0] += 100; ctrl.onPacket(QZCommandPacket.parse("12.0"));     // t=1100: window closed → queued
        time[0] += 100; ctrl.onPacket(QZCommandPacket.parse("14.0"));     // t=1200: window closed → queued
        time[0] += 100; ctrl.onPacket(QZCommandPacket.parse("16.0"));     // t=1300: window closed → queued
        assertEquals("only the initial dispatch fires during the burst", 1, commands.size());

        time[0] += CommandDispatcher.SWIPE_THROTTLE_MS; // t=1800: window open
        ctrl.onPacket(QZCommandPacket.parse("-1"));                        // sentinel 1 → drains 12.0
        assertEquals(2, commands.size());
        assertEquals("input swipe 1845 790 1845 513 200", commands.get(1));

        time[0] += CommandDispatcher.SWIPE_THROTTLE_MS; // t=2300: window open
        ctrl.onPacket(QZCommandPacket.parse("-1"));                        // sentinel 2 → drains 14.0
        assertEquals(3, commands.size());
        assertEquals("input swipe 1845 790 1845 466 200", commands.get(2));

        time[0] += CommandDispatcher.SWIPE_THROTTLE_MS; // t=2800: window open
        ctrl.onPacket(QZCommandPacket.parse("-1"));                        // sentinel 3 → drains 16.0
        assertEquals(4, commands.size());
        assertEquals("input swipe 1845 790 1845 420 200", commands.get(3));
    }
}

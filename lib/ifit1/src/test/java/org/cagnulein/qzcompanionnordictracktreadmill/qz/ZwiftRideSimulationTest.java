package org.cagnulein.qzcompanionnordictracktreadmill.qz;

import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S22iDevice;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end simulation of a Zwift ride session against an S22i bike.
 *
 * Tests exercise the full pipeline without UDP sockets:
 *   grade message → DeviceController.onPacket() → S22iDevice.applyIncline()
 *   → Device.swipe() → Device.commandExecutor (captured)
 *
 * Time is injected so throttle behavior is controlled without sleeping.
 * All assertions verify both the swipe command format and the y1→y2 chain
 * (confirming that state tracking is correct across calls).
 *
 * S22i formula: x=75, piecewise — v≤0: (int)(622-10*v); v>0: (int)(622-18.57*v)
 * Calibrated 2026-04-19: slope 18.57 px/%, intercept 622 (iFit neutral from ride log).
 *   grade 0%  → y2=622   (initial y1=622)
 *   grade 5%  → y2=529
 *   grade 8%  → y2=473
 *   grade 10% → y2=436
 *
 * Incoming grades are quantized to the nearest 0.5% before comparison and dispatch.
 * This ensures swipes only target valid iFit snap positions (0.0, 0.5, 1.0, ...) so
 * that Zwift's sub-0.5% jitter (e.g. 6.7, 6.8, 6.9) never fires a swipe that can't
 * move the physical slider.
 *
 * Run via run-tests.sh (pure JVM, no Android SDK required).
 */
public class ZwiftRideSimulationTest {

    private final List<String> commands = new ArrayList<>();
    private final long[] time = {1_000L};

    /** Creates a S22iDevice that captures swipes into the shared commands list. */
    private S22iDevice bike() {
        S22iDevice dev = new S22iDevice();
        dev.commandExecutor = cmd -> commands.add(cmd);
        return dev;
    }

    /** Creates a DeviceController with an injectable clock backed by {@code time}. */
    private DeviceController ctrl(S22iDevice device) {
        return new DeviceController(device, () -> time[0]);
    }

    @Before
    public void setup() { commands.clear(); }

    /** Advances time by ms and dispatches one Zwift grade message (2-part format). */
    private void send(DeviceController ctrl, float grade, long advanceMs) {
        time[0] += advanceMs;
        ctrl.onPacket(QZCommandPacket.parse(grade + ";0"));
    }

    // ── helper: expected swipe string ────────────────────────────────────────

    private static String swipe(int y1, int y2) {
        return "input swipe 75 " + y1 + " 75 " + y2 + " 200";
    }

    /** S22i target y for a given grade percentage. Piecewise calibrated 2026-04-19. */
    private static int targetThumbY(float grade) {
        return grade <= 0.0f ? (int) (622.0 - 10.0 * grade)
                             : (int) (622.0 - 18.57 * grade);
    }

    private static int dispatchY(int fromY, int toY) {
        return toY;
    }

    // ── test 1: full Zwift ride — correct swipe chain ─────────────────────────

    /**
     * Verifies that five grade changes produce five swipes with the correct
     * y1→y2 chain.  y1 of each swipe must equal y2 of the previous — confirming
     * that state tracking is working correctly.
     */
    @Test
    public void fullRide_fiveGradeChanges_correctSwipeChain() {
        S22iDevice bike = bike();
        DeviceController ctrl = ctrl(bike);

        float[] grades = {0f, 5f, 10f, 5f, 0f};
        for (float g : grades) {
            send(ctrl, g, 600);  // 600ms > 500ms throttle
        }

        assertEquals("expected 5 swipes for 5 grade changes", 5, commands.size());

        // y1 of each swipe = logical thumbY from previous interval (not the dispatched overshoot Y)
        int logicalY = 622;
        for (int i = 0; i < grades.length; i++) {
            int toY = targetThumbY(grades[i]);
            assertEquals("swipe " + i + " (grade " + grades[i] + "%)",
                    swipe(logicalY, dispatchY(logicalY, toY)), commands.get(i));
            logicalY = toY;
        }
    }

    // ── test 2: throttle — FIFO queue drains oldest first ────────────────────

    /**
     * Three grade changes fired within the 500ms throttle window:
     * only the first fires immediately; the queue drains FIFO — 8f before 10f.
     */
    @Test
    public void throttle_rapidMessages_fifoQueueDrains() {
        S22iDevice bike = bike();
        DeviceController ctrl = ctrl(bike);

        // All three within the 500ms window: t=1100, t=1200, t=1300
        send(ctrl, 5f,  100);  // fires immediately (window fresh)
        send(ctrl, 8f,  100);  // throttled → queue=[8f]
        send(ctrl, 10f, 100);  // throttled → queue=[8f, 10f]

        assertEquals(1, commands.size());
        assertEquals(swipe(622, dispatchY(622, targetThumbY(5f))), commands.get(0));

        // Advance past throttle window — oldest queued (8f) drains first
        send(ctrl, 10f, 600);  // t=1900, window open → drains 8f

        assertEquals(2, commands.size());
        assertEquals(swipe(targetThumbY(5f), dispatchY(targetThumbY(5f), targetThumbY(8f))), commands.get(1));
    }

    // ── test 3: de-dup — same grade twice produces only one swipe ─────────────

    @Test
    public void dedup_sameGradeTwice_onlyFirstSwipeFires() {
        S22iDevice bike = bike();
        DeviceController ctrl = ctrl(bike);

        send(ctrl, 7f, 600);  // fires
        send(ctrl, 7f, 600);  // de-dup: same as last, skipped

        assertEquals(1, commands.size());
        assertEquals(swipe(622, dispatchY(622, targetThumbY(7f))), commands.get(0));
    }

    // ── test 4: sentinel flood — no swipes ────────────────────────────────────

    /**
     * Zwift sends "-1;-100" as a sentinel when the connection drops or no
     * grade data is available.  These must never trigger a swipe.
     */
    @Test
    public void sentinelFlood_noSwipesFire() {
        S22iDevice bike = bike();
        DeviceController ctrl = ctrl(bike);

        for (int i = 0; i < 20; i++) {
            time[0] += 600;
            ctrl.onPacket(QZCommandPacket.parse("-1;-100"));
        }

        assertTrue("no swipes expected for 20 sentinel messages", commands.isEmpty());
    }

    // ── test 5: decimal separator variants produce identical swipes ───────────

    @Test
    public void decimalSeparator_commaAndDot_identicalSwipes() {
        S22iDevice bikeDot   = new S22iDevice();
        S22iDevice bikeComma = bike();

        List<String> dotCmds   = new ArrayList<>();
        List<String> commaCmds = new ArrayList<>();

        bikeDot.commandExecutor   = cmd -> dotCmds.add(cmd);
        bikeComma.commandExecutor = cmd -> commaCmds.add(cmd);

        DeviceController dotCtrl   = new DeviceController(bikeDot,   () -> time[0]);
        DeviceController commaCtrl = new DeviceController(bikeComma, () -> time[0] + 1);

        time[0] += 600;
        dotCtrl.onPacket(QZCommandPacket.parse("5.0;0"));
        commaCtrl.onPacket(QZCommandPacket.parse("5,0;0"));

        assertEquals(1, dotCmds.size());
        assertEquals(1, commaCmds.size());
        assertEquals("comma and dot separators must produce the same swipe",
                dotCmds.get(0), commaCmds.get(0));
    }

    // ── test 6: alpe du zwift profile — realistic climb ───────────────────────

    /**
     * Simulates the Alpe du Zwift climb profile (simplified): rising grades,
     * a plateau, then a descent.  Verifies that each swipe in the sequence
     * chains correctly and that the final flat position is reached.
     */
    @Test
    public void alpeProfile_climbAndDescent_correctSequence() {
        S22iDevice bike = bike();
        DeviceController ctrl = ctrl(bike);

        float[] profile = {2f, 5f, 8f, 10f, 8f, 5f, 2f, 0f};
        for (float g : profile) {
            send(ctrl, g, 600);
        }

        assertEquals(profile.length, commands.size());

        int logicalY = 622;
        for (int i = 0; i < profile.length; i++) {
            int toY = targetThumbY(profile[i]);
            assertEquals("step " + i + " (grade " + profile[i] + "%)",
                    swipe(logicalY, dispatchY(logicalY, toY)), commands.get(i));
            logicalY = toY;
        }

        // After the descent, we should be near flat (y = 622, the calibrated zero point)
        int finalY = targetThumbY(0f);
        assertEquals("final position should be flat", 622, finalY);
        assertEquals(swipe(targetThumbY(2f), dispatchY(targetThumbY(2f), finalY)), commands.get(commands.size() - 1));
    }

    // ── test 7: sub-grid changes are suppressed via quantization ─────────────

    /**
     * Grades are quantized to the nearest 0.5% before dispatching.
     * 6.7% quantizes to 7.0% (same as last fired), so no swipe fires.
     * 6.5% quantizes to 6.5% (different), so a swipe fires.
     */
    @Test
    public void subThresholdChange_belowHalfPercent_suppressed() {
        S22iDevice bike = bike();
        DeviceController ctrl = ctrl(bike);

        send(ctrl, 7.0f, 600);  // quantized 7.0 → fires
        send(ctrl, 6.7f, 600);  // quantized 7.0 → de-dup (same as last)
        send(ctrl, 6.5f, 600);  // quantized 6.5 → fires

        assertEquals("only snap-grid changes should fire; 6.7→7.0 quantizes to same as last", 2, commands.size());
        assertEquals(swipe(622,           dispatchY(622,           targetThumbY(7.0f))), commands.get(0));
        assertEquals(swipe(targetThumbY(7.0f), dispatchY(targetThumbY(7.0f), targetThumbY(6.5f))), commands.get(1));
    }

    /**
     * A grade that quantizes to a different 0.5% snap point must fire.
     * 6.5% quantizes to 6.5%, which differs from last fired 7.0%.
     */
    @Test
    public void exactThreshold_halfPercent_fires() {
        S22iDevice bike = bike();
        DeviceController ctrl = ctrl(bike);

        send(ctrl, 7.0f, 600);  // quantized 7.0 → fires
        send(ctrl, 6.5f, 600);  // quantized 6.5 → different snap point → fires

        assertEquals(2, commands.size());
        assertEquals(swipe(targetThumbY(7.0f), dispatchY(targetThumbY(7.0f), targetThumbY(6.5f))), commands.get(1));
    }

    // ── test 8: Mountain Mash regression — slow descent in 0.1% steps ────────

    /**
     * Regression for the Mountain Mash route: Zwift descends in 0.1% increments
     * (~1.7 px each), which left the bike stuck at the peak grade.
     *
     * With the 0.5% threshold, only 1-in-5 messages fires a swipe.  Each fired
     * swipe covers ~8.6 pixels — enough to reliably cross a snap boundary.
     *
     * Descending from 7.0% to 5.0%:
     *   fires at 7.0%, 6.5%, 6.0%, 5.5%, 5.0%  (5 swipes, not 21)
     */
    @Test
    public void mountainMash_slowDescent_firesEveryHalfPercent() {
        S22iDevice bike = bike();
        DeviceController ctrl = ctrl(bike);

        // Peak grade — fires immediately
        send(ctrl, 7.0f, 600);

        // Descend in 0.1% steps from 6.9% to 5.0%
        for (int i = 69; i >= 50; i--) {
            send(ctrl, i / 10.0f, 600);
        }

        // Expected fires: 7.0, 6.5, 6.0, 5.5, 5.0
        float[] expectedGrades = {7.0f, 6.5f, 6.0f, 5.5f, 5.0f};
        assertEquals("descent in 0.1% steps should fire at every 0.5% boundary",
                expectedGrades.length, commands.size());

        int logicalY = 622;
        for (int i = 0; i < expectedGrades.length; i++) {
            int toY = targetThumbY(expectedGrades[i]);
            assertEquals("fire " + i + " (grade " + expectedGrades[i] + "%)",
                    swipe(logicalY, dispatchY(logicalY, toY)), commands.get(i));
            logicalY = toY;
        }
    }

    /**
     * Symmetric ascent: Zwift climbs in 0.1% steps from 5.0% to 7.0%.
     * Fires at 5.0%, 5.5%, 6.0%, 6.5%, 7.0% — same 0.5% cadence.
     */
    @Test
    public void mountainMash_slowAscent_firesEveryHalfPercent() {
        S22iDevice bike = bike();
        DeviceController ctrl = ctrl(bike);

        // Starting grade
        send(ctrl, 5.0f, 600);

        // Ascend in 0.1% steps from 5.1% to 7.0%
        for (int i = 51; i <= 70; i++) {
            send(ctrl, i / 10.0f, 600);
        }

        // Expected fires: 5.0, 5.5, 6.0, 6.5, 7.0
        float[] expectedGrades = {5.0f, 5.5f, 6.0f, 6.5f, 7.0f};
        assertEquals("ascent in 0.1% steps should fire at every 0.5% boundary",
                expectedGrades.length, commands.size());

        int logicalY = 622;
        for (int i = 0; i < expectedGrades.length; i++) {
            int toY = targetThumbY(expectedGrades[i]);
            assertEquals("fire " + i + " (grade " + expectedGrades[i] + "%)",
                    swipe(logicalY, dispatchY(logicalY, toY)), commands.get(i));
            logicalY = toY;
        }
    }
}

package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S22iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.command.InclineCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * End-to-end replay of the Zwift Hilly Route (Watopia) grade profile through S22iDevice.
 *
 * Source: bestbikesplit.com/zwift/229508 — 31 intervals, 9.12 km total.
 * Zwift scales route grades to 50% before sending to QZCompanion.
 *
 * Pipeline per interval:
 *   actualGrade × 0.5  →  roundToOneDecimal  →  quantize (0.5% steps)  →  targetThumbY swipe
 *
 * Each interval is dispatched directly via the iFit1 command handler — throttle logic
 * lives in CommandDispatcher, not in the device, so de-dup logic is exercised
 * in isolation here.
 *
 * Expected result: 30 dispatches — interval 30 is de-duped because
 *   -2.19% × 0.5 = -1.095 → -1.1% → quantize = -1.0  (same as interval 29).
 */
public class HillyRouteReplayTest {

    // Actual route grades from bestbikesplit.com/zwift/229508
    private static final float[] ACTUAL_GRADES = {
        +0.66f, +5.42f, +7.85f, +5.42f, +2.69f, -1.94f, +0.67f, -0.52f,
        -0.23f, -3.36f, +3.13f,  0.00f, -2.37f, -4.27f, -6.44f, -2.60f,
        -0.54f, +2.30f, -1.81f, -3.24f,  0.00f, +4.13f, -3.15f, -5.38f,
        +1.43f, -0.17f, -1.60f, +1.65f, -1.92f, -2.19f, -0.19f
    };

    // Expected swipe command per interval. null = de-duped (no dispatch expected).
    // Pipeline: actual × 0.5 → roundToOneDecimal → quantize(0.5 steps) → targetThumbY
    // S22i formula: targetThumbY(v) = (int)(622 - 18.57*v) for all v. Calibrated 2026-04-19/22.
    // trackX = 75, initial thumbY = 622, h=0 (accessibility gestures have no spring-back)
    private static final String[] EXPECTED = {
        "input swipe 75 622 75 612 200",  //  1: q+0.5 → y612
        "input swipe 75 612 75 575 200",  //  2: q+2.5 → y575
        "input swipe 75 575 75 547 200",  //  3: q+4.0 → y547
        "input swipe 75 547 75 575 200",  //  4: q+2.5 → y575
        "input swipe 75 575 75 594 200",  //  5: q+1.5 → y594
        "input swipe 75 594 75 640 200",  //  6: q-1.0 → y640
        "input swipe 75 640 75 612 200",  //  7: q+0.5 → y612
        "input swipe 75 612 75 631 200",  //  8: q-0.5 → y631
        "input swipe 75 631 75 622 200",  //  9: q+0.0 → y622
        "input swipe 75 622 75 649 200",  // 10: q-1.5 → y649
        "input swipe 75 649 75 594 200",  // 11: q+1.5 → y594
        "input swipe 75 594 75 622 200",  // 12: q+0.0 → y622
        "input swipe 75 622 75 640 200",  // 13: q-1.0 → y640
        "input swipe 75 640 75 659 200",  // 14: q-2.0 → y659
        "input swipe 75 659 75 677 200",  // 15: q-3.0 → y677
        "input swipe 75 677 75 649 200",  // 16: q-1.5 → y649
        "input swipe 75 649 75 631 200",  // 17: q-0.5 → y631
        "input swipe 75 631 75 603 200",  // 18: q+1.0 → y603
        "input swipe 75 603 75 640 200",  // 19: q-1.0 → y640
        "input swipe 75 640 75 649 200",  // 20: q-1.5 → y649
        "input swipe 75 649 75 622 200",  // 21: q+0.0 → y622
        "input swipe 75 622 75 584 200",  // 22: q+2.0 → y584
        "input swipe 75 584 75 649 200",  // 23: q-1.5 → y649
        "input swipe 75 649 75 668 200",  // 24: q-2.5 → y668
        "input swipe 75 668 75 612 200",  // 25: q+0.5 → y612
        "input swipe 75 612 75 622 200",  // 26: q+0.0 → y622
        "input swipe 75 622 75 640 200",  // 27: q-1.0 → y640
        "input swipe 75 640 75 603 200",  // 28: q+1.0 → y603
        "input swipe 75 603 75 640 200",  // 29: q-1.0 → y640
        null,                             // 30: q-1.0 → DE-DUPED (same as #29)
        "input swipe 75 640 75 622 200",  // 31: q+0.0 → y622
    };

    @Test
    public void hillyRoute_replayAt50pctZwiftScaling_dispatchesCorrectSwipes() {
        List<String> dispatched = new ArrayList<>();
        S22iDevice dev = new S22iDevice();
        dev.commandExecutor = cmd -> dispatched.add(cmd);

        for (int i = 0; i < ACTUAL_GRADES.length; i++) {
            int sizeBefore = dispatched.size();
            dev.applyCommand(new InclineCommand(QZCommandPacket.roundToOneDecimal(ACTUAL_GRADES[i] * 0.5f)));

            if (EXPECTED[i] == null) {
                assertEquals("interval " + (i + 1) + " should be de-duped — no command expected",
                        sizeBefore, dispatched.size());
            } else {
                assertEquals("interval " + (i + 1) + " should produce exactly one command",
                        sizeBefore + 1, dispatched.size());
                assertEquals("interval " + (i + 1) + " wrong swipe target",
                        EXPECTED[i], dispatched.get(dispatched.size() - 1));
            }
        }

        long expectedDispatches = Arrays.stream(EXPECTED).filter(e -> e != null).count();
        assertEquals("total dispatched commands", expectedDispatches, dispatched.size());
    }
}

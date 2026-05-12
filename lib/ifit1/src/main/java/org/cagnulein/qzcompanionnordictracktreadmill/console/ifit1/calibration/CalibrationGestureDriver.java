package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration;

import android.util.Log;

import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.GestureService;

/** Thin logging wrapper around Accessibility gestures used by calibration. */
public final class CalibrationGestureDriver implements CalibrationRunner.Gestures {
    private static final String TAG = "QZ:Calibration";

    public boolean isReady() {
        return GestureService.isConnected();
    }

    public boolean tap(int x, int y) {
        if (!isReady()) {
            Log.e(TAG, "tap dropped: AccessibilityService not connected x=" + x + " y=" + y);
            return false;
        }
        Log.i(TAG, "tap x=" + x + " y=" + y);
        GestureService.performTap(x, y);
        return true;
    }

    public boolean swipe(int x, int fromY, int toY) {
        if (!isReady()) {
            Log.e(TAG, "swipe dropped: AccessibilityService not connected x=" + x
                    + " fromY=" + fromY + " toY=" + toY);
            return false;
        }
        Log.i(TAG, "swipe x=" + x + " fromY=" + fromY + " toY=" + toY);
        GestureService.performSwipe(x, fromY, x, toY, GestureService.SWIPE_DURATION_MS);
        return true;
    }
}

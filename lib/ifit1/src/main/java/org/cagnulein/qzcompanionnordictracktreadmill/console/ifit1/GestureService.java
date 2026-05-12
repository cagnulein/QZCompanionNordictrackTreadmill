package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class GestureService extends AccessibilityService {

    private static final String TAG = "QZ:IFit";
    public static final int SWIPE_DURATION_MS = 200;

    private static GestureService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    // --- Gesture injection (existing) ---

    public static boolean isConnected() {
        return instance != null;
    }

    public static void performTap(float x, float y) {
        if (instance == null) {
            Log.e(TAG, "performTap: AccessibilityService not connected");
            return;
        }
        Path path = new Path();
        path.moveTo(x, y);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            boolean accepted = instance.dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) {
                    Log.d(TAG, "tap completed x=" + (int)x + " y=" + (int)y);
                }
                @Override public void onCancelled(GestureDescription g) {
                    Log.e(TAG, "tap CANCELLED x=" + (int)x + " y=" + (int)y);
                }
            }, null);
            if (!accepted) Log.e(TAG, "performTap: dispatchGesture rejected");
        }
    }

    public static void performSwipe(float startX, float startY, float endX, float endY, long duration) {
        if (instance == null) {
            Log.e(TAG, "performSwipe: AccessibilityService not connected");
            return;
        }
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
            boolean accepted = instance.dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) {
                    Log.d(TAG, "gesture completed x=" + (int)startX + " y=" + (int)startY
                            + "→" + (int)endY);
                }
                @Override public void onCancelled(GestureDescription g) {
                    Log.e(TAG, "gesture CANCELLED x=" + (int)startX + " y=" + (int)startY
                            + "→" + (int)endY);
                }
            }, null);
            if (!accepted) Log.e(TAG, "performSwipe: dispatchGesture rejected");
        }
    }
}

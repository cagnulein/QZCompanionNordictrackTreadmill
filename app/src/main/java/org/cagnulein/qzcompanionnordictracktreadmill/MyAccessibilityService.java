package org.cagnulein.qzcompanionnordictracktreadmill;

// MyAccessibilityService.java
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class MyAccessibilityService extends AccessibilityService {
    private static MyAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // You can react to specific events here if needed
    }

    @Override
    public void onInterrupt() {
    }

    public static void performSwipe(float startX, float startY, float endX, float endY, long duration) {
        if (instance == null) return;

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
            boolean result = instance.dispatchGesture(builder.build(), null, null);
            Log.d("MyAccesibilityService", "result: " + result);
        }


    }
}
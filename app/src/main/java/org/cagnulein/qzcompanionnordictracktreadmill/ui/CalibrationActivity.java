package org.cagnulein.qzcompanionnordictracktreadmill.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import org.cagnulein.qzcompanionnordictracktreadmill.R;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration.CalibrationFit;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration.CalibrationResult;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration.CalibrationRunner;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.DeviceCalibration;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.DeviceRegistry;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.GestureService;

import java.util.Locale;

public class CalibrationActivity extends AppCompatActivity {
    private static final long IFIT_HANDOFF_DELAY_MS = 4000L;

    private TextView phaseLabel;
    private TextView progressDetail;
    private TextView resultFormula;
    private TextView resultQuality;
    private TextView messageText;
    private ProgressBar progressBar;
    private LinearLayout prereqList;
    private View resultsSection;
    private MaterialButton btnStart;
    private MaterialButton btnDone;
    private MaterialButton btnRetry;
    private MaterialButton btnCancel;

    private CalibrationRunner runner;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_discover_incline);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Calibrate Device");

        phaseLabel = findViewById(R.id.phaseLabel);
        progressDetail = findViewById(R.id.progressDetail);
        resultFormula = findViewById(R.id.resultFormula);
        resultQuality = findViewById(R.id.resultQuality);
        messageText = findViewById(R.id.messageText);
        progressBar = findViewById(R.id.progressBar);
        prereqList = findViewById(R.id.prereqList);
        resultsSection = findViewById(R.id.resultsSection);
        btnStart = findViewById(R.id.btnStart);
        btnDone = findViewById(R.id.btnDone);
        btnRetry = findViewById(R.id.btnRetry);
        btnCancel = findViewById(R.id.btnCancel);

        btnStart.setOnClickListener(v -> startCalibration());
        btnRetry.setOnClickListener(v -> resetReady());
        btnDone.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> {
            if (runner != null) runner.cancel();
            finish();
        });

        renderPrerequisites();
        resetReady();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing() && runner != null) runner.cancel();
        releaseWakeLock();
        super.onDestroy();
    }

    private void resetReady() {
        running = false;
        runner = null;
        progressBar.setIndeterminate(false);
        progressBar.setProgress(0);
        phaseLabel.setText("Ready");
        progressDetail.setText("Start an active iFit manual workout before continuing.");
        resultsSection.setVisibility(View.GONE);
        messageText.setVisibility(View.GONE);
        btnStart.setVisibility(View.VISIBLE);
        btnDone.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);
        btnCancel.setVisibility(View.VISIBLE);
        btnStart.setEnabled(hasPrerequisites());
        renderPrerequisites();
    }

    private void startCalibration() {
        if (running) return;
        running = true;
        acquireWakeLock();
        progressBar.setIndeterminate(true);
        progressBar.setProgress(0);
        resultsSection.setVisibility(View.GONE);
        messageText.setVisibility(View.GONE);
        btnStart.setVisibility(View.GONE);
        btnDone.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);
        btnCancel.setVisibility(View.VISIBLE);
        phaseLabel.setText("Opening iFit");
        progressDetail.setText("Switching to iFit before the first calibration gesture.");
        launchIfit();
        handler.postDelayed(() -> {
            if (running && runner == null) startRunner();
        }, IFIT_HANDOFF_DELAY_MS);
    }

    private void startRunner() {
        runner = new CalibrationRunner(getResources().getDisplayMetrics());
        runner.start(new CalibrationRunner.Listener() {
            @Override
            public void onState(CalibrationRunner.State state, String detail) {
                MainActivity.writeLog("QZ:Calibration " + state + " " + detail);
                runOnUiThread(() -> renderState(state, detail));
            }

            @Override
            public void onComplete(CalibrationResult result, DeviceCalibration calibration,
                                   CalibrationFit.FitResult inclineFit,
                                   CalibrationFit.FitResult resistanceFit) {
                persistCalibratedDevice();
                runOnUiThread(() -> renderComplete(inclineFit, resistanceFit));
            }

            @Override
            public void onFailed(String message, Throwable error) {
                Log.e("QZ:Calibration", "Calibration failed", error);
                runOnUiThread(() -> renderFailed(message != null ? message : "Unknown error"));
            }
        });
    }

    private void renderState(CalibrationRunner.State state, String detail) {
        phaseLabel.setText(labelFor(state));
        progressDetail.setText(detail);
        if (state == CalibrationRunner.State.INCLINE_COARSE) progressBar.setProgress(30);
        else if (state == CalibrationRunner.State.INCLINE_FINE) progressBar.setProgress(60);
        else if (state == CalibrationRunner.State.FIT) progressBar.setProgress(80);
        else if (state == CalibrationRunner.State.SAVE) progressBar.setProgress(90);
    }

    private void renderComplete(CalibrationFit.FitResult inclineFit,
                                CalibrationFit.FitResult resistanceFit) {
        running = false;
        runner = null;
        releaseWakeLock();
        progressBar.setIndeterminate(false);
        progressBar.setProgress(100);
        phaseLabel.setText("Saved");
        progressDetail.setText("Custom calibrated device is selected.");
        resultsSection.setVisibility(View.VISIBLE);
        resultFormula.setText(String.format(Locale.US,
                "Incline: origin %.2f, scale %.4f", inclineFit.origin, inclineFit.scale));
        String resistance = resistanceFit != null
                ? String.format(Locale.US, "\nResistance: origin %.2f, scale %.4f, R² %.4f",
                        resistanceFit.origin, resistanceFit.scale, resistanceFit.r2)
                : "\nResistance skipped";
        resultQuality.setText(String.format(Locale.US,
                "Incline R² %.4f%s%s",
                inclineFit.r2,
                inclineFit.isWarningQuality() ? "  Low fit quality" : "",
                resistance));
        messageText.setVisibility(View.GONE);
        btnStart.setVisibility(View.GONE);
        btnDone.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.GONE);
    }

    private void renderFailed(String message) {
        running = false;
        runner = null;
        releaseWakeLock();
        progressBar.setIndeterminate(false);
        phaseLabel.setText("Failed");
        progressDetail.setText("Calibration did not complete.");
        messageText.setText(message);
        messageText.setVisibility(View.VISIBLE);
        btnStart.setVisibility(View.GONE);
        btnDone.setVisibility(View.GONE);
        btnRetry.setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.VISIBLE);
    }

    private void renderPrerequisites() {
        prereqList.removeAllViews();
        addPrereq("Accessibility", isAccessibilityEnabled(),
                "QZ Companion can inject slider gestures");
        addPrereq("Metric log access", hasReadLogsPermission(),
                "iFit metrics can be read from mono-stdout");
        addPrereq("Storage", hasStoragePermission(),
                "qz-calibration.json can be written to /sdcard");
    }

    private void addPrereq(String name, boolean ok, String detail) {
        Context ctx = prereqList.getContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));

        TextView dot = new TextView(ctx);
        dot.setText(ok ? "● " : "● ");
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dot.setTextColor(ok ? 0xFF4CAF50 : 0xFFFF5722);
        dot.setTypeface(null, Typeface.BOLD);

        TextView text = new TextView(ctx);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(name, new StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ");
        sb.append(detail);
        text.setText(sb);

        row.addView(dot);
        row.addView(text);
        prereqList.addView(row);
    }

    private boolean hasPrerequisites() {
        return isAccessibilityEnabled() && hasReadLogsPermission() && hasStoragePermission();
    }

    private boolean hasReadLogsPermission() {
        return checkCallingOrSelfPermission("android.permission.READ_LOGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasStoragePermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isAccessibilityEnabled() {
        return GestureService.isConnected() || isAccessibilityServiceEnabled(this, GestureService.class);
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException ignored) {}
        if (accessibilityEnabled != 1) return false;

        android.content.ComponentName target = new android.content.ComponentName(context, accessibilityService);
        String settingValue = Settings.Secure.getString(
                context.getApplicationContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (settingValue == null) return false;

        for (String entry : settingValue.split(":")) {
            android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(entry.trim());
            if (target.equals(cn)) return true;
        }
        return false;
    }

    private void persistCalibratedDevice() {
        SharedPreferences prefs = getSharedPreferences("QZ", MODE_PRIVATE);
        prefs.edit()
                .putString(MainActivity.PREF_DEVICE_ID,
                        DeviceRegistry.DeviceId.custom_calibrated.name())
                .apply();
    }

    private void launchIfit() {
        Intent ifitIntent = getPackageManager().getLaunchIntentForPackage("com.ifit.standalone");
        if (ifitIntent == null) {
            ifitIntent = new Intent(Intent.ACTION_MAIN);
            ifitIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            ifitIntent.setPackage("com.ifit.standalone");
        }
        ifitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(ifitIntent); }
        catch (Exception ignored) {}
    }

    private String labelFor(CalibrationRunner.State state) {
        switch (state) {
            case PREFLIGHT: return "Checking";
            case INCLINE_COARSE: return "Incline Sweep";
            case INCLINE_FINE: return "Fine Sweep";
            case FIT: return "Fitting";
            case SAVE: return "Saving";
            case COMPLETE: return "Saved";
            case FAILED: return "Failed";
            case CANCELLED: return "Cancelled";
            default: return "Ready";
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return;
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "QZCompanion:Calibration");
        wakeLock.acquire(15 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics()));
    }
}

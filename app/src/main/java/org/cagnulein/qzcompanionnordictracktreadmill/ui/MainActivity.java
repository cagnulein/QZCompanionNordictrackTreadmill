package org.cagnulein.qzcompanionnordictracktreadmill.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.net.Inet4Address;
import java.net.NetworkInterface;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.sql.Timestamp;
import java.util.Date;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandListenerService;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration.CalibrationFit;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration.CalibrationResult;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.calibration.CalibrationRunner;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.GestureService;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZTelemetryUnicastingService;
import org.cagnulein.qzcompanionnordictracktreadmill.dircon.DirectConnectCommandBridge;
import org.cagnulein.qzcompanionnordictracktreadmill.dircon.ZwiftDirectConnectService;
import org.cagnulein.qzcompanionnordictracktreadmill.command.CommandDecoder;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.CalibrationDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.DeviceCalibration;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.DeviceRegistry;
import org.cagnulein.qzcompanionnordictracktreadmill.platform.IFitPlatform;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.TelemetryHub;
import org.cagnulein.qzcompanionnordictracktreadmill.BuildConfig;
import org.cagnulein.qzcompanionnordictracktreadmill.R;
import org.cagnulein.qzcompanionnordictracktreadmill.platform.crash.CrashHandler;

import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity {
    private static final long MAX_LOG_FILE_BYTES = 1024 * 1024;
    private static BufferedWriter logFileWriter = null;

    private DeviceAdapter deviceAdapter;
    private DeviceController activeController = null;
    private static SharedPreferences sharedPreferences;
    private IFitPlatform platform;

    private DeviceRegistry.DeviceId committedDeviceId = null;
    private DeviceRegistry.DeviceId pendingDeviceId   = null;
    private boolean deviceListExpanded = false;

    private View      devicePickerCard;
    private TextView  selectedDeviceLabel;
    private ImageView devicePickerChevron;
    private View      deviceListContainer;
    private CalibrationRunner calibrationRunner = null;

    private final android.os.Handler heartbeatHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable heartbeatTick = new Runnable() {
        @Override public void run() {
            updateRequirementsCard();
            heartbeatHandler.postDelayed(this, 5_000);
        }
    };

    private boolean checkPermissions(){
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            return false;
        }
    }

    public static void initLogFile() {
        try {
            File logFile = new File(Environment.getExternalStorageDirectory(), "qzcompanion.log");
            if (logFile.exists() && logFile.length() > MAX_LOG_FILE_BYTES) {
                File bak = new File(Environment.getExternalStorageDirectory(), "qzcompanion.log.bak");
                bak.delete();
                logFile.renameTo(bak);
            }
            logFileWriter = new BufferedWriter(new FileWriter(logFile, true));
        } catch (IOException e) {
            Log.e("QZ:Main", "Failed to open log file: " + e.getMessage());
        }
    }

    public static final String PREF_DEVICE_ID = "deviceId";
    public static final String PREF_DEBUG_LOG = "debugLog";
    public static final String PREF_DIRECT_CONNECT = ZwiftDirectConnectService.PREF_ENABLED;

    public static SharedPreferences prefs() { return sharedPreferences; }
    public static boolean isDebugLog() { return sharedPreferences != null && sharedPreferences.getBoolean(PREF_DEBUG_LOG, false); }

    public static void writeLog(String command) {
        String line = new Timestamp(new Date().getTime()) + " " + command;
        if (logFileWriter != null) {
            try {
                logFileWriter.write(line);
                logFileWriter.newLine();
                logFileWriter.flush();
            } catch (IOException ignored) {}
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        sharedPreferences = getSharedPreferences("QZ", MODE_PRIVATE);
        loadCalibration();
        initLogFile();
        platform = IFitPlatform.detect(this);
        initUi();
        startServices();
        if (platform.kind == IFitPlatform.Kind.IFIT2_GRPC) {
            configurePickerForiFit2();
            selectDevice(null);
        } else {
            restoreDeviceSelection();
        }
        applyDirectConnectPreference();
        updateStatusChip();
        rebindAccessibilityService();
    }

    private void loadCalibration() {
        File calFile = new File(Environment.getExternalStorageDirectory(), "qz-calibration.json");
        if (calFile.exists()) {
            try {
                DeviceCalibration.current = DeviceCalibration.loadFromJson(calFile);
                String savedId = sharedPreferences.getString(PREF_DEVICE_ID,
                        DeviceRegistry.DeviceId.other.name());
                if (DeviceRegistry.DeviceId.other.name().equals(savedId)) {
                    sharedPreferences.edit()
                            .putString(PREF_DEVICE_ID, DeviceRegistry.DeviceId.custom_calibrated.name())
                            .apply();
                }
                Log.i("QZ:Main", "Loaded calibration from qz-calibration.json");
            } catch (Exception e) {
                Log.w("QZ:Main", "qz-calibration.json load failed, falling back: " + e.getMessage());
                DeviceCalibration.current = DeviceCalibration.load(sharedPreferences);
            }
        } else {
            DeviceCalibration.current = DeviceCalibration.load(sharedPreferences);
        }
    }

    private void initUi() {
        if (getSupportActionBar() != null) {
            String buildId = BuildConfig.IS_CI_BUILD
                    ? "build " + BuildConfig.VERSION_CODE
                    : "dev-" + BuildConfig.GIT_HASH;
            getSupportActionBar().setSubtitle("v" + BuildConfig.VERSION_NAME + "  ·  " + buildId);
        }

        devicePickerCard    = findViewById(R.id.devicePickerCard);
        selectedDeviceLabel = findViewById(R.id.selectedDeviceLabel);
        devicePickerChevron = findViewById(R.id.devicePickerChevron);
        deviceListContainer = findViewById(R.id.deviceListContainer);

        RecyclerView deviceList = findViewById(R.id.deviceList);
        deviceList.setLayoutManager(new LinearLayoutManager(this));
        deviceAdapter = new DeviceAdapter(id -> pendingDeviceId = id);
        deviceList.setAdapter(deviceAdapter);

        devicePickerCard.setOnClickListener(v -> toggleDeviceList());
        findViewById(R.id.btnSaveDeviceSelection).setOnClickListener(v -> onSaveDeviceSelection());
        findViewById(R.id.btnCancelDeviceSelection).setOnClickListener(v -> onCancelDeviceSelection());
    }

    private void restoreDeviceSelection() {
        String savedId = sharedPreferences.getString(PREF_DEVICE_ID, DeviceRegistry.DeviceId.other.name());
        try {
            DeviceRegistry.DeviceId id = DeviceRegistry.DeviceId.valueOf(savedId);
            committedDeviceId = id;
            pendingDeviceId   = id;
            deviceAdapter.setSelectedId(id);
            selectDevice(DeviceRegistry.forId(id));
            updateSelectedDeviceLabel();
        } catch (IllegalArgumentException ignored) {}
    }

    private void configurePickerForiFit2() {
        selectedDeviceLabel.setText(platform.createDevice(this).displayName());
        devicePickerChevron.setVisibility(View.GONE);
        devicePickerCard.setOnClickListener(null);
        deviceListContainer.setVisibility(View.GONE);
    }

    private void syncDeviceSelectionFromPrefs() {
        if (platform != null && platform.kind == IFitPlatform.Kind.IFIT2_GRPC) return;
        String savedId = sharedPreferences.getString(PREF_DEVICE_ID, DeviceRegistry.DeviceId.other.name());
        if (committedDeviceId != null && committedDeviceId.name().equals(savedId)) return;
        restoreDeviceSelection();
    }

    private void startServices() {
        Intent inServer = new Intent(getApplicationContext(), QZCommandListenerService.class);
        getApplicationContext().startService(inServer);
        Intent in = new Intent(getApplicationContext(), QZTelemetryUnicastingService.class);
        getApplicationContext().startService(in);
    }

    private void applyDirectConnectPreference() {
        boolean enabled = sharedPreferences.getBoolean(PREF_DIRECT_CONNECT, false);
        Intent intent = new Intent(getApplicationContext(), ZwiftDirectConnectService.class);
        if (enabled) getApplicationContext().startService(intent);
        else getApplicationContext().stopService(intent);
        updateRequirementsCard();
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
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

    private void rebindAccessibilityService() {
        String svc = getPackageName() + "/.console.ifit1.GestureService";
        try {
            Settings.Secure.putString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, svc);
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Log.i("QZ:Main", "AccessibilityService re-bound on startup");
        } catch (SecurityException e) {
            Log.w("QZ:Main", "WRITE_SECURE_SETTINGS not granted — grant via: "
                    + "adb shell pm grant " + getPackageName()
                    + " android.permission.WRITE_SECURE_SETTINGS");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        menu.findItem(R.id.menu_verbose_logging).setChecked(
                sharedPreferences.getBoolean(PREF_DEBUG_LOG, false));
        menu.findItem(R.id.menu_direct_connect).setChecked(
                sharedPreferences.getBoolean(PREF_DIRECT_CONNECT, false));
        updateCalibrationMenu(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateCalibrationMenu(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    private void updateCalibrationMenu(Menu menu) {
        MenuItem calibrate = menu.findItem(R.id.menu_calibrate);
        if (calibrate != null) calibrate.setVisible(hasCalibrationPrerequisites());
        MenuItem debug = menu.findItem(R.id.menu_debug_incline_calibration);
        if (debug != null) debug.setVisible(
                sharedPreferences.getBoolean(PREF_DEBUG_LOG, false));
    }

    private boolean hasCalibrationPrerequisites() {
        return isAccessibilityServiceEnabled(this, GestureService.class)
                && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
                && checkCallingOrSelfPermission("android.permission.READ_LOGS")
                    == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_verbose_logging) {
            boolean next = !item.isChecked();
            item.setChecked(next);
            sharedPreferences.edit().putBoolean(PREF_DEBUG_LOG, next).apply();
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Settings Saved")
                    .setMessage("Restart the app to apply logging changes.")
                    .setPositiveButton("OK", (d, w) -> d.dismiss())
                    .show();
            return true;
        } else if (id == R.id.menu_direct_connect) {
            boolean next = !item.isChecked();
            item.setChecked(next);
            sharedPreferences.edit().putBoolean(PREF_DIRECT_CONNECT, next).apply();
            applyDirectConnectPreference();
            Toast.makeText(this,
                    next ? "Zwift Direct Connect enabled" : "Zwift Direct Connect disabled",
                    Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.menu_calibrate) {
            startActivity(new Intent(this, CalibrationActivity.class));
            return true;
        } else if (id == R.id.menu_debug_incline_calibration) {
            confirmDebugInclineCalibration();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDebugInclineCalibration() {
        new AlertDialog.Builder(this)
                .setTitle("Debug Incline Calibration")
                .setMessage("Start an active iFit manual workout first. The app will switch to iFit and move the incline slider.")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Start", (d, w) -> startDebugInclineCalibration())
                .show();
    }

    private void startDebugInclineCalibration() {
        if (calibrationRunner != null) {
            Toast.makeText(this, "Calibration already running", Toast.LENGTH_SHORT).show();
            return;
        }

        calibrationRunner = new CalibrationRunner(getResources().getDisplayMetrics());
        calibrationRunner.start(new CalibrationRunner.Listener() {
            @Override
            public void onState(CalibrationRunner.State state, String detail) {
                writeLog("QZ:Calibration " + state + " " + detail);
            }

            @Override
            public void onComplete(CalibrationResult result, DeviceCalibration calibration,
                                   CalibrationFit.FitResult inclineFit,
                                   CalibrationFit.FitResult resistanceFit) {
                runOnUiThread(() -> {
                    calibrationRunner = null;
                    applyCalibratedDeviceSelection();
                    String resistance = resistanceFit != null
                            ? "\nresistance origin=" + String.format(java.util.Locale.US, "%.2f", resistanceFit.origin)
                                    + "  scale=" + String.format(java.util.Locale.US, "%.4f", resistanceFit.scale)
                                    + "  R²=" + String.format(java.util.Locale.US, "%.4f", resistanceFit.r2)
                            : "\nresistance skipped";
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Incline Calibration Saved")
                            .setMessage("origin=" + String.format(java.util.Locale.US, "%.2f", inclineFit.origin)
                                    + "  scale=" + String.format(java.util.Locale.US, "%.4f", inclineFit.scale)
                                    + "  R²=" + String.format(java.util.Locale.US, "%.4f", inclineFit.r2)
                                    + resistance)
                            .setPositiveButton("OK", (d, w) -> d.dismiss())
                            .show();
                });
            }

            @Override
            public void onFailed(String message, Throwable error) {
                Log.e("QZ:Calibration", "Incline calibration failed", error);
                runOnUiThread(() -> {
                    calibrationRunner = null;
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Incline Calibration Failed")
                            .setMessage(message != null ? message : "Unknown error")
                            .setPositiveButton("OK", (d, w) -> d.dismiss())
                            .show();
                });
            }
        });

        Toast.makeText(this, "Incline calibration running", Toast.LENGTH_SHORT).show();
        launchIfit();
    }

    private void applyCalibratedDeviceSelection() {
        committedDeviceId = DeviceRegistry.DeviceId.custom_calibrated;
        pendingDeviceId = committedDeviceId;
        deviceAdapter.setSelectedId(committedDeviceId);
        sharedPreferences.edit()
                .putString(PREF_DEVICE_ID, committedDeviceId.name())
                .apply();
        selectDevice(DeviceRegistry.forId(committedDeviceId));
        updateSelectedDeviceLabel();
        collapseDeviceList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncDeviceSelectionFromPrefs();
        updateStatusChip();
        updateRequirementsCard();
        invalidateOptionsMenu();
        heartbeatHandler.postDelayed(heartbeatTick, 5_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        heartbeatHandler.removeCallbacks(heartbeatTick);
    }

    private void updateRequirementsCard() {
        LinearLayout list = findViewById(R.id.requirementsList);
        if (list == null || activeController == null) return;
        list.removeAllViews();

        boolean ok = isAccessibilityServiceEnabled(this, GestureService.class);
        addRequirementRow(list, ok,
                "Accessibility service",
                ok ? "Enabled — app can adjust speed and incline"
                   : "Tap to enable — required to control the bike",
                ok ? null : v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        long lastHb = QZCommandListenerService.lastQzHeartbeatMs;
        boolean heartbeatActive = lastHb > 0
                && (System.currentTimeMillis() - lastHb) < QZCommandListenerService.QZ_HEARTBEAT_TIMEOUT_MS;
        java.net.InetAddress qzAddr = QZCommandListenerService.qzAddress;
        addRequirementRow(list, heartbeatActive,
                "QZ",
                heartbeatActive
                        ? "QZ heartbeat received from " + qzAddr.getHostAddress() + " on port " + QZCommandListenerService.LISTEN_PORT + ". Device telemetry unicast active."
                        : "Waiting for QZ heartbeat commands on port " + QZCommandListenerService.LISTEN_PORT + ". Device telemetry unicast paused.",
                null);

        boolean directEnabled = sharedPreferences.getBoolean(PREF_DIRECT_CONNECT, false);
        String directDetail;
        boolean directOk = !directEnabled || ZwiftDirectConnectService.isAdvertising()
                || ZwiftDirectConnectService.connectedClient() != null;
        if (!directEnabled) {
            directDetail = "Disabled — enable from the overflow menu to advertise on port 36866";
        } else if (ZwiftDirectConnectService.connectedClient() != null) {
            directDetail = "Connected to Zwift client "
                    + ZwiftDirectConnectService.connectedClient() + " on port 36866";
        } else if (ZwiftDirectConnectService.isAdvertising()) {
            directDetail = "Advertising _wahoo-fitness-tnp._tcp on port 36866";
        } else if (ZwiftDirectConnectService.lastError() != null) {
            directDetail = ZwiftDirectConnectService.lastError();
        } else {
            directDetail = "Starting advertisement on port 36866";
        }
        addRequirementRow(list, directOk,
                "Zwift Direct Connect",
                directDetail,
                null);

        boolean canAdvertise = false;
        try {
            BluetoothManager btMgr = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter btAdapter = btMgr != null ? btMgr.getAdapter() : null;
            canAdvertise = btAdapter != null && btAdapter.isMultipleAdvertisementSupported();
        } catch (SecurityException ignored) {
            // BLUETOOTH permission not granted; treat as not capable (informational row only)
        }
        addRequirementRow(list, !canAdvertise,
                "Bluetooth",
                canAdvertise
                        ? "BLE advertising supported — install QZ or connect Zwift directly to your device"
                        : "BLE advertising not supported — QZCompanion is the right approach",
                null);
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

    private void addRequirementRow(LinearLayout container, boolean ok,
                                   String name, String detail,
                                   View.OnClickListener action) {
        Context ctx = container.getContext();
        int px16 = dpToPx(ctx, 16);
        int px10 = dpToPx(ctx, 10);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(px16, px10, px16, px10);
        if (action != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(action);
            TypedValue ripple = new TypedValue();
            ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            row.setBackgroundResource(ripple.resourceId);
        }

        TextView dot = new TextView(ctx);
        dot.setText("● ");
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dot.setTextColor(ok ? 0xFF4CAF50 : 0xFFFF5722);
        dot.setTypeface(null, Typeface.BOLD);

        TextView text = new TextView(ctx);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(name, new android.text.style.StyleSpan(Typeface.BOLD),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ");
        sb.append(detail);
        if (action != null) {
            sb.append("  ›");
        }
        text.setText(sb);

        row.addView(dot);
        row.addView(text);
        container.addView(row);
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()));
    }

    private void updateStatusChip() {
        TextView chip = findViewById(R.id.statusChip);
        if (chip == null) return;
        String deviceName = activeController != null
                ? activeController.device().displayName()
                : "No device selected";
        String ip = getLocalIpAddress();
        chip.setText("UDP " + QZCommandListenerService.LISTEN_PORT + "  ·  " + deviceName + "  ·  " + ip);
        updateSelectedDeviceLabel();
    }

    private void updateSelectedDeviceLabel() {
        if (selectedDeviceLabel == null) return;
        selectedDeviceLabel.setText(activeController != null
                ? activeController.device().displayName()
                : "No device selected");
    }

    private void toggleDeviceList() {
        if (deviceListExpanded) collapseDeviceList();
        else expandDeviceList();
    }

    private void expandDeviceList() {
        pendingDeviceId = committedDeviceId;
        if (committedDeviceId != null) deviceAdapter.setSelectedId(committedDeviceId);
        deviceListContainer.setVisibility(View.VISIBLE);
        devicePickerChevron.animate().rotation(180f).setDuration(200).start();
        deviceListExpanded = true;
    }

    private void collapseDeviceList() {
        if (committedDeviceId != null) deviceAdapter.setSelectedId(committedDeviceId);
        pendingDeviceId = committedDeviceId;
        deviceListContainer.setVisibility(View.GONE);
        devicePickerChevron.animate().rotation(0f).setDuration(200).start();
        deviceListExpanded = false;
    }

    private void onSaveDeviceSelection() {
        if (pendingDeviceId == null) { collapseDeviceList(); return; }
        committedDeviceId = pendingDeviceId;
        selectDevice(DeviceRegistry.forId(pendingDeviceId));
        sharedPreferences.edit()
                .putString(PREF_DEVICE_ID, pendingDeviceId.name())
                .apply();
        updateSelectedDeviceLabel();
        collapseDeviceList();
    }

    private void onCancelDeviceSelection() {
        collapseDeviceList();
    }

    private String getLocalIpAddress() {
        try {
            for (NetworkInterface ni :
                    java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress addr :
                        java.util.Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address)
                        return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "no IP";
    }

    /** Selects the active device and wires it to command and telemetry streams.
     *  On iFit2, {@code device} is ignored — the platform auto-selects the machine.
     *  On iFit1, {@code device} is the user's picker selection. */
    private void selectDevice(Device device) {
        Device effectiveDevice = (platform != null && platform.kind == IFitPlatform.Kind.IFIT2_GRPC)
                ? platform.createDevice(this)
                : device;
        if (effectiveDevice == null) return;
        Log.i("QZ:Main", "device selected: " + effectiveDevice.displayName());
        TelemetryHub.configure(platform.createTelemetryReader(this));
        effectiveDevice.logger = (level, tag, msg) -> Log.println(level, tag, msg);
        if (activeController != null) activeController.shutdown();
        boolean isGrpc = platform != null && platform.kind == IFitPlatform.Kind.IFIT2_GRPC;
        CommandDecoder preDecoder = isGrpc ? null : new CalibrationDevice()::decodeCommands;
        activeController = new DeviceController(effectiveDevice, preDecoder);
        QZCommandListenerService.setSubscriber(activeController);
        DirectConnectCommandBridge.setSink(activeController::enqueueCommand);
        updateStatusChip();
        updateRequirementsCard();
    }

}

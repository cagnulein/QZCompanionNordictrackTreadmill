# Unattended Calibration Test Plan

**Executor:** Claude Code agent  
**Target:** S22i at `192.168.1.213:5555`  
**Package:** `org.cagnulein.qzcompanionnordictracktreadmill` (alias `$PKG`)  
**Goal:** Build → Install → Configure → Run `discover-device.py` → Verify sliders respond to UDP commands  
**Human intervention required:** None (all steps are automated including iFit navigation)

---

## How to Invoke

From a Claude Code session in this repo:

```
Run the unattended calibration test defined in tools/test-calibration-unattended.md
```

The agent reads this document and executes each phase in order, aborting on any `FAIL` checkpoint.

---

## Environment Variables (set once at start of run)

```bash
DEVICE=192.168.1.213:5555
PKG=org.cagnulein.qzcompanionnordictracktreadmill
A11Y_SVC="$PKG/org.cagnulein.qzcompanionnordictracktreadmill.service.MyAccessibilityService"
APK=app/build/outputs/apk/debug/app-debug.apk
```

---

## Phase 0 — Pre-flight

### 0.1 ADB connectivity
```bash
adb connect $DEVICE
adb -s $DEVICE shell echo "ok"
```
**PASS:** output is `ok`  
**FAIL:** abort — device not reachable; check Wi-Fi and ADB-over-TCP setting on tablet.

### 0.2 Build environment
```bash
java -version 2>&1 | grep "21\."
```
**PASS:** Java 21 confirmed  
**FAIL:** `gradle.properties` points to wrong JVM; edit `org.gradle.java.home`.

---

## Phase 1 — Build

```bash
./gradlew assembleDebug 2>&1 | tail -5
```
**PASS:** last line contains `BUILD SUCCESSFUL`  
**FAIL:** read full output, identify compilation error, fix, re-run before proceeding.

---

## Phase 2 — Install

```bash
adb -s $DEVICE install -r $APK
```
**PASS:** output contains `Success`  
**FAIL:** if `INSTALL_FAILED_VERSION_DOWNGRADE`, retry with `install -r -d`. If other error, examine message.

---

## Phase 3 — Permissions

Run all; verify each succeeds silently (non-zero exit = failure).

```bash
# Lets MonoStdoutMetricReader stream iFit logcat from within the app
adb -s $DEVICE shell pm grant $PKG android.permission.READ_LOGS

# Lets MainActivity re-bind its own AccessibilityService on every startup
adb -s $DEVICE shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS

# Lets MainActivity read/write /sdcard/qz-calibration.json on Android 11+
adb -s $DEVICE shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow
```

**If `appops set` returns an error:** run instead:
```bash
adb -s $DEVICE shell pm grant $PKG android.permission.MANAGE_EXTERNAL_STORAGE
```
If that also fails, skip — `/sdcard/` is still writable via `adb push` and the app can read it if granted at the OS level.

---

## Phase 4 — Enable Accessibility Service

QZCompanion re-binds its own AccessibilityService on every startup via `WRITE_SECURE_SETTINGS`
(granted in Phase 3). No manual ADB toggle is needed here.

**Note:** if `WRITE_SECURE_SETTINGS` was not granted in Phase 3, the app logs:
`WRITE_SECURE_SETTINGS not granted — grant via: adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`
In that case, re-run Phase 3 before continuing.

---

## Phase 5 — Activate iFit Manual Workout

This phase navigates iFit to an active manual ride so that `Changed Grade to:` events flow into logcat. It uses `uiautomator dump` to inspect the live screen state and `input tap` to interact — no hardcoded coordinates.

### 5.1 Launch iFit
```bash
adb -s $DEVICE shell monkey -p com.ifit.standalone -c android.intent.category.LAUNCHER 1
sleep 5
```

### 5.2 Discover current screen and navigate to Manual Ride

Repeat the following loop up to 8 times (≈ 40 seconds total), advancing one step per iteration:

```bash
adb -s $DEVICE shell uiautomator dump /sdcard/ui.xml
adb -s $DEVICE pull /sdcard/ui.xml /tmp/ifit-ui.xml
```

Parse `/tmp/ifit-ui.xml` to find the first matching rule below and execute the corresponding tap:

| Priority | Match text (case-insensitive, in any `text=` or `content-desc=` attribute) | Action |
|---|---|---|
| 0 | `"manual start"` | Tap it — starts a manual workout directly from the dashboard |
| 1 | `"end warmup"` | Tap it — skip warmup and enter active workout immediately |
| 2 | `"let's go"` or `"lets go"` or `"get started"` | Tap it — dismiss welcome screen |
| 3 | `"workouts"` or `"explore"` or `"start"` (in nav/tab area) | Tap it — navigate to workout library |
| 4 | `"manual"` or `"manual ride"` or `"manual workout"` | Tap it — select manual workout |
| 5 | `"go"` or `"start ride"` (large button, not nav) | Tap it — start the workout |
| 6 | `"resume"` | Tap it — resume a paused workout |

**Important:** Do not match `"begin"` as a substring — `"Workout begins in"` is a countdown label on the warmup screen, not a button. Use `"end warmup"` (priority 1) to skip the warmup countdown instead.

To tap an element found at bounds `[left,top][right,bottom]`:
```bash
# mid_x = (left + right) / 2,  mid_y = (top + bottom) / 2
adb -s $DEVICE shell input tap $mid_x $mid_y
sleep 5
```

**After each tap:** check logcat for metric events — if found, skip remaining iterations.

```bash
adb -s $DEVICE logcat -d -s mono-stdout | grep "Changed" | tail -5
```

### 5.3 Verify metric events are flowing
```bash
# Clear stale events, then wait 8 seconds for fresh ones
adb -s $DEVICE logcat -c
sleep 8
adb -s $DEVICE logcat -d -s mono-stdout | grep "Changed"
```
**PASS:** at least one `Changed Grade to:` or `Changed Resistance to:` line  
**FAIL:** navigate back to phase 5.2 and try again (up to 3 retries). If still failing after 3 retries, abort with message: "iFit workout not active — open iFit, start Manual Ride, press GO, then re-run."

---

## Phase 6 — Launch QZCompanion

```bash
adb -s $DEVICE shell settings put system screen_off_timeout 600000
adb -s $DEVICE shell am start -n $PKG/.MainActivity
sleep 3
```

Grant the runtime storage permission dialog if present:
```bash
adb -s $DEVICE shell uiautomator dump /sdcard/ui.xml
adb -s $DEVICE pull /sdcard/ui.xml /tmp/qz-ui.xml
# If "Allow" button is visible in the XML, tap it
```

QZCompanion automatically re-binds its AccessibilityService on startup via `WRITE_SECURE_SETTINGS`.
Confirm it fired:
```bash
adb -s $DEVICE logcat -d | grep "QZ:Main" | tail -3
```
**PASS:** log contains `AccessibilityService re-bound on startup`  
**FAIL:** log contains `WRITE_SECURE_SETTINGS not granted` — re-run Phase 3.

**Verify the service is live** by sending a test CALSWIPE and checking logcat:

```bash
python3 -c "import socket; s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); \
  s.sendto(b'CALSWIPE:57:250:450', ('192.168.1.213', 8003)); s.close()"
sleep 2
adb -s $DEVICE logcat -d | grep CALSWIPE | tail -3
```

**PASS:** log line reads `CALSWIPE x=57 250->450` (no "not connected" warning)  
**FAIL:** log reads `CALSWIPE: accessibility service not connected` — reboot the device and re-run from Phase 3.

---

## Phase 7 — Run `discover-device.py`

```bash
python3 tools/discover-device.py --device $DEVICE --a11y --push 2>&1 | tee /tmp/calibration-run.log
```

This step takes approximately **5–12 minutes** (coarse + fine sweeps for both sliders; when coarse R²≥0.99 fine sweep is skipped — S22i typically completes in ~5 min).

The script automatically handles warmup dismissal (END WARMUP tap) before the sweep. iFit logs resistance changes as `Changed CurrentGear to:` on the S22i — the script matches this automatically.

**PASS criteria** — all must be true:
1. Output contains `✓ Written: qz-calibration.json`
2. Output contains `✓ Pushed to /sdcard/qz-calibration.json`
3. Incline R² ≥ 0.97 and no `⚠ poor fit` warning for incline
4. Resistance R² ≥ 0.97 (S22i always produces a linear result when past warmup)

**WARN (non-blocking):**
- Resistance `⚠ Indicator not located` — the homing scan didn't find the indicator during the 6-probe scan; the sweep still runs from y=200 downward, which works if the indicator is already near max.  If resistance R² is poor, force-restart iFit, start a new manual workout, and re-run.
- Resistance sweep skipped (< 3 readings) — only if workout was in warmup when script ran; re-run after confirming END WARMUP was tapped

**FAIL (abort):**
- Script exits non-zero
- Incline has fewer than 3 fine points
- `ERROR:` line in output

---

## Phase 8 — Restart QZCompanion and Verify Calibration Loaded

```bash
adb -s $DEVICE shell am force-stop $PKG
sleep 2
adb -s $DEVICE logcat -c
adb -s $DEVICE shell am start -n $PKG/.MainActivity
sleep 5
```

Check logcat for calibration load confirmation:
```bash
adb -s $DEVICE logcat -d | grep "QZ:Main"
```
**PASS:** contains `Loaded calibration from qz-calibration.json`  
**FAIL:** contains `qz-calibration.json load failed` — print the error line; likely a JSON parse error.

Check UI shows correct device:
```bash
adb -s $DEVICE shell uiautomator dump /sdcard/ui.xml
adb -s $DEVICE pull /sdcard/ui.xml /tmp/qz-post.xml
# Verify "Custom (Calibrated)" appears in XML
```
**PASS:** `Custom (Calibrated)` found in XML  
**FAIL:** wrong device selected — check SharedPreferences deviceId key; may need to select manually.

---

## Phase 9 — Smoke Test: Incline Command

Send an incline command over UDP. Packet format: `"<grade>;-100"` (2 fields, `;` delimited).

```bash
adb -s $DEVICE logcat -c
# Send incline 3% command to QZCompanion on the tablet
# nc (netcat) must be available on the host; -u = UDP, -w1 = 1s timeout
echo -n "3.0;-100" | nc -u -w1 192.168.1.213 8003
sleep 6
```

Check logcat for dispatch and gesture:
```bash
adb -s $DEVICE logcat -d | grep -E "QZ:Dispatch|GestureResult|requestIncline"
```
**PASS:** log contains `requestIncline(bike): 3.0` and either `applyIncline` or a `GestureResultCallback` completion line  
**WARN:** log contains `de-dup: skipping incline` — slider already at 3%; send a different value (e.g., 6%) and recheck.  
**FAIL:** no dispatch log at all — QZCompanion may not be receiving UDP; check firewall/network.

---

## Phase 10 — Smoke Test: Resistance Command

Send a resistance command. Packet format: `"<level>"` (1 field).

```bash
adb -s $DEVICE logcat -c
echo -n "5" | nc -u -w1 192.168.1.213 8003
sleep 6
adb -s $DEVICE logcat -d | grep -E "QZ:Dispatch|requestResistance|applyResistance"
```
**PASS:** log contains `requestResistance(bike): 5.0` and `applyResistance(bike): 5.0`  
**SKIP (not FAIL):** if resistance sweep was skipped in Phase 7, resistance slider is null — skip this phase.

---

## Phase 11 — Cleanup and Report

Restore screen timeout:
```bash
adb -s $DEVICE shell settings put system screen_off_timeout 60000
```

Print summary:
```bash
echo "=== Calibration Values ==="
cat qz-calibration.json

echo ""
echo "=== Sweep R² ==="
grep "Fit:" /tmp/calibration-run.log

echo ""
echo "=== Verdict ==="
# PASS if Phase 7 R² ≥ 0.97, Phase 9 gesture confirmed, Phase 10 gesture confirmed (or skipped)
```

---

## Abort / Error Handling Summary

| Phase | Abort condition | Recovery hint |
|---|---|---|
| 0 | ADB not reachable | Check Wi-Fi, re-enable ADB over TCP |
| 1 | Build fails | Fix compilation error, re-run |
| 3 | `WRITE_SECURE_SETTINGS` grant fails | Re-run `pm grant` command manually |
| 5 | No iFit metric events after 3 retries | User must start Manual Ride |
| 6 | `CALSWIPE: accessibility service not connected` | Reboot device, re-run from Phase 3 |
| 7 | Incline R² < 0.97 | Re-run sweep; check slider responsiveness |
| 8 | JSON load failed | Check `/sdcard/qz-calibration.json` content |
| 9 | No UDP dispatch | Check same-subnet routing; try from tablet itself |

---

## Estimated Total Runtime

| Phase | Duration |
|---|---|
| Build | ~3 min |
| Install + permissions | < 1 min |
| iFit navigation | 1–2 min |
| discover-device.py sweep | 8–12 min |
| Smoke tests | 2 min |
| **Total** | **~15–20 min** |

# SKILL: Build, Install, and Calibrate QZCompanion on S22i

Target device: NordicTrack S22i at `192.168.1.213:5555`  
Package: `org.cagnulein.qzcompanionnordictracktreadmill`

---

## Prerequisites

```bash
adb connect 192.168.1.213:5555
adb -s 192.168.1.213:5555 devices   # must show "device", not "unauthorized"
```

If the device shows `unauthorized`: on the tablet tap "Allow" on the USB debugging prompt, then retry.

---

## 1. Build the Debug APK

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

To also run unit tests before building:
```bash
./gradlew testDebugUnitTest assembleDebug
```

---

## 2. Install on the Device

```bash
adb -s 192.168.1.213:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` replaces an existing installation without wiping data. Use `-r -d` to downgrade if needed.

---

## 3. Grant Required Permissions

These must be done after every fresh install (they do not survive uninstall).

```bash
PKG=org.cagnulein.qzcompanionnordictracktreadmill
DEVICE=192.168.1.213:5555

# READ_LOGS — lets the app stream iFit's logcat (needed for metric reading)
adb -s $DEVICE shell pm grant $PKG android.permission.READ_LOGS

# MANAGE_EXTERNAL_STORAGE — lets the app read/write /sdcard/qz-calibration.json
# Try the adb shortcut first; if it fails, use the manual path below
adb -s $DEVICE shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow
```

**If `appops set MANAGE_EXTERNAL_STORAGE` fails** (Android version dependent):  
On the tablet: Settings → Apps → QZCompanion → Permissions → Files and media → Allow management of all files.

---

## 4. Enable the Accessibility Service

The swipe gesture injector will not work until this is on. It is not grantable via ADB.

```bash
adb -s 192.168.1.213:5555 shell am start \
  -a android.settings.ACCESSIBILITY_SETTINGS
```

On the tablet: tap **QZ Companion** in the list → toggle **On** → confirm the system warning.

To verify from the command line after enabling:
```bash
adb -s 192.168.1.213:5555 shell settings get secure enabled_accessibility_services | \
  grep -i qzcompanion && echo "✓ Accessibility enabled" || echo "✗ Not enabled"
```

---

## 5. Launch and Configure the App

```bash
adb -s 192.168.1.213:5555 shell am start -n \
  org.cagnulein.qzcompanionnordictracktreadmill/.MainActivity
```

In the app:

1. **Grant storage permission** — tap Allow on the runtime dialog (WRITE_EXTERNAL_STORAGE).
2. **Select device** — if no `qz-calibration.json` is present yet, tap **S22i Bike (NTEX02117.2)** as a starting point. After running `discover-device.py --push` and restarting, the app will auto-switch to **Custom (Calibrated)**.
3. **Enable verbose logging** — tap the overflow menu (⋮) → **Verbose Logging** → OK. This is required for `MetricReaderUnicastingService` to log the metric lines that `discover-device.py` reads.

---

## 6. Set Up for `discover-device.py`

### On the tablet

1. Open **iFit**.
2. Tap **Workouts → Manual Workout → Manual Ride**.
3. Tap **GO** — the workout timer must be actively counting up.
4. Leave the workout screen visible. Do not lock the screen or navigate away.

Keep the screen on during the sweep:
```bash
adb -s 192.168.1.213:5555 shell settings put system screen_off_timeout 600000
```
(10 minutes — reset to 60000 when done.)

### Verify iFit events are flowing

```bash
adb -s 192.168.1.213:5555 logcat -s mono-stdout | grep "Changed"
```

You should see lines like:
```
V/mono-stdout(1234): [42] [Trace:FitPro] Changed Grade to: 3.5
V/mono-stdout(1234): [42] [Trace:FitPro] Changed Resistance to: 8
```

If nothing appears after 10 seconds while the workout is active, iFit is not logging to `mono-stdout` — check that the workout timer is running and the screen is not dimmed.

---

## 7. Run `discover-device.py`

```bash
# Dry run — inspect R² before pushing
python3 tools/discover-device.py --device 192.168.1.213:5555

# Apply — push JSON to /sdcard/ and print restart commands
python3 tools/discover-device.py --device 192.168.1.213:5555 --push
```

After `--push`, restart the app to apply the new calibration:

```bash
PKG=org.cagnulein.qzcompanionnordictracktreadmill
DEVICE=192.168.1.213:5555
adb -s $DEVICE shell am force-stop $PKG
adb -s $DEVICE shell am start -n $PKG/.MainActivity
```

The status chip should now read **Custom (Calibrated)**.

See `tools/discover-device-runbook.md` for full sweep details, output format, and troubleshooting.

---

## Full One-Shot Script (experienced users)

```bash
DEVICE=192.168.1.213:5555
PKG=org.cagnulein.qzcompanionnordictracktreadmill

./gradlew assembleDebug && \
adb -s $DEVICE install -r app/build/outputs/apk/debug/app-debug.apk && \
adb -s $DEVICE shell pm grant $PKG android.permission.READ_LOGS && \
adb -s $DEVICE shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow && \
adb -s $DEVICE shell settings put system screen_off_timeout 600000 && \
adb -s $DEVICE shell am start -n $PKG/.MainActivity
```

Then manually: enable Accessibility → open iFit → start Manual Ride → GO → run `discover-device.py`.

---

## Resetting Between Test Cycles

```bash
DEVICE=192.168.1.213:5555
PKG=org.cagnulein.qzcompanionnordictracktreadmill

# Clear app data (resets device selection and stored calibration)
adb -s $DEVICE shell pm clear $PKG

# Remove calibration JSON
adb -s $DEVICE shell rm -f /sdcard/qz-calibration.json

# Remove log files
adb -s $DEVICE shell rm -f /sdcard/qzcompanion.log /sdcard/qzcompanion.log.bak
```

After clearing app data, re-run steps 3–5 (permissions are wiped with `pm clear`).

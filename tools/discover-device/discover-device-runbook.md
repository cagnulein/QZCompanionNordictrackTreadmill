# Runbook: External Calibration Fallback with `discover-device.py`

QZCompanion now includes an in-app guided calibration flow. Use **QZ Companion → Calibrate** for normal device setup: start an active iFit manual workout, launch calibration from the app, and let it save/apply `custom_calibrated` without ADB or restarting QZCompanion.

Keep `discover-device.py` as an external fallback for contributors, recovery, unattended validation, and comparing results while the in-app flow is validated across more hardware. It still generates the same slider calibration file (`qz-calibration.json`) that QZCompanion loads and applies.

---

## Prerequisites

| Requirement | Notes |
|---|---|
| `adb` in `PATH` | Android SDK Platform Tools; verify with `adb version` |
| Python 3.6+ | stdlib only — no pip installs |
| iFit tablet powered on, screen unlocked | |
| ADB over TCP enabled on tablet | See "Enable ADB over TCP" below |
| iFit app open in an **active manual workout** | Slider events are only emitted during an active session |
| QZCompanion installed and running | Required for `--a11y` mode (Xamarin/API 25 devices) |

For in-app calibration, the tablet only needs QZCompanion installed with Accessibility enabled plus `READ_LOGS`, `WRITE_SECURE_SETTINGS`, and storage permissions granted through the existing setup flow.

### Enable ADB over TCP (one-time per tablet)

1. **Settings → About tablet → Software information** — tap "Build number" 7 times to unlock Developer Options.
2. **Settings → Developer options** → enable "USB debugging" and "Wireless debugging".
3. Note the tablet's IP address: **Settings → About tablet → Status information → IP address**.
4. From your laptop:
   ```bash
   adb connect <tablet-ip>:5555
   adb devices   # confirm device appears as "device"
   ```

---

## Which mode do I need?

| Device | Android version | Swipe mode |
|---|---|---|
| NordicTrack S22i, S15i, and other Xamarin iFit bikes | API 25 (Android 7.1.2) | **`--a11y`** — required |
| Newer iFit devices (non-Xamarin) | API 26+ | default (no flag) |

`adb shell input swipe` does not reach Xamarin's SurfaceView on API 25. The `--a11y` flag
routes all calibration swipes through QZCompanion's AccessibilityService via UDP, which
does reach Xamarin.

---

## Step-by-Step (Standard, API 26+)

### 1. Start an active manual workout in iFit

1. Open iFit on the tablet.
2. Tap **Workouts → Manual Workout → Manual Ride** (or Manual Run for treadmills).
3. Tap **GO** — the workout timer must be running.
4. Leave the workout screen visible; do not navigate away or let the screen dim.

> The script aborts if no metric events arrive within 15 seconds. This is the most common
> failure mode — always verify the timer is running before starting the script.

### 2. Run the script (dry run first)

```bash
python3 tools/discover-device.py --device <tablet-ip>:5555
```

The script:
1. Detects screen resolution and derives incline/resistance track X coordinates.
2. Waits up to 15 s for iFit metric events to confirm the workout is active.
3. Sweeps the **incline** slider (left track) with a coarse pass (50 px steps).
4. If the coarse fit is already excellent (R² ≥ 0.99), uses it directly — common on
   discrete-button sliders like the S22i where coarse steps land on exact button positions.
   Otherwise runs a fine pass (25 px steps) for continuous sliders.
5. Repeats for the **resistance** slider (right track).
6. Fits linear formulas and prints R² values.
7. Writes `qz-calibration.json` in the current directory.

Inspect the output and verify R² before pushing.

### 3. Push and restart

```bash
python3 tools/discover-device.py --device <tablet-ip>:5555 --push
```

Force-stop and restart QZCompanion to apply:

```bash
PKG=org.cagnulein.qzcompanionnordictracktreadmill
adb -s <tablet-ip>:5555 shell am force-stop $PKG
adb -s <tablet-ip>:5555 shell am start -n $PKG/.MainActivity
```

---

## Step-by-Step (Xamarin iFit, `--a11y` mode)

This procedure was validated on a **NordicTrack S22i (NTEX02117.2)** at API 25.

The script handles all QZCompanion setup automatically — launching it, binding the
Accessibility Service, verifying the CALSWIPE relay, and restarting it after push.

### 1. Install QZCompanion and grant one-time permissions (one-time per install)

```bash
DEVICE=<tablet-ip>:5555
PKG=org.cagnulein.qzcompanionnordictracktreadmill

adb -s $DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $DEVICE shell pm grant $PKG android.permission.READ_LOGS
adb -s $DEVICE shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
```

Both permissions must be re-granted after every fresh install (they do not survive uninstall).

- `READ_LOGS` — lets QZCompanion stream iFit's logcat.
- `WRITE_SECURE_SETTINGS` — lets QZCompanion re-bind its own AccessibilityService on every
  startup, eliminating the need to manually toggle Settings → Accessibility after reboots
  or force-stops.

### 2. Start an active manual workout in iFit

1. Open iFit on the tablet.
2. Tap **Workouts → Manual Workout → Manual Ride**.
3. Tap **GO** — the workout timer must be running.
4. Leave the workout screen visible.

### 3. Run the script

```bash
python3 tools/discover-device.py --device $DEVICE --a11y --push
```

The script's pre-flight block does the rest automatically:
- Launches QZCompanion (which re-binds its AccessibilityService on startup via `WRITE_SECURE_SETTINGS`)
- Dismisses the one-time "Use QZ Companion?" accessibility dialog if present
- Sends a test CALSWIPE to verify the relay is live; retries once if needed
- Restores iFit to the foreground before the sweep starts

After the sweep, `--push` writes the calibration file and restarts QZCompanion
automatically, confirming `Loaded calibration from qz-calibration.json` from logcat.

---

## Interpreting the Output

### Discrete-button slider (S22i and similar)

```
── Incline sweep (trackX=57) ──
    coarse y= 200 → (no change)
    coarse y= 250 → grade=17.5
    coarse y= 300 → grade=15.0
    ...
    coarse y= 800 → grade=-10.0
    coarse y= 850 → (no change)
  Active incline range: Y 200–850
  Coarse fit excellent (R²=1.0000) — skipping fine sweep.
  Fit: origin=600.0  scale=20.0000  R²=1.0000
```

When the coarse pass is already perfect (R²≥0.99), the fine sweep is skipped. This is
normal for discrete-button sliders where the 50 px coarse steps land exactly on button
centres. The sweep completes in ~4 minutes instead of ~10.

### Continuous slider (API 26+ devices)

```
── Incline sweep (trackX=75) ──
    coarse y= 200 → (no change)
    coarse y= 250 → grade=3.0
    ...
    fine   y= 230 → grade=2.50
    ...
  Fit: origin=622.1  scale=18.5700  R²=0.9981
```

Both coarse and fine passes run. The fine pass (25 px steps) improves precision for
sliders that respond to sub-button-width positioning.

### Output fields

| Field | Meaning |
|---|---|
| `origin` | Y pixel where the slider sits at grade=0 (or resistance=minLevel) |
| `scale` | Pixels per grade/resistance unit |
| `R²` | Goodness of fit — ≥ 0.97 is acceptable; script warns `⚠ poor fit` below that |
| `minLevel` | Lowest resistance level observed (resistance only; typically 1) |

If resistance yields fewer than 3 readings it is skipped. The output JSON will contain
only `"incline"`; QZCompanion will control incline but not resistance.

---

## Optional Arguments

| Argument | Default | Description |
|---|---|---|
| `--device` | *(required)* | ADB device address, e.g. `192.168.1.213:5555` |
| `--a11y` | off | Route swipes via QZCompanion's AccessibilityService (required for API 25/Xamarin iFit) |
| `--push` | off | Push output JSON to `/sdcard/qz-calibration.json` on the device |
| `--out FILE` | `qz-calibration.json` | Local output file path |

---

## Verifying the Calibration

After restarting QZCompanion:

1. Confirm the status chip shows **Custom (Calibrated)**.
2. Send an incline command from Zwift/QZ; confirm the slider moves to the expected position.
3. If resistance was calibrated, send a resistance command and confirm it responds.

Quick UDP smoke test (Python, no netcat required):

```bash
# Incline 5%
python3 -c "
import socket
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.sendto(b'5.0;-100', ('<tablet-ip>', 8003))
s.close()
"
sleep 5
adb -s <tablet-ip>:5555 logcat -d | grep -E "requestIncline|applyIncline" | tail -3
# Expect: requestIncline(bike): 5.0 ... applyIncline(bike): 5.0
```

---

## Troubleshooting

### "ERROR: No iFit events received in 15s"

The workout must be actively running (timer ticking, not paused).

```bash
adb -s <ip>:5555 logcat -s mono-stdout | grep "Changed"
```

You should see `Changed Grade to:` lines within a few seconds of tapping the incline
slider. If nothing appears, go back to iFit and confirm the workout is in progress.

For `--a11y` mode specifically: also confirm QZCompanion is running and the CALSWIPE
smoke test passes (see step 4 of the `--a11y` procedure above).

### "CALSWIPE: accessibility service not connected"

QZCompanion re-binds its AccessibilityService on every startup via `WRITE_SECURE_SETTINGS`.
This error means either the permission was not granted or the app is not running.

Check logcat for the cause:
```bash
adb -s <ip>:5555 logcat -d | grep "QZ:Main" | tail -3
```

- `AccessibilityService re-bound on startup` — service bound correctly; CALSWIPE failure is
  transient. Force-stop and relaunch QZCompanion, then retry.
- `WRITE_SECURE_SETTINGS not granted` — re-grant the permission:
  ```bash
  adb -s <ip>:5555 shell pm grant org.cagnulein.qzcompanionnordictracktreadmill \
      android.permission.WRITE_SECURE_SETTINGS
  ```
  Then force-stop and relaunch QZCompanion.
- Neither line present — QZCompanion may not have started yet; wait a few seconds and retry.

If the error persists after relaunching, reboot the tablet — this always clears the stuck state.

### "Only N coarse readings — need ≥ 3" (incline)

The incline slider is not responding to swipes. Possible causes:

- **Wrong mode:** On API 25/Xamarin iFit, `adb shell input swipe` is silently ignored.
  Use `--a11y`.
- **iFit not in foreground:** The active workout screen must be visible. Do not switch
  apps during the sweep.
- **Screen dimmed:** Keep the screen on (`settings put system screen_off_timeout 600000`).
- **Wrong trackX:** On 1920-wide screens the incline button column centre is X=57 (not 75).
  Verify by sending a test tap:
  ```bash
  adb -s <ip>:5555 logcat -c
  adb -s <ip>:5555 shell input tap 57 350
  sleep 2
  adb -s <ip>:5555 logcat -d -s mono-stdout | grep "Changed Grade"
  ```

### "WARNING: Only N coarse resistance readings — skipping"

The probe scan (y=800 → y=300) didn't find the resistance slider. The most common causes:

- **Workout in warmup:** iFit locks resistance at level 1 during the warmup countdown. The
  slider ignores all input until you tap **END WARMUP**. The `--a11y` pre-flight block
  does this automatically; if you're running without `--a11y`, tap it manually first.
- **Slider already at max:** all probe swipes are upward and produce no change. In
  `--a11y` mode re-run the script — the probe starts at y=800 (near the bottom of the
  resistance track) so a max-position slider should still respond at the lowest probe steps.

Resistance calibration via `--a11y` was validated on the **NordicTrack S22i at API 25**
and produces R²=1.0000 in normal conditions. If it fails on a different device, check
that `trackX=1845` is correct for your screen width (see `screen_profile()` in the script).

### R² < 0.97 warning

The fit is poor. Try:

- Re-running the script (let iFit settle fully between runs).
- Confirming the slider moves smoothly without mechanical binding.
- On discrete sliders the coarse quality gate (R²≥0.99) should fire automatically. If
  coarse R² is poor, the slider may not be responding at every coarse step — check for
  dead zones or a required long-press that the swipe gesture is missing.

### QZCompanion still shows the previous device after restart

`qz-calibration.json` must be at exactly `/sdcard/qz-calibration.json`. Verify:

```bash
adb -s <ip>:5555 shell ls -la /sdcard/qz-calibration.json
adb -s <ip>:5555 shell cat /sdcard/qz-calibration.json
```

If missing, push manually:

```bash
adb -s <ip>:5555 push qz-calibration.json /sdcard/qz-calibration.json
```

---

## Output File Format

```json
{
  "incline": {
    "trackX": 57,
    "origin": 600.00,
    "scale": 20.0000
  },
  "resistance": {
    "trackX": 1845,
    "origin": 724.30,
    "scale": 17.4300,
    "minLevel": 1
  }
}
```

The `"resistance"` section is omitted if resistance calibration was skipped.

**Formula:** `targetY = origin − scale × value`  
For incline: `targetY = 600 − 20 × grade` (S22i example).  
For resistance: `targetY = origin − scale × (level − minLevel)`.

---

## Device Reference

| Device | Screen | Incline trackX | Incline Origin | Incline Scale | Resistance trackX | Resistance Origin | Resistance Scale | Mode |
|---|---|---|---|---|---|---|---|---|
| NordicTrack S22i (NTEX02117.2) | 1920×1080 | 57 | 600.0 | 20.0 | 1845 | 750.0 | 25.0 | `--a11y` |

Add rows as new devices are calibrated.

---

## Re-calibrating

Re-run the script any time slider accuracy degrades or after a firmware/screen update.
The new file overwrites the old on push. Force-stop and restart QZCompanion to apply.

```bash
adb -s <ip>:5555 shell am force-stop org.cagnulein.qzcompanionnordictracktreadmill
# (re-bind a11y if using --a11y mode)
adb -s <ip>:5555 shell am start -n \
  org.cagnulein.qzcompanionnordictracktreadmill/.MainActivity
```

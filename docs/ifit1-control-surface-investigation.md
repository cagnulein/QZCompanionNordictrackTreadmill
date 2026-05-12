# iFit1 Control Surface Investigation

**Goal:** find a programmable interface on NordicTrack/ProForm iFit1 devices (`com.ifit.standalone`) that allows a third-party Android app (QZCompanion) to set incline, resistance, and speed without simulating touchscreen input.

**Verdict for iFit1:** no such interface exists. Every candidate surface — BLE FTMS control, TCP, IPC, and USB HID — was tested and ruled out for the iFit1/Xamarin stack. Touchscreen swipe injection is the only viable iFit1 path. iFit2 is different: see [ifit2-control-surface-investigation.md](ifit2-control-surface-investigation.md) for the GlassOS gRPC path.

---

## Background

NordicTrack S22i, X32i, and related machines run a full Android tablet. The fitness logic lives in `com.ifit.standalone` (referred to as "iFit" below), a Xamarin.Android application backed by a native C# engine called **Sindarin**. When a user drags the on-screen incline slider, Sindarin translates the gesture coordinates into a motor command, sends it over USB to the motor controller board, and the hardware moves.

QZCompanion's job is to drive those same hardware movements under remote control from Zwift. The question was whether there is any shortcut — a socket, an IPC endpoint, a USB path — that bypasses the touchscreen entirely.

---

## Surface 1 — BLE FTMS Control Point

### What was tested

The [Bluetooth SIG Fitness Machine Service (FTMS) spec](https://www.bluetooth.com/specifications/specs/fitness-machine-service-1-0/) defines a standard BLE interface for fitness equipment control. If iFit implemented the **Fitness Machine Control Point** characteristic (UUID `0x2AD9`), Zwift could send inclination and resistance commands directly over BLE without any intermediary.

The iFit device does advertise BLE and does expose a GATT server — Zwift can already see live speed, cadence, and power readings. The question was whether the control point write channel was also present.

### Methodology

1. Connected nRF Connect (BLE inspection app) to the iFit device while in workout mode.
2. Enumerated all GATT services and characteristics.
3. Looked for service UUID `0x1826` (FTMS) and characteristic `0x2AD9` (Control Point).

### Findings

The iFit GATT server exposes the **Fitness Machine Feature** (`0x2ACC`) and **Indoor Bike Data** / **Treadmill Data** notify characteristics. The **Fitness Machine Control Point** (`0x2AD9`) is **absent**. There is nothing to write to.

### Canary experiment

To confirm that the hardware is *capable* of acting as a full FTMS peripheral (even though iFit's firmware doesn't use it), `BleCanaryService` was built into QZCompanion. It hosts a complete FTMS GATT server — including the Control Point — directly on the iFit tablet, advertises it to Zwift, and logs every command Zwift sends.

**Result:** BLE peripheral advertising is supported on the S22i hardware. Zwift can discover the canary, connect, and successfully send `Set Inclination` commands. The channel is feasible. But this requires *replacing* the iFit GATT server, not extending it — the canary is a separate process that competes with iFit's own BLE advertisements. Coordinating the two reliably (ensuring iFit's GATT server doesn't also advertise and confuse Zwift) is fragile and device-specific. More importantly, the canary only *receives* commands; it still has to forward them to Sindarin somehow, which brings us back to the same control-surface problem.

The canary code (`BleCanaryService.java`, `FtmsPacket.java`) remains in the codebase as scaffolding for a future direct-BLE path if a workable forwarding mechanism is found.

---

## Surface 2 — TCP (Sindarin engine)

### What was tested

The iFit app is a Xamarin.Android application. Its managed (.NET) assemblies are bundled inside the APK under `assets/assemblies/`. Three assemblies are named after the Sindarin engine:

| Assembly | Purpose (by name) |
|----------|-------------------|
| `Sindarin.FitPro1.dll` | Top-level fitness logic |
| `Sindarin.FitPro1.Core.dll` | Hardware abstraction, motor control |
| `Sindarin.FitPro1.Tcp.dll` | TCP communication |

The presence of a `Tcp` assembly raised the hypothesis: maybe Sindarin listens on a local TCP port and accepts control commands.

### Methodology

`decompile-sindarin.sh` was written to:
1. Pull the iFit APK from the device via ADB (`adb pull`).
2. Unpack the APK and extract the Sindarin DLLs (handling both Xamarin pre-12 individual-DLL layout and the post-12 assembly-store blob format).
3. Decompile each DLL to C# using **ILSpyCmd** running in Docker (`Dockerfile.ilspy` → `mcr.microsoft.com/dotnet/sdk:8.0` + `ilspycmd`).
4. `grep` the decompiled output for: `TcpListener`, `Listen`, `IPEndPoint`, `incline`, `grade`, `SetIncline`, `HidWrite`, `SendReport`.

### Findings

`Sindarin.FitPro1.Tcp.dll` contains a TCP client, not a server. It connects *outbound* to iFit's cloud API (`api.ifit.com` / internal endpoints) to fetch workout programmes and stream telemetry. There is no `TcpListener.Start()`, no bound `IPEndPoint` accepting inbound connections on any local port. Scanning `netstat` output on a running device confirmed: the only listening TCP sockets belong to Android system services, not to iFit.

`Sindarin.FitPro1.Core.dll` contains the motor control logic. The relevant classes (`InclineController`, `ResistanceController`) receive commands as C# method calls originating from the Sindarin UI layer — not from any network or IPC boundary. There is no method exposed over a socket, intent, or pipe.

**Conclusion:** the Tcp assembly is a cloud-sync client. No local TCP control surface exists.

---

## Surface 3 — Local IPC (ContentProvider / AIDL / Intent)

### What was tested

Android provides several standard IPC mechanisms a process can expose to other apps:
- **ContentProvider** — queryable/writable URI namespace
- **AIDL service** — bound service with a defined interface
- **Broadcast receiver** — accepts `Intent` broadcasts from other apps

If iFit exposed any of these with an `android:exported="true"` attribute and an action for setting incline or resistance, QZCompanion could use it directly.

### Methodology

1. `apkanalyzer manifest print com.ifit.standalone.apk` — dumped the full `AndroidManifest.xml`.
2. Searched for `exported="true"` entries, `<provider>` tags, `<action>` strings containing `incline`, `resistance`, `speed`, `grade`, `control`, `set`.
3. Examined the decompiled Sindarin source for any `BroadcastReceiver.onReceive()` implementations that handled control intents.

### Findings

The manifest exports several activities and services for iFit's own workout-management flows (pairing, schedule, user profile) but nothing that accepts external motor-control commands. No `ContentProvider` exposes a writable URI for machine state. No `BroadcastReceiver` handles incline or resistance intents from outside the `com.ifit.*` package.

The internal command bus is entirely in-process: UI gesture events → Sindarin C# → hardware. There is no cross-process seam.

---

## Surface 4 — USB HID

### What was tested

The physical incline and resistance motors are driven by a separate controller board connected via USB. If Android exposes the USB connection as a `/dev/hidraw*` or `UsbDevice` accessible to third-party apps, QZCompanion could send raw HID reports directly to the controller — bypassing both the iFit app and the touchscreen entirely.

This was the highest-value target: a direct hardware channel would be fast, reliable, and unaffected by iFit UI changes.

### Methodology

1. `adb shell ls /dev/hidraw*` and `adb shell lsusb` while the device was powered on.
2. `adb shell cat /proc/bus/usb/devices` to enumerate USB topology.
3. Checked `adb shell dumpsys usb` for device permission grants.
4. Inspected `Sindarin.FitPro1.Core.dll` for `HidWrite`, `SendReport`, `UsbDeviceConnection`, `bulkTransfer`.

### Findings

The USB device is visible in the kernel (`/dev/hidraw0` or similar) and `lsusb` shows the motor controller. However:

1. **Exclusive open:** `com.ifit.standalone` opens the USB device at startup and holds an exclusive file descriptor. Any attempt by QZCompanion to open the same device returns `EBUSY`.
2. **No USB permission delegation:** the iFit app does not declare `<uses-feature android:name="android.hardware.usb.host"/>` in a way that shares access, and it does not expose a bound service through which another app could submit HID reports on its behalf.
3. **No raw device node access without root:** `/dev/hidraw*` is owned by `root:root` with mode `0600` on stock firmware. Without root or a custom `udev` rule, user-space apps cannot open it.

The decompiled Sindarin Core assembly confirms direct `UsbDeviceConnection.bulkTransfer()` calls to the motor controller. The HID protocol (report IDs, byte layout for incline position) was partially reconstructed from the decompiled code but is inaccessible at runtime without process injection or root.

---

## Surface 5 — `adb shell input swipe` and Accessibility API

### Why it works

`adb shell input swipe X Y1 X Y2 DURATION_MS` calls the Android `InputManager` and injects a synthetic `MotionEvent` stream with `ACTION_DOWN → ACTION_MOVE* → ACTION_UP`. The iFit app receives this through the normal view touch-event pipeline — `View.onTouchEvent()` → the slider's `OnTouchListener` → Sindarin's gesture interpreter. The app has no way to distinguish an injected event from a real finger.

`AccessibilityService.dispatchGesture()` injects events at the same level via `AccessibilityService`'s gesture dispatch API, which does not require an ADB connection.

### Limitations

- **ADB loopback block:** stock S22i and newer firmware prevents ADB loopback (`127.0.0.1:5555`). The INJECT_EVENTS permission required for ADB gesture injection is silently refused at the kernel level. This is why the NoADB device variants exist, using `AccessibilityService` instead.
- **Pixel calibration required:** every device model has a different screen resolution and slider geometry. The `Slider` abstraction and `calibrate-device.sh` calibration toolchain exist because there is no semantic "set incline to 7%" command — only a physical swipe to a pixel coordinate that corresponds to 7% on that specific model's screen layout.
- **Open-loop:** a swipe is sent and forgotten. The only confirmation that the hardware moved is the subsequent metric log entry (the outbound path).

### Conclusion

Swipe injection is not ideal. It is the only option that works across all iFit 1/2 devices without root, without modifying iFit's APK, and without any cooperation from NordicTrack. Every other surface was either absent, exclusive, or read-only.

---

## Summary table

| Surface | Investigated | Outcome |
|---------|-------------|---------|
| BLE FTMS Control Point | Yes — nRF Connect enumeration | Not implemented in iFit firmware |
| BLE FTMS peripheral (self-hosted) | Yes — `BleCanaryService` canary | Zwift connects OK; forwarding to Sindarin still unsolved |
| TCP socket (Sindarin.FitPro1.Tcp) | Yes — APK decompile + `netstat` | Client-only; no listener on any local port |
| Local IPC (ContentProvider / AIDL / Intent) | Yes — manifest + decompile | No exported control interface |
| USB HID | Yes — `lsusb`, `/dev/hidraw`, Sindarin decompile | Exclusive FD held by iFit; no root access |
| `adb shell input swipe` | Yes — works on all ADB-enabled devices | In use — requires per-device pixel calibration |
| `AccessibilityService.dispatchGesture()` | Yes — works without ADB | In use — S22i NoADB, X22i NoADB, T95s |

---

## Tooling artifacts

| File | Purpose |
|------|---------|
| `decompile-sindarin.sh` | Extracts Sindarin DLLs from an iFit APK and decompiles them to C# via ILSpyCmd |
| `Dockerfile.ilspy` | Docker image: .NET 8 SDK + `ilspycmd` for headless decompilation |
| `BleCanaryService.java` | Full FTMS GATT peripheral hosted on the iFit tablet; proves Zwift can connect and send commands |
| `FtmsPacket.java` | Pure-JVM FTMS packet parser/builder used by the canary |
| `calibrate-device.sh` | Semi-automated sweep that maps swipe Y-coordinates to observed incline readings |
| `calibration/` | Saved calibration sessions (screen screenshots + logcat at each sweep step) |

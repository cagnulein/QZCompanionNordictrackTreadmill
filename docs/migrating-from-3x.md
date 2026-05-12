# Migrating from 3.x to 4.x

This guide is for contributors who know the 3.x codebase, especially maintainers
who have carried device support and releases through the older structure. It
does not replace [architecture.md](architecture.md); it explains the jump: why
the refactor happened, where familiar code moved, and how to do common work in
4.x.

---

## Why 4.x Looks Different

3.x concentrated most behavior in two services:

| 3.x file | Responsibility load |
|----------|---------------------|
| `UDPListenerService.java` | UDP receive loop, device enum, pixel constants, device-specific dispatch branches, throttle, cache, de-duplication. |
| `QZService.java` | Metric polling, iFit version detection, static metric state, and UDP metric broadcasting. |

That worked while the project was small, but it made every new device risky:
contributors had to edit large shared files, copy formulas into several
branches, and test dispatch behavior on real hardware. There were no tests to
catch regressions. One concrete example was duplicate suppression: boxed
`Float` values were compared by reference, so duplicate commands could still
produce swipes.

4.x separates those concerns:

- Android services are thin adapters around UDP, logcat, and app lifecycle.
- Devices are self-contained classes with their own formulas and sliders.
- Command dispatch is pure Java and testable without Android.
- Metric state lives with sliders instead of global static fields.
- Calibration produces the same constants contributors need for hardcoded device
  support.

The goal is not to discard the 3.x knowledge. The goal is to make that knowledge
local, reviewable, and testable.

---

## Name and Concept Map

| 3.x concept | 4.x location |
|-------------|--------------|
| `UDPListenerService` | `qz/QZCommandListenerService` |
| `QZService` metric output | `qz/QZTelemetryUnicastingService` |
| `MyAccessibilityService` | `console/GestureService` |
| `_device` enum | `DeviceRegistry.DeviceId` |
| `setDevice()` device switch | `DeviceRegistry` plus one device class per model |
| Inline pixel constants | Constructors and formulas in `device/bike/` or `device/treadmill/` |
| Inline dispatch `if/else` chains | `BikeDevice`, `TreadmillDevice`, typed `Command`, typed `Slider` |
| `lastSwipeMs` throttle state | `CommandDispatcher` |
| `lastSpeedFloat`, `lastInclinationFloat`, etc. | Per-slider live telemetry values |
| `ifit_v2` reader branching | `MonoStdoutTelemetryReader` via `logcat -s mono-stdout` |
| RadioGroup device list | `ui/DeviceAdapter` sectioned RecyclerView |
| OCR or external-only calibration | In-app calibration plus ADB fallback tools |

If you are looking for old `QZService` behavior, start with
`QZTelemetryUnicastingService` for outbound UDP, `MonoStdoutTelemetryReader` for
metric parsing, and `Device`/`Slider` classes for live metric state.

---

## Adding a Device Now

In 3.x, adding a device usually meant touching the enum, `setDevice()`, one or
more dispatch branches, and sometimes metric-reader configuration. The risk was
that a small device addition changed shared logic for every existing model.

In 4.x, the normal path is:

1. Get calibration data from the physical device. Prefer the in-app calibration
   flow; use `tools/discover-device.py` when an ADB-based run is needed.
2. Create one class under `device/bike/` or `device/treadmill/`.
3. Put the device's origin constants, scale formulas, screen profile, and any
   quantization or hysteresis overrides in that class.
4. Register the new `DeviceId` and instance in `DeviceRegistry`.
5. Add tests for the formulas or special behavior you introduced.
6. Run `python3 tools/discover-device/validate_swipe_targets.py`.

The calibration output is intentionally useful before the code lands: users can
ride with `custom_calibrated`, and contributors can copy the fitted origin and
scale values into a permanent device class.

For the full current pattern, use [architecture.md](architecture.md) and
[ifit1-device-reference.md](ifit1-device-reference.md). For test examples, use
[testing-methodology.md](../app/src/test/java/org/cagnulein/qzcompanionnordictracktreadmill/testing-methodology.md).

---

## Dispatch and State Changes

3.x handled packet parsing, throttle checks, device branching, swipe construction,
and duplicate suppression inside the UDP service. 4.x turns that into a pipeline:

```text
QZCommandListenerService
  -> DeviceController
  -> CommandDispatcher
  -> Command.applyTo(device)
  -> typed Slider.handle()
  -> GestureService.performSwipe()
```

Use this ownership rule when changing behavior:

| Behavior | 4.x owner |
|----------|-----------|
| UDP delimiters, sentinels, calibration packet prefix | `QZCommandPacket` |
| Queueing and throttle timing | `CommandDispatcher` |
| Treadmill vs bike packet decoding | `TreadmillDevice` / `BikeDevice` |
| Per-device formulas | Concrete device class |
| Per-axis de-duplication, quantization, hysteresis, speed gate | Typed `Slider` |
| Accessibility gesture injection | `GestureService` |

Metric state also moved. Instead of static floats on `QZService`, telemetry is
delivered as `Telemetry` objects. `Device.applyTelemetry()` forwards those values
to sliders, and live sliders use them to correct thumb position drift or release
cached speed commands.

---

## Metric Reading

4.x has two committed platform paths. iFit2 / GlassOS gRPC is the preferred path when available; iFit1 / gesture + logcat remains the legacy compatibility path for older consoles.

3.x had multiple ways to read iFit metrics: tailing files, grepping snapshots,
shelling out, and branching on iFit version-specific paths and keywords. That
created a matrix of device strategy times iFit version.

4.x uses one source per platform, committed at startup by `iFitPlatform.detect()`:

**iFit1 (Xamarin/Mono, `com.ifit.standalone`):** `MonoStdoutTelemetryReader` reads
`logcat -s mono-stdout`. Metric changes are emitted on that logcat tag across the
supported device family. This is the only reader for iFit1 devices.

**iFit2 (Kotlin/gRPC, `com.ifit.rivendell`):** `GlassOsTelemetryReader` opens the
gRPC channel to `glassos_service` on `localhost:54321`, subscribes to
`WorkoutService.WorkoutStateChanged`, and activates per-axis subscriptions (incline,
resistance, speed, cadence, watts, HR) when the workout transitions to
`WORKOUT_STATE_RUNNING`. There is no iFit1 fallback on a committed-iFit2 platform.

Device classes do not choose a metric reader anymore, and there is no runtime
fallback between the two readers.

---

## UI, Preferences, and Releases

The old main screen used a large `RadioGroup` and stored the selected device as a
view-resource integer. 4.x uses a sectioned `RecyclerView` backed by
`DeviceAdapter` and stores the selected `DeviceId.name()` string.

Practical migration note: a 3.x saved device selection will not map directly to
the 4.x string preference. Existing installs may fall back to the default device
until the user selects their model again.

Release metadata also changed:

| 3.x | 4.x |
|-----|-----|
| Version fields spread across Gradle, manifest, and workflow tag strings. | `version.properties` is the only file to edit for `versionName`. |
| `versionCode` was manually coordinated. | CI uses the GitHub run number; local builds fall back to `1`. |
| Release labels could drift from APK metadata. | CI reads the version file and adds the build number automatically. |

To cut a 4.x release, bump `versionName` in `version.properties` and let CI supply
the build number.

---

## What Did Not Change

- QZ still sends commands to QZ Companion over UDP port 8003.
- QZ Companion still unicasts metrics back to QZ on UDP port 8002.
- The QZ app does not need a wire-format change for the 4.x refactor.
- On iFit1, the app still controls iFit by injecting accessibility gestures. On iFit2, commands go over gRPC — gesture injection is bypassed entirely.
- Device support still depends on accurate screen-coordinate formulas and real
  hardware feedback.

The main change is where that knowledge lives: in small, named, testable classes
instead of shared service branches.

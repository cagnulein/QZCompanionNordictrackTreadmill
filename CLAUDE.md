# QZ Companion NordicTrack Treadmill - Claude Documentation

## Project Structure
Android app for controlling NordicTrack and ProForm fitness devices through QZ Companion's two iFit integration paths: preferred iFit2 / GlassOS gRPC control, and legacy iFit1 / AccessibilityService gesture control.

The build is a multi-module Gradle project. Business logic lives in three library modules under `lib/`; the `app/` module is a thin Android shell (services, platform, UI).

### Gradle Modules

| Module | Role |
|--------|------|
| `app/` | Android shell — UDP services, UI, platform receivers. Depends on all three lib modules. |
| `lib/core/` | Platform-agnostic domain layer — command model, telemetry bus, abstract `Device`. No Android imports; JVM-testable without Robolectric. |
| `lib/ifit1/` | Legacy gesture-based compatibility for iFit1 consoles. Depends on `lib:core`. Requires `AccessibilityService` at runtime. |
| `lib/ifit2/` | Preferred gRPC-based integration for iFit2 / GlassOS consoles. Depends on `lib:core` + gRPC stack. |

Each module has a `README.md` with its dependency rules and entry points.

### Key Files

**app/**
- `qz/QZCommandListenerService.java` — UDP listener (port 8003); calls `QZCommandSubscriber.onPacket()` for each datagram
- `qz/QZTelemetryUnicastingService.java` — subscribes to `TelemetryHub`, encodes and unicasts QZ metric packets on port 8002
- `ui/MainActivity.java` — main UI; sectioned device list, status chip, requirements card, overflow debug menu
- `app/src/main/res/layout/activity_main.xml` — sectioned RecyclerView UI
- `app/build.gradle` — Android build configuration
- `app/src/main/AndroidManifest.xml` — Android manifest
- `.github/workflows/main.yml` — GitHub Actions CI/CD

**lib/core/**
- `device/Device.java` — abstract base class for all fitness devices
- `device/DeviceController.java` — owns `Device` + `CommandDispatcher` + telemetry subscription; the seam between command packets, telemetry, and the device layer

**lib/ifit1/**
- `console/ifit1/GestureService.java` — performs swipe gestures via the Android Accessibility API
- `device/ifit1/DeviceRegistry.java` — `DeviceId` enum + `EnumMap` of all supported devices
- `device/ifit1/DeviceCalibration.java` — loads `qz-calibration.json` at startup

### Package Layout

All modules share the root package `org.cagnulein.qzcompanionnordictracktreadmill`.

```
app/
├── qz/               QZCommandListenerService, QZTelemetryUnicastingService
├── platform/         IFitPlatform; boot/restart receivers and crash handling
│   ├── crash/        CrashHandler
│   └── receiver/     BootReceiver, ServiceRestartReceiver
└── ui/               MainActivity, CalibrationActivity, DeviceAdapter

lib/core/
├── qz/               QZCommandPacket, QZMetricPacket, QZCommandSubscriber, QZTelemetryEncoder
├── command/          Command, CommandDispatcher, SpeedCommand, InclineCommand,
│                     ResistanceCommand, GearCommand, RawSwipeCommand
├── telemetry/        TelemetryHub, TelemetryReader, Telemetry, SpeedTelemetry,
│                     InclineTelemetry, ResistanceTelemetry, GearTelemetry
└── device/           Device, DeviceController

lib/ifit1/
├── console/ifit1/    GestureService, MonoStdoutTelemetryReader
│   └── calibration/  CalibrationRunner and supporting classes
└── device/ifit1/     GestureDevice, GestureBikeDevice, GestureTreadmillDevice, DeviceRegistry,
                      DeviceCalibration, ScreenProfile, SnapToOriginCommand
    ├── bike/          One class per bike device (S22iDevice, S15iDevice, …)
    ├── treadmill/     One class per treadmill device (X11iDevice, X32iDevice, …)
    └── slider/        Slider, InclineSlider, SpeedSlider, ResistanceSlider, GearSlider

lib/ifit2/
├── console/ifit2/    GrpcTelemetryReader, GrpcCommandTransport, GrpcCredentials, CommandTransport
└── device/ifit2/     GrpcDevice, GrpcBikeDevice, GrpcTreadmillDevice
```

---

## New Device Implementation Pattern

All devices are self-contained classes. There is no enum switch or coordinate lookup table. New integration work should target iFit2 first when the device runs GlassOS. Add or modify iFit1 gesture devices only when supporting older iFit1 hardware that cannot use the gRPC path.

### 1. Create the device class

Bike device (`lib/ifit1/src/main/java/.../device/ifit1/bike/MyNewDevice.java`):
```java
package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.GestureBikeDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.ScreenProfile;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.slider.InclineSlider;

public class MyNewDevice extends GestureBikeDevice {
    private static final int ORIGIN_INCLINE_THUMBY = /* formula intercept */;

    public MyNewDevice() {
        super(
            new InclineSlider(ScreenProfile.W1920.leftTrackX, ORIGIN_INCLINE_THUMBY, MyNewDevice::offsetInclineThumbY),
            null  // second slot: ResistanceSlider, GearSlider, or null
        );
    }
    @Override public String displayName() { return "My New Device"; }

    private static int offsetInclineThumbY(double v) { return ORIGIN_INCLINE_THUMBY - (int)(/* scale */ * v); }
}
```

Treadmill device: extend `GestureTreadmillDevice` and pass `(incline, speed)` as `InclineSlider` and `SpeedSlider`. Bike devices pass `(incline, resistance)` where the resistance slot accepts `InclineSlider`, `ResistanceSlider`, or `GearSlider` depending on the physical axis. All devices use `AccessibilityService` by default — no `requiresAdb()` or `requiresAccessibility()` overrides needed. When `currentThumbY`, `quantize`, or `hysteresisPixels` need overriding, use an anonymous typed-slider subclass combining the formula constructor with the override body. Use `InclineSlider.live()` / `SpeedSlider.live()` / `ResistanceSlider.live()` / `GearSlider.live()` when the slider should derive its current thumb position from the live metric feed.

### 2. Register the device

Add a `DeviceId` value to `DeviceRegistry.DeviceId`, then add an entry in `DeviceRegistry.DEVICES`:
```java
.put(DeviceId.my_new_device, new MyNewDevice())
```

### 3. Add to the UI

Add the `DeviceId` to the appropriate category in `ui/DeviceAdapter` (items are built from `DeviceRegistry.Category` automatically).

---

## Version Management

### Files to Update for Each Release
**Update ONE value only:**

1. **version.properties** (root of repo)
   - `versionName` (semantic versioning): `3.6.29 → 3.6.30`

`versionCode` is set automatically from the GitHub Actions run number — never edit it manually. `build.gradle` reads `versionName` from `version.properties`. `AndroidManifest.xml` no longer carries version fields — AGP injects them at build time. CI publishes the release as `3.6.30 (build 214)` automatically.

Local debug builds show `dev-<git-hash>` in the action bar subtitle instead of a build number.

### Version Bump Process
```bash
# 1. Patch release (bug fix)
3.6.19 → 3.6.20

# 2. Minor release (new feature)
3.6.19 → 3.7.0

# 3. Major release (breaking change)
3.6.19 → 4.0.0
```

---

## Device Naming Convention
- `DeviceId` enum value: `{series}{model}_{variant}` (e.g. `s22i_NTEX02117_2`)
- `displayName()`: `"{Series} {Type} ({Model})"` (e.g. `"S22i Bike (NTEX02117.2)"`)
- Class name: `{Series}{Model}Device.java` (e.g. `S22iNtex02117Device.java`)
- No strings.xml — display names are hardcoded in `displayName()`

---

## Documentation

Most docs live in `docs/`. A few live adjacent to the code they describe:

- `lib/core/README.md`, `lib/ifit1/README.md`, `lib/ifit2/README.md` — module boundary contracts (purpose, dependency rules, entry points); edit when module responsibilities change
- `docs/ifit1-device-reference.md` — iFit1 per-device pixel formulas, ScreenProfile table, validator notes; edit alongside iFit1 gesture device classes
- `app/src/test/java/.../testing-methodology.md` — test file inventory, swipe assertion patterns, how to add tests for a new device; edit alongside test files

---

## Code Exploration

Prefer the `LSP` tool (JDTLS) over `grep`/`find` when exploring Java code — it resolves symbols, finds references, and navigates the type hierarchy accurately without reading irrelevant files.

---

## Code Style Guidelines
- All comments must be in English
- Use descriptive variable names
- Follow existing indentation patterns
- No comments explaining what the code does — only why (non-obvious constraints, workarounds)

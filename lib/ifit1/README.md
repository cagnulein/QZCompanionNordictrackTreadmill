# lib:ifit1

Legacy compatibility module for iFit Generation 1 consoles (NordicTrack/ProForm devices that run a monolithic Android UI). It translates abstract `Command` values into precise pixel-coordinate swipe gestures delivered through the Android `AccessibilityService`.

## Dependency rules

- **Depends on:** `lib:core`
- **Must never import:** `lib:ifit2`
- **Requires at runtime:** `GestureService` connected as a live `AccessibilityService`

Tests use Robolectric (the only module that does). This is unavoidable because gesture dispatch touches the Android `AccessibilityService` API.

## Entry points

| Class | Role |
|---|---|
| `DeviceRegistry` | The authoritative list of supported devices. `DeviceId` enum + `EnumMap` of instances. UI and controller code reference devices by `DeviceId` only — they never import concrete device classes. |
| `GestureBikeDevice` | Base class for all bike devices. Extend this when adding a new bike. |
| `GestureTreadmillDevice` | Base class for all treadmill devices. Extend this when adding a new treadmill. |
| `GestureService` | The `AccessibilityService` that performs swipes. Check `GestureService.isConnected()` before calling `performSwipe`. |
| `DeviceCalibration` | Loads `qz-calibration.json` at startup. Used by `CalibratedBikeDevice` for devices whose pixel formulas were measured rather than hardcoded. |

## Key constraints

Each device is a self-contained class with its slider pixel formulas hardcoded as constants. There is no coordinate lookup table or switch statement — adding an iFit1 device means adding a class, not touching shared data. See `docs/ifit1-device-reference.md` for the per-device formula derivations and `CLAUDE.md` for the full implementation pattern.

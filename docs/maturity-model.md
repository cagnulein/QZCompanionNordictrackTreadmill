# QZCompanion App Maturity Model

Eight dimensions, each scored 0–3. The model is specific to this project's constraints:
an Android app that controls fitness devices via touch injection, with no server side,
no network API, and a hardware-in-the-loop feedback problem.

---

## Scoring Scale

| Score | Meaning |
|-------|---------|
| **0** | Missing or broken |
| **1** | Ad-hoc / works but fragile |
| **2** | Defined, documented, repeatable |
| **3** | Measured, automated, self-correcting |

---

## Dimensions

### 1. Test Coverage

Does the code have tests that catch regressions before hardware access is needed?

| Score | Criteria |
|-------|----------|
| 0 | No tests |
| 1 | Some unit tests for isolated helpers |
| 2 | Core dispatch pipeline covered: throttle, de-dup, quantize, swipe formula; edge cases (sentinel values, locale separators) covered |
| 3 | Full end-to-end route replay test (e.g. HillyRouteReplayTest); all device formula paths exercised; metric reader tested against real log samples |

### 2. Observability

Can you tell what the app is doing and why, during and after a ride?

| Score | Criteria |
|-------|----------|
| 0 | No logging |
| 1 | Ad-hoc `Log.i` calls, unstructured |
| 2 | Structured log tags (`QZ:Dispatch`, `QZ:UDP`); throttle, de-dup, and cache decisions logged with reasons; ride monitoring script captures metrics |
| 3 | Post-ride analysis script produces actionable report (commands dispatched, de-dup rate, memory/CPU trend, ADB failures); anomalies flagged automatically |

### 3. Device Portability

How much work is required to add a new device or variant?

| Score | Criteria |
|-------|----------|
| 0 | Device logic scattered across multiple unrelated files |
| 1 | Device class exists but adding one requires changes in 3+ files |
| 2 | Single `DeviceRegistry`; new device = one new class + one registry entry; no changes to dispatch or UI code |
| 3 | New device class is self-contained: formula, execution mode, metric reader, and display name in one file; covered by at least one unit test |

### 4. Formula Auditability

Can you understand, verify, and correct the coordinate math for a device?

| Score | Criteria |
|-------|----------|
| 0 | Magic numbers, no explanation |
| 1 | Constants named but derivation unknown |
| 2 | Formula documented with derivation method (two-point linear fit); correction factors (e.g. S22i `v > 3.0` overshoot offset) explained inline |
| 3 | Formula verified against the real device via calibration sweep; commanded vs. observed table exists; tolerance confirmed ≤ 0.5% (one quantize step) |

### 5. Build Reproducibility

Does every commit produce the same artifact, automatically?

| Score | Criteria |
|-------|----------|
| 0 | Manual builds, version bumped by hand in multiple files |
| 1 | Gradle builds locally; version managed but inconsistently |
| 2 | CI builds on every push; version sourced from single `version.properties`; `versionCode` from run number |
| 3 | Signed release APK published automatically on tag; build inputs fully declared (no external mutable dependencies) |

### 6. Failure Resilience

Does the app recover gracefully when things go wrong?

| Score | Criteria |
|-------|----------|
| 0 | ADB failure = silent hang or crash |
| 1 | Error logged but no recovery |
| 2 | ADB auto-reconnects with delay; throttle window prevents command storms on reconnect; all failure paths log at error level |
| 3 | Per-feature graceful degradation: app continues functioning (minus the failed feature) on any single failure; no uncaught exceptions reach the user |

### 7. Calibration Capability

Can you verify that the device is actually reaching the commanded state?

| Score | Criteria |
|-------|----------|
| 0 | No feedback channel; purely open-loop |
| 1 | Manual observation (watch the display) |
| 2 | OCR incline reading confirmed working; ADB sweep script exists; commanded vs. observed can be captured |
| 3 | Automated calibration sweep produces correction table; formula re-derivation documented; calibration result feeds a regression test |

### 8. Documentation

Can a new contributor understand and extend the app without reading every file?

| Score | Criteria |
|-------|----------|
| 0 | No docs |
| 1 | CLAUDE.md with basic add-device pattern |
| 2 | Architecture doc (data flow, class roles), device formula reference table, calibration runbook |
| 3 | Docs kept adjacent to the code they describe; formula reference generated or verified against source; runbooks tested (commands in runbooks actually work) |

---

## Current Scores (as of 2026-05-01)

| Dimension | Score | Notes |
|-----------|-------|-------|
| Test Coverage | 3 | HillyRouteReplayTest (1) + ZwiftRideSimulationTest (10) + ZwiftRideRobolectricTest (5) for route replay + de-dup; TreadmillDeviceTest (142), BikeDeviceTest (64), QZCommandPacketTest (23), QZMetricPacketTest (29), TelemetryReaderTest (3), CommandDispatcherTest (16), QZCommandListenerServiceTest (7), QZTelemetryUnicastingServiceTest (5), UdpPipelineTest (5); all 44 registry devices have ≥1 test |
| Observability | 3 | Structured tags (QZ:Dispatch, QZ:Snapshot, QZ:Device, QZ:IFit, QZ:Crash, QZ:Main, QZ:Service, QZ:TelemetryReaderService, QZ:CommandListenerService); analyze-ride.sh reports dispatch count, de-dup/throttle, INJECT_EVENTS failures, heap/CPU/thread/FD stats, monotonic heap-trend flag, 5 auto-warning thresholds; stale "ADB DOWN" counter in analyze-ride.sh:176 (permanently 0, harmless) |
| Device Portability | 3 | All devices self-contained in device/bike/ and device/treadmill/ (one file per device); DeviceRegistry + DeviceAdapter; all devices have ≥1 test; defaultTelemetryReader() returns MonoStdoutTelemetryReader for all devices |
| Formula Auditability | 1 | S22i has full two-point derivation inline (incline: "Single linear fit — 18.57 px/%"; resistance: "Slope = (323−724) / 23 = −401/23 ≈ −17.43 px per level"); X32i, S15i, Ntex71021 and ~40 others have named origin constants but no derivation or methodology notes |
| Build Reproducibility | 2 | CI signs and publishes on every master push; version from version.properties; versionCode from run number; action refs unpinned (checkout@v2, sign-android-release@v1, add-and-commit@v9, create-release-with-debugapk@v2.0.0) |
| Failure Resilience | 3 | UDP listener loop catches per-packet exceptions and continues (no permanent thread death); MonoStdoutTelemetryReader stream errors caught in-loop (reader thread survives); performSwipe logs Log.e on null instance and dispatchGesture=false; CrashHandler logs QZ:Crash + saves to CrashPrefs before kill |
| Calibration Capability | 3 | In-app guided calibration: Accessibility gesture sweep, shared mono-stdout metric stream, least-squares fit, R² gate, JSON save/apply, immediate `custom_calibrated` selection; optional resistance support with skip-on-insufficient-points behavior; discover-device.py retained as ADB fallback with --a11y mode for Xamarin/API 25 devices; unattended test plan in tools/test-calibration-unattended.md; DeviceCalibrationRegressionTest verifies formula tolerance ±1px (< 0.5% quantize step) across full incline and resistance ranges |
| Documentation | 2 | architecture.md, migrating-from-3x.md in docs/; ifit1-device-reference.md in docs/; testing-methodology.md adjacent to test/; discover-device-runbook.md adjacent to tools/discover-device.py; runbooks not tested end-to-end |

**Overall: 20 / 24**

### Code Quality Gate (not a scored dimension)

`/smells` project command runs a two-pass Clean Code review (Uncle Bob checklist) on changed
files. Integrated into `/ship` as a gate: critical smells (flag arguments, commented-out code,
base/derivative coupling, Law of Demeter violations) block the commit; advisory smells appear
in the preview.

---

## Next Step Per Dimension

| Dimension | Next action to reach score+1 |
|-----------|------------------------------|
| Test Coverage | ✓ Done — all devices covered |
| Observability | ✓ Done — memory/CPU/thread/FD trends captured; anomaly thresholds automated; stale "ADB DOWN" label in analyze-ride.sh:176 is harmless (no ADB in pipeline) |
| Device Portability | ✓ Done — orthogonal reader design complete; all DeviceId entries have ≥1 test |
| Formula Auditability | Add two-point derivation comments to X32i and one other device file (methodology visible without real-device access); then expand |
| Build Reproducibility | Pin all GitHub Actions to SHA digests to eliminate mutable external dependencies |
| Failure Resilience | ✓ Done — service loops resilient; uncaught exceptions logged at QZ:Crash |
| Calibration Capability | ✓ Done — DeviceCalibrationRegressionTest (13 tests); Slider constructor bug fixed as a side-effect |
| Documentation | Verify runbook commands execute correctly end-to-end |

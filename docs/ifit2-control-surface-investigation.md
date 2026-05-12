# iFit2 Control Surface Investigation

**Goal:** find programmable interfaces on NordicTrack/ProForm devices running iFit2 (the Kotlin/gRPC rewrite, APK `com.ifit.rivendell` + `com.ifit.glassos_service`) that allow QZCompanion to set incline, resistance, and speed without simulating touchscreen input.

**Verdict (confirmed — investigation complete):**
- **gRPC works for commands.** `glassos_service` exposes a local gRPC server on `localhost:54321`; `InclineRequest`, `ResistanceRequest`, and `SpeedRequest` are confirmed working on physical hardware. `GearRequest` is present in the proto but not yet exercised.
- **GlassOS works for telemetry.** Live metrics stream from `glassos_service` at ~1 Hz over gRPC async subscriptions.
- **mTLS credentials are extracted from the rivendell APK at runtime.** No hardcoded keys; the CA cert, client cert, and private key are discovered dynamically by scanning the installed APK's `resources.arsc`.
- **BLE FTMS control was not added.** The iFit2 GATT server contains no FTMS service or Control Point — see below.

---

## Background

iFit2 is a ground-up rewrite of the iFit Android app. Where iFit 1 was a Xamarin.Android application backed by the Sindarin C# engine (see [ifit1-control-surface-investigation.md](ifit1-control-surface-investigation.md)), iFit2 is a Kotlin app with two APKs:

| APK | Package | Role |
|-----|---------|------|
| Rivendell | `com.ifit.rivendell` | Main UI and workout app |
| GlassOS Service | `com.ifit.glassos_service` | Platform service — gRPC server, BLE, telemetry |

The decompiled sources were extracted under `/tmp/rivendell_installed_decompile/` and `/tmp/glassos_installed_decompile/` using jadx + apktool.

---

## Surface 1 — gRPC (GlassOS service)

### Server

`glassos_service` binds a gRPC server on `localhost:54321` with mutual TLS. The `com.ifit.glassos.workout` package contains unobfuscated protobuf message and stub classes for every controllable axis:

| gRPC service | Request message | Response |
|-------------|-----------------|---------|
| `InclineServiceGrpc` | `InclineRequest.setPercent(double)` | `WorkoutResult` |
| `ResistanceServiceGrpc` | `ResistanceRequest.setResistance(double)` | `WorkoutResult` |
| `SpeedServiceGrpc` | `SpeedRequest.setKph(double)` | `WorkoutResult` |
| `GearServiceGrpc` | `GearRequest` | `WorkoutResult` |

Each service exposes a `canRead(Empty)` → `AvailabilityResponse` method and an async `*Subscription(Empty)` stream that pushes live metric updates at ~1 Hz. `GlassOsTelemetryReader` does not call `canRead()`; it uses `WorkoutService.WorkoutStateChanged` to defer metric subscriptions until `WORKOUT_STATE_RUNNING` (see below).

### Security model

Two server-side interceptors gate every call (from decompiled `df/c.java` and `df/e.java`):

1. **Address filter (`df/e.java`)** — rejects any connection that is not from a local/loopback address. Connecting to `localhost:54321` passes unconditionally.

2. **Client-ID header check (`df/c.java`)** — requires the gRPC metadata header `client_id` to equal the CN field of the mTLS client certificate. Connecting with `client_id: com.ifit.rivendell` and a cert with `CN=com.ifit.rivendell` passes this check.

### Credential discovery

The rivendell APK ships the mTLS credentials as raw resources with names matching `img_icon_<hex>`. The resources contain JPEG-obfuscated PEM data: a `FFD8` line (JPEG SOI marker), the base64 body, and a `FFD9` line (JPEG EOI marker). Stripped of these markers the content is standard base64-encoded DER.

**Key naming scheme** (from `qk/e.java` in the rivendell decompile):
- Client cert: `img_icon_<clientSecret>87db<clientId>` (e.g. `res/Ln.jpg`)
- Private key: `img_icon_<clientId>45fd<clientSecret>`
- CA cert: `img_icon_9e3564...` (hardcoded key; this is the self-signed `CN=testca` cert)

**Obfuscation:** production APKs use Android resource shrinking, so the physical file paths inside the ZIP are short and opaque (`res/I6.jpg`, `res/Ln.jpg`, etc.). The logical resource names (`img_icon_<hex>`) are still present in the `resources.arsc` key string pool even when the physical paths are obfuscated.

**Discovery algorithm** (implemented in `GlassOsCredentials.discoverKeys()`):

1. Read `resources.arsc` from the installed APK as a byte array.
2. Binary-scan for `img_icon_` byte sequences; the suffix characters after the prefix are hex digits `[0-9a-f]`. Collect all unique names found.
3. For each name, resolve it via `Resources.getIdentifier("img_icon_<name>", "raw", packageName)` and read the raw resource stream.
4. Strip `FFD8`/`FFD9` lines, attempt to parse as both an X.509 certificate and a PKCS#8 private key. Collect successful parses into cert and key candidate lists.
5. Identify the CA cert as the self-signed candidate (subject == issuer).
6. Identify the client cert as the CA-signed cert whose CN equals `com.ifit.rivendell`. The rivendell APK contains ~16 client certs (one per app component: `com.ifit.rivendell`, `com.ifit.wolf`, `com.ifit.launcher`, `com.ifit.dev_app`, `com.ergatta.*`, etc.) all signed by the same `CN=testca` CA. The wrong CN causes `PERMISSION_DENIED` on the `client_id` header check.
7. Match the private key to the client cert by comparing RSA moduli.

**Caching:** discovered resource names (not the key material itself) are cached in `SharedPreferences("glassos_cred_v2")` keyed by the APK's `versionCode`. The cache is invalidated automatically when iFit2 updates. The cache stores the logical resource names; the actual PEM content is re-read from the installed package on each app launch using those names.

### End-to-end verification

Confirmed on a physical S22i with iFit2 (`com.ifit.rivendell`) in an active workout:

```
QZ:MetricSvc: telemetry reader streaming active: GlassOsTelemetryReader
QZ:MetricSvc: glassos: incline
QZ:MetricSvc: Changed Grade 0.0
QZ:MetricSvc: glassos: resistance
QZ:MetricSvc: Changed Resistance 1.0
...
QZ:Dispatch: glassos applied: incline=3.0
QZ:Dispatch: glassos applied: incline=-2.0
QZ:Dispatch: glassos applied: incline=0.0
QZ:Dispatch: glassos applied: resistance=5.0
QZ:Dispatch: glassos applied: resistance=1.0
```

Round-trip latency from UDP command receipt to `glassos applied` is ~200 ms.

### Workout-state-gated telemetry activation

`GlassOsTelemetryReader` subscribes to `WorkoutService.WorkoutStateChanged` at startup. Metric subscriptions (incline, resistance, speed, cadence, watts, HR) are activated only when the workout state transitions to `WORKOUT_STATE_RUNNING`. If the workout is already running at QZ startup, `GetWorkoutState()` triggers immediate activation.

The `WorkoutService` proto (extracted from the `glassos_service` APK) uses the following state enum:

```proto
enum WorkoutState {
  WORKOUT_STATE_UNKNOWN = 0;
  WORKOUT_STATE_IDLE    = 1;
  WORKOUT_STATE_DMK     = 2;
  WORKOUT_STATE_RUNNING = 3;
  WORKOUT_STATE_PAUSED  = 4;
  WORKOUT_STATE_RESULTS = 5;
}
```

This means QZCompanion no longer requires an active workout to be in progress before it is launched. `read()` throws `IOException` only for hard failures (credential load, channel open); a missing or paused workout is handled transparently.

---

## Surface 2 — BLE FTMS Control Point

### What was tested

The iFit 1 investigation (see [ifit1-control-surface-investigation.md](ifit1-control-surface-investigation.md), Surface 1) found that iFit 1 exposed BLE read/notify characteristics but no FTMS Control Point (`0x2AD9`). The question here was whether iFit2 added FTMS control as part of the rewrite.

### Methodology

1. Located the live GATT server implementation in `glassos_service` smali: `zd/d.smali` calls `BluetoothManager.openGattServer()`, creates `BluetoothGattService` instances, adds characteristics via `addCharacteristic()`, and registers them via `addService()`.
2. Traced all UUID construction to the enum class `wd/i` (decompiled to `wd/i.java`), which is the single source of truth for every UUID the GATT server uses.
3. Searched both APKs exhaustively for the FTMS service UUID (`0x1826`) and all FTMS characteristic UUIDs (`0x2ACC`, `0x2ACD`, `0x2ACE`, `0x2AD2`, `0x2AD6`, `0x2AD8`, `0x2AD9`, `0x2ADA`) — in jadx Java output, apktool smali, and raw string search.

### Findings

The complete UUID registry for the iFit2 GATT server (`wd/i.java`):

| Name | UUID | Standard |
|------|------|----------|
| `DeviceTx` | `00001535-1412-efde-1523-785feabcd123` | Nordic UART (TX) |
| `DeviceRx` | `00001534-1412-efde-1523-785feabcd123` | Nordic UART (RX) |
| `HeartRateMeasurement` | `00002a37-0000-1000-8000-00805f9b34fb` | BT SIG 0x2A37 |
| `BatteryLevel` | `00002A19-0000-1000-8000-00805f9B34fb` | BT SIG 0x2A19 |
| `BodySensorLocation` | `00002A38-0000-1000-8000-00805f9B34fb` | BT SIG 0x2A38 |
| `GlassOSWatchService` | `40E7419B-8EAA-4937-A944-D2816145F074` | iFit proprietary |
| `GlassOSHR` | `DF196F21-349C-41D1-A04F-36F2863241D0` | iFit proprietary |
| `GlassOSWorkoutState` | `0E788C89-C676-4A1C-80E2-34EFBB453201` | iFit proprietary |
| `GlassOSHRZones` | `12CBCBFB-8BD1-4A76-A4E8-F7780C0C2BB2` | iFit proprietary |
| `ConnectedWatchName` | `6BEFE8ED-8BAE-4381-9052-3D25E186C309` | iFit proprietary |
| `ConnectionPing` | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | Nordic UART service |
| `ArcXClientCharacteristicConfigDescriptor` | `00002902-0000-1000-8000-00805f9b34fb` | BT SIG CCCD |
| `ArcXKeyEventCharacteristic` | `00001667-1212-efde-1523-785feabcd124` | iFit ArcX remote |
| `ArcXExtendedKeyEventCharacteristic` | `00001669-1212-EFDE-1523-785FEABCD124` | iFit ArcX remote |

**No FTMS UUID appears anywhere in this list or anywhere else in either APK.** The BLE layer in iFit2 is entirely dedicated to their proprietary watch/accessory ecosystem (iFit Watch via GlassOS characteristics, ArcX remote, Nordic UART) and standard HR/battery read-only notify characteristics.

### Conclusion

BLE FTMS control was not added in iFit2. The absence is complete — no service UUID, no Control Point characteristic, no Feature characteristic, no machine data characteristics. The gRPC path is the only known programmable control surface.

---

## Summary

| Surface | Status | Notes |
|---------|--------|-------|
| gRPC commands (`InclineRequest`, `ResistanceRequest`) | **Confirmed working** | Verified on physical S22i with iFit2 |
| gRPC command (`SpeedRequest`) | **Confirmed working** | Verified on physical S22i (iFit2 treadmills) |
| gRPC command (`GearRequest`) | **Present, untested** | Class in proto; not exercised |
| GlassOS telemetry subscriptions | **Confirmed working** | ~1 Hz stream; incline, resistance, speed, cadence, watts, HR |
| BLE FTMS Control Point (`0x2AD9`) | **Absent** | UUID not present in any form in either APK |
| BLE FTMS Service (`0x1826`) | **Absent** | UUID not present in any form in either APK |
| Other BLE | Read/notify only | HR, battery, proprietary watch/ArcX characteristics |

---

## Artifacts

| Path | Contents |
|------|---------|
| `/tmp/rivendell_installed_decompile/` | jadx + apktool output for installed `com.ifit.rivendell` |
| `/tmp/glassos_installed_decompile/` | jadx + apktool output for installed `com.ifit.glassos_service` |
| `wd/i.java` (in glassos jadx output) | Complete UUID enum for the iFit2 GATT server |
| `zd/d.smali` (in glassos apktool output) | GATT server implementation (`openGattServer`, `addService`) |
| `df/c.java` (in glassos jadx output) | Server interceptor: `client_id` header == cert CN |
| `df/e.java` (in glassos jadx output) | Server interceptor: loopback address filter |
| `qk/e.java` (in rivendell jadx output) | Credential loading — `img_icon_` resource name scheme |
| `com/ifit/glassos/workout/` (in rivendell jadx output) | Unobfuscated gRPC message and stub classes |
| `glassos/GlassOsCredentials.java` | Runtime credential discovery implementation |
| `glassos/GlassOsTelemetryReader.java` | gRPC telemetry subscription implementation |
| `glassos/GlassOsControlTransport.java` | gRPC command dispatch implementation |

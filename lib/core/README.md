# lib:core

Platform-agnostic domain layer shared by all device implementations. Defines the protocol, command model, telemetry bus, and the abstract `Device` contract. Neither `lib:ifit1` nor `lib:ifit2` exist without this.

## Dependency rules

- **Depends on:** nothing (Java stdlib only)
- **Must never import:** Android framework classes (`android.*`), `lib:ifit1`, `lib:ifit2`

The no-Android constraint is the load-bearing one: it keeps all business logic testable on the JVM with plain JUnit — no Robolectric, no emulator. The build plugin is `com.android.library` (for AAR packaging), but the source must stay pure Java.

## Entry points

| Class | Role |
|---|---|
| `Device` | Abstract base for all fitness devices. Subclass this in ifit1 or ifit2. |
| `DeviceController` | Wires a `Device` to the command queue and telemetry bus. Owns the `CommandDispatcher` and `TelemetryHub.Subscription`. One per active device. |
| `TelemetryHub` | Process-wide fanout bus. Call `TelemetryHub.configure(reader)` once at startup with the platform-appropriate reader; everything else subscribes via `TelemetryHub.shared().subscribe(...)`. |
| `CommandDispatcher` | Throttled command queue (500 ms window, capacity 5). `DeviceController` owns one; rarely touched directly. |

`Device.Logger` and `Device.CommandExecutor` are functional interfaces defined here so that implementations can log and execute shell commands without importing Android classes.

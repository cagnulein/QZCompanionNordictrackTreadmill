# lib:ifit2

Preferred integration module for iFit Generation 2 / GlassOS consoles. It sends commands directly through the local GlassOS gRPC service — no `AccessibilityService` required. Proto definitions live in `src/main/proto/` and are compiled by the protobuf Gradle plugin at build time.

## Dependency rules

- **Depends on:** `lib:core`, gRPC stack (`grpc-okhttp`, `grpc-protobuf-lite`, `grpc-stub`)
- **Must never import:** `lib:ifit1`
- **Does not require** `AccessibilityService` — the only Android runtime requirement is network access

## Entry points

| Class | Role |
|---|---|
| `GrpcBikeDevice` | Base class for gRPC-controlled bikes. |
| `GrpcTreadmillDevice` | Base class for gRPC-controlled treadmills. |
| `GrpcCommandTransport` | Production `CommandTransport` implementation. Holds the gRPC channel; call `shutdown()` to release it. |
| `CommandTransport` | Interface (`apply` + `shutdown`). Implement this in tests to avoid a live gRPC connection. |
| `GrpcCredentials` | Provides the credentials required to authenticate with the iFit2 gRPC endpoint. |

## Key constraints

`CommandTransport` is the seam for testing — `GrpcDevice` depends on the interface, not the implementation, so tests can inject a fake transport without a live device. The generated proto/gRPC sources in `build/generated/` are build artifacts; never edit them directly.

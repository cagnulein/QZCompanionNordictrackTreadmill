# Contributing

QZ Companion is an Android app that bridges NordicTrack/ProForm fitness devices to the [QZ](https://github.com/cagnulein/qdomyos-zwift) ecosystem. It supports two iFit integration paths: iFit2 / GlassOS gRPC is the preferred path when available, while iFit1 / gesture + logcat remains supported as a legacy compatibility path for older consoles.

## Quick Start

```bash
git clone <repo>
cd QZCompanionNordictrackTreadmill
./run-tests.sh          # run the unit test suite
```

Open the `app/` directory in Android Studio to build and sideload to a device.

## Adding a Device

The most common contribution is support for a new treadmill or bike model. Prefer the iFit2/gRPC path when the target console runs GlassOS; add or modify iFit1 gesture devices only for older hardware that cannot use the gRPC path. Follow the step-by-step pattern in [CLAUDE.md](CLAUDE.md#new-device-implementation-pattern), then add tests following [testing-methodology.md](app/src/test/java/org/cagnulein/qzcompanionnordictracktreadmill/testing-methodology.md).

See [docs/architecture.md](docs/architecture.md) for a full picture of how the system fits together, and [docs/migrating-from-3x.md](docs/migrating-from-3x.md) for context on how the codebase evolved.

## Code Style

See [Code Style Guidelines](CLAUDE.md#code-style-guidelines) in CLAUDE.md. Key points:

- Comments only for non-obvious *why*, never *what*
- English only
- No multi-line comment blocks or docstrings

## Pull Requests

- One device (or one fix) per PR — keep scope narrow
- Tests are required for new devices
- Run `./run-tests.sh` before opening a PR

## Questions

Open a [GitHub Issue](../../issues) or start a [Discussion](../../discussions).

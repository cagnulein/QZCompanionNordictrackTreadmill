# QZ Companion NordicTrack Treadmill Fork

This repository is a development fork of QZ Companion for NordicTrack and ProForm iFit consoles. It builds on the original QZ Companion work by Al Udell and Roberto Viola / Cagnulein, and on QZ / qdomyos-zwift by Roberto Viola / Cagnulein.

The fork is now primarily a workbench and archive for two related paths:

- **iFit2 / GlassOS:** the preferred path. Commands and telemetry use the console's local GlassOS gRPC service, avoiding touchscreen gesture automation.
- **iFit1 legacy consoles:** the compatibility path. Commands are applied through Android Accessibility swipe gestures, and telemetry is read from iFit's `mono-stdout` logcat stream.

The focused public-app direction is [`fitzdircon`](docs/fitzdircon-project-plan.md): an iFit2 / GlassOS to Zwift Direct Connect app with no QZ mode, no iFit1 fallback, no calibration workflow, and no runtime ADB requirement.

## Current Direction

This fork is not intended to stay a broad user-facing product forever. Its value is that it contains the full investigation trail:

- legacy iFit1 gesture control and calibration lessons;
- the tested iFit2 / GlassOS gRPC command and telemetry path;
- Zwift Direct Connect discovery and adapter work;
- hardware runbooks, ride replay tests, and architecture docs.

`fitzdircon` is the intended clean public shape: one app that connects iFit2 bike and treadmill consoles directly to Zwift through Wahoo Direct Connect.

## What Works Today

- **QZ UDP bridge:** QZ can send speed, incline, and resistance targets to this app over UDP.
- **iFit2 / GlassOS gRPC:** iFit2 consoles can receive typed incline, resistance, and speed commands through the local GlassOS service, with telemetry coming back over gRPC subscriptions.
- **iFit1 Accessibility path:** older iFit1 consoles remain supported through swipe gestures and mono-stdout telemetry.
- **Zwift Direct Connect adapter:** the fork contains early Direct Connect support so Zwift can discover and connect over the network.
- **Calibration tooling:** legacy iFit1 devices can be calibrated with in-app and external tooling, but this is not planned for the standalone `fitzdircon` app.

## Should I Use This?

Use this fork if you are developing, testing, or studying the QZ Companion integration paths.

Use or wait for `fitzdircon` if your goal is simpler: an iFit2 / GlassOS bike or treadmill connected directly to Zwift without QZ, iFit1 support, calibration tools, or legacy install baggage.

For current users of the original QZ Companion flow, the old install instructions are still available in [docs/legacy-qz-companion-readme.md](docs/legacy-qz-companion-readme.md), but they are no longer the best front door for this fork's direction.

## Install And Runtime Notes

For this fork, ADB is useful for development, installation, upgrades, diagnostics, and some legacy support workflows. It should not be treated as the preferred runtime control path.

For the planned `fitzdircon` app:

- browser-based APK install is the recommended install/upgrade path;
- ADB is an optional fallback/support tool;
- normal riding should not require ADB to stay enabled;
- Direct Connect should be enabled by default.

See [docs/fitzdircon-install-improvements.md](docs/fitzdircon-install-improvements.md) for the install and upgrade plan.

## Documentation

- [Fork history](docs/fork-history.md): how this fork evolved from QZ Companion workbench to the `fitzdircon` direction.
- [Architecture](docs/architecture.md): current code structure and the iFit1/iFit2 data paths.
- [iFit2 control surface investigation](docs/ifit2-control-surface-investigation.md): GlassOS gRPC commands, telemetry, credentials, and limitations.
- [iFit1 control surface investigation](docs/ifit1-control-surface-investigation.md): why legacy consoles require touchscreen gesture injection.
- [Wahoo Direct Connect discovery](docs/wahoo-direct-connect-discovery.md): Direct Connect protocol findings and Zwift pairing notes.
- [Fresh public `fitzdircon` project plan](docs/fitzdircon-project-plan.md): planned standalone public repo and product shape.
- [iFit1 device reference](docs/ifit1-device-reference.md): legacy device formulas and screen-profile notes.
- [Migrating from 3.x](docs/migrating-from-3x.md): architectural migration notes for the 4.x fork work.

## Attribution

This fork is a derivative continuation of:

- QZ Companion NordicTrack Treadmill by Al Udell and Roberto Viola / Cagnulein.
- QZ / qdomyos-zwift by Roberto Viola / Cagnulein.

The goal of `fitzdircon` is not to replace those broad projects. It is a focused simplification for one path: iFit2 / GlassOS consoles talking directly to Zwift through Wahoo Direct Connect.

QZ, QZ Companion, qdomyos-zwift, iFit, Zwift, Wahoo, NordicTrack, and ProForm are names owned by their respective projects or companies. This fork and the planned `fitzdircon` project are not affiliated with iFit, Zwift, Wahoo, NordicTrack, or ProForm.

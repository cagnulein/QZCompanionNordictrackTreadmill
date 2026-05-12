# Fresh Public `fitzdircon` Project Plan

## Summary

Create a new public GitHub project named `fitzdircon` as the official app. Keep `michaelwo/QZCompanionNordictrackTreadmill` private as a development/archive fork. Import only the code needed for iFit2 / GlassOS to Zwift Direct Connect, preserving AGPL-3.0 licensing and explicit QZ/QZ Companion attribution.

## Background

`fitzdircon` should be understood as a focused continuation of QZ Companion's iFit work, not as a criticism of QZ or QZ Companion. QZ and QZ Companion made this work possible and remain broad, multi-device projects. `fitzdircon` narrows the problem to one path: iFit2 / GlassOS bike and treadmill consoles talking directly to Zwift through Wahoo Direct Connect.

Earlier iFit1 support was built around the only practical control surface available at the time: the manual workout UI. For a given console, speed, incline, and resistance swipe coordinates could be measured or calculated, and QZ Companion could drive those controls with Android Accessibility or ADB-style swipe commands. That approach worked for some devices, but it was inherently cumbersome and error-prone because it depended on the exact iFit version, screen resolution, control layout, active manual-workout state, current slider position, and the UI being visible and ready to receive input.

iFit2 / GlassOS changes that tradeoff. Its workout UI is substantially more complex than the older iFit1 manual workout screen, so carrying the same swipe-coordinate strategy forward would make an already fragile approach even harder to maintain. More importantly, iFit2 exposes a local GlassOS gRPC service for typed telemetry and control. `fitzdircon` uses that service directly instead of automating the visual UI.

The iFit2/gRPC findings should be easy for QZ and QZ Companion contributors to reuse: proto definitions, credential loading notes, telemetry mapping, command mapping, known limitations, and tested-device results should be documented clearly and offered back upstream where useful.

## Repository Strategy

- Create a new public repo: `michaelwo/fitzdircon`.
- Make the fork private once all three are true: `fitzdircon` is public, the first release APK is downloadable from GitHub Releases, and no external CI or download links point to the fork. Check `git remote -v`, GitHub Actions secrets, and any published install docs before flipping visibility.
- Use a fresh initial commit or small curated commit series rather than preserving the full fork history.
- Before the first commit, remove all QZ/QZ Companion product identity, protocol code, services, package paths, install scripts, screenshots, docs, metadata, tests, preferences, log files, and UI copy, except for explicit license/attribution references.
- Do not import private artifacts, credential-bearing remotes, old release binaries, legacy install packages, or excluded repo-local tooling.

## Code Import

- Start from the existing repo locally, but copy forward only:
  - Android app shell
  - `lib/core` command, telemetry, device, and Direct Connect code
  - `lib/ifit2` gRPC code and proto definitions
  - Direct Connect Android service and command bridge
  - boot/startup pieces that still make sense for a standalone Direct Connect app
- Exclude all code and concepts from the forked legacy paths, including:
  - `lib/ifit1`
  - `app/qz`
  - `lib/core/qz` — note: `DeviceController` currently depends on `QZCommandSubscriber` from this package; simply deleting the package will not compile. The QZ command entry point must be replaced with a Direct Connect equivalent before `lib/core/qz` can be removed. Treat this as a refactor task, not a directory exclusion.
  - calibration UI/tools
  - accessibility service/config
  - QZ UDP tests
  - iFit1 readers, `MonoStdoutTelemetryReader`, `DeviceRegistry`, sliders, gesture control, calibration runners/results/fits, `ScreenProfile`, raw swipe commands, legacy device profiles, and related docs/tests
- Exclude these directories/files from the new repo: `.devcontainer`, `.idea`, `proguard-rules.pro`, `build-download`, `calibration`, `fastlane`, `tools`, `asyncapi.yaml`, all `*.bat` files, and `SKILL.md`.
- Rename identity during import: app name `fitzdircon`, Gradle root name `fitzdircon`, package/applicationId `org.fitzdircon`.
- Change runtime log tags from `QZ:*` to `FZ:*`.

## Product Shape

- Set initial `versionName` to `0.1.0`.
- Treat `0.x` releases as beta until installed and confirmed running on several iFit2 bike and treadmill devices.
- Reserve `1.0.0` for the first release with confirmed multi-device bike and treadmill validation.
- First screen is operational, not marketing:
  - iFit2 / GlassOS availability
  - detected machine class
  - local IP
  - Direct Connect advertising state
  - Zwift client connection state
  - latest telemetry status
  - unsupported-console diagnostic when iFit2 is unavailable
- Direct Connect is enabled by default.
- No device picker, no iFit1 fallback, no QZ mode, no calibration mode.
- ADB is not required for runtime operation. Browser-based APK installation is the recommended install/upgrade path; ADB install scripts are optional fallback/support tooling only.

## CI/CD and Release Infrastructure

- Generate a release keystore once before the first public beta. Store it as GitHub Actions secrets: `KEYSTORE_BASE64` (base64-encoded `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Keep `applicationId` as `org.fitzdircon` permanently — changing it after a public release breaks APK-over-APK upgrades and loses user data.
- CI workflow (`.github/workflows/main.yml`) has two jobs:
  - **build**: triggered on every push and PR — runs `./gradlew :lib:core:testDebugUnitTest :lib:ifit2:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`.
  - **release**: triggered on tag push matching `v*` — runs a signed `assembleRelease`, computes SHA-256 of the APK, and publishes both the APK and the checksum to the GitHub Release for that tag.
- The published GitHub Release must include: signed APK, `SHA256SUMS.txt`, and a link to the matching source tag. This is required by AGPL-3.0 (corresponding source must be available for every distributed binary).

## Licensing And Attribution

- Keep AGPL-3.0 for `fitzdircon`.
- Add attribution for:
  - QZ Companion Nordictrack Treadmill by Al Udell and Roberto Viola / Cagnulein
  - QZ / qdomyos-zwift by Roberto Viola / Cagnulein
- State that `fitzdircon` is a derivative simplification focused on iFit2-to-Zwift Direct Connect.
- Include non-affiliation language for iFit, Zwift, Wahoo, NordicTrack, and ProForm.
- QZ/QZ Companion names may appear only in licensing, attribution, and derivative-work history.
- If APKs are distributed, ensure the corresponding source for that exact release is public in `fitzdircon`.

## Test And Verification Plan

- Bring over and update:
  - Direct Connect packet/profile/service-info tests
  - command dispatch tests
  - telemetry hub tests
  - iFit2 gRPC command/decode tests
- Remove QZ UDP, iFit1, calibration, accessibility, device-registry, slider, gesture, mono-stdout, and legacy profile tests.
- Add app startup tests for:
  - iFit2 available
  - iFit2 unavailable
  - Direct Connect starts by default
  - Direct Connect commands reach the active iFit2 device controller
  - log tags use `FZ:*`
- Add repository hygiene verification before first public commit:
  - `rg -n "QZCommand|QZMetric|QZTelemetry|org\.cagnulein|qz\." --glob '!LICENSE*' --glob '!NOTICE*' --glob '!ATTRIBUTION*' .` returns no matches (implementation artifacts fully removed).
  - `rg -n "QZ Companion|qdomyos-zwift" LICENSE NOTICE ATTRIBUTION 2>/dev/null` returns at least one match per file (attribution is present).
  - `rg -n "ifit1|MonoStdout|mono-stdout|DeviceRegistry|Slider|Gesture|AccessibilityService|Calibration|ScreenProfile|RawSwipe|QZCommand|QZTelemetry|QZMetric|QZCommandPacket|QZMetricPacket" .` returns no implementation/test/doc matches except intentional attribution/history notes.
  - `rg --files | rg "ifit1|qz|calibration|gesture|slider|screenprofile|device.?registry"` returns no imported legacy paths.
  - `rg --files | rg '(^|/)(\.devcontainer|\.idea|build-download|calibration|fastlane|tools)(/|$)|proguard-rules\.pro$|asyncapi\.yaml$|\.bat$|SKILL\.md$'` returns no matches.
- Verify build with:
  - `./gradlew :lib:core:testDebugUnitTest`
  - `./gradlew :lib:ifit2:testDebugUnitTest`
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :app:assembleDebug`

## Assumptions

- Fresh public repo is preferred over GitHub fork history.
- The private fork remains an archive/workbench, not the public source-of-truth for releases.
- `fitzdircon` remains AGPL-3.0 because it imports derived code.
- Initial release prioritizes a clean, compiling iFit2 Direct Connect beta app over preserving old docs, screenshots, or installer polish.

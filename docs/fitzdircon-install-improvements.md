# `fitzdircon` Install And Upgrade Improvements Plan

## Summary

Improve installation and upgrades around the standalone iFit2 / GlassOS Direct Connect app by making browser-based APK install the primary user path, keeping ADB as an advanced fallback only, and adding release/update diagnostics that help users confirm the console is ready for Zwift.

## Key Changes

- Publish every release with a stable signed APK, matching source tag, SHA-256 checksum, and short release notes.
- Keep the Android `applicationId` and signing key stable from the first public beta so APK-over-APK upgrades preserve app data.
- Provide a short stable download URL and QR code that open the latest GitHub release APK from the console browser.
- Document one primary path: download APK in the console browser, allow installs from that browser source, install or upgrade, then launch `fitzdircon`.
- Keep ADB as optional fallback/support tooling only: connect, install with `adb install -r`, launch the app, and collect diagnostics when needed.
- Remove legacy privileged-permission expectations from user docs. Runtime operation should not require ADB grants, Accessibility, `WRITE_SECURE_SETTINGS`, `READ_LOGS`, overlay permission, or broad storage access.

## In-App Experience

- Add an operational first-run and post-upgrade status screen showing iFit2 / GlassOS availability, credential discovery, local IP, Direct Connect advertising, Zwift connection state, and latest telemetry.
- Add an update status section showing installed version, latest known release, release notes link, APK download link, source link, and checksum.
- When an update is available, open the browser/package installer flow rather than attempting silent self-update, since silent installation requires device-owner, root, or system privileges.
- Show clear unsupported-console diagnostics when iFit2 packages or the local GlassOS gRPC service are unavailable.

## Test Plan

- Verify fresh browser install on at least one iFit2 bike and one iFit2 treadmill.
- Verify APK-over-APK upgrade preserves preferences and starts Direct Connect by default.
- Verify ADB fallback install works with `adb install -r` and launches the app.
- Verify the app reports correct status after fresh install, upgrade, missing iFit2 packages, and unreachable GlassOS service.
- Verify published APK checksum matches release metadata and the tagged public source corresponds to the binary release.

## Assumptions

- GitHub Releases are the initial public distribution channel.
- Browser APK install is the recommended path because it avoids requiring users to enable or maintain ADB.
- ADB remains useful for troubleshooting, log capture, and recovery installs, but not for normal riding or Direct Connect operation.
- Automatic silent updates are out of scope unless the app later becomes device-owner, root-managed, or system-installed.

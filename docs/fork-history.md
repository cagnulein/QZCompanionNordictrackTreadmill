
# Fork History: From QZ Companion Workbench To FitzDirCon

## Background 

This work begins with my aunt asking me to join zwift riding group over Christmas dinner in 2025. I had an Noridic Track s22i collecting dust in my basement, and she made me promise that I’d get it setup so I could ride with her. Started exploring options, and got QZ connecting with Zwift in late Jan. First ride with the group was in February. Rode several times over Feb and March, but started to notice how the incline changes in Zwift weren’t translating over to my bike correctly. Dug into the QZ / QZ Companion repos and saw other folks reporting having similar issues, but it wasn’t quite clear how or if they had them solved.  The QZ app itself supports a ton of devices, has a ton of features, but the Nordic Track integration looked like a shim that did not have much contributor activity, so I figured I’d see if I could fix it myself and evaluate claude code in the process. 

This history starts with fork-era work in April 2026. It is not a full retelling of the upstream QZ Companion NordicTrack Treadmill project, which began years earlier and made this work possible. The fork inherited a working but broad Android companion app: QZ sent control targets over UDP, QZ Companion translated those targets into iFit console actions, and many device-specific coordinate fixes had accumulated over time.

The fork changed the shape of the project. It started as a stabilization and testing effort around a real NordicTrack S22i bike, then became a deeper architectural pass, then uncovered a cleaner iFit2 / GlassOS gRPC path, and finally pointed toward a focused public app: `fitzdircon`.

## April 7-14, 2026: Making The Existing Path Testable

The fork began by turning the existing command path into something that could be reasoned about. The first major fork commit extracted a device model, added `CommandDispatcher`, and brought in a large test baseline. That set the tone: before chasing more devices or features, the fork needed a stable vocabulary for commands, devices, dispatch, and verification.

CI was tightened almost immediately. The build moved to Java 17 for modern Android Gradle Plugin compatibility, fork builds stopped assuming upstream signing secrets, unit tests ran before release assembly, and test results became part of the normal feedback loop. This mattered because the fork was no longer just a local experiment; it was a workbench that needed to survive repeated hardware-driven changes.

The first hard hardware lesson came from the S22i. ADB loopback authorization was unreliable or blocked on stock firmware, so the fork added an S22i "No ADB" path using `AccessibilityService.dispatchGesture()` for swipe injection. That was not yet the final destination, but it removed a major runtime dependency for one important device.

The S22i work also exposed how fragile pixel-level iFit1 control could be. Zwift sent small grade changes, while iFit snapped the physical UI to 0.5% increments. The fork added quantization, de-duplication thresholds, and snap-aware incline handling so small route jitter would not turn into redundant or ineffective swipes. Long-ride stability work followed: WakeLock fixes, thread cleanup, resource leak fixes, and less chatty network updates.

## April 15-19, 2026: Devices, Sliders, And Observability

Once the command path was testable, the fork moved behavior into the objects that owned it. `Device` absorbed snapshot and dispatch-related state, scattered `instanceof` checks were removed, and a `Slider` abstraction replaced repeated axis logic. This was more than cleanup: it made incline, resistance, and speed feel like real domain concepts instead of loose coordinate math.

During this period the app became easier to debug during real rides. Version and build information appeared in the UI, logs moved from an unbounded in-memory string to a ring buffer, and persistent logs were written with rotation. The device picker was rebuilt with `RecyclerView`, and monitor/analyze scripts were hardened around the actual logcat tags and ride diagnostics.

Testing expanded from helper coverage to route-level confidence. The fork added end-to-end ride replay tests and formula coverage across the device catalog. That let calibration and refactors move faster without guessing whether a device's coordinate path had been broken.

Calibration became a serious theme. The S22i incline and resistance formulas were recalibrated against live behavior, with experiments around OCR and in-app calibration. Some of that work was later replaced, but it established the important pattern: the fork would use telemetry and hardware feedback to correct screen automation rather than treating formulas as static guesses.

The local Claude workflow also shaped the code here. Design conversations pushed toward contributor clarity, including typed slider concepts for incline, resistance, and speed. The point was not abstraction for its own sake; it was to make the code read like the problem domain so future contributors could understand where behavior belonged.

## April 21-28, 2026: The 4.x Shape And Runtime ADB Retirement

The next phase made the app feel like an operational tool. `MainActivity` was redesigned around a status chip, requirements card, and clearer device selection. Documentation was refreshed to explain the full Zwift loop: Zwift to QZ, QZ to QZ Companion, QZ Companion to the machine, telemetry back to QZ, and then back to Zwift.

The fork formalized screen-coordinate knowledge with `ScreenProfile`, anchoring track positions to the iFit APK 2.6.90 layout instead of scattering magic values through device classes. Validation tooling checked swipe targets against those profiles, and docs were updated so formula maintenance was auditable.

Runtime ADB was then removed from the normal iFit1 control path. Devices moved to AccessibilityService-based swipes, while shell and file dependencies were pushed out of the hot path. That decision simplified the mental model: ADB could still help install, inspect, or calibrate, but riding should not depend on an active ADB control channel.

Telemetry delivery also matured. The app moved from LAN broadcast toward unicast once QZ was discovered, while still broadcasting enough for discovery. The rename from "broadcasting" to "unicasting" captured the actual behavior and made the architecture easier to describe.

Auto-discovery and OCR were explored heavily in this window. The fork tried screen capture and ML Kit OCR, hit practical memory and lifecycle problems, then replaced that path with mono-stdout telemetry for calibration-like discovery. The history matters because the project did not simply accumulate features; it tested ideas, kept what worked, and removed what did not.

## May 1-6, 2026: Calibration And Workflow Discipline

At the start of May, calibration became both an external contributor tool and an in-app workflow. `discover-device.py` gained ADB-based and `--a11y` modes, then improved its probes, quality gates, stability waits, snap filtering, and resistance sweeps. The `--a11y` mode could auto-configure QZ Companion so a contributor did not have to manually wire every setting before collecting useful data.

The app briefly gained privileged startup behavior to rebind Accessibility using `WRITE_SECURE_SETTINGS`, but that also sharpened the distinction between runtime requirements and setup support. Later `fitzdircon` planning would carry that lesson forward: ADB and privileged grants should not be part of normal riding.

OCR and AutoDiscover code were removed once the mono-stdout and calibration paths proved cleaner. Calibration classes moved into the iFit1 console area, docs were updated, and regression tests checked calibration results against expected formula tolerances.

The domain model continued to tighten. `QZCommandPacket` and `QZMetricPacket` clarified the UDP boundary, while internal telemetry moved toward domain objects instead of letting packet shapes leak into device logic. The project also moved toward typed commands, `DeviceController`, and clearer subscriber relationships.

This period also captured the fork's working discipline. The Claude `/ship` workflow required running the test suite, bumping `version.properties`, committing selectively, pushing explicitly to the `fork` remote, and confirming CI. That last detail came from a real workflow correction: bare `git push` could target upstream `origin`, so fork work had to push to `michaelwo/QZCompanionNordictrackTreadmill` deliberately.

## May 7-9, 2026: The iFit2 Breakthrough

The biggest technical turn came when the fork stopped treating screen automation as the best possible path for new consoles. iFit2 / GlassOS exposed a local gRPC service on the console, and the fork added a real GlassOS command transport and telemetry reader.

The investigation showed that GlassOS could accept typed incline, resistance, and speed commands through gRPC, and could stream live telemetry through async subscriptions. The gRPC server was loopback-only and protected by mutual TLS, so the fork added runtime credential discovery from the installed Rivendell APK. It learned to scan `resources.arsc`, resolve obfuscated raw resources, identify the correct certificate by common name, match the private key, and cache resource names by APK version without hardcoding key material.

That discovery changed the architecture. iFit1 became the legacy gesture-plus-logcat path for older consoles. iFit2 became the preferred path: typed commands over gRPC, telemetry over gRPC, no pixel formulas, no Accessibility gesture injection, and no active workout requirement before app launch as long as the GlassOS service was running when QZ Companion started.

The deploy runbook captured this as repeatable hardware proof. A clean build avoided stale `GIT_HASH`, the app was installed on the S22i, the installed version was verified, iFit2 was foregrounded, `GlassOsTelemetryReader` was confirmed in logs, and UDP smoke tests drove incline and resistance through the GlassOS path. That was an important milestone: the fork could prove the full loop on real hardware, not only in unit tests.

The package structure changed to match the new reality. iFit1 slider-backed devices were separated from shared device code, iFit2 devices moved toward a platform-neutral base, command routing went through platform handlers, then command dispatch was unified with telemetry. Finally the code was extracted into Gradle modules: `lib/core`, `lib/ifit1`, and `lib/ifit2`.

## Direct Connect And The FitzDirCon Direction

The next product-level discovery was Wahoo Direct Connect. If QZ Companion could present a Zwift-facing Direct Connect trainer over the network, then the iFit2 path no longer needed to daisy-chain through QZ for the core use case. The fork added a Zwift Direct Connect adapter and documented the discovery work.

That changed the question from "how do we keep improving QZ Companion?" to "what is the smallest useful app for iFit2 owners who want Zwift?" Several names were explored, including `ifwift` and `fitzcon`, before landing on `fitzdircon`: a compact name for iFit-to-Zwift through Direct Connect.

The intended public project narrowed sharply:

- iFit2 / GlassOS only.
- Zwift Direct Connect enabled by default.
- No iFit1 gesture path.
- No QZ UDP mode.
- No calibration workflow.
- No device picker.
- Runtime log tags renamed from `QZ:*` to `FZ:*`.
- Public AGPL-3.0 source with explicit QZ and QZ Companion attribution.

The tone of that plan matters. `fitzdircon` is not framed as a criticism or a competitor of QZ, QZ Companion, or qdomyos-zwift. Those projects made the work possible and remain broader tools. The fork's contribution is narrower: document and package the iFit2 / GlassOS gRPC findings clearly enough that iFit2 owners can use them directly and QZ contributors can reuse them upstream if useful.

## Where The Fork Stands

By May 2026, the fork had become more than a collection of device coordinate patches. It had a tested command pipeline, structured telemetry, iFit1 legacy support, iFit2 gRPC support, hardware deployment runbooks, calibration tooling, Direct Connect infrastructure, and a clear extraction plan.

The fork remains useful as a private workbench and archive because it contains the full path: legacy iFit1 lessons, calibration experiments, QZ integration, and the investigation trail that led to GlassOS gRPC. `fitzdircon` is the next product shape: a focused public app that carries forward only the iFit2-to-Zwift Direct Connect path.

The story of the fork is therefore a narrowing story. It began by making a fragile broad app safer. It learned which parts were essential and which parts were historical baggage. It ended with a simpler target: typed local control from iFit2 to Zwift, with as little machinery in the middle as possible.

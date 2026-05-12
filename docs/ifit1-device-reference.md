# iFit1 Device Reference

This document covers the legacy iFit1 gesture devices only. iFit2 / GlassOS devices should use the preferred gRPC integration path and do not need pixel-coordinate formulas.

All coordinates are screen pixels on the iFit1 touch display, standardised to **iFit APK 2.6.90** (versionCode 4963, `com.ifit.standalone`). TrackX values are derived from the APK layout resources and expressed via the `ScreenProfile` enum in source — see `device/ScreenProfile.java`. If the iFit1 app is updated to a version with different slider geometry, re-derive the constants from the new APK before editing iFit1 device classes. Swipe format:

```
input swipe <trackX> <fromY> <trackX> <targetThumbY(v)> 200
```

Swipe duration is always 200 ms. `fromY` is the slider's current tracked position; `targetThumbY(v)` is computed from the formula for the requested value `v`. Sliders constructed with the `.live()` factory override `currentThumbY()` to re-derive the starting position from the slider's internally tracked live metric value before each swipe, correcting drift.

For the full dispatch pipeline see [architecture.md](architecture.md). For how to add a device and write formula tests see the steps at the bottom of this file and [testing-methodology.md](../app/src/test/java/org/cagnulein/qzcompanionnordictracktreadmill/testing-methodology.md).

---

## iFit1 Devices at a Glance

All 44 iFit1 devices use `AccessibilityService` for swipe injection and `MonoStdoutTelemetryReader` for metric reading.

| DeviceId | Display name |
|----------|-------------|
| `s15i` | S15i Bike |
| `s22i` | S22i Bike |
| `s22i_NTEX02121_5` | S22i Bike (NTEX02121.5) |
| `s22i_NTEX02117_2` | S22i Bike (NTEX02117.2) |
| `s27i` | S27i Bike |
| `NTEX71021` | NTEX71021 Bike |
| `tdf10` | TDF10 Bike |
| `tdf10_inclination` | TDF10 Bike (Inclination) |
| `proform_studio_bike_pro22` | ProForm Studio Bike Pro 2.2 |
| `proform_carbon_e7` | ProForm Carbon E7 Bike |
| `proform_carbon_c10` | ProForm Carbon C10 Bike |
| `custom_calibrated` | Custom (Calibrated) |
| `x9i` | X9i Treadmill |
| `x11i` | X11i Treadmill |
| `x14i` | X14i Treadmill |
| `x22i` | X22i Treadmill |
| `x22i_v2` | X22i V2 Treadmill |
| `x32i` | X32i Treadmill |
| `x32i_NTL39019` | X32i Treadmill (NTL39019) |
| `x32i_NTL39221` | X32i Treadmill (NTL39221) |
| `c1750` | C1750 Treadmill |
| `c1750_2020` | C1750 Treadmill (2020) |
| `c1750_2020_kph` | C1750 Treadmill (2020 KPH) |
| `c1750_mph_minus3grade` | C1750 Treadmill (MPH -3% Grade) |
| `c1750_NTL14122_2_MPH` | C1750 Treadmill (NTL14122.2 MPH) |
| `c1750_2021` | C1750 Treadmill (2021) |
| `t65s` | T6.5s Treadmill |
| `t75s` | T7.5s Treadmill |
| `t85s` | T8.5s Treadmill |
| `t95s` | T9.5s Treadmill |
| `s40` | S40 Treadmill |
| `nordictrack_2450` | NordicTrack 2450 Treadmill |
| `nordictrack_2950` | NordicTrack 2950 Treadmill |
| `nordictrack_2950_maxspeed22` | NordicTrack 2950 Treadmill (Max Speed 22) |
| `proform_2000` | ProForm 2000 Treadmill |
| `proform_carbon_t14` | ProForm Carbon T14 Treadmill |
| `proform_pro_9000` | ProForm Pro 9000 Treadmill |
| `proform_pro_2000` | ProForm Pro 2000 Treadmill |
| `grand_tour_pro` | Grand Tour Pro Treadmill |
| `elite900` | Elite 900 Treadmill |
| `elite1000` | Elite 1000 Treadmill |
| `exp7i` | EXP 7i Treadmill |
| `se9i_elliptical` | SE9i Elliptical |
| `other` | Other Treadmill |

---

## Command Execution Modes

All 44 iFit1 devices use `GestureService.performSwipe()`. Enable the **QZCompanion** accessibility service in **Settings → Accessibility** before using any iFit1 gesture device.

---

## Bikes

Bike UDP message: `"inclinePct;?"` (2-part) or `"resistanceLvl"` (1-part). Sentinels `-1` and `-100` are discarded.

### S15i (`s15i`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Incline | 75 | 616 | `616 - (int)(v * 17.65)` | live |
| Gear | 1845 | 790 | `790 - (int)(v * 23.16)` | live |

---

### S22i (`s22i`)

| Slider | trackX | Initial Y | Formula | Quantize | currentThumbY | Hysteresis |
|--------|--------|-----------|---------|----------|---------------|------------|
| Incline | 75 | 622 | `v≤0: (int)(622 - 10*v)` / `v>0: (int)(622 - 18.57*v)` | 0.5% steps | live | travel ≥40px → 15px; <40px → 10px |
| Resistance | 1845 | 724 | `(int)(724 - 401.0/23 * (v-1))` | integer levels | live (when ≥1) | — |

**Calibration (2026-05-01, 1920×1080, `discover-device.py --a11y`):** Incline: 12-point coarse sweep, R²=1.0000 — `Y = 600 − 20.0 × grade`. Resistance: 6-point coarse sweep, R²=1.0000 — `Y = 750 − 25.0 × (level − 1)`. The hardcoded `S22iDevice` formula above (origin 622/724, scale 18.57/17.43) is from an earlier manual measurement and remains the static fallback; `CalibratedBikeDevice` uses the discover-device.py values when `qz-calibration.json` is present.

Hysteresis compensates for physical slider stiction: swipe overshoots `targetThumbY` in the direction of travel; `thumbY` tracks the logical target so de-dup is unaffected. The live-mode incline slider re-derives its position from the iFit-reported grade before each swipe, correcting residual drift.

---

### S22i (NTEX02121.5) (`s22i_NTEX02121_5`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Incline | 75 | 800 | `800 - (int)((v + 10) * 19)` | live |

---

### S22i (NTEX02117.2) (`s22i_NTEX02117_2`)

Shares all slider geometry with `S22iDevice`.

---

### S27i (`s27i`)

Range: incline −10%..+20% → Y 803..248; resistance levels 1..24 → Y 803..248.

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Incline | 75 | 803 | `803 - (int)((v + 10) * (803-248) / 30.0)` | live |
| Resistance | 1845 | 803 | `803 - (int)((v - 1) * (803-248) / 23.0)` | live |

---

### NTEX71021 (`NTEX71021`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Resistance | 950 | 493 | `(int)(493 - 13.57 * v)` |

---

### TDF10 (`tdf10`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Resistance | 1205 | 604 | `(int)(619.91 - 15.913 * v)` |

---

### TDF10 Inclination (`tdf10_inclination`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Incline | 75 | 482 | `(int)(-12.499 * v + 482.2)` |

---

### ProForm Studio Bike Pro 2.2 (`proform_studio_bike_pro22`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Resistance | 1845 | 805 | `(int)(826.25 - 21.25 * v)` |

---

### ProForm Carbon E7 (`proform_carbon_e7`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Incline | 74 | 440 | `440 - (int)(v * 11)` | live |
| Resistance | 950 | 440 | `440 - (int)(v * 9.16)` | live |

---

### ProForm Carbon C10 (`proform_carbon_c10`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Resistance | 1205 | 632 | `632 - (int)(v * 18.45)` | live |

---

### Custom (Calibrated) (`custom_calibrated`)

`CalibratedBikeDevice` — formula derived at runtime from `DeviceCalibration` loaded from `/sdcard/qz-calibration.json`, which is written by the in-app guided calibration flow or by the external `tools/discover-device.py` fallback. Falls back to `DeviceCalibration.load(SharedPreferences)` (legacy, incline-only) if the file is absent. See `tools/discover-device-runbook.md` for the external fallback.

---

## Treadmills

Treadmill UDP message: `"speedKmh;inclinePct"` (2 parts). Speed commands are gated in `SpeedSlider`: no swipe fires while the belt is stopped (`liveValueOrZero() <= 0`); the pending speed is held in a one-slot cache and applied the moment `KPH > 0` arrives.

### X9i (`x9i`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Speed | 725 | 332 | `(int)(345.6315 - 13.6315 * v)` |
| Incline | 73 | 332 | `(int)(311.91 - 3.3478 * v)` |

---

### X11i (`x11i`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Speed | 1205 | 600 | `(int)(621.997 - 21.785 * v)` |
| Incline | 75 | 557 | `(int)(565.491 - 8.44 * v)` |

---

### X14i (`x14i`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1845 | 807 | `807 - (int)((v - 1.0) * 31)` | live |
| Incline | 75 | 645 | lookup table (`INCLINE_TABLE`) | live |

---

### X22i (`x22i`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Speed | 1845 | 785 | `(int)(785 - 23.636 * v)` |
| Incline | 75 | 785 | `(int)(785 - 11.304 * (v + 6))` |

---

### X22i V2 (`x22i_v2`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Speed | 1845 | 838 | `(int)(838 - 26.136 * v)` |
| Incline | 75 | 742 | `(int)(742 - 11.9 * (v + 6))` |

---

### X32i (`x32i`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Speed | 1845 | 927 | `(int)(834.85 - 26.946 * v)` |
| Incline | 75 | 881 | `(int)(734.07 - 12.297 * v)` |

---

### X32i (NTL39019) (`x32i_NTL39019`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Speed | 1845 | 779 | `(int)(817.5 - 42.5 * v * 0.621371)` *(mph input scaled to km/h)* |
| Incline | 75 | 749 | `(int)(749 - 11.8424 * v)` |

---

### X32i (NTL39221) (`x32i_NTL39221`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1845 | 579 | `(int)(900.26 - 46.63 * v * 0.621371)` *(mph scaled)* | live |
| Incline | 75 | 750 | `750 - (int)(v * 12.05)` | live |

---

### C1750 (`c1750`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1845 | 785 | `785 - (int)((v - 1.0) * 31.42)` | live |
| Incline | 75 | 700 | `(int)(700 - 34.9 * v)` | — |

---

### C1750 (2020) (`c1750_2020`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1205 | 575 | `575 - (int)((v * 0.621371 - 1.0) * 28.91)` *(mph scaled)* | live |
| Incline | 75 | 520 | `520 - (int)(v * 20)` | live |

---

### C1750 (2020 KPH) (`c1750_2020_kph`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1205 | 598 | lookup table | live |
| Incline | 75 | 525 | lookup table (`INCLINE_TABLE`) | live |

---

### C1750 (MPH -3% Grade) (`c1750_mph_minus3grade`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1205 | 603 | `603 - (int)((v * 0.621371 - 0.5) * 21.722)` *(mph scaled)* | live |
| Incline | 75 | 603 | `603 - (int)((v + 3.0) * 21.722)` | live |

---

### C1750 (NTL14122.2 MPH) (`c1750_NTL14122_2_MPH`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1845 | 787 | `787 - (int)(v * 43.5)` | live |
| Incline | 75 | 787 | `787 - (int)((v + 3) * 29)` | live |

---

### C1750 (2021) (`c1750_2021`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1205 | 620 | `620 - (int)((v - 1.0) * 20.73)` | live |
| Incline | 75 | 553 | `(int)(553 - 22 * v)` | — |

---

### T6.5s, T7.5s, Grand Tour Pro

These three devices share the `T65sDevice` geometry — only the display name differs.

| DeviceId | Display name |
|----------|-------------|
| `t65s` | T6.5s Treadmill |
| `t75s` | T7.5s Treadmill |
| `grand_tour_pro` | Grand Tour Pro Treadmill |

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Speed | 1205 | 495 | `(int)(578.36 - 35.866 * v * 0.621371)` *(mph scaled)* |
| Incline | 75 | 585 | `(int)(576.91 - 34.182 * v)` |

---

### T8.5s (`t85s`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1205 | 609 | `(int)(629.81 - 20.81 * v)` | live |
| Incline | 75 | 609 | `(int)(609 - 36.417 * v)` | live |

---

### T9.5s (`t95s`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1845 | 847 | `847 - (int)(30.0 * v)` | live |
| Incline | 75 | 846 | `846 - (int)(46.0 * v)` | live |

---

### S40 (`s40`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Speed | 950 | 507 | `(int)(507 - 12.5 * v)` |
| Incline | 74 | 490 | `(int)(490 - 21.4 * v)` |

---

### NordicTrack 2450 (`nordictrack_2450`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1845 | 807 | `(int)(-26.33 * (v * 0.621371) + 831.39)` *(mph scaled)* | live |
| Incline | 75 | 715 | `715 - (int)((v + 3) * 29.26)` | live |

---

### NordicTrack 2950 (`nordictrack_2950`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1845 | 807 | `807 - (int)((v - 1.0) * 31)` | live |
| Incline | 75 | 807 | `807 - (int)((v + 3) * 31.1)` | live |

---

### NordicTrack 2950 (Max Speed 22) (`nordictrack_2950_maxspeed22`)

Same incline geometry as `nordictrack_2950`. Speed formula adjusted for lower maximum:

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1845 | 682 | `682 - (int)((v - 1.0) * 26.5)` | live |
| Incline | 75 | 807 | `807 - (int)((v + 3) * 31.1)` | live |

---

### ProForm 2000 (`proform_2000`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1205 | 598 | `(int)(631.03 - 19.921 * v)` | — |
| Incline | 75 | 520 | `520 - (int)((v + 3) * 21.804)` | live |

---

### ProForm Carbon T14 (`proform_carbon_t14`)

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Speed | 1845 | 810 | `(int)(810 - 52.8 * v * 0.621371)` *(mph scaled)* |
| Incline | 75 | 844 | `(int)(844 - 46.833 * v)` |

---

### ProForm Pro 9000 (`proform_pro_9000`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1845 | 800 | `800 - (int)((v * 0.621371 - 1.0) * 41.667)` *(mph scaled)* | live |
| Incline | 75 | 720 | `720 - (int)(v * 34.583)` | live |

---

### Elite 1000 and ProForm Pro 2000

Both share `Elite1000Device` geometry — only the display name differs.

| DeviceId | Display name |
|----------|-------------|
| `elite1000` | Elite 1000 Treadmill |
| `proform_pro_2000` | ProForm Pro 2000 Treadmill |

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 1205 | 600 | `600 - (int)(v * 0.621371 * 31.33)` *(mph scaled)* | live |
| Incline | 75 | 589 | `589 - (int)(v * 32.8)` | live |

---

### Elite 900 (`elite900`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 950 | 450 | `450 - (int)(v * 14.705)` | live |
| Incline | 74 | 450 | `450 - (int)(v * 20.83)` | live |

---

### EXP 7i (`exp7i`)

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Speed | 950 | 453 | `453 - (int)((v * 0.621371 - 1.0) * 22.702)` *(mph scaled)* | live |
| Incline | 74 | 442 | `442 - (int)(v * 21.802)` | live |

---

## Other

### SE9i Elliptical (`se9i_elliptical`)

Physically an elliptical but treated as a bike (extends `BikeDevice`). Incline range 0–20 levels → Y 858..208; resistance levels 1–24 → Y 858..208.

| Slider | trackX | Initial Y | Formula | currentThumbY |
|--------|--------|-----------|---------|---------------|
| Incline | 75 | 858 | `858 - (int)(v * (858-208) / 20.0)` | live |
| Resistance | 1845 | 858 | `858 - (int)((v-1) * (858-208) / 23.0)` | live |

---

### Other Treadmill (`other`)

Fallback device. Uses ProForm 2000 geometry without `currentThumbY`. Useful for initial testing on unknown hardware.

| Slider | trackX | Initial Y | Formula |
|--------|--------|-----------|---------|
| Speed | 1205 | 0 | `(int)(631.03 - 19.921 * v)` |
| Incline | 75 | 0 | `(int)(520.11 - 21.804 * v)` |

---

## Adding a New iFit1 Device

Prefer the iFit2/gRPC path when the target console runs GlassOS. Add a new iFit1 gesture device only for older hardware that cannot use the gRPC path.

1. Create a class in `device/bike/` (extends `BikeDevice`) or `device/treadmill/` (extends `TreadmillDevice`).
2. Declare `private static int offsetInclineThumbY(double v)` (and `offsetSpeedThumbY` / `offsetResistanceThumbY` as needed). Pass one or two typed slider instances to `super()`: `new InclineSlider(ScreenProfile.Wxxx.leftTrackX, ORIGIN_INCLINE_THUMBY, MyDevice::offsetInclineThumbY)` for the incline axis; `new SpeedSlider(...)` for speed; `new ResistanceSlider(...)` for resistance; `new GearSlider(...)` for gear-based bikes. Choose the `ScreenProfile` that matches the device's screen width (W1920, W1280, W1024, W800). Set `ORIGIN_xxx_THUMBY` equal to `offsetXxxThumbY(0)` (the formula's y-intercept). When `quantize()`, `currentThumbY()`, or `hysteresisPixels()` also need overriding, use an anonymous typed-slider subclass instead.
3. Override `displayName()`. There is no `requiresAdb()` or `requiresAccessibility()` to implement — all iFit1 devices use `AccessibilityService` exclusively and `Slider.moveTo()` handles gesture dispatch.
4. Add a `DeviceId` enum value to `DeviceRegistry.DeviceId` and a `m.put(DeviceId.my_device, new MyDevice())` line in `DeviceRegistry.DEVICES`.
5. Add the `DeviceId` to the appropriate list in `DeviceAdapter` — `BIKE_DEVICES`, `TREADMILL_DEVICES`, or `OTHER_DEVICES`. The UI is not automatic; if you skip this step the device will not appear in the app.
6. Add formula tests in `BikeDeviceTest` or `TreadmillDeviceTest`. See [testing-methodology.md](../app/src/test/java/org/cagnulein/qzcompanionnordictracktreadmill/testing-methodology.md) for the pattern.

See `S22iDevice` + `BikeDevice` for the canonical bike example and `X11iDevice` + `TreadmillDevice` for treadmill.

---

## Deriving Slider Formulas

The formula `offsetXxxThumbY(v)` is a linear mapping: `Y = a - b * v`.

From two known (value, Y) pairs:
```
b = (Y1 - Y2) / (v2 - v1)
a = Y1 + b * v1
```

For more than two points, use `tools/discover-device.py` — it runs a full ADB sweep, fits the formula via least-squares, reports R², and writes `qz-calibration.json` directly. See `tools/discover-device-runbook.md`.

To observe current slider positions, take a screenshot: `adb shell screencap -p /sdcard/screen.png`.

---

## Validating Slider Coordinates Against the APK Layout

All `trackX` and `offsetXxxThumbY()` values can be cross-checked against the iFit APK's Android layout resources without hardware. Run:

```bash
python3 tools/discover-device/validate_swipe_targets.py
```

Exit code 0 means all checks pass. The script reads the decoded APK (`ifit_decoded/res/`) and all device Java files.

### How the validator derives expected trackX values

Constants are anchored to **iFit APK 2.6.90** (versionCode 4963). The iFit workout HUD (`inworkouttablet.xml`) positions slider containers using two dimensions from the APK:

```
workout_slider_margin =  12.0 dp   (gap between screen edge and slider container)
workout_slider_width  = 125.0 dp   (container width; track is centred at 50%)
```

At the iFit tablet's density (1 dp = 1 px on all tested models), the expected track centres are:

| Slider side | Expected trackX | Derivation |
|-------------|----------------|------------|
| Left (incline / speed) | ≈ 75 px | `12 + 125 / 2 = 74.5 dp` |
| Right (resistance / speed) | screen\_width − 75 px | e.g. `1920 − 74.5 = 1845.5 → 1845 px` |

The right-slider trackX also implies the device's screen width:

| right trackX | Inferred screen width | Devices |
|---|---|---|
| ~725  | 800 px  | X9i |
| ~950  | 1024 px | Elite 900, EXP 7i, NTEX71021, ProForm Carbon E7, S40 |
| ~1205 | 1280 px | C1750 (2020/2021), ProForm 2000, T6.5s family, X11i, TDF10, ProForm Carbon C10 |
| ~1845 | 1920 px | S22i, S27i, X22i/X32i family, C1750, NordicTrack 2450/2950, ProForm Carbon T14, T9.5s, X14i |

### Checks the validator runs

| Check | What it verifies |
|-------|-----------------|
| **A — Horizontal position** | `trackX ≈ 74.5` for left sliders; right `trackX + 74.5` lands on a recognised screen width (±15 px) |
| **B — Initial thumb vs. formula** | `initialThumbY` passed to the typed slider constructor should equal `formula(0)` (the ORIGIN constant) for incline/speed sliders |
| **C — Monotonicity** | Higher metric values produce lower Y (slider moves up); inverted slopes are flagged |
| **D — Sindarin global bounds** | `offsetXxxThumbY()` at `MaxIncline=40°`, `MinIncline=−20°`, and practical max speed 22 km/h must land within `[0, screen_height]` |

### Known anomalies

All trackX values are now standardised to iFit APK 2.6.90 (`ScreenProfile` constants). Devices that previously had hardware-calibrated trackX values deviating from the APK layout (ProForm Pro 9000, ProForm Studio Bike Pro 2.2, SE9i Elliptical, and others) have been updated. The original calibrated values are preserved in constructor comments in the Java source for reference.

#!/usr/bin/env python3
"""
discover-device.py — external fallback incline + resistance calibration for iFit bikes.

QZCompanion now has an in-app guided calibration flow. Prefer the in-app
Calibrate action for normal device setup. Keep this ADB-based script for
contributors, recovery, unattended validation, and comparing external results
against the in-app runner.

Sweeps both sliders, reads grade/resistance events from logcat -s mono-stdout,
fits Y = origin - scale * value for each slider, and writes qz-calibration.json.
Push the file to /sdcard/ and restart QZCompanion to apply when using this
external fallback.

Two sweep modes:
  default   -- adb shell input swipe (works on Android 11+, non-Xamarin iFit)
  --a11y    -- routes swipes via QZCompanion's AccessibilityService over UDP port
               8003 (CALSWIPE) for both incline and resistance — required for
               API 25/Xamarin iFit (NordicTrack S22i etc.).
               In --a11y mode the script handles all setup automatically:
               it launches QZCompanion, binds the AccessibilityService, verifies
               the CALSWIPE relay, dismisses any warmup screen, and restores
               iFit to the foreground.
               Resistance is a vertical slider; a probe scan locates the thumb
               and chains upward swipes to home to max, then sweeps downward
               with thumb-tracking so every CALSWIPE starts at the thumb.

Usage:
    python3 tools/discover-device.py --device 192.168.1.213:5555 [--push] [--out FILE]
    python3 tools/discover-device.py --device 192.168.1.213:5555 --a11y [--push]

Requirements:
    - adb in PATH, connected to device
    - iFit running in active manual workout (screen on)
    - Python 3.6+, stdlib only
    - --a11y: QZCompanion APK installed on device (script starts and configures it)
"""

import argparse
import json
import re
import socket
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET


_QZ_PKG  = "org.cagnulein.qzcompanionnordictracktreadmill"
_A11Y_SVC = f"{_QZ_PKG}/{_QZ_PKG}.service.MyAccessibilityService"


# ── logcat parsing ────────────────────────────────────────────────────────────

GRADE_RE      = re.compile(r"Changed Grade to:\s*([-\d.]+)")
# S22i (and similar iFit bikes) log gear changes as "Changed CurrentGear to:" rather than
# "Changed Resistance to:".  Both patterns are matched so the same code works across devices.
RESISTANCE_RE = re.compile(r"Changed (?:Resistance|CurrentGear) to:\s*([-\d.]+)")


class MetricReader:
    """Background thread that streams mono-stdout logcat and caches latest values."""

    def __init__(self, device):
        self._device = device
        self._grade      = None
        self._resistance = None
        self._grade_ts      = 0.0
        self._resistance_ts = 0.0
        self._lock = threading.Lock()
        self._proc = None

    def start(self):
        self._proc = subprocess.Popen(
            ["adb", "-s", self._device, "logcat", "-s", "mono-stdout"],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True,
        )
        t = threading.Thread(target=self._read, daemon=True)
        t.start()

    def stop(self):
        if self._proc:
            self._proc.kill()

    def _read(self):
        for line in self._proc.stdout:
            now = time.time()
            m = GRADE_RE.search(line)
            if m:
                with self._lock:
                    self._grade    = float(m.group(1))
                    self._grade_ts = now
                continue
            m = RESISTANCE_RE.search(line)
            if m:
                v = float(m.group(1))
                if v >= 1:          # filter iFit's noise "Changed Resistance to: 0" events
                    with self._lock:
                        self._resistance    = v
                        self._resistance_ts = now

    def wait_fresh_grade(self, after_ts, timeout=5.0):
        """Return grade value if a new event arrived after after_ts, else None."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self._lock:
                if self._grade_ts > after_ts and self._grade is not None:
                    return self._grade
            time.sleep(0.2)
        return None

    def wait_fresh_resistance(self, after_ts, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self._lock:
                if self._resistance_ts > after_ts and self._resistance is not None:
                    return self._resistance
            time.sleep(0.2)
        return None

    def wait_stable_grade(self, after_ts, settle=1.0, timeout=8.0):
        """Return the settled grade: last value that didn't change for settle seconds.

        iFit emits a transit event as the gesture passes through intermediate buttons,
        then a settled event when the thumb lands.  Waiting for stability discards the
        transit events and returns the final landed value.
        """
        return self._wait_stable("grade", after_ts, settle, timeout)

    def wait_stable_resistance(self, after_ts, settle=1.0, timeout=8.0):
        return self._wait_stable("resistance", after_ts, settle, timeout)

    def _wait_stable(self, metric, after_ts, settle, timeout):
        val_attr = f"_{metric}"
        ts_attr  = f"_{metric}_ts"
        deadline = time.time() + timeout
        last_val = None
        last_change = 0.0
        while time.time() < deadline:
            with self._lock:
                val = getattr(self, val_attr)
                ts  = getattr(self, ts_attr)
            if ts > after_ts and val is not None:
                if val != last_val:
                    last_val    = val
                    last_change = time.time()
                elif time.time() - last_change >= settle:
                    return last_val
            time.sleep(0.1)
        return last_val   # return whatever settled last, even if settle window didn't expire

    def any_event_ts(self):
        with self._lock:
            return max(self._grade_ts, self._resistance_ts)


# ── ADB helpers ───────────────────────────────────────────────────────────────

def adb(device, *args, check=True):
    return subprocess.run(
        ["adb", "-s", device] + list(args),
        capture_output=True, text=True, check=check,
    )


# Module-level: set to device IP when --a11y is active, else None.
_a11y_host = None


# ── a11y setup helpers ────────────────────────────────────────────────────────

def _dismiss_dialog(device, button_text):
    """Tap a dialog button by text, if present. Silent on any failure."""
    try:
        adb(device, "shell", "uiautomator", "dump", "/sdcard/qz_ui_tmp.xml", check=False)
        xml_text = adb(device, "shell", "cat", "/sdcard/qz_ui_tmp.xml", check=False).stdout
        root = ET.fromstring(xml_text)
        for node in root.iter("node"):
            t = (node.get("text", "") or node.get("content-desc", "")).strip()
            if t.upper() == button_text.upper():
                m = re.findall(r"\d+", node.get("bounds", ""))
                if len(m) == 4:
                    x = (int(m[0]) + int(m[2])) // 2
                    y = (int(m[1]) + int(m[3])) // 2
                    print(f"  Dismissing [{button_text}] dialog at ({x},{y})…")
                    adb(device, "shell", "input", "tap", str(x), str(y))
                    time.sleep(2)
                return
    except Exception:
        pass


def a11y_preflight(device, inc_x):
    """Launch QZCompanion, bind its AccessibilityService, verify CALSWIPE, restore iFit.

    Replaces the multi-step manual procedure in the runbook.  The caller only needs
    to ensure iFit is in an active workout before running the sweep.
    """
    print("── a11y pre-flight ──")

    # Keep the screen on for the full sweep.
    adb(device, "shell", "settings", "put", "system", "screen_off_timeout", "600000")

    # Launch (or bring to foreground) QZCompanion.
    print("  Launching QZCompanion…")
    adb(device, "shell", "am", "start", "-n", f"{_QZ_PKG}/.MainActivity", check=False)
    time.sleep(4)

    # Dismiss the "Use QZ Companion?" confirmation dialog that appears on first run
    # or after app data is cleared.
    _dismiss_dialog(device, "OK")

    # (Re-)bind the AccessibilityService.  `am start` alone does not restore a binding
    # severed by a prior force-stop; toggling the settings forces the OS to reconnect.
    def _bind():
        adb(device, "shell", "settings", "put", "secure",
            "enabled_accessibility_services", _A11Y_SVC)
        adb(device, "shell", "settings", "put", "secure",
            "accessibility_enabled", "1")

    print("  Binding AccessibilityService…")
    _bind()

    # Verify with a test CALSWIPE.  Retry once — the service can take a few extra
    # seconds to connect after the settings toggle.
    for attempt in range(1, 3):
        time.sleep(3)
        adb(device, "logcat", "-c")
        time.sleep(0.3)
        probe = f"CALSWIPE:{int(inc_x)}:300:325"
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.sendto(probe.encode(), (_a11y_host, 8003))
        s.close()
        time.sleep(2)
        log = adb(device, "logcat", "-d").stdout
        if f"CALSWIPE x={int(inc_x)}" in log and "not connected" not in log:
            print("  ✓ AccessibilityService connected\n")
            break
        if attempt < 2:
            print(f"  Attempt {attempt}: service not yet ready — re-binding…")
            _bind()
    else:
        print("ERROR: AccessibilityService did not connect after 2 attempts.")
        print("  → Reboot the tablet and re-run.")
        print(f"  → Or enable manually: Settings → Accessibility → QZ Companion → On.")
        sys.exit(1)

    # Restore iFit to the foreground — QZCompanion may have taken focus.
    print("  Restoring iFit to foreground…")
    adb(device, "shell", "monkey", "-p", "com.ifit.standalone",
        "-c", "android.intent.category.LAUNCHER", "1", check=False)
    time.sleep(5)

    # Dismiss warmup screen if present.  iFit locks resistance to level 1 during
    # the warmup countdown; swiping the resistance slider has no effect until the
    # warmup ends.  Tap END WARMUP so both sliders are live before we sweep.
    try:
        adb(device, "shell", "uiautomator", "dump", "/sdcard/qz_ui_tmp.xml", check=False)
        xml_text = adb(device, "shell", "cat", "/sdcard/qz_ui_tmp.xml", check=False).stdout
        root = ET.fromstring(xml_text)
        for node in root.iter("node"):
            t = (node.get("text", "") or node.get("content-desc", "")).strip().upper()
            if t == "END WARMUP":
                m = re.findall(r"\d+", node.get("bounds", ""))
                if len(m) == 4:
                    cx = (int(m[0]) + int(m[2])) // 2
                    cy = (int(m[1]) + int(m[3])) // 2
                    print(f"  Warmup detected — tapping END WARMUP at ({cx},{cy})…")
                    adb(device, "shell", "input", "tap", str(cx), str(cy))
                    time.sleep(5)
                break
    except Exception:
        pass


def _a11y_restart_qz(device):
    """After --push: force-stop QZCompanion, re-bind a11y, restart.  Replaces manual steps."""
    print("\nRestarting QZCompanion to apply calibration…")
    adb(device, "shell", "am", "force-stop", _QZ_PKG)
    time.sleep(2)
    adb(device, "shell", "settings", "put", "secure",
        "enabled_accessibility_services", _A11Y_SVC)
    adb(device, "shell", "settings", "put", "secure", "accessibility_enabled", "1")
    time.sleep(1)
    adb(device, "shell", "am", "start", "-n", f"{_QZ_PKG}/.MainActivity")
    time.sleep(4)
    # Check logcat to confirm calibration loaded.
    log = adb(device, "logcat", "-d").stdout
    if "Loaded calibration from qz-calibration.json" in log:
        print("✓ QZCompanion restarted — calibration loaded, status chip: Custom (Calibrated)")
    else:
        print("✓ QZCompanion restarted")
        print("  (Could not confirm calibration load from logcat — check the app manually.)")

    adb(device, "shell", "settings", "put", "system", "screen_off_timeout", "60000")


def swipe(device, x, from_y, to_y, duration_ms=200):
    """Swipe the slider track. Uses CALSWIPE UDP relay when --a11y is active."""
    if _a11y_host is not None:
        msg = f"CALSWIPE:{int(x)}:{int(from_y)}:{int(to_y)}"
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.sendto(msg.encode(), (_a11y_host, 8003))
        s.close()
    else:
        adb(device, "shell", "input", "swipe",
            str(x), str(from_y), str(x), str(to_y), str(duration_ms))


def tap(device, x, y):
    """Tap a single point via adb shell input tap.

    On Xamarin/API 25 devices, adb shell input swipe does not reach the iFit
    SurfaceView, but adb shell input tap does.  The resistance slider responds
    to discrete taps at each button position rather than drag gestures, so the
    resistance sweep uses this instead of swipe() in --a11y mode.
    """
    adb(device, "shell", "input", "tap", str(int(x)), str(int(y)))


def screen_size(device):
    out = adb(device, "shell", "wm", "size").stdout
    m = re.search(r"(\d+)x(\d+)", out)
    if not m:
        raise RuntimeError(f"Could not parse screen size from: {out!r}")
    return int(m.group(1)), int(m.group(2))


def screen_profile(width):
    """Return (incline_trackX, resistance_trackX).

    inc_x is the horizontal centre of the incline button column, not the ScreenProfile
    track used by QZCompanion for swiping.  For Xamarin iFit the AccessibilityService swipe
    must start at the button-column centre (≈57 px on a 1920-wide screen); starting at the
    column right edge (75 px) is unreliable.  The trackX written to qz-calibration.json is
    this centre value, so QZCompanion also benefits.
    """
    if width >= 1920: return 57, 1845
    if width >= 1280: return 57, 1205
    if width >= 1024: return 56,  950
    return 55, 725


# ── sweep logic ───────────────────────────────────────────────────────────────

COARSE_STEP   = 50
COARSE_MARGIN = 200
COARSE_SETTLE = 2.5   # seconds
COARSE_TIMEOUT = 4.0

FINE_STEP    = 25
FINE_SETTLE  = 2.0
FINE_TIMEOUT = 6.0


def coarse_sweep(device, reader, track_x, screen_h, metric, start_y=None, use_tap=False):
    """Sweep full height at coarse resolution; return [(y, value)] where value changed.

    use_tap=True uses adb shell input tap instead of swipe — required for resistance
    buttons on Xamarin/API 25 devices where input swipe doesn't reach the SurfaceView.
    """
    wait_fn = reader.wait_fresh_grade if metric == "grade" else reader.wait_fresh_resistance
    readings = []
    # start_y: where the slider thumb is before this sweep begins (set by probe tap in
    # --a11y mode). Each CALSWIPE starts from the previous endpoint so iFit recognises it.
    prev_y = start_y if start_y is not None else COARSE_MARGIN
    for y in range(COARSE_MARGIN, screen_h - COARSE_MARGIN + 1, COARSE_STEP):
        ts = time.time()
        if use_tap:
            tap(device, track_x, y)
        else:
            swipe(device, track_x, prev_y, y)
            prev_y = y
        time.sleep(COARSE_SETTLE)
        v = wait_fn(ts, COARSE_TIMEOUT)
        if v is not None:
            readings.append((y, v))
            print(f"    coarse y={y:4d} → {metric}={v}")
        else:
            print(f"    coarse y={y:4d} → (no change)")
    return readings


def find_active_range(readings, screen_h):
    """Expand one coarse step beyond the observed endpoints."""
    if len(readings) < 3:
        return None
    y_top    = max(COARSE_MARGIN,              readings[0][0]  - COARSE_STEP)
    y_bottom = min(screen_h - COARSE_MARGIN,  readings[-1][0] + COARSE_STEP)
    if y_bottom - y_top < FINE_STEP * 3:
        return None
    return y_top, y_bottom


def fine_sweep(device, reader, track_x, y_top, y_bottom, screen_h, metric, use_tap=False):
    """Fine sweep in the active range; return [(y, value)].

    Applies a monotonicity filter: for a downward incline/resistance sweep
    (increasing Y = decreasing metric), any step where the metric jumped UP
    relative to the previous accepted reading is a slider-snap artefact and is
    silently dropped before fitting.

    use_tap=True: each step is a discrete tap rather than a drag swipe — required
    for resistance buttons on Xamarin/API 25 devices.
    """
    # Fine sweep uses wait_stable to discard iFit's transit events (fired as the gesture
    # passes through intermediate buttons) and capture only the settled landed value.
    wait_fn = reader.wait_stable_grade if metric == "grade" else reader.wait_stable_resistance
    # Reset to top of active range.  For swipe mode, start from y_bottom so iFit
    # recognises the gesture; for tap mode, just tap y_top directly.
    if use_tap:
        tap(device, track_x, y_top)
    else:
        swipe(device, track_x, y_bottom, y_top)
    time.sleep(FINE_SETTLE * 2)
    prev_y = y_top
    raw = []
    for y in range(y_top, y_bottom + 1, FINE_STEP):
        ts = time.time()
        if use_tap:
            tap(device, track_x, y)
        else:
            swipe(device, track_x, prev_y, y)
            prev_y = y
        time.sleep(FINE_SETTLE)
        v = wait_fn(ts, timeout=FINE_TIMEOUT)
        if v is not None:
            raw.append((y, v))
            print(f"    fine   y={y:4d} → {metric}={v:.2f}")
        else:
            print(f"    fine   y={y:4d} → (timeout)")

    # Monotonicity filter: drop any reading that jumped UP (snap artefact).
    # Tolerance of 1 grade unit absorbs normal measurement noise.
    # When a snap is dropped, update the running baseline to the snapped value so
    # the next reading (measured from the snapped position) is accepted correctly.
    MONO_TOL = 1.0
    points = []
    prev_metric = None
    for pt in raw:
        if prev_metric is not None and pt[1] > prev_metric + MONO_TOL:
            print(f"    [snap artefact dropped: y={pt[0]} {metric}={pt[1]:.2f}"
                  f" > prev {prev_metric:.2f}]")
            prev_metric = pt[1]   # slider is now at this position; next step measured from here
        else:
            points.append(pt)
            prev_metric = pt[1]
    return points


# ── formula fitting ───────────────────────────────────────────────────────────

def _ols(points):
    """OLS fit for [(y_pixel, metric_value)]. Returns (intercept, slope)."""
    n = len(points)
    ys = [p[0] for p in points]
    xs = [p[1] for p in points]
    sx = sum(xs);  sy = sum(ys)
    sxx = sum(xi * xi for xi in xs)
    sxy = sum(xi * yi for xi, yi in zip(xs, ys))
    denom = n * sxx - sx * sx
    if abs(denom) < 1e-9:
        raise ValueError("Degenerate fit — metric values did not vary")
    slope = (n * sxy - sx * sy) / denom
    intercept = (sy - slope * sx) / n
    return intercept, slope


def fit_linear(points):
    """
    Fit Y = origin - scale * x  (origin and scale both positive for iFit sliders).
    Returns (origin, scale, r2).  Pure Python OLS, no numpy.

    One pass of 3σ outlier rejection: fit → compute residuals → drop the worst
    point if its residual exceeds 3 * median-absolute-residual, then refit.
    iFit occasionally fires a delayed grade event from the previous gesture that
    the stale-guard misses by a few ms; this catches those single-point spikes.
    """
    n = len(points)
    intercept, slope = _ols(points)

    # Compute residuals and MAR
    residuals = [p[0] - (intercept + slope * p[1]) for p in points]
    sorted_abs = sorted(abs(r) for r in residuals)
    mar = sorted_abs[n // 2]                         # median absolute residual
    threshold = max(3 * mar, 5.0)                    # at least 5 px so we don't over-reject

    worst_idx  = max(range(n), key=lambda i: abs(residuals[i]))
    if abs(residuals[worst_idx]) > threshold and n - 1 >= 3:
        rejected = points[worst_idx]
        points   = [p for i, p in enumerate(points) if i != worst_idx]
        print(f"    [outlier rejected: y={rejected[0]} metric={rejected[1]:.2f} "
              f"residual={residuals[worst_idx]:+.1f}px]")
        intercept, slope = _ols(points)

    ys     = [p[0] for p in points]
    xs     = [p[1] for p in points]
    origin = intercept
    scale  = -slope
    y_mean = sum(ys) / len(ys)
    ss_tot = sum((yi - y_mean) ** 2 for yi in ys)
    ss_res = sum((p[0] - (intercept + slope * p[1])) ** 2 for p in points)
    r2 = 1.0 - ss_res / ss_tot if ss_tot > 0 else 1.0
    return origin, scale, r2


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Discover iFit bike slider calibration via ADB"
    )
    parser.add_argument("--device", required=True,
                        help="ADB device address, e.g. 192.168.1.213:5555")
    parser.add_argument("--push", action="store_true",
                        help="Push output file to /sdcard/qz-calibration.json on device")
    parser.add_argument("--out", default="qz-calibration.json",
                        help="Local output file path (default: qz-calibration.json)")
    parser.add_argument("--a11y", action="store_true",
                        help="Route swipes via QZCompanion's AccessibilityService over UDP "
                             "(required for API 25/Xamarin iFit devices such as NordicTrack S22i)")
    args = parser.parse_args()

    device = args.device

    global _a11y_host
    if args.a11y:
        _a11y_host = device.split(":")[0]
        print(f"Mode: --a11y (swipes via QZCompanion UDP relay at {_a11y_host}:8003)")

    # ── screen geometry ───────────────────────────────────────────────────────
    w, h = screen_size(device)
    print(f"Screen: {w}×{h}")
    inc_x, res_x = screen_profile(w)
    print(f"Track X — incline: {inc_x}  resistance: {res_x}")

    # ── a11y setup (--a11y mode only) ─────────────────────────────────────────
    if _a11y_host is not None:
        a11y_preflight(device, inc_x)

    # ── start metric reader ───────────────────────────────────────────────────
    adb(device, "logcat", "-c")
    reader = MetricReader(device)
    reader.start()
    # Give the logcat subprocess a moment to start reading before we fire probe events.
    time.sleep(1.0)

    # In --a11y mode, grade events only fire on slider movement and adb shell input swipe
    # doesn't reach Xamarin's SurfaceView.  Use adb shell input tap (which does reach it)
    # to force two distinct grade values — at least one tap will cause a change event.
    # Y=450 (≈grade 10) and Y=250 (≈grade 20) are far apart so one always differs from
    # whatever grade is currently active, guaranteeing at least one change event fires.
    if _a11y_host is not None:
        print("Probe taps (--a11y mode): tapping grade≈10 then grade≈20 buttons…")
        adb(device, "shell", "input", "tap", str(inc_x), "450")
        time.sleep(1.5)
        # Leave slider near max grade so the coarse sweep starts from a known position.
        adb(device, "shell", "input", "tap", str(inc_x), "250")
        time.sleep(1.5)

    print("\nWaiting up to 15s for iFit metric events (workout must be active)…")
    deadline = time.time() + 15
    while time.time() < deadline:
        if reader.any_event_ts() > 0:
            break
        time.sleep(0.5)
    else:
        print("ERROR: No iFit events received in 15s.")
        if _a11y_host is not None:
            print("  → Ensure QZCompanion is running with Accessibility Service enabled.")
        print("  → Open iFit, start a Manual Ride, press GO, then re-run.")
        reader.stop()
        sys.exit(1)
    print("✓ iFit is responding\n")

    result = {}

    # ── incline sweep ─────────────────────────────────────────────────────────
    print(f"── Incline sweep (trackX={inc_x}) ──")
    # In --a11y mode the probe left the slider at Y=250 (grade≈20); pass that as start_y.
    inc_start_y = 250 if _a11y_host is not None else None
    coarse = coarse_sweep(device, reader, inc_x, h, "grade", start_y=inc_start_y)
    r = find_active_range(coarse, h)
    if r is None:
        print(f"ERROR: Only {len(coarse)} coarse incline readings — need ≥ 3.")
        print("  Is the workout active and the incline slider responding?")
        reader.stop()
        sys.exit(1)
    print(f"  Active incline range: Y {r[0]}–{r[1]}")

    # Quality gate: if coarse data is already highly linear (R² ≥ 0.99), use it
    # directly.  Discrete sliders (e.g. iFit S22i) measure at exact button positions
    # during the coarse sweep, so the coarse fit is more accurate than a fine sweep
    # that lands between buttons and causes snap artefacts.
    coarse_origin, coarse_scale, coarse_r2 = fit_linear(coarse)
    if coarse_r2 >= 0.99:
        print(f"  Coarse fit excellent (R²={coarse_r2:.4f}) — skipping fine sweep.")
        origin, scale, r2 = coarse_origin, coarse_scale, coarse_r2
        points = coarse
    else:
        points = fine_sweep(device, reader, inc_x, r[0], r[1], h, "grade")
        if len(points) < 3:
            print(f"ERROR: Only {len(points)} incline fine points — need ≥ 3.")
            reader.stop()
            sys.exit(1)
        origin, scale, r2 = fit_linear(points)

    flag = "  ⚠ poor fit" if r2 < 0.97 else ""
    print(f"  Fit: origin={origin:.1f}  scale={scale:.4f}  R²={r2:.4f}{flag}")
    result["incline"] = {
        "trackX": inc_x,
        "origin": round(origin, 2),
        "scale":  round(scale, 4),
    }

    # ── resistance sweep ──────────────────────────────────────────────────────
    print(f"\n── Resistance sweep (trackX={res_x}) ──")

    if _a11y_host is not None:
        # Resistance is a vertical slider like incline.  CALSWIPE must start AT the
        # thumb's current position; a swipe starting away from the thumb is ignored.
        # Strategy:
        #   1. Probe downward from y=800 in 100px steps; first response reveals thumb_y.
        #   2. Chain 100px upward swipes (each from current thumb_y) until thumb ≤ 250.
        #   3. Sweep downward tracking thumb_y so every step starts at the thumb.
        thumb_y = None
        print("  Probing for resistance slider…")
        for probe_y in [800, 700, 600, 500, 400, 300]:
            probe_ts = time.time()
            swipe(device, res_x, probe_y, probe_y - 100)
            v = reader.wait_fresh_resistance(probe_ts, timeout=3.0)
            if v is not None:
                # The probe swipe dragged the thumb from ~probe_y to probe_y-100.
                # Track the thumb at the swipe endpoint.
                thumb_y = probe_y - 100
                print(f"  ✓ Slider responds at y≈{probe_y} (level {v:.0f}) — "
                      f"thumb_y={thumb_y}, homing to max…")
                # Home to max resistance: chain upward 100px swipes each starting from
                # the tracked thumb position.  Stop when thumb is at or above y=250
                # (S22i max-resistance is around y=225; going further gains nothing).
                while thumb_y > 250:
                    to_y = max(200, thumb_y - 100)
                    swipe(device, res_x, thumb_y, to_y)
                    thumb_y = to_y
                    time.sleep(0.8)
                time.sleep(2.0)
                break
        else:
            print("  ⚠ Resistance slider not found — skipping")

        if thumb_y is None:
            coarse_r = []
        else:
            # Sweep downward from current thumb position (max level), tracking thumb.
            # Each swipe moves the thumb by COARSE_STEP so every swipe starts exactly
            # at the thumb's current position — required for iFit to accept the gesture.
            coarse_r = []
            prev_y = thumb_y
            for y in range(thumb_y + COARSE_STEP, h - COARSE_MARGIN + 1, COARSE_STEP):
                ts = time.time()
                swipe(device, res_x, prev_y, y)
                prev_y = y
                time.sleep(COARSE_SETTLE)
                v = reader.wait_fresh_resistance(ts, COARSE_TIMEOUT)
                if v is not None and v >= 1:
                    coarse_r.append((y, v))
                    print(f"    coarse y={y:4d} → resistance={v:.0f}")
                    if v <= 1:
                        break
                else:
                    print(f"    coarse y={y:4d} → (no response)")
    else:
        coarse_r = coarse_sweep(device, reader, res_x, h, "resistance")
    rr = find_active_range(coarse_r, h)
    if rr is None:
        print(f"  WARNING: Only {len(coarse_r)} coarse resistance readings — skipping.")
    else:
        print(f"  Active resistance range: Y {rr[0]}–{rr[1]}")
        min_level = int(round(min(v for _, v in coarse_r)))
        coarse_adj = [(y, v - min_level) for y, v in coarse_r]
        cr_origin, cr_scale, cr_r2 = fit_linear(coarse_adj)
        if cr_r2 >= 0.99:
            print(f"  Coarse fit excellent (R²={cr_r2:.4f}) — skipping fine sweep.")
            origin_r, scale_r, r2_r = cr_origin, cr_scale, cr_r2
        else:
            points_r = fine_sweep(device, reader, res_x, rr[0], rr[1], h, "resistance")
            if len(points_r) < 3:
                print(f"  WARNING: Only {len(points_r)} resistance fine points — skipping.")
                rr = None
            else:
                min_level = int(round(min(v for _, v in points_r)))
                pts_adj   = [(y, v - min_level) for y, v in points_r]
                origin_r, scale_r, r2_r = fit_linear(pts_adj)
        if rr is not None:
            flag_r = "  ⚠ poor fit" if r2_r < 0.97 else ""
            print(f"  Fit: origin={origin_r:.1f}  scale={scale_r:.4f}"
                  f"  minLevel={min_level}  R²={r2_r:.4f}{flag_r}")
            result["resistance"] = {
                "trackX":   res_x,
                "origin":   round(origin_r, 2),
                "scale":    round(scale_r, 4),
                "minLevel": min_level,
            }

    reader.stop()

    # ── write output ──────────────────────────────────────────────────────────
    with open(args.out, "w") as f:
        json.dump(result, f, indent=2)
        f.write("\n")
    print(f"\n✓ Written: {args.out}")
    print(json.dumps(result, indent=2))

    if args.push:
        adb(device, "push", args.out, "/sdcard/qz-calibration.json")
        print("\n✓ Pushed to /sdcard/qz-calibration.json")
        if _a11y_host is not None:
            _a11y_restart_qz(device)
        else:
            print("  Force-stop and restart QZCompanion to apply:")
            print(f"    adb -s {device} shell am force-stop {_QZ_PKG}")
            print(f"    adb -s {device} shell am start -n {_QZ_PKG}/.MainActivity")


if __name__ == "__main__":
    main()

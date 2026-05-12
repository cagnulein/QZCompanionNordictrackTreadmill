#!/usr/bin/env python3
"""
Validate swipe-target pixel coordinates in device classes against iFit APK layout geometry.

Ground truth source:
  ifit_decoded/res/values/dimens.xml
    workout_slider_margin = 12dp
    workout_slider_width  = 125dp
  → left slider centre X  = 12 + 62.5 = 74.5dp
  → right slider centre X = screen_width_dp − 74.5dp

At mdpi (density ≈ 1.0) this gives:
  S22i left  trackX = 75  ✓   (1920px screen)
  S22i right trackX = 1845 ✓

Validated against: S22iDevice, X22iDevice, X32iDevice (all match within ±2px)

Checks performed
  A  trackX plausibility  — left ≈ 74.5, right infers screen width to a known category
  B  initialThumbY review — report the metric value v where targetY(v) = initialThumbY
  C  formula monotonicity — higher metric should map to lower Y (upward swipe)
  D  Sindarin global caps — formula at MaxIncline/MaxKph/MaxResistance stays on-screen

Usage: python3 tools/discover-device/validate_swipe_targets.py
Exit : 0 = clean (warnings only);  1 = at least one ERROR
"""

import os
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

REPO_ROOT = Path(__file__).parent.parent.parent.resolve()
DEVICE_BIKE_DIR      = REPO_ROOT / "app/src/main/java/org/cagnulein/qzcompanionnordictracktreadmill/device/bike"
DEVICE_TREADMILL_DIR = REPO_ROOT / "app/src/main/java/org/cagnulein/qzcompanionnordictracktreadmill/device/treadmill"
IFIT_DIMENS_BASE     = REPO_ROOT / "QZCompanionNordictrackTreadmill/ifit/ifit/ifit_decoded/res"

# ---------------------------------------------------------------------------
# Ground truth from iFit APK layout XML
# ---------------------------------------------------------------------------

# From ifit_decoded/res/values/dimens.xml (base values, not overridden by any qualifier)
SLIDER_MARGIN_DP  = 12.0    # workout_slider_margin
SLIDER_WIDTH_DP   = 125.0   # workout_slider_width

# Derived: slider container centre X from screen edge (left edge for left slider,
# right edge for right slider)
EXPECTED_EDGE_OFFSET_DP = SLIDER_MARGIN_DP + SLIDER_WIDTH_DP / 2  # = 74.5dp

# Known standard screen widths (px at density=1.0 / mdpi)
# Derived by running: screen_width = right_trackX + EXPECTED_EDGE_OFFSET_DP
STANDARD_WIDTHS  = [800, 1024, 1280, 1920]
WIDTH_TOLERANCE  = 15   # ± px to accept as matching a standard width

# TrackX tolerance for the left slider (should be ≈ 74.5px at density=1)
LEFT_TRACKX_EXPECTED = 74.5
LEFT_TRACKX_TOLERANCE = 20   # generous — some devices use slightly wider left columns

# ScreenProfile constants — must match device/ScreenProfile.java (iFit APK 2.6.90)
_SCREEN_PROFILE = {
    "ScreenProfile.W1920.leftTrackX":   75,
    "ScreenProfile.W1920.rightTrackX":  1845,
    "ScreenProfile.W1280.leftTrackX":   75,
    "ScreenProfile.W1280.rightTrackX":  1205,
    "ScreenProfile.W1024.leftTrackX":   74,
    "ScreenProfile.W1024.rightTrackX":  950,
    "ScreenProfile.W800.leftTrackX":    73,
    "ScreenProfile.W800.rightTrackX":   725,
}

def _resolve_track_x(expr: str) -> int:
    """Resolve a ScreenProfile expression or raw int to a pixel value. -1 if unknown."""
    expr = expr.strip()
    if not expr:
        return -1
    if expr in _SCREEN_PROFILE:
        return _SCREEN_PROFILE[expr]
    try:
        return int(expr)
    except ValueError:
        return -1


# Sindarin global caps (from FitnessValueValidationDefaults in Sindarin.Core.decompiled.cs)
SINDARIN_MAX_INCLINE   =  40.0   # degrees/percent
SINDARIN_MIN_INCLINE   = -20.0
SINDARIN_MAX_KPH       = 100.0   # global cap; practical treadmill max ≈ 22 km/h
SINDARIN_PRACTICAL_MAX_KPH = 22.0   # used for on-screen bounds check
SINDARIN_MAX_RESISTANCE=  26.0

# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class SliderInfo:
    role:          str            # "incline" | "speed" | "resistance" | "gear" | "unknown"
    initial_y:     int
    track_x:       int
    formula_str:   str            # raw targetY body, for display
    intercept:     Optional[float] = None   # for linear formulae: y = intercept - slope*v
    slope:         Optional[float] = None
    formula_type:  str = "linear"           # "linear" | "lookup" | "piecewise" | "complex"


@dataclass
class DeviceInfo:
    class_name:  str
    device_type: str               # "bike" | "treadmill"
    sliders:     list = field(default_factory=list)  # list[SliderInfo]
    inherits:    bool = False      # True if no Slider() calls (delegates to parent)
    skip:        bool = False      # True for CalibratedBikeDevice / other unanalsyable


# ---------------------------------------------------------------------------
# Java parsing
# ---------------------------------------------------------------------------

def _extract_slider_blocks(java_text: str) -> list[str]:
    """Return a list of anonymous Slider body strings found in the file."""
    blocks = []
    start = 0
    while True:
        m = re.search(r'new\s+Slider\s*\(', java_text[start:])
        if not m:
            break
        # Adjust match positions to be relative to full java_text
        m_start = start + m.start()
        # Find the matching closing brace of the anonymous class body.
        # Walk from the '{' that opens the class body.
        pos = start + m.end()
        # Advance past the initialY argument and closing paren
        depth_paren = 1
        while pos < len(java_text) and depth_paren > 0:
            if java_text[pos] == '(':
                depth_paren += 1
            elif java_text[pos] == ')':
                depth_paren -= 1
            pos += 1
        # Now pos is just after ')' — find '{'
        open_brace = java_text.find('{', pos)
        if open_brace == -1:
            break
        depth_brace = 1
        idx = open_brace + 1
        while idx < len(java_text) and depth_brace > 0:
            if java_text[idx] == '{':
                depth_brace += 1
            elif java_text[idx] == '}':
                depth_brace -= 1
            idx += 1
        block = java_text[m_start:idx]
        blocks.append(block)
        start = idx
    return blocks


def _parse_slider_block(block: str) -> SliderInfo:
    """Parse one Slider anonymous-class block into a SliderInfo."""
    # Initial Y and trackX: new Slider(NNN) or new Slider(NNN, ScreenProfile.Xxx.yyy)
    init_m = re.search(
        r'new\s+Slider\s*\(\s*(\d+)\s*(?:,\s*([^)]+?))?\s*\)',
        block
    )
    initial_y = int(init_m.group(1)) if init_m else 0
    track_x_expr = (init_m.group(2) or "").strip() if init_m else ""
    track_x = _resolve_track_x(track_x_expr)

    # targetY body
    ty_m = re.search(
        r'targetY\s*\(\s*double\s+v\s*\)\s*\{(.*?)\}',
        block, re.DOTALL
    )
    formula_body = ty_m.group(1).strip() if ty_m else ""

    # Infer formula type
    formula_type = "complex"
    if "lookupStep" in formula_body or "INCLINE_TABLE" in formula_body or "SPEED_TABLE" in formula_body:
        formula_type = "lookup"
    elif "if " in formula_body or "else" in formula_body:
        formula_type = "piecewise"
    else:
        formula_type = "linear"

    # Extract intercept and slope for linear formulae
    intercept = None
    slope = None
    if formula_type == "linear":
        # Normalise: strip return, (int)(...), whitespace
        expr = formula_body
        expr = re.sub(r'\breturn\b', '', expr).strip().rstrip(';').strip()
        expr = re.sub(r'\(int\)\s*', '', expr)  # remove (int) casts
        expr = expr.strip().strip('(').rstrip(')')  # remove outer parens if any

        # Attempt to match common patterns:
        # Pattern 1: A - B * v            e.g.  622.0 - 18.57 * v
        # Pattern 2: A - B * (v + C)      e.g.  785 - 11.30 * (v + 6)
        # Pattern 3: A - B * (v - C)      e.g.  807 - (v - 1.0) * 31
        # Pattern 4: A - B * v * 0.621371 (mph conversion)
        # Pattern 5: -B * v + A            e.g.  -12.499 * v + 482.2
        # We try several patterns and use the first match.

        NUM = r'[\d.]+(?:/[\d.]+)?'  # number optionally in A/B form
        _n  = lambda m: eval(m)  # safe because pattern only matches digits/./+

        # Pre-substitute A/B literals like 401.0/23 → float
        def eval_num(s):
            s = s.strip()
            if '/' in s:
                a, b = s.split('/', 1)
                return float(a) / float(b)
            return float(s)

        def try_linear(expr):
            # Canonical form: intercept - slope * v  (slope may include mph factor)
            # We will attempt several regex patterns in order.

            # Remove inner parens around the whole expression
            e = re.sub(r'^\((.*)\)$', r'\1', expr.strip())

            patterns = [
                # (A - B * v * 0.621371)  or  A - B*v*0.621371
                (r'^({N})\s*-\s*({N})\s*\*\s*v\s*\*\s*0\.621371$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(1)), eval_num(m.group(2)) * 0.621371)),

                # A - B * (v + C)
                (r'^({N})\s*-\s*({N})\s*\*\s*\(\s*v\s*\+\s*({N})\s*\)$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(1)) - eval_num(m.group(2)) * eval_num(m.group(3)),
                             eval_num(m.group(2)))),

                # A - B * (v - C)  or A - (v - C) * B
                (r'^({N})\s*-\s*({N})\s*\*\s*\(\s*v\s*-\s*({N})\s*\)$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(1)) + eval_num(m.group(2)) * eval_num(m.group(3)),
                             eval_num(m.group(2)))),
                (r'^({N})\s*-\s*\(\s*v\s*-\s*({N})\s*\)\s*\*\s*({N})$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(1)) + eval_num(m.group(3)) * eval_num(m.group(2)),
                             eval_num(m.group(3)))),

                # A - B * (v * 0.621371 - C)
                (r'^({N})\s*-\s*({N})\s*\*\s*\(\s*v\s*\*\s*0\.621371\s*-\s*({N})\s*\)$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(1)) + eval_num(m.group(2)) * eval_num(m.group(3)),
                             eval_num(m.group(2)) * 0.621371)),

                # A - (v + C) * B  (factor order reversed)
                (r'^({N})\s*-\s*\(\s*v\s*\+\s*({N})\s*\)\s*\*\s*({N})$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(1)) - eval_num(m.group(3)) * eval_num(m.group(2)),
                             eval_num(m.group(3)))),

                # A - B * v   (simple)
                (r'^({N})\s*-\s*({N})\s*\*\s*v$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(1)), eval_num(m.group(2)))),

                # A - v * B
                (r'^({N})\s*-\s*v\s*\*\s*({N})$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(1)), eval_num(m.group(2)))),

                # A - (v * B)   e.g. 450 - (v * 14.705)
                (r'^({N})\s*-\s*\(\s*v\s*\*\s*({N})\s*\)$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(1)), eval_num(m.group(2)))),

                # -B * v + A
                (r'^-({N})\s*\*\s*v\s*\+\s*({N})$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(2)), eval_num(m.group(1)))),

                # A + B * v   (positive slope — inverted slider)
                (r'^({N})\s*\+\s*({N})\s*\*\s*v$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(1)), -eval_num(m.group(2)))),

                # B * v * 0.621371 + A  with negative leading
                (r'^-({N})\s*\*\s*\(\s*v\s*\*\s*0\.621371\s*\)\s*\+\s*({N})$'.replace('{N}', NUM),
                 lambda m: (eval_num(m.group(2)), eval_num(m.group(1)) * 0.621371)),
            ]

            for pattern, extractor in patterns:
                m = re.match(pattern, e)
                if m:
                    try:
                        return extractor(m)
                    except Exception:
                        pass
            return None, None

        intercept, slope = try_linear(expr)

    # Guess role from context clues (very rough heuristic)
    role = "unknown"
    lblock = block.lower()
    if "current.speed()" in lblock or "current.speed" in lblock:
        role = "speed"
    elif "current.incline()" in lblock or "current.inclinepct" in lblock:
        role = "incline"
    elif "current.resistancelvl" in lblock or "resistancelvl" in lblock:
        role = "resistance"
    elif "current.incline()" in lblock:
        role = "incline"

    return SliderInfo(
        role=role,
        initial_y=initial_y,
        track_x=track_x,
        formula_str=formula_body[:120].replace("\n", " ").strip(),
        intercept=intercept,
        slope=slope,
        formula_type=formula_type,
    )


def parse_device_file(path: Path, device_type: str) -> DeviceInfo:
    text = path.read_text(encoding="utf-8")
    class_name = path.stem

    # CalibratedBikeDevice — formula is runtime-computed, skip numeric checks
    if "CalibratedBikeDevice" in class_name:
        return DeviceInfo(class_name=class_name, device_type=device_type, skip=True)

    blocks = _extract_slider_blocks(text)

    if not blocks:
        # No Slider() calls: either inherits from parent or has no sliders
        return DeviceInfo(class_name=class_name, device_type=device_type, inherits=True)

    sliders = [_parse_slider_block(b) for b in blocks]

    # Assign roles from position when heuristic fails.
    # Treadmills: first slider = speed (right side), second = incline (left side)
    # Bikes (2 sliders): first = incline (left), second = resistance (right)
    if device_type == "treadmill":
        defaults = ["speed", "incline"]
    else:
        defaults = ["incline", "resistance"]

    for i, sl in enumerate(sliders):
        if sl.role == "unknown" and i < len(defaults):
            sl.role = defaults[i]

    # Single-slider bikes: determine side from trackX rather than position.
    # trackX > 400 → right side = resistance;  trackX < 200 → left side = incline.
    if device_type == "bike" and len(sliders) == 1:
        tx = sliders[0].track_x
        if tx > 400:
            sliders[0].role = "resistance"
        elif tx < 200:
            sliders[0].role = "incline"

    return DeviceInfo(class_name=class_name, device_type=device_type, sliders=sliders)


# ---------------------------------------------------------------------------
# XML dimens parsing
# ---------------------------------------------------------------------------

def _dp(value_str: str) -> float:
    return float(value_str.replace("dip", "").replace("dp", "").strip())


def _load_slider_dimens(dimens_dir: Path) -> dict:
    """Return a map of qualifier → {name: value} for workout_slider_* keys."""
    configs = {}
    for p in sorted(dimens_dir.rglob("dimens.xml")):
        qualifier = p.parent.name  # e.g. "values-sw720dp-mdpi"
        try:
            tree = ET.parse(p)
        except ET.ParseError:
            continue
        data = {}
        for elem in tree.iter():
            name = elem.get("name", "")
            if not name.startswith("workout_slider"):
                continue
            val = (elem.text or "").strip()
            if val:
                data[name] = val
        if any(k in data for k in ("workout_slider_height", "workout_slider_width")):
            configs[qualifier] = data
    return configs


# ---------------------------------------------------------------------------
# Validation checks
# ---------------------------------------------------------------------------

KNOWN_WIDTHS = [800, 1024, 1280, 1920]

def _nearest_standard_width(w: float) -> Optional[int]:
    """Return the nearest known standard width if within tolerance, else None."""
    for sw in KNOWN_WIDTHS:
        if abs(w - sw) <= WIDTH_TOLERANCE:
            return sw
    return None


def check_track_x(sl: SliderInfo, device_type: str, slider_idx: int) -> list[tuple]:
    """
    Returns list of (level, message) tuples.
    level: "OK" | "WARN" | "ERROR"
    """
    msgs = []
    x = sl.track_x
    if x <= 0:
        msgs.append(("ERROR", f"trackX={x} is not positive"))
        return msgs

    # Classify slider side
    is_right = (
        (device_type == "treadmill" and sl.role == "speed") or
        (device_type == "bike"      and sl.role in ("resistance", "gear"))
    )
    is_left = (
        (device_type == "treadmill" and sl.role == "incline") or
        (device_type == "bike"      and sl.role == "incline")
    )

    if is_left:
        err = abs(x - LEFT_TRACKX_EXPECTED)
        if err <= 2:
            msgs.append(("OK",   f"trackX={x} ✓  (expected {LEFT_TRACKX_EXPECTED:.1f})"))
        elif err <= LEFT_TRACKX_TOLERANCE:
            msgs.append(("WARN", f"trackX={x} is {err:.1f}px off expected {LEFT_TRACKX_EXPECTED:.1f}"))
        else:
            msgs.append(("ERROR", f"trackX={x} is {err:.1f}px off expected {LEFT_TRACKX_EXPECTED:.1f}"))

    elif is_right:
        inferred_w = x + EXPECTED_EDGE_OFFSET_DP
        nearest = _nearest_standard_width(inferred_w)
        if nearest:
            err = abs(inferred_w - nearest)
            if err <= 2:
                msgs.append(("OK",   f"trackX={x} → screen {nearest}px ✓"))
            else:
                msgs.append(("WARN", f"trackX={x} → inferred {inferred_w:.1f}px ≈ {nearest}px (off {err:.1f}px)"))
        else:
            msgs.append(("WARN", f"trackX={x} → inferred screen {inferred_w:.1f}px — not a recognised standard width"))

    else:
        # Unknown side — just report
        msgs.append(("OK", f"trackX={x} (single/other slider — skipping side-check)"))

    return msgs


def check_monotonicity(sl: SliderInfo) -> tuple:
    if sl.formula_type != "linear" or sl.slope is None:
        return ("OK", "non-linear formula — monotonicity not checked")
    # slope > 0 means targetY decreases as v increases → good (slider goes up)
    if sl.slope > 0:
        return ("OK", f"slope={sl.slope:.4f} > 0 ✓")
    elif sl.slope == 0:
        return ("WARN", "slope=0 — formula is constant (slider never moves)")
    else:
        return ("ERROR", f"slope={sl.slope:.4f} < 0 — higher metric → HIGHER Y (inverted slider!)")


def check_sindarin_bounds(sl: SliderInfo, screen_width: Optional[int]) -> list[tuple]:
    if sl.formula_type != "linear" or sl.intercept is None or sl.slope is None:
        return [("OK", "non-linear formula — bounds not checked")]

    screen_h = 1080  # assume 1080px tall at density=1 (landscape); worst case

    msgs = []
    checks = []
    if sl.role == "incline":
        checks = [
            (SINDARIN_MAX_INCLINE,  "max incline 40°"),
            (SINDARIN_MIN_INCLINE,  "min incline −20°"),
        ]
    elif sl.role == "speed":
        checks = [(SINDARIN_PRACTICAL_MAX_KPH, "practical max speed 22 km/h")]
    elif sl.role in ("resistance", "gear"):
        checks = [(SINDARIN_MAX_RESISTANCE, "max resistance 26")]

    for v, label in checks:
        y = int(sl.intercept - sl.slope * v)
        if y < 0:
            msgs.append(("WARN", f"targetY({v}) = {y} < 0 at {label} — would overshoot screen top"))
        elif y > screen_h:
            msgs.append(("WARN", f"targetY({v}) = {y} > {screen_h} at {label} — might overshoot screen bottom"))
        else:
            msgs.append(("OK", f"targetY({v}) = {y} at {label} ✓"))

    return msgs or [("OK", "no bounds to check")]


def check_initial_thumb(sl: SliderInfo) -> tuple:
    if sl.formula_type != "linear" or sl.slope is None or sl.slope == 0:
        return ("OK", f"initialThumbY={sl.initial_y} (non-linear)")
    # Solve: initial_y = intercept - slope * v  →  v = (intercept - initial_y) / slope
    v = (sl.intercept - sl.initial_y) / sl.slope
    # Is this a plausible initial position?
    plausible = True
    note = ""
    if sl.role == "incline":
        if v < -20 or v > 40:
            plausible = False
            note = " ← outside Sindarin incline range, may indicate stale initialThumbY"
    elif sl.role == "speed":
        if v < -2 or v > 30:
            plausible = False
            note = " ← outside expected speed range (suggest: 0–2 km/h for start position)"
    elif sl.role in ("resistance", "gear"):
        if v < 0 or v > 30:
            plausible = False
            note = " ← outside expected resistance range"

    level = "WARN" if not plausible else "OK"
    return (level, f"initialThumbY={sl.initial_y} → assumes v≈{v:.1f} at startup{note}")


# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------

def _infer_screen_width(device: DeviceInfo) -> Optional[int]:
    for sl in device.sliders:
        is_right = (
            (device.device_type == "treadmill" and sl.role == "speed") or
            (device.device_type == "bike"      and sl.role in ("resistance", "gear"))
        )
        if is_right and sl.track_x > 0:
            inferred = sl.track_x + EXPECTED_EDGE_OFFSET_DP
            return _nearest_standard_width(inferred) or int(inferred)
    return None


def run_validation(devices: list[DeviceInfo]) -> int:
    """Print full report; return number of ERRORs."""
    error_count = 0

    # Group by screen width
    by_width = {}
    for dev in devices:
        if dev.skip or dev.inherits:
            continue
        w = _infer_screen_width(dev)
        key = w if w else "UNKNOWN"
        by_width.setdefault(key, []).append(dev)

    # Sort keys: numeric widths ascending, then UNKNOWN
    keys_sorted = sorted(
        by_width.keys(),
        key=lambda k: (0, k) if isinstance(k, int) else (1, 0)
    )

    for w_key in keys_sorted:
        group = by_width[w_key]
        if isinstance(w_key, int) and w_key in KNOWN_WIDTHS:
            print(f"\n{'='*70}")
            print(f"  Screen width: {w_key}px  ({len(group)} devices)")
            print(f"{'='*70}")
        else:
            print(f"\n{'='*70}")
            print(f"  Screen width: {w_key}px  ({len(group)} devices)  ← unrecognised width")
            print(f"{'='*70}")

        for dev in sorted(group, key=lambda d: d.class_name):
            print(f"\n  [{dev.device_type.upper()}] {dev.class_name}")
            if not dev.sliders:
                print("    (no sliders parsed)")
                continue

            for sl in dev.sliders:
                label = f"    {sl.role}  trackX={sl.track_x}"
                if sl.formula_type == "linear" and sl.intercept is not None:
                    label += f"  formula: {sl.intercept:.2f} - {sl.slope:.4f}*v"
                else:
                    label += f"  formula: [{sl.formula_type}]"
                print(label)

                all_msgs = []
                all_msgs += check_track_x(sl, dev.device_type, dev.sliders.index(sl))
                all_msgs.append(check_monotonicity(sl))
                all_msgs += check_sindarin_bounds(sl, w_key if isinstance(w_key, int) else None)
                all_msgs.append(check_initial_thumb(sl))

                for level, msg in all_msgs:
                    if level == "ERROR":
                        error_count += 1
                        marker = "  ✗ ERROR"
                    elif level == "WARN":
                        marker = "  ⚠  WARN"
                    else:
                        marker = "    OK  "
                    print(f"         {marker}  {msg}")

    # Summarise inherited and skipped
    inherited = [d for d in devices if d.inherits]
    skipped   = [d for d in devices if d.skip]

    if inherited:
        print(f"\n{'─'*50}")
        print("  Devices that inherit sliders from parent (not re-analysed):")
        for d in sorted(inherited, key=lambda x: x.class_name):
            print(f"    {d.device_type}  {d.class_name}")

    if skipped:
        print(f"\n{'─'*50}")
        print("  Devices skipped (runtime-computed formula):")
        for d in sorted(skipped, key=lambda x: x.class_name):
            print(f"    {d.device_type}  {d.class_name}")

    return error_count


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print("=== QZ Swipe Target Validator ===")
    print(f"Ground truth: workout_slider_margin={SLIDER_MARGIN_DP}dp, "
          f"workout_slider_width={SLIDER_WIDTH_DP}dp")
    print(f"Expected left-slider trackX ≈ {EXPECTED_EDGE_OFFSET_DP}dp  "
          f"(right: screen_width − {EXPECTED_EDGE_OFFSET_DP}dp)")

    devices = []
    for java_file in sorted(DEVICE_BIKE_DIR.glob("*.java")):
        devices.append(parse_device_file(java_file, "bike"))
    for java_file in sorted(DEVICE_TREADMILL_DIR.glob("*.java")):
        devices.append(parse_device_file(java_file, "treadmill"))

    print(f"Parsed {len(devices)} device files  "
          f"({sum(1 for d in devices if not d.skip and not d.inherits)} with sliders, "
          f"{sum(1 for d in devices if d.inherits)} inherited, "
          f"{sum(1 for d in devices if d.skip)} skipped)")

    errors = run_validation(devices)

    print(f"\n{'='*70}")
    if errors == 0:
        print(f"  RESULT: CLEAN — 0 errors")
    else:
        print(f"  RESULT: {errors} ERROR(s) found — see above")
    print(f"{'='*70}\n")

    return 0 if errors == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

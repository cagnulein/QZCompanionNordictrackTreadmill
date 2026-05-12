#!/usr/bin/env python3
"""
ifit2-control-surface-discovery.py

Capture and probe the iFit2 active-workout control surface.

This is intentionally a discovery tool, not calibration.  It records screenshots,
uiautomator XML, screen geometry, recent iFit telemetry, and optional tap/scroll
probe results so we can model iFit2's button/list behavior before changing the
runtime control path.

Examples:
    python3 tools/discover-device/ifit2-control-surface-discovery.py \
        --device 192.168.1.213:5555 --state collapsed_charts_on

    python3 tools/discover-device/ifit2-control-surface-discovery.py \
        --device 192.168.1.213:5555 --state collapsed_charts_on \
        --tap incline,113,496,0

    python3 tools/discover-device/ifit2-control-surface-discovery.py \
        --device 192.168.1.213:5555 --state resistance_scroll_down \
        --scroll resistance,1807,760,1807,300,500
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime


GRADE_RE = re.compile(r"Changed Grade to:\s*([-\d.]+)")
RESISTANCE_RE = re.compile(r"Changed (?:Resistance|CurrentGear) to:\s*([-\d.]+)")


def adb(device, *args, check=True, binary=False):
    return subprocess.run(
        ["adb", "-s", device] + list(args),
        capture_output=True,
        text=not binary,
        check=check,
    )


def safe_name(name):
    text = re.sub(r"[^A-Za-z0-9_.-]+", "_", name.strip())
    return text.strip("_") or "state"


def ensure_dir(path):
    os.makedirs(path, exist_ok=True)
    return path


def screen_size(device):
    out = adb(device, "shell", "wm", "size").stdout
    m = re.search(r"(\d+)x(\d+)", out)
    if not m:
        raise RuntimeError(f"Could not parse screen size from: {out!r}")
    return int(m.group(1)), int(m.group(2))


def capture_screenshot(device, path):
    proc = adb(device, "exec-out", "screencap", "-p", binary=True)
    with open(path, "wb") as f:
        f.write(proc.stdout)


def capture_ui_xml(device, path):
    remote = "/sdcard/qz_ifit2_ui.xml"
    adb(device, "shell", "uiautomator", "dump", remote, check=False)
    xml_text = adb(device, "shell", "cat", remote, check=False).stdout
    with open(path, "w", encoding="utf-8") as f:
        f.write(xml_text)
    return xml_text


def parse_bounds(bounds):
    nums = [int(n) for n in re.findall(r"\d+", bounds or "")]
    if len(nums) != 4:
        return None
    left, top, right, bottom = nums
    return {
        "left": left,
        "top": top,
        "right": right,
        "bottom": bottom,
        "centerX": (left + right) // 2,
        "centerY": (top + bottom) // 2,
    }


def extract_accessible_nodes(xml_text):
    nodes = []
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return nodes
    for node in root.iter("node"):
        text = (node.get("text") or "").strip()
        desc = (node.get("content-desc") or "").strip()
        if not text and not desc:
            continue
        bounds = parse_bounds(node.get("bounds"))
        nodes.append({
            "text": text,
            "contentDescription": desc,
            "resourceId": node.get("resource-id") or "",
            "class": node.get("class") or "",
            "clickable": node.get("clickable") == "true",
            "bounds": bounds,
        })
    return nodes


def read_recent_telemetry(device):
    out = adb(device, "logcat", "-d", "-s", "mono-stdout", check=False).stdout
    events = []
    latest = {"grade": None, "resistance": None}
    for line in out.splitlines():
        m = GRADE_RE.search(line)
        if m:
            value = float(m.group(1))
            latest["grade"] = value
            events.append({"metric": "grade", "value": value, "line": line})
            continue
        m = RESISTANCE_RE.search(line)
        if m:
            value = float(m.group(1))
            latest["resistance"] = value
            events.append({"metric": "resistance", "value": value, "line": line})
    return {
        "latest": latest,
        "events": events[-25:],
    }


def wait_for_metric(device, axis, timeout_s):
    metric = "grade" if axis == "incline" else "resistance"
    deadline = time.time() + timeout_s
    latest = None
    events = []
    while time.time() < deadline:
        telemetry = read_recent_telemetry(device)
        for event in telemetry["events"]:
            if event["metric"] == metric:
                latest = event["value"]
                events.append(event)
        if latest is not None:
            return latest, events[-10:]
        time.sleep(0.25)
    return latest, events[-10:]


def parse_tap(value):
    parts = [p.strip() for p in value.split(",")]
    if len(parts) not in (3, 4):
        raise argparse.ArgumentTypeError(
            "--tap must be axis,x,y or axis,x,y,expectedValue"
        )
    axis = parts[0].lower()
    if axis not in ("incline", "resistance"):
        raise argparse.ArgumentTypeError("tap axis must be incline or resistance")
    try:
        x = int(parts[1])
        y = int(parts[2])
        expected = float(parts[3]) if len(parts) == 4 and parts[3] else None
    except ValueError as e:
        raise argparse.ArgumentTypeError(str(e))
    return {"axis": axis, "x": x, "y": y, "expected": expected}


def parse_scroll(value):
    parts = [p.strip() for p in value.split(",")]
    if len(parts) not in (5, 6):
        raise argparse.ArgumentTypeError(
            "--scroll must be axis,x1,y1,x2,y2 or axis,x1,y1,x2,y2,durationMs"
        )
    axis = parts[0].lower()
    if axis not in ("incline", "resistance"):
        raise argparse.ArgumentTypeError("scroll axis must be incline or resistance")
    try:
        x1, y1, x2, y2 = [int(p) for p in parts[1:5]]
        duration = int(parts[5]) if len(parts) == 6 else 500
    except ValueError as e:
        raise argparse.ArgumentTypeError(str(e))
    return {
        "axis": axis,
        "x1": x1,
        "y1": y1,
        "x2": x2,
        "y2": y2,
        "durationMs": duration,
    }


def run_tap_probe(device, tap_probe, timeout_s):
    adb(device, "logcat", "-c")
    time.sleep(0.2)
    started = time.time()
    adb(device, "shell", "input", "tap", str(tap_probe["x"]), str(tap_probe["y"]))
    value, events = wait_for_metric(device, tap_probe["axis"], timeout_s)
    expected = tap_probe["expected"]
    matched = expected is not None and value is not None and abs(value - expected) <= 0.01
    return {
        "type": "tap",
        "startedAt": started,
        "axis": tap_probe["axis"],
        "x": tap_probe["x"],
        "y": tap_probe["y"],
        "expected": expected,
        "observed": value,
        "matchedExpected": matched if expected is not None else None,
        "events": events,
    }


def run_scroll_probe(device, scroll_probe, settle_s):
    started = time.time()
    adb(
        device,
        "shell",
        "input",
        "swipe",
        str(scroll_probe["x1"]),
        str(scroll_probe["y1"]),
        str(scroll_probe["x2"]),
        str(scroll_probe["y2"]),
        str(scroll_probe["durationMs"]),
    )
    time.sleep(settle_s)
    return {
        "type": "scroll",
        "startedAt": started,
        **scroll_probe,
    }


def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, sort_keys=True)
        f.write("\n")


def append_jsonl(path, obj):
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps(obj, sort_keys=True))
        f.write("\n")


def default_out_dir():
    ts = datetime.now().strftime("%Y%m%d-%H%M%S")
    return os.path.join("/tmp", f"ifit2-discovery-{ts}")


def main():
    parser = argparse.ArgumentParser(
        description="Capture and probe iFit2 active-workout controls."
    )
    parser.add_argument("--device", required=True,
                        help="ADB device address, e.g. 192.168.1.213:5555")
    parser.add_argument("--state", required=True,
                        help="Human-readable UI state label, e.g. collapsed_charts_on")
    parser.add_argument("--out-dir", default=default_out_dir(),
                        help="Output directory (default: /tmp/ifit2-discovery-<timestamp>)")
    parser.add_argument("--note", default="",
                        help="Optional note to include in the snapshot metadata")
    parser.add_argument("--tap", action="append", type=parse_tap, default=[],
                        help="Probe a tap: axis,x,y or axis,x,y,expectedValue")
    parser.add_argument("--scroll", action="append", type=parse_scroll, default=[],
                        help="Probe a scroll: axis,x1,y1,x2,y2[,durationMs]")
    parser.add_argument("--probe-timeout", type=float, default=4.0,
                        help="Seconds to wait for telemetry after each tap probe")
    parser.add_argument("--scroll-settle", type=float, default=1.0,
                        help="Seconds to wait after each scroll probe before recapturing")
    args = parser.parse_args()

    out_dir = ensure_dir(args.out_dir)
    state = safe_name(args.state)
    state_dir = ensure_dir(os.path.join(out_dir, state))

    width, height = screen_size(args.device)
    screenshot_path = os.path.join(state_dir, f"{state}.png")
    xml_path = os.path.join(state_dir, f"{state}.xml")
    metadata_path = os.path.join(state_dir, f"{state}.json")
    session_path = os.path.join(out_dir, "session.jsonl")

    print(f"Output: {state_dir}")
    print(f"Screen: {width}x{height}")

    print("Capturing baseline screenshot and UI XML...")
    capture_screenshot(args.device, screenshot_path)
    xml_text = capture_ui_xml(args.device, xml_path)
    nodes = extract_accessible_nodes(xml_text)
    baseline_telemetry = read_recent_telemetry(args.device)

    probes = []
    for scroll_probe in args.scroll:
        print(
            "Scroll probe "
            f"{scroll_probe['axis']} ({scroll_probe['x1']},{scroll_probe['y1']})"
            f" -> ({scroll_probe['x2']},{scroll_probe['y2']})"
        )
        result = run_scroll_probe(args.device, scroll_probe, args.scroll_settle)
        probes.append(result)

    for tap_probe in args.tap:
        expected = tap_probe["expected"]
        suffix = f" expecting {expected}" if expected is not None else ""
        print(f"Tap probe {tap_probe['axis']} at ({tap_probe['x']},{tap_probe['y']}){suffix}")
        result = run_tap_probe(args.device, tap_probe, args.probe_timeout)
        probes.append(result)
        print(f"  observed: {result['observed']}")

    post_screenshot_path = None
    post_xml_path = None
    post_nodes = []
    post_telemetry = None
    if probes:
        post_screenshot_path = os.path.join(state_dir, f"{state}-after-probes.png")
        post_xml_path = os.path.join(state_dir, f"{state}-after-probes.xml")
        print("Capturing post-probe screenshot and UI XML...")
        capture_screenshot(args.device, post_screenshot_path)
        post_xml_text = capture_ui_xml(args.device, post_xml_path)
        post_nodes = extract_accessible_nodes(post_xml_text)
        post_telemetry = read_recent_telemetry(args.device)

    record = {
        "capturedAt": datetime.now().isoformat(timespec="seconds"),
        "device": args.device,
        "state": state,
        "note": args.note,
        "screen": {"width": width, "height": height},
        "files": {
            "screenshot": screenshot_path,
            "uiXml": xml_path,
            "postProbeScreenshot": post_screenshot_path,
            "postProbeUiXml": post_xml_path,
        },
        "baselineTelemetry": baseline_telemetry,
        "accessibleNodes": nodes,
        "probes": probes,
        "postProbeTelemetry": post_telemetry,
        "postProbeAccessibleNodes": post_nodes,
    }

    write_json(metadata_path, record)
    append_jsonl(session_path, record)

    print(f"Wrote metadata: {metadata_path}")
    print(f"Appended session log: {session_path}")
    if nodes:
        print(f"Accessible nodes found: {len(nodes)}")
    else:
        print("Accessible nodes found: 0 (expected for some iFit/Xamarin surfaces)")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as e:
        sys.stderr.write(f"Command failed: {' '.join(e.cmd)}\n")
        if e.stdout:
            sys.stderr.write(e.stdout if isinstance(e.stdout, str) else e.stdout.decode())
        if e.stderr:
            sys.stderr.write(e.stderr if isinstance(e.stderr, str) else e.stderr.decode())
        sys.exit(e.returncode)

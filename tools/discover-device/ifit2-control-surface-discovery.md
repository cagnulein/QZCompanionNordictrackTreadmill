# iFit2 Control Surface Discovery

This runbook captures the iFit2 active-workout UI before we change QZCompanion's
runtime control path.  It is for discovery only: screenshots, UI XML, telemetry,
and optional tap/scroll probes.

## Prerequisites

- ADB connected to the iFit tablet.
- iFit2 open in an active Quick Workout/manual ride.
- The bike is empty and safe to move; tap probes can change incline/resistance.
- `mono-stdout` telemetry is flowing during the workout.

Check connectivity:

```bash
adb devices
adb -s 192.168.1.213:5555 shell wm size
```

## Baseline Captures

Capture each visible UI state manually.  Before each command, adjust the iFit2 UI
on the tablet to the named state.

```bash
OUT=/tmp/ifit2-s22i-discovery
DEVICE=192.168.1.213:5555

python3 tools/discover-device/ifit2-control-surface-discovery.py \
  --device $DEVICE \
  --out-dir $OUT \
  --state collapsed_charts_on \
  --note "Bottom workout bar collapsed; charts visible"

python3 tools/discover-device/ifit2-control-surface-discovery.py \
  --device $DEVICE \
  --out-dir $OUT \
  --state expanded_charts_on \
  --note "Bottom workout bar expanded; charts visible"

python3 tools/discover-device/ifit2-control-surface-discovery.py \
  --device $DEVICE \
  --out-dir $OUT \
  --state collapsed_metrics_only \
  --note "Bottom workout bar collapsed; charts hidden"
```

The script writes one directory per state plus a session log:

```text
/tmp/ifit2-s22i-discovery/
  session.jsonl
  collapsed_charts_on/
    collapsed_charts_on.png
    collapsed_charts_on.xml
    collapsed_charts_on.json
```

## Tap Probes

Use tap probes only after baseline screenshots are captured.  The expected value
is optional, but including it makes mismatches obvious.

From the first iFit2 S22i screenshot, visible approximate centers were:

- Incline column: `x=113`, values `9, 6, 3, 0, -3, -6, -10`
- Resistance column: `x=1807`, values `16, 14, 12, 10, 8, 6, 4`

Example probes:

```bash
python3 tools/discover-device/ifit2-control-surface-discovery.py \
  --device $DEVICE \
  --out-dir $OUT \
  --state collapsed_charts_on_tap_incline_0 \
  --tap incline,113,496,0

python3 tools/discover-device/ifit2-control-surface-discovery.py \
  --device $DEVICE \
  --out-dir $OUT \
  --state collapsed_charts_on_tap_resistance_10 \
  --tap resistance,1807,496,10
```

Each tap clears logcat first, performs the tap, waits for fresh iFit telemetry,
then records the observed value in the state JSON.

## Scroll Probes

Use scroll probes to learn whether the side button lists expose hidden values and
whether button centers remain stable after scrolling.

```bash
python3 tools/discover-device/ifit2-control-surface-discovery.py \
  --device $DEVICE \
  --out-dir $OUT \
  --state resistance_scroll_down \
  --scroll resistance,1807,760,1807,300,500

python3 tools/discover-device/ifit2-control-surface-discovery.py \
  --device $DEVICE \
  --out-dir $OUT \
  --state incline_scroll_up \
  --scroll incline,113,300,113,760,500
```

After every scroll, compare the `*-after-probes.png` image with the baseline
image for that state.  If the visible values moved, run tap probes against the
new visible centers.

## Discovery Matrix

Capture at least these state combinations:

| State | What to Record |
|---|---|
| Bottom bar collapsed, charts on | Visible incline/resistance values and tap results |
| Bottom bar expanded, charts on | Whether button centers or unsafe bottom zones change |
| Bottom bar collapsed, charts off | Whether side lists gain or lose vertical space |
| Bottom bar expanded, charts off | Worst-case overlap with workout controls |
| Left list scrolled high/mid/low | Incline values exposed and scroll limits |
| Right list scrolled high/mid/low | Resistance values exposed and scroll limits |

## Output Review

The implementation work should use the generated `session.jsonl` to decide:

- whether iFit2 can be controlled by stable button centers;
- whether list scroll position must be detected before tapping;
- which regions must be treated as unsafe because they hit pause/settings/chart controls;
- whether `uiautomator` exposes any useful text, or whether screenshot plus telemetry
  confirmation is the only reliable source.

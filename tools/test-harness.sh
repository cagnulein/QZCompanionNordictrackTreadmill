#!/bin/bash
# test-harness.sh — simulate a Zwift ride against QZCompanion
#
# Usage:
#   ./test-harness.sh                  dry-run: prints expected swipe commands
#   ./test-harness.sh 192.168.1.213    live: sends UDP to running QZCompanion on S22i
#
# In dry-run mode no network traffic is sent; the script prints what commands the
# bike should receive so you can verify the formula is correct before a live test.
# In live mode, watch the incline slider on the bike respond to each grade change.
#
# Requirements (live mode only):
#   nc (netcat) — available on macOS and most Linux distros
#   QZCompanion installed and running on the S22i with "S22i Bike" selected

TARGET=${1:-"DRY_RUN"}
PORT=8003
DELAY=1.5   # seconds between messages — outside the 500ms throttle window

# ── S22i formula (S22iNoAdbDevice — AccessibilityService path) ────────────────
# x=75, y1=previous logical targetY (self-correcting from iFit log in production)
# Single linear fit: targetY(v) = (int)(622 - 18.57*v) for all v.
# Calibrated 2026-04-19 (positive) + 2026-04-22 (negative); slope=18.57 px/%, intercept=622.
# hysteresis=0: AccessibilityService swipes land exactly at targetY (no spring-back).
expected_y() {
    local grade=$1
    echo "$grade" | awk '{printf "%d", int(622 - 18.57 * $1)}'
}

# No hysteresis overshoot — dispatch lands exactly at targetY.
dispatch_y() {
    local from_y=$1
    local to_y=$2
    echo "$to_y"
}

expected_swipe() {
    local y1=$1
    local grade=$2
    local y2 disp
    y2=$(expected_y "$grade")
    disp=$(dispatch_y "$y1" "$y2")
    echo "input swipe 75 $y1 75 $disp 200  (grade ${grade}%, targetY=${y2})"
}

send_grade() {
    local grade=$1
    local y1=$2
    if [ "$TARGET" = "DRY_RUN" ]; then
        echo "  $(expected_swipe "$y1" "$grade")"
    else
        echo -n "${grade};0" | nc -u -w1 "$TARGET" "$PORT"
        echo "  sent grade ${grade}%  →  $(expected_swipe "$y1" "$grade")"
    fi
}

# ── Alpe du Zwift — simplified profile ───────────────────────────────────────
run_profile() {
    local -a grades=("$@")
    local y1=622  # S22i initial incline position (grade=0)

    for grade in "${grades[@]}"; do
        send_grade "$grade" "$y1"
        y1=$(expected_y "$grade")
        sleep "$DELAY"
    done
}

# ── Sentinel ──────────────────────────────────────────────────────────────────
send_sentinel() {
    if [ "$TARGET" = "DRY_RUN" ]; then
        echo "  (sentinel -1;-100 — no swipe expected)"
    else
        echo -n "-1;-100" | nc -u -w1 "$TARGET" "$PORT"
        echo "  sent sentinel"
    fi
}

# ── Main ─────────────────────────────────────────────────────────────────────
if [ "$TARGET" = "DRY_RUN" ]; then
    echo "=== QZCompanion Test Harness — DRY RUN ==="
    echo "Expected swipe commands (no UDP sent):"
    echo ""
else
    echo "=== QZCompanion Test Harness — LIVE ==="
    echo "Target: $TARGET:$PORT"
    echo "Sending UDP grade changes every ${DELAY}s"
    echo "Watch the incline slider on the S22i respond."
    echo ""
fi

echo "--- Profile: flat start → climb → descent → flat → negative ---"
run_profile 0.0 3.0 7.0 10.0 12.0 10.0 7.0 3.0 0.0 -1.0 -3.0 -8.0 -3.0 -1.0 0.0
echo ""

echo "--- Sentinel (end of ride) ---"
send_sentinel
echo ""

echo "Done."

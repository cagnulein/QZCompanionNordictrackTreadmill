#!/usr/bin/env bash
# analyze-ride.sh — Post-ride analysis of QZCompanion performance data.
#
# Usage:
#   ./analyze-ride.sh <ride_dir>
#
# where <ride_dir> is the directory produced by monitor-ride.sh, containing:
#   metrics.csv   — per-interval samples
#   logcat.txt    — continuous logcat capture
#
# Output: a human-readable summary report printed to stdout.
# Also writes <ride_dir>/report.txt with the same content.

set -euo pipefail

RIDE_DIR=${1:-""}
if [[ -z "${RIDE_DIR}" || ! -d "${RIDE_DIR}" ]]; then
    echo "Usage: $0 <ride_dir>" >&2
    exit 1
fi

CSV="${RIDE_DIR}/metrics.csv"
LOGCAT="${RIDE_DIR}/logcat.txt"
REPORT="${RIDE_DIR}/report.txt"

if [[ ! -f "${CSV}" ]]; then
    echo "ERROR: ${CSV} not found" >&2
    exit 1
fi

# ── awk helpers ───────────────────────────────────────────────────────────────

# stats <col_index> from CSV (1-based, skips header)
col_stats() {
    local col=$1
    awk -F, -v col="${col}" '
        NR==1 { next }
        {
            v = $col + 0
            sum += v
            count++
            if (count == 1 || v < min) min = v
            if (count == 1 || v > max) max = v
        }
        END {
            if (count > 0)
                printf "min=%-8s max=%-8s avg=%.1f (n=%d)", min, max, sum/count, count
            else
                print "no data"
        }
    ' "${CSV}"
}

# last value in a column
col_last() {
    local col=$1
    awk -F, -v col="${col}" 'NR>1 { last=$col } END { print last+0 }' "${CSV}"
}

# first value in a column
col_first() {
    local col=$1
    awk -F, -v col="${col}" 'NR==2 { print $col+0; exit }' "${CSV}"
}

# duration from elapsed_s column
ride_duration() {
    awk -F, 'NR>1 { last=$1 } END { printf "%dm%ds", int(last/60), last%60 }' "${CSV}"
}

# delta between first and last for a column
col_delta() {
    local col=$1
    local first last
    first=$(col_first "${col}")
    last=$(col_last "${col}")
    echo $(( last - first ))
}

# ── logcat stats ──────────────────────────────────────────────────────────────

logcat_count() {
    local pattern=$1
    if [[ -f "${LOGCAT}" ]]; then
        # grep -c exits 1 when count=0 but still prints "0"; || true suppresses the exit
        grep -c "${pattern}" "${LOGCAT}" 2>/dev/null || true
    else
        echo 0
    fi
}

logcat_errors() {
    if [[ -f "${LOGCAT}" ]]; then
        grep -c " E " "${LOGCAT}" 2>/dev/null || true
    else
        echo 0
    fi
}

# ── report ────────────────────────────────────────────────────────────────────

{
DURATION=$(ride_duration)
SAMPLE_COUNT=$(( $(wc -l < "${CSV}") - 1 ))

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║            QZCompanion Ride Analysis Report                  ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "  Ride directory : ${RIDE_DIR}"
echo "  Duration       : ${DURATION}"
echo "  Samples        : ${SAMPLE_COUNT}"
echo ""

echo "── Memory ──────────────────────────────────────────────────────"
echo "  Heap RSS (KB)  : $(col_stats 3)"
echo ""

echo "── Threads ─────────────────────────────────────────────────────"
echo "  Thread count   : $(col_stats 4)"
echo ""

echo "── File Descriptors ────────────────────────────────────────────"
FD_STATS=$(col_stats 5)
if [[ "${FD_STATS}" == *"min=0"*"max=0"* ]]; then
    echo "  FD count       : n/a (requires root or debuggable build)"
else
    echo "  FD count       : ${FD_STATS}"
fi
echo ""

echo "── CPU ─────────────────────────────────────────────────────────"
echo "  CPU %          : $(col_stats 6)"
echo ""

echo "── iFit Log Size ───────────────────────────────────────────────"
echo "  Log size (KB)  : $(col_stats 7)"
IFIT_FIRST=$(col_first 7)
IFIT_LAST=$(col_last 7)
IFIT_GROWTH=$(( IFIT_LAST - IFIT_FIRST ))
echo "  Growth         : ${IFIT_GROWTH} KB over ride"
echo ""

echo "── Network (wlan0, cumulative deltas over ride) ─────────────────"
TX_DELTA=$(col_delta 8)
TX_PKT_DELTA=$(col_delta 9)
RX_DELTA=$(col_delta 10)
RX_PKT_DELTA=$(col_delta 11)
DURATION_S=$(awk -F, 'NR>1 { last=$1 } END { print last+0 }' "${CSV}")

echo "  TX bytes  : ${TX_DELTA}"
echo "  TX packets: ${TX_PKT_DELTA}"
echo "  RX bytes  : ${RX_DELTA}"
echo "  RX packets: ${RX_PKT_DELTA}"
if [[ "${DURATION_S}" -gt 0 ]]; then
    TX_PPS=$(( TX_PKT_DELTA / DURATION_S ))
    RX_PPS=$(( RX_PKT_DELTA / DURATION_S ))
    echo "  TX pkt/s  : ${TX_PPS}  (avg over ride)"
    echo "  RX pkt/s  : ${RX_PPS}  (avg over ride)"
fi
echo ""

if [[ -f "${LOGCAT}" ]]; then
    echo "── Logcat Events ───────────────────────────────────────────────"
    SWIPE_COUNT=$(logcat_count "apply.*bike\|apply.*treadmill\|applyIncline\|applyResistance\|applySpeed")
    DEDUP_COUNT=$(logcat_count "de-dup:")
    THROTTLE_COUNT=$(logcat_count "throttle:")
    ERROR_COUNT=$(logcat_errors)
    INJECT_FAIL=$(logcat_count "INJECT_EVENTS")
    ADB_DOWN=$(logcat_count "ADB DOWN")
    echo "  Commands dispatched: ${SWIPE_COUNT}"
    echo "  De-dup blocks      : ${DEDUP_COUNT}"
    echo "  Throttle blocks    : ${THROTTLE_COUNT}"
    echo "  Log errors (E/)    : ${ERROR_COUNT}"
    echo "  INJECT_EVENTS fail : ${INJECT_FAIL}"
    echo "  ADB DOWN events    : ${ADB_DOWN}"
    echo ""
fi

echo "── Thresholds / Flags ──────────────────────────────────────────"
HEAP_MAX=$(awk -F, 'NR>1 { if ($3+0 > max) max=$3+0 } END { print max+0 }' "${CSV}")
THREAD_MAX=$(awk -F, 'NR>1 { if ($4+0 > max) max=$4+0 } END { print max+0 }' "${CSV}")
FD_MAX=$(awk -F, 'NR>1 { if ($5+0 > max) max=$5+0 } END { print max+0 }' "${CSV}")

ISSUES=0
if (( HEAP_MAX > 200000 )); then
    echo "  [WARN] Peak heap ${HEAP_MAX} KB > 200 MB — possible memory pressure"
    ISSUES=$(( ISSUES + 1 ))
fi
if (( THREAD_MAX > 50 )); then
    echo "  [WARN] Peak thread count ${THREAD_MAX} > 50 — possible thread leak"
    ISSUES=$(( ISSUES + 1 ))
fi
if (( FD_MAX > 0 && FD_MAX > 200 )); then
    echo "  [WARN] Peak FD count ${FD_MAX} > 200 — possible FD leak"
    ISSUES=$(( ISSUES + 1 ))
fi
if [[ -f "${LOGCAT}" ]]; then
    if (( INJECT_FAIL > 0 )); then
        echo "  [WARN] INJECT_EVENTS failures detected — check accessibility permission"
        ISSUES=$(( ISSUES + 1 ))
    fi
    if (( ERROR_COUNT > 10 )); then
        echo "  [WARN] More than 10 logcat errors — review ${LOGCAT}"
        ISSUES=$(( ISSUES + 1 ))
    fi
fi
# Check for monotonic growth in heap (potential leak indicator)
HEAP_TREND=$(awk -F, '
    NR==1 { next }
    {
        heap=$3+0
        if (NR==2) { prev=heap; next }
        if (heap >= prev) inc++
        else dec++
        prev=heap
    }
    END {
        if (inc+dec > 0 && inc/(inc+dec) > 0.8)
            print "GROWING"
        else
            print "STABLE"
    }
' "${CSV}")
if [[ "${HEAP_TREND}" == "GROWING" ]]; then
    echo "  [WARN] Heap growing monotonically in >80% of samples — possible memory leak"
    ISSUES=$(( ISSUES + 1 ))
fi

if (( ISSUES == 0 )); then
    echo "  All checks passed — no issues detected"
fi
echo ""
echo "════════════════════════════════════════════════════════════════"

} | tee "${REPORT}"

echo ""
echo "Report saved to: ${REPORT}"

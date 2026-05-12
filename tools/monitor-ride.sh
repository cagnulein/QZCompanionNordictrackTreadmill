#!/usr/bin/env bash
# monitor-ride.sh — Capture QZCompanion runtime metrics via ADB during a ride.
#
# Usage:
#   ./monitor-ride.sh [interval_seconds] [output_dir]
#
# Defaults:
#   interval_seconds = 60
#   output_dir       = ./ride-$(date +%Y%m%d-%H%M%S)
#
# What it captures every interval:
#   heap_rss_kb   — VmRSS from /proc/<pid>/status  (actual RAM used)
#   threads       — Threads count from /proc/<pid>/status
#   fd_count      — number of open file descriptors in /proc/<pid>/fd
#   cpu_pct       — CPU% from dumpsys cpuinfo (averaged over last interval)
#   ifit_log_kb   — size in KB of the iFit log file being polled
#   net_tx_bytes  — wlan0 cumulative TX bytes from /proc/net/dev
#   net_tx_pkts   — wlan0 cumulative TX packets
#   net_rx_bytes  — wlan0 cumulative RX bytes
#   net_rx_pkts   — wlan0 cumulative RX packets
#
# Logcat is captured continuously in a background process and saved to
# <output_dir>/logcat.txt. All QZ: tags are included automatically.
#
# Press Ctrl-C to stop the ride and finalize output.

set -euo pipefail

INTERVAL=${1:-60}
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
OUT_DIR=${2:-"./ride-${TIMESTAMP}"}
CSV="${OUT_DIR}/metrics.csv"
LOGCAT_FILE="${OUT_DIR}/logcat.txt"
PKG="org.cagnulein.qzcompanionnordictracktreadmill"

# ── pre-flight ────────────────────────────────────────────────────────────────

if ! command -v adb &>/dev/null; then
    echo "ERROR: adb not found — install Android Platform Tools and ensure it is on PATH" >&2
    exit 1
fi

if ! adb get-state &>/dev/null; then
    echo "ERROR: No device connected (run: adb devices)" >&2
    exit 1
fi

mkdir -p "${OUT_DIR}"
echo "Output directory: ${OUT_DIR}"

# ── helpers ───────────────────────────────────────────────────────────────────

adb_shell() { adb shell "$@" 2>/dev/null | tr -d '\r'; }

get_pid() {
    # || true prevents set -e from killing the script when the app isn't running
    adb shell "pidof ${PKG}" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true
}

get_ifit_log_path() {
    # Prefer the v2 path; fall back to wolflogs
    local v2="/sdcard/android/data/com.ifit.glassos_service/files/.valinorlogs/log.latest.txt"
    if adb shell "[ -f ${v2} ] && echo yes" 2>/dev/null | tr -d '\r' | grep -q yes; then
        echo "${v2}"
    else
        # Filter and sort locally — Android toybox pipeline behaviour is unreliable
        local filename
        filename=$(adb shell "ls /sdcard/.wolflogs/" 2>/dev/null \
            | tr -d '\r' \
            | grep -E '_logs.*\.txt$' \
            | sort \
            | tail -1)
        [[ -n "${filename}" ]] && echo "/sdcard/.wolflogs/${filename}"
    fi
}

# ── CSV header ────────────────────────────────────────────────────────────────

echo "elapsed_s,pid,heap_rss_kb,threads,fd_count,cpu_pct,ifit_log_kb,net_tx_bytes,net_tx_pkts,net_rx_bytes,net_rx_pkts" \
    | tee "${CSV}"

# ── start logcat capture ──────────────────────────────────────────────────────

echo "Starting continuous logcat capture → ${LOGCAT_FILE}"
adb logcat -v time "*:I" \
    > "${LOGCAT_FILE}" 2>&1 &
LOGCAT_PID=$!

cleanup() {
    echo ""
    echo "Stopping logcat (pid ${LOGCAT_PID})..."
    kill "${LOGCAT_PID}" 2>/dev/null || true
    echo "Done. Files saved to ${OUT_DIR}/"
    echo "  Metrics : ${CSV}"
    echo "  Logcat  : ${LOGCAT_FILE}"
    echo ""
    echo "Run tools/analyze-ride.sh ${OUT_DIR} for a summary report."
}
trap cleanup EXIT INT TERM

# ── main loop ─────────────────────────────────────────────────────────────────

START_S=$(date +%s)
IFIT_LOG=$(get_ifit_log_path)
echo "iFit log path: ${IFIT_LOG:-<not found>}"
echo ""
echo "Polling every ${INTERVAL}s. Press Ctrl-C to stop."
echo ""

while true; do
    NOW_S=$(date +%s)
    ELAPSED=$(( NOW_S - START_S ))

    PID=$(get_pid)
    if [[ -z "${PID}" ]]; then
        echo "[${ELAPSED}s] WARNING: ${PKG} not running"
        sleep "${INTERVAL}"
        continue
    fi

    # heap + threads from /proc/<pid>/status
    STATUS=$(adb_shell "cat /proc/${PID}/status")
    HEAP_RSS=$(echo "${STATUS}" | awk '/^VmRSS:/{print $2}')
    THREADS=$(echo "${STATUS}"  | awk '/^Threads:/{print $2}')

    # open FD count
    FD_COUNT=$(adb_shell "ls /proc/${PID}/fd 2>/dev/null | wc -l" | tr -d ' ')

    # CPU% from dumpsys cpuinfo — find the line for our package
    CPU_PCT=$(adb_shell "dumpsys cpuinfo" \
        | grep "${PKG}" \
        | head -1 \
        | grep -oE '[0-9]+(\.[0-9]+)?%' \
        | head -1 \
        | tr -d '%' \
        || true)
    CPU_PCT=${CPU_PCT:-0}

    # iFit log size in KB
    if [[ -n "${IFIT_LOG}" ]]; then
        IFIT_KB=$(adb_shell "du -k ${IFIT_LOG} 2>/dev/null | awk '{print \$1}'" || true)
        IFIT_KB=${IFIT_KB:-0}
    else
        IFIT_KB=0
    fi

    # Network counters from /proc/net/dev (wlan0)
    # Format: iface: rx_bytes rx_pkts ... tx_bytes tx_pkts ...
    NET_LINE=$(adb_shell "cat /proc/net/dev" | grep wlan0 || true)
    NET_RX_BYTES=$(echo "${NET_LINE}" | awk '{print $2}')
    NET_RX_PKTS=$(echo "${NET_LINE}"  | awk '{print $3}')
    NET_TX_BYTES=$(echo "${NET_LINE}" | awk '{print $10}')
    NET_TX_PKTS=$(echo "${NET_LINE}"  | awk '{print $11}')

    ROW="${ELAPSED},${PID},${HEAP_RSS:-0},${THREADS:-0},${FD_COUNT:-0},${CPU_PCT},${IFIT_KB},${NET_TX_BYTES:-0},${NET_TX_PKTS:-0},${NET_RX_BYTES:-0},${NET_RX_PKTS:-0}"
    echo "${ROW}" | tee -a "${CSV}"

    sleep "${INTERVAL}"
done

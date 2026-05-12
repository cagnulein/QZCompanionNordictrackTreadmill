# Runbook: 1-Hour Production Ride Profile

Captures a full performance baseline for QZCompanion during a real Zwift ride on the S22i.
The output — `metrics.csv`, `logcat.txt`, and `report.txt` — is the ground truth for memory,
CPU, dispatch rate, and de-dup behaviour under sustained load.

Run this once per firmware update, app release, or any change to the dispatch pipeline.

---

## Prerequisites

**On the development machine:**
- `adb` on PATH (`adb version` prints without error)
- This repo checked out (`tools/monitor-ride.sh` and `tools/analyze-ride.sh` present and executable)
- At least 50 MB free disk space for logcat output

**On the S22i:**
- QZCompanion installed and the correct device selected (`S22i Bike`)
- ADB debugging enabled (Settings → Developer options → USB debugging or wireless ADB)
- Zwift connected and a route loaded (Watopia Hilly Route recommended — it exercises the full incline dispatch range)
- QZ app connected to Zwift and streaming to QZCompanion

**Verify ADB connection before starting:**
```bash
adb devices
# Should show one device, not "offline"
adb shell pidof org.cagnulein.qzcompanionnordictracktreadmill
# Should print a PID — if empty, QZCompanion is not running
```

---

## Pre-Ride Checklist

- [ ] ADB device listed and online
- [ ] QZCompanion PID confirmed running
- [ ] Zwift ride started and grade changes visible on the S22i display
- [ ] At least 5 minutes of warm-up riding done (confirms QZCompanion is dispatching before profiling starts)
- [ ] Dev machine plugged in (1-hour logcat capture is disk- and CPU-light but don't let the machine sleep)
- [ ] Terminal window large enough to see the polling output without wrapping

---

## Step 1 — Start Monitoring

Open a terminal in the repo root and run:

```bash
tools/monitor-ride.sh 60
```

The `60` is the polling interval in seconds. For a 1-hour ride this produces ~60 CSV rows —
enough resolution to see trends without too much noise.

Expected startup output:
```
Output directory: ./ride-20260417-182737
Starting continuous logcat capture → ./ride-20260417-182737/logcat.txt
iFit log path: /sdcard/android/data/com.ifit.glassos_service/files/.valinorlogs/log.latest.txt

Polling every 60s. Press Ctrl-C to stop.

0,12345,98432,34,0,3.2,1024,884736,6142,12345678,89012
```

**If iFit log path shows `<not found>`:** the iFit log detection failed. The app will still capture all other metrics and logcat. Investigate the iFit log path separately after the ride — it does not block the profile.

**If you see `WARNING: org.cagnulein... not running`:** QZCompanion crashed or was killed. The polling loop continues waiting; restart the app on the device and it will resume automatically on the next interval.

---

## Step 2 — Ride

Ride for the full session (target: 60 minutes minimum). No action needed from the dev machine — the script polls automatically every 60 seconds and prints each row to the terminal.

**During the ride, watch for:**
- Rows that print `0` for all fields — indicates ADB momentarily lost the PID (usually recovers)
- The `heap_rss_kb` column increasing monotonically — early sign of a memory leak
- The `cpu_pct` column spiking above 20% — unexpected for this app under normal load

---

## Step 3 — Stop and Collect

When the ride ends, press **Ctrl-C** in the terminal. The cleanup handler will:
1. Stop the background logcat process
2. Print the output directory path

```
Stopping logcat (pid 54321)...
Done. Files saved to ./ride-20260417-182737/
  Metrics : ./ride-20260417-182737/metrics.csv
  Logcat  : ./ride-20260417-182737/logcat.txt

Run tools/analyze-ride.sh ./ride-20260417-182737 for a summary report.
```

---

## Step 4 — Analyze

```bash
tools/analyze-ride.sh ./ride-20260417-182737
```

This writes `report.txt` into the ride directory and prints the same content to stdout.

**Healthy ride report looks like:**

```
  Commands dispatched: 20–60     (varies with route grade changes)
  De-dup blocks      : 50–200    (high is normal — Zwift sends grades at high frequency)
  Throttle blocks    : 0–20      (low; higher values mean grade changes are arriving faster than 500ms)
  Log errors (E/)    : < 10
  INJECT_EVENTS fail : 0
  ADB DOWN events    : 0

  Heap RSS (KB)  : min stable, max < 200 MB, no monotonic growth
  Thread count   : stable, < 50
  All checks passed — no issues detected
```

---

## Interpreting Results

| Observation | Likely cause | Action |
|-------------|-------------|--------|
| `Commands dispatched: 0` | QZCompanion was not connected to the QZ app, or logcat filter missed the tags | Verify QZ app was streaming; check logcat raw for `QZ:Dispatch` tags |
| `De-dup blocks` >> `Commands dispatched` | Normal for Zwift — it sends grade continuously; de-dup is working correctly | No action |
| `Throttle blocks` > 50 | Grade changes arriving faster than 500 ms throttle window; some commands cached and coalesced | Expected; review if user reports sluggish response |
| Heap growing in > 80% of samples | Possible memory leak | File an issue; compare with a previous profile to identify when it started |
| `ADB DOWN events` > 0 | ADB connection dropped and reconnected during ride | Check USB/WiFi stability; review reconnect log lines |
| `INJECT_EVENTS fail` > 0 | Accessibility permission revoked or not granted | Re-enable QZCompanion in Android Accessibility Settings |
| CPU% > 20% sustained | Unexpected — check if another process on device is contending | Run `adb shell top` during next ride to identify |
| iFit log growing > 50 MB/hour | iFit firmware is unusually chatty; log is not read by QZCompanion but will consume disk space | Monitor device storage; consider clearing the log file manually between rides |

---

## Step 5 — Archive

Save the ride directory with a descriptive name before running another profile:

```bash
mv ./ride-20260417-182737 ./profiles/baseline-zwift-hilly-route-v3.6.30
```

Keep at least two profiles on hand — one pre-release and one post-release — so regressions
can be spotted by diffing report.txt files:

```bash
diff profiles/baseline-*/report.txt
```

---

## Known Limitations

- **FD count shows 0:** `/proc/<pid>/fd` requires root or a debuggable build. The report will display "n/a" — this is expected on production builds.
- **CPU% may show 0:** `dumpsys cpuinfo` output format varies by Android version. If the column is consistently 0, verify manually with `adb shell top -n1 | grep qzcompanion`.
- **Logcat is all-process:** The `*:I` filter captures everything at INFO level, not just QZCompanion. The `logcat.txt` file will be large on a busy device. `analyze-ride.sh` filters by keyword so this does not affect the report.
- **`ifit_log_kb` reflects file growth, not reads:** QZCompanion no longer reads the iFit log file — metrics come from `logcat -s mono-stdout`. The `ifit_log_kb` column monitors disk usage only; a steadily growing value is expected and does not indicate a problem unless it approaches device storage limits.

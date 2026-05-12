package org.cagnulein.qzcompanionnordictracktreadmill.perf;

import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.MonoStdoutTelemetryReader;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S22iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * CI performance guardrail for a ride-like workload.
 *
 * This is intentionally a deterministic JVM replay, not a microbenchmark. It exercises the same
 * parser/controller/device paths as a ride, records broad process-health metrics, and writes a JSON
 * artifact for trend review in GitHub Actions.
 */
public class RidePerformanceReplayTest {

    private static final int MIN_EXPECTED_COMMANDS = 20;
    private static final long MAX_REPLAY_WALL_MS = 5_000;
    private static final long MAX_HEAP_GROWTH_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_THREAD_GROWTH = 2;

    @After
    public void restoreStaticState() {
        MonoStdoutTelemetryReader.factory = () -> Runtime.getRuntime().exec(
                new String[]{"logcat", "-s", "mono-stdout"});
        MonoStdoutTelemetryReader.onError = e -> {};
        MonoStdoutTelemetryReader.onLine = s -> {};
    }

    @Test
    public void longRideReplay_staysWithinCiBudgetsAndWritesReport() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        forceGc();

        long heapBefore = usedHeap(runtime);
        int threadsBefore = Thread.activeCount();
        long wallStart = System.nanoTime();

        MetricReplay metrics = replayMetrics();
        CommandReplay commands = replayCommands();

        long wallMs = (System.nanoTime() - wallStart) / 1_000_000L;
        forceGc();
        long heapAfter = usedHeap(runtime);
        int threadsAfter = Thread.activeCount();

        PerfReport report = new PerfReport(
                "ci-long-ride-v1",
                commands.simulatedDurationMs,
                commands.packetsProcessed,
                metrics.metricLinesParsed,
                metrics.packetsParsed,
                commands.commandsEmitted,
                commands.duplicateOrSuppressedInputs,
                wallMs,
                heapBefore,
                heapAfter,
                heapAfter - heapBefore,
                threadsBefore,
                threadsAfter,
                threadsAfter - threadsBefore);
        writeReport(report);

        assertTrue("expected enough emitted commands to exercise the pipeline",
                commands.commandsEmitted >= MIN_EXPECTED_COMMANDS);
        assertEquals("all iFit fixture lines should parse into metric packets",
                metrics.metricLinesParsed, metrics.packetsParsed);
        assertTrue("ride replay exceeded CI wall-clock budget: " + wallMs + "ms",
                wallMs <= MAX_REPLAY_WALL_MS);
        assertTrue("heap growth exceeded budget: " + report.heapGrowthBytes + " bytes",
                report.heapGrowthBytes <= MAX_HEAP_GROWTH_BYTES);
        assertTrue("thread growth exceeded budget: " + report.threadGrowth,
                report.threadGrowth <= MAX_THREAD_GROWTH);
    }

    private static MetricReplay replayMetrics() throws Exception {
        String fixture = readResource("perf/ifit-mono-stdout.log");
        int metricLines = countNonBlankLines(fixture);
        List<Telemetry> packets = new ArrayList<>();

        MonoStdoutTelemetryReader.factory = () -> fakeProcess(fixture);
        MonoStdoutTelemetryReader.onError = e -> { throw new AssertionError(e); };

        MonoStdoutTelemetryReader reader = new MonoStdoutTelemetryReader();
        reader.subscribe(packets::add);
        reader.read();
        reader.awaitStream();

        return new MetricReplay(metricLines, packets.size());
    }

    private static CommandReplay replayCommands() throws Exception {
        String fixture = readResource("perf/ride-commands.csv");
        List<String> emitted = new ArrayList<>();
        long[] timeMs = {0L};

        S22iDevice device = new S22iDevice();
        device.commandExecutor = emitted::add;
        DeviceController controller = new DeviceController(device, () -> timeMs[0]);

        int packets = 0;
        long firstMs = -1L;
        long lastMs = 0L;
        for (String line : fixture.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] parts = trimmed.split(",", 2);
            if (parts.length != 2) throw new AssertionError("Invalid command fixture row: " + line);
            long eventMs = Long.parseLong(parts[0]);
            if (firstMs < 0) firstMs = eventMs;
            lastMs = eventMs;
            timeMs[0] = eventMs;
            controller.onPacket(QZCommandPacket.parse(parts[1]));
            packets++;
        }

        int suppressed = packets - emitted.size();
        return new CommandReplay(Math.max(0L, lastMs - firstMs), packets, emitted.size(), suppressed);
    }

    private static String readResource(String path) throws IOException {
        InputStream stream = RidePerformanceReplayTest.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) throw new IOException("Missing test resource: " + path);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = stream.read(buffer)) >= 0) out.write(buffer, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Process fakeProcess(String output) {
        return new Process() {
            public InputStream getInputStream() { return new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)); }
            public int waitFor() { return 0; }
            public OutputStream getOutputStream() { return null; }
            public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
            public int exitValue() { return 0; }
            public void destroy() {}
        };
    }

    private static int countNonBlankLines(String text) {
        int count = 0;
        for (String line : text.split("\\R")) {
            if (!line.trim().isEmpty()) count++;
        }
        return count;
    }

    private static long usedHeap(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void forceGc() throws InterruptedException {
        System.gc();
        Thread.sleep(50);
    }

    private static void writeReport(PerfReport report) throws IOException {
        File reportDir = new File(projectBuildDir(), "reports/perf");
        if (!reportDir.exists() && !reportDir.mkdirs()) {
            throw new IOException("Could not create " + reportDir.getAbsolutePath());
        }
        Files.write(new File(reportDir, "ride-performance.json").toPath(),
                report.toJson().getBytes(StandardCharsets.UTF_8));
    }

    private static File projectBuildDir() {
        File cwd = new File(System.getProperty("user.dir"));
        File appDir = new File(cwd, "app");
        if (appDir.isDirectory()) return new File(appDir, "build");
        return new File(cwd, "build");
    }

    private static final class MetricReplay {
        final int metricLinesParsed;
        final int packetsParsed;

        MetricReplay(int metricLinesParsed, int packetsParsed) {
            this.metricLinesParsed = metricLinesParsed;
            this.packetsParsed = packetsParsed;
        }
    }

    private static final class CommandReplay {
        final long simulatedDurationMs;
        final int packetsProcessed;
        final int commandsEmitted;
        final int duplicateOrSuppressedInputs;

        CommandReplay(long simulatedDurationMs, int packetsProcessed, int commandsEmitted,
                      int duplicateOrSuppressedInputs) {
            this.simulatedDurationMs = simulatedDurationMs;
            this.packetsProcessed = packetsProcessed;
            this.commandsEmitted = commandsEmitted;
            this.duplicateOrSuppressedInputs = duplicateOrSuppressedInputs;
        }
    }

    private static final class PerfReport {
        final String scenario;
        final long simulatedDurationMs;
        final int commandPacketsProcessed;
        final int metricLinesParsed;
        final int metricPacketsParsed;
        final int commandsEmitted;
        final int duplicateOrSuppressedInputs;
        final long wallMs;
        final long heapBeforeBytes;
        final long heapAfterBytes;
        final long heapGrowthBytes;
        final int threadsBefore;
        final int threadsAfter;
        final int threadGrowth;

        PerfReport(String scenario, long simulatedDurationMs, int commandPacketsProcessed,
                   int metricLinesParsed, int metricPacketsParsed, int commandsEmitted,
                   int duplicateOrSuppressedInputs, long wallMs, long heapBeforeBytes,
                   long heapAfterBytes, long heapGrowthBytes, int threadsBefore,
                   int threadsAfter, int threadGrowth) {
            this.scenario = scenario;
            this.simulatedDurationMs = simulatedDurationMs;
            this.commandPacketsProcessed = commandPacketsProcessed;
            this.metricLinesParsed = metricLinesParsed;
            this.metricPacketsParsed = metricPacketsParsed;
            this.commandsEmitted = commandsEmitted;
            this.duplicateOrSuppressedInputs = duplicateOrSuppressedInputs;
            this.wallMs = wallMs;
            this.heapBeforeBytes = heapBeforeBytes;
            this.heapAfterBytes = heapAfterBytes;
            this.heapGrowthBytes = heapGrowthBytes;
            this.threadsBefore = threadsBefore;
            this.threadsAfter = threadsAfter;
            this.threadGrowth = threadGrowth;
        }

        String toJson() {
            return "{\n" +
                    "  \"scenario\": \"" + scenario + "\",\n" +
                    "  \"simulatedDurationMs\": " + simulatedDurationMs + ",\n" +
                    "  \"commandPacketsProcessed\": " + commandPacketsProcessed + ",\n" +
                    "  \"metricLinesParsed\": " + metricLinesParsed + ",\n" +
                    "  \"metricPacketsParsed\": " + metricPacketsParsed + ",\n" +
                    "  \"commandsEmitted\": " + commandsEmitted + ",\n" +
                    "  \"duplicateOrSuppressedInputs\": " + duplicateOrSuppressedInputs + ",\n" +
                    "  \"wallMs\": " + wallMs + ",\n" +
                    "  \"heapBeforeBytes\": " + heapBeforeBytes + ",\n" +
                    "  \"heapAfterBytes\": " + heapAfterBytes + ",\n" +
                    "  \"heapGrowthBytes\": " + heapGrowthBytes + ",\n" +
                    "  \"threadsBefore\": " + threadsBefore + ",\n" +
                    "  \"threadsAfter\": " + threadsAfter + ",\n" +
                    "  \"threadGrowth\": " + threadGrowth + "\n" +
                    "}\n";
        }
    }
}

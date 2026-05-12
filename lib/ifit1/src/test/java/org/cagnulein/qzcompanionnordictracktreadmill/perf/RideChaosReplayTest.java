package org.cagnulein.qzcompanionnordictracktreadmill.perf;

import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.MonoStdoutTelemetryReader;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceController;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.bike.S22iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.device.ifit1.treadmill.X11iDevice;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Destructive edge tests for the ride pipeline. These are not exact correctness checks; they are
 * crash/wedge/leak guards for intentionally ridiculous inputs.
 */
public class RideChaosReplayTest {

    private static final long MAX_SCENARIO_WALL_MS = 10_000;
    private static final long MAX_HEAP_GROWTH_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_THREAD_GROWTH = 3;

    private final List<ChaosReport> reports = new ArrayList<>();

    @After
    public void restoreStaticState() throws IOException {
        MonoStdoutTelemetryReader.factory = () -> Runtime.getRuntime().exec(
                new String[]{"logcat", "-s", "mono-stdout"});
        MonoStdoutTelemetryReader.onError = e -> {};
        MonoStdoutTelemetryReader.onLine = s -> {};
        writeReports();
    }

    @Test
    public void chaosProfiles_doNotCrashOrGrowUnboundedAndWriteReport() throws Exception {
        reports.add(packetFuzzAgainstBike());
        reports.add(numericExtremesAgainstTreadmill());
        reports.add(queueAndSentinelChaos());
        reports.add(metricReaderChaos());

        for (ChaosReport report : reports) {
            assertEquals(report.scenario + " should not leak uncaught exceptions",
                    0, report.unexpectedExceptions);
            assertTrue(report.scenario + " exceeded wall budget: " + report.wallMs + "ms",
                    report.wallMs <= MAX_SCENARIO_WALL_MS);
            assertTrue(report.scenario + " exceeded heap budget: " + report.heapGrowthBytes,
                    report.heapGrowthBytes <= MAX_HEAP_GROWTH_BYTES);
            assertTrue(report.scenario + " exceeded thread budget: " + report.threadGrowth,
                    report.threadGrowth <= MAX_THREAD_GROWTH);
        }
    }

    private static ChaosReport packetFuzzAgainstBike() throws Exception {
        ScenarioProbe probe = new ScenarioProbe("packet-fuzz-bike");
        probe.start();

        CounterLogger logger = new CounterLogger();
        CommandCounter commands = new CommandCounter();
        long[] timeMs = {1_000L};
        S22iDevice device = new S22iDevice();
        device.logger = logger;
        device.commandExecutor = commands::accept;
        DeviceController controller = new DeviceController(device, () -> timeMs[0]);

        int inputs = 0;
        int unexpected = 0;
        for (String raw : fuzzPackets()) {
            try {
                timeMs[0] += 600L;
                controller.onPacket(QZCommandPacket.parse(raw));
            } catch (Throwable t) {
                unexpected++;
            }
            inputs++;
        }
        controller.shutdown();

        ChaosReport report = probe.finish(inputs, commands.count, logger.dropCount, 0, 0, 0, unexpected);
        assertTrue("fuzz should not emit more commands than inputs", commands.count <= inputs);
        return report;
    }

    private static ChaosReport numericExtremesAgainstTreadmill() throws Exception {
        ScenarioProbe probe = new ScenarioProbe("numeric-extremes-treadmill");
        probe.start();

        CounterLogger logger = new CounterLogger();
        CommandCounter commands = new CommandCounter();
        long[] timeMs = {1_000L};
        X11iDevice device = new X11iDevice();
        device.logger = logger;
        device.commandExecutor = commands::accept;
        DeviceController controller = new DeviceController(device, () -> timeMs[0]);

        String[] values = {
                "999999", "-999999", "NaN", "Infinity", "-Infinity", "1e39", "-1e39",
                "3,5", " 7.0 ", "+4.0", "--1", "0x10", "1_000", "", " "
        };
        int inputs = 0;
        int unexpected = 0;
        for (String speed : values) {
            for (String incline : values) {
                try {
                    timeMs[0] += 600L;
                    controller.onPacket(QZCommandPacket.parse(speed + ";" + incline));
                } catch (Throwable t) {
                    unexpected++;
                }
                inputs++;
            }
        }
        controller.shutdown();

        ChaosReport report = probe.finish(inputs, commands.count, logger.dropCount, 0, 0, 0, unexpected);
        assertTrue("numeric extremes should remain bounded", commands.count <= inputs * 2);
        return report;
    }

    private static ChaosReport queueAndSentinelChaos() throws Exception {
        ScenarioProbe probe = new ScenarioProbe("queue-sentinel-chaos");
        probe.start();

        CounterLogger logger = new CounterLogger();
        CommandCounter commands = new CommandCounter();
        long[] timeMs = {0L};
        S22iDevice device = new S22iDevice();
        device.logger = logger;
        device.commandExecutor = commands::accept;
        DeviceController controller = new DeviceController(device, () -> timeMs[0]);

        int inputs = 0;
        int unexpected = 0;
        for (int i = 0; i < 12_000; i++) {
            try {
                timeMs[0] = i * 10L;
                if (i % 17 == 0) {
                    controller.onPacket(QZCommandPacket.parse(QZCommandPacket.END_OF_RIDE));
                } else if (i % 29 == 0) {
                    controller.onPacket(QZCommandPacket.parse("CALSWIPE:" + i + ":" + (i + 1) + ":" + (i + 2)));
                } else {
                    controller.onPacket(QZCommandPacket.parse(((i % 50) * 0.5f) + ";0"));
                }
            } catch (Throwable t) {
                unexpected++;
            }
            inputs++;
        }
        controller.shutdown();

        ChaosReport report = probe.finish(inputs, commands.count, logger.dropCount, 0, 0, 0, unexpected);
        assertTrue("chaos flood should force queue drops", logger.dropCount > 0);
        assertTrue("chaos flood should remain throttled", commands.count <= 400);
        return report;
    }

    private static ChaosReport metricReaderChaos() throws Exception {
        ScenarioProbe probe = new ScenarioProbe("metric-reader-chaos");
        probe.start();

        AtomicInteger errors = new AtomicInteger();
        List<Telemetry> packets = new ArrayList<>();
        StringBuilder stream = new StringBuilder();
        int totalLines = 0;
        int validLines = 0;
        String longTail = repeat("9", 8_192);
        for (int i = 0; i < 1_000; i++) {
            stream.append("V/mono-stdout(2174): [Trace:FitPro] Changed KPH to: ").append(i % 20).append("\n");
            totalLines++;
            validLines++;
            stream.append("V/mono-stdout(2174): [Trace:FitPro] Changed Grade to: ").append(longTail).append("\n");
            totalLines++;
            stream.append("V/mono-stdout(2174): [Trace:FitPro] Changed Watts to: NaN\n");
            totalLines++;
            validLines++;
            stream.append("V/mono-stdout(2174): [Trace:FitPro] ").append(repeat("x", 16_384)).append("\n");
            totalLines++;
        }

        MonoStdoutTelemetryReader.onError = e -> errors.incrementAndGet();
        MonoStdoutTelemetryReader.factory = () -> fakeProcess(stream.toString());
        MonoStdoutTelemetryReader reader = new MonoStdoutTelemetryReader();
        reader.subscribe(packets::add);
        reader.read();
        reader.awaitStream();

        for (int i = 0; i < 50; i++) {
            MonoStdoutTelemetryReader.factory = () -> { throw new IOException("chaos launch failure"); };
            reader.read();
            reader.awaitStream();
        }

        ChaosReport report = probe.finish(0, 0, 0, totalLines, packets.size(), errors.get(), 0);
        assertTrue("valid lines should still emit packets", packets.size() >= validLines);
        assertEquals("throwing factories should be reported", 50, errors.get());
        return report;
    }

    private static List<String> fuzzPackets() {
        List<String> packets = new ArrayList<>();
        String[] seeds = {
                "", " ", ";", ";;", "0", "0;0", "-1;-100", "abc", "abc;def",
                "NaN;0", "Infinity;0", "-Infinity;0", "1e39;0", "0;1e39",
                "3,5;2,5", "CALSWIPE", "CALSWIPE:", "CALSWIPE:a:b:c",
                "CALSWIPE:1:2:3", "SNAP_ORIGIN", "\u0000;\u0000", "🚴;🏔",
                repeat("9", 4_096), repeat("1;", 512)
        };
        for (String seed : seeds) packets.add(seed);
        for (int i = 0; i < 2_000; i++) {
            switch (i % 7) {
                case 0: packets.add(i + ";" + (-i)); break;
                case 1: packets.add("x" + i + ";0"); break;
                case 2: packets.add((i * 0.1f) + ";" + repeat(";", i % 9)); break;
                case 3: packets.add("CALSWIPE:" + i + ":" + -i + ":" + (i * 2)); break;
                case 4: packets.add("NaN;" + i); break;
                case 5: packets.add("Infinity;" + -i); break;
                default: packets.add(repeat("a", i % 128) + ";" + repeat("b", i % 64)); break;
            }
        }
        return packets;
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

    private static String repeat(String value, int count) {
        StringBuilder out = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private void writeReports() throws IOException {
        if (reports.isEmpty()) return;
        File reportDir = new File(projectBuildDir(), "reports/perf");
        if (!reportDir.exists() && !reportDir.mkdirs()) {
            throw new IOException("Could not create " + reportDir.getAbsolutePath());
        }
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"scenarios\": [\n");
        for (int i = 0; i < reports.size(); i++) {
            if (i > 0) json.append(",\n");
            json.append(reports.get(i).toJson("    "));
        }
        json.append("\n  ]\n}\n");
        Files.write(new File(reportDir, "ride-chaos.json").toPath(),
                json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static File projectBuildDir() {
        File cwd = new File(System.getProperty("user.dir"));
        File appDir = new File(cwd, "app");
        if (appDir.isDirectory()) return new File(appDir, "build");
        return new File(cwd, "build");
    }

    private static long usedHeap(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void forceGc() throws InterruptedException {
        System.gc();
        Thread.sleep(50);
    }

    private static final class ScenarioProbe {
        final String scenario;
        Runtime runtime;
        long wallStartNs;
        long heapBefore;
        int threadsBefore;

        ScenarioProbe(String scenario) {
            this.scenario = scenario;
        }

        void start() throws InterruptedException {
            runtime = Runtime.getRuntime();
            forceGc();
            heapBefore = usedHeap(runtime);
            threadsBefore = Thread.activeCount();
            wallStartNs = System.nanoTime();
        }

        ChaosReport finish(int commandInputs, int commandsEmitted, int droppedInputs,
                           int metricLinesProcessed, int metricPacketsEmitted, int parseErrors,
                           int unexpectedExceptions) throws InterruptedException {
            long wallMs = (System.nanoTime() - wallStartNs) / 1_000_000L;
            forceGc();
            long heapAfter = usedHeap(runtime);
            int threadsAfter = Thread.activeCount();
            return new ChaosReport(
                    scenario,
                    commandInputs,
                    commandsEmitted,
                    droppedInputs,
                    metricLinesProcessed,
                    metricPacketsEmitted,
                    parseErrors,
                    unexpectedExceptions,
                    wallMs,
                    heapBefore,
                    heapAfter,
                    heapAfter - heapBefore,
                    threadsBefore,
                    threadsAfter,
                    threadsAfter - threadsBefore);
        }
    }

    private static final class CounterLogger implements Device.Logger {
        int dropCount = 0;

        @Override
        public void log(int level, String tag, String msg) {
            if (msg != null && msg.startsWith("drop:")) dropCount++;
        }
    }

    private static final class CommandCounter {
        int count = 0;

        void accept(String ignored) {
            count++;
        }
    }

    private static final class ChaosReport {
        final String scenario;
        final int commandInputs;
        final int commandsEmitted;
        final int droppedInputs;
        final int metricLinesProcessed;
        final int metricPacketsEmitted;
        final int parseErrors;
        final int unexpectedExceptions;
        final long wallMs;
        final long heapBeforeBytes;
        final long heapAfterBytes;
        final long heapGrowthBytes;
        final int threadsBefore;
        final int threadsAfter;
        final int threadGrowth;

        ChaosReport(String scenario, int commandInputs, int commandsEmitted, int droppedInputs,
                    int metricLinesProcessed, int metricPacketsEmitted, int parseErrors,
                    int unexpectedExceptions, long wallMs, long heapBeforeBytes, long heapAfterBytes,
                    long heapGrowthBytes, int threadsBefore, int threadsAfter, int threadGrowth) {
            this.scenario = scenario;
            this.commandInputs = commandInputs;
            this.commandsEmitted = commandsEmitted;
            this.droppedInputs = droppedInputs;
            this.metricLinesProcessed = metricLinesProcessed;
            this.metricPacketsEmitted = metricPacketsEmitted;
            this.parseErrors = parseErrors;
            this.unexpectedExceptions = unexpectedExceptions;
            this.wallMs = wallMs;
            this.heapBeforeBytes = heapBeforeBytes;
            this.heapAfterBytes = heapAfterBytes;
            this.heapGrowthBytes = heapGrowthBytes;
            this.threadsBefore = threadsBefore;
            this.threadsAfter = threadsAfter;
            this.threadGrowth = threadGrowth;
        }

        String toJson(String indent) {
            return indent + "{\n" +
                    indent + "  \"scenario\": \"" + scenario + "\",\n" +
                    indent + "  \"commandInputs\": " + commandInputs + ",\n" +
                    indent + "  \"commandsEmitted\": " + commandsEmitted + ",\n" +
                    indent + "  \"droppedInputs\": " + droppedInputs + ",\n" +
                    indent + "  \"metricLinesProcessed\": " + metricLinesProcessed + ",\n" +
                    indent + "  \"metricPacketsEmitted\": " + metricPacketsEmitted + ",\n" +
                    indent + "  \"parseErrors\": " + parseErrors + ",\n" +
                    indent + "  \"unexpectedExceptions\": " + unexpectedExceptions + ",\n" +
                    indent + "  \"wallMs\": " + wallMs + ",\n" +
                    indent + "  \"heapBeforeBytes\": " + heapBeforeBytes + ",\n" +
                    indent + "  \"heapAfterBytes\": " + heapAfterBytes + ",\n" +
                    indent + "  \"heapGrowthBytes\": " + heapGrowthBytes + ",\n" +
                    indent + "  \"threadsBefore\": " + threadsBefore + ",\n" +
                    indent + "  \"threadsAfter\": " + threadsAfter + ",\n" +
                    indent + "  \"threadGrowth\": " + threadGrowth + "\n" +
                    indent + "}";
        }
    }
}

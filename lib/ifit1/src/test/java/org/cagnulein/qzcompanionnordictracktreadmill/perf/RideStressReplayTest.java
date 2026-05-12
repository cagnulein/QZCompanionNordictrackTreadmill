package org.cagnulein.qzcompanionnordictracktreadmill.perf;

import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1.MonoStdoutTelemetryReader;
import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled/manual stress suite for edge-finding. These tests are deliberately broader and harsher
 * than the PR baseline, but still deterministic enough to run in GitHub Actions without a device.
 */
public class RideStressReplayTest {

    private static final long MAX_SCENARIO_WALL_MS = 10_000;
    private static final long MAX_HEAP_GROWTH_BYTES = 48L * 1024L * 1024L;
    private static final int MAX_THREAD_GROWTH = 3;

    private final List<StressReport> reports = new ArrayList<>();

    @After
    public void restoreStaticState() throws IOException {
        MonoStdoutTelemetryReader.factory = () -> Runtime.getRuntime().exec(
                new String[]{"logcat", "-s", "mono-stdout"});
        MonoStdoutTelemetryReader.onError = e -> {};
        MonoStdoutTelemetryReader.onLine = s -> {};
        writeReports();
    }

    @Test
    public void stressProfiles_stayWithinBroadBudgetsAndWriteReport() throws Exception {
        reports.add(longSoak());
        reports.add(burstOverload());
        reports.add(sentinelDrain());
        reports.add(noisyIfitMetricStream());
        reports.add(readerRestart());

        for (StressReport report : reports) {
            assertTrue(report.scenario + " exceeded wall budget: " + report.wallMs + "ms",
                    report.wallMs <= MAX_SCENARIO_WALL_MS);
            assertTrue(report.scenario + " exceeded heap budget: " + report.heapGrowthBytes,
                    report.heapGrowthBytes <= MAX_HEAP_GROWTH_BYTES);
            assertTrue(report.scenario + " exceeded thread budget: " + report.threadGrowth,
                    report.threadGrowth <= MAX_THREAD_GROWTH);
        }
    }

    private static StressReport longSoak() throws Exception {
        ScenarioProbe probe = new ScenarioProbe("long-soak-90m");
        probe.start();

        CounterLogger logger = new CounterLogger();
        CommandCounter commands = new CommandCounter();
        long[] timeMs = {0L};
        S22iDevice device = new S22iDevice();
        device.logger = logger;
        device.commandExecutor = commands::accept;
        DeviceController controller = new DeviceController(device, () -> timeMs[0]);

        float[] route = {-3.0f, -1.0f, 0.0f, 2.0f, 4.5f, 7.0f, 10.0f, 12.0f, 9.5f, 6.5f, 3.0f, 0.0f};
        int inputs = 0;
        for (int i = 0; i < 9_000; i++) {
            timeMs[0] = i * 600L;
            float grade = route[i % route.length];
            controller.onPacket(QZCommandPacket.parse(grade + ";0"));
            inputs++;
        }
        controller.shutdown();

        StressReport report = probe.finish(inputs, commands.count, logger.dropCount, 0, 0, 0);
        assertTrue("long soak should emit many commands", commands.count > 8_000);
        assertEquals("long soak should not drop at normal ride cadence", 0, logger.dropCount);
        return report;
    }

    private static StressReport burstOverload() throws Exception {
        ScenarioProbe probe = new ScenarioProbe("burst-overload-20hz");
        probe.start();

        CounterLogger logger = new CounterLogger();
        CommandCounter commands = new CommandCounter();
        long[] timeMs = {0L};
        S22iDevice device = new S22iDevice();
        device.logger = logger;
        device.commandExecutor = commands::accept;
        DeviceController controller = new DeviceController(device, () -> timeMs[0]);

        int inputs = 0;
        for (int i = 0; i < 6_000; i++) {
            timeMs[0] = i * 50L;
            controller.onPacket(QZCommandPacket.parse(((i % 25) * 0.5f) + ";0"));
            inputs++;
            if (timeMs[0] > 0 && timeMs[0] % 500L == 0L) {
                controller.onPacket(QZCommandPacket.parse(QZCommandPacket.END_OF_RIDE));
            }
        }
        controller.shutdown();

        StressReport report = probe.finish(inputs, commands.count, logger.dropCount, 0, 0, 0);
        assertTrue("burst should force queue drops", logger.dropCount > 0);
        assertTrue("burst commands should stay throttled", commands.count <= 650);
        assertTrue("burst should still drain some queued commands", commands.count > 500);
        return report;
    }

    private static StressReport sentinelDrain() throws Exception {
        ScenarioProbe probe = new ScenarioProbe("sentinel-drain-fifo");
        probe.start();

        CounterLogger logger = new CounterLogger();
        List<String> emitted = new ArrayList<>();
        long[] timeMs = {100L};
        S22iDevice device = new S22iDevice();
        device.logger = logger;
        device.commandExecutor = emitted::add;
        DeviceController controller = new DeviceController(device, () -> timeMs[0]);

        float[] queued = {1f, 2f, 3f, 4f, 5f, 6f};
        for (float grade : queued) {
            controller.onPacket(QZCommandPacket.parse(grade + ";0"));
        }
        for (int i = 0; i < 5; i++) {
            timeMs[0] += 500L;
            controller.onPacket(QZCommandPacket.parse(QZCommandPacket.END_OF_RIDE));
        }
        controller.shutdown();

        StressReport report = probe.finish(queued.length + 5, emitted.size(), logger.dropCount, 0, 0, 0);
        assertEquals("sixth rapid command should be dropped at queue capacity", 1, logger.dropCount);
        assertEquals("sentinels should drain the five queued commands", 5, emitted.size());
        assertEquals("input swipe 75 622 75 603 200", emitted.get(0));
        assertEquals("input swipe 75 603 75 584 200", emitted.get(1));
        assertEquals("input swipe 75 584 75 566 200", emitted.get(2));
        assertEquals("input swipe 75 566 75 547 200", emitted.get(3));
        assertEquals("input swipe 75 547 75 529 200", emitted.get(4));
        return report;
    }

    private static StressReport noisyIfitMetricStream() throws Exception {
        ScenarioProbe probe = new ScenarioProbe("noisy-ifit-metric-stream");
        probe.start();

        StringBuilder stream = new StringBuilder();
        int validLines = 0;
        int totalLines = 0;
        for (int i = 0; i < 3_000; i++) {
            stream.append("V/mono-stdout(2174): [Trace:FitPro] Changed KPH to: ")
                    .append(i % 22).append(".0\n");
            validLines++;
            totalLines++;
            stream.append("V/mono-stdout(2174): [Trace:FitPro] Changed Grade to: ")
                    .append((i % 25) * 0.5f).append("\n");
            validLines++;
            totalLines++;
            stream.append("V/mono-stdout(2174): [Trace:FitPro] Changed Watts to: nope\n");
            totalLines++;
            stream.append("V/mono-stdout(2174): [Trace:FitPro] partial line without numeric tail\n");
            totalLines++;
            stream.append("QZ:Service Changed KPH 99.9\n");
            totalLines++;
        }

        List<Telemetry> packets = new ArrayList<>();
        AtomicInteger errors = new AtomicInteger();
        MonoStdoutTelemetryReader.factory = () -> fakeProcess(stream.toString());
        MonoStdoutTelemetryReader.onError = e -> errors.incrementAndGet();
        MonoStdoutTelemetryReader reader = new MonoStdoutTelemetryReader();
        reader.subscribe(packets::add);
        reader.read();
        reader.awaitStream();

        StressReport report = probe.finish(0, 0, 0, totalLines, packets.size(), errors.get());
        assertEquals("all valid metric lines should emit packets", validLines, packets.size());
        assertEquals("malformed metric lines should be ignored without parser errors", 0, errors.get());
        return report;
    }

    private static StressReport readerRestart() throws Exception {
        ScenarioProbe probe = new ScenarioProbe("metric-reader-restart");
        probe.start();

        AtomicInteger launches = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        List<Telemetry> packets = new ArrayList<>();
        MonoStdoutTelemetryReader.onError = e -> errors.incrementAndGet();

        MonoStdoutTelemetryReader reader = new MonoStdoutTelemetryReader();
        reader.subscribe(packets::add);
        for (int i = 0; i < 20; i++) {
            final int value = i;
            MonoStdoutTelemetryReader.factory = () -> {
                launches.incrementAndGet();
                return fakeProcess("V/mono-stdout(2174): [Trace:FitPro] Changed RPM to: " + value + "\n");
            };
            reader.read();
            reader.awaitStream();
        }
        for (int i = 0; i < 5; i++) {
            MonoStdoutTelemetryReader.factory = () -> { throw new IOException("synthetic stream failure"); };
            reader.read();
            reader.awaitStream();
        }

        StressReport report = probe.finish(25, 0, 0, 20, packets.size(), errors.get());
        assertEquals("finite streams should launch once per read", 20, launches.get());
        assertEquals("finite streams should emit one packet each", 20, packets.size());
        assertEquals("throwing factories should report one error each", 5, errors.get());
        return report;
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
        Files.write(new File(reportDir, "ride-stress.json").toPath(),
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

        StressReport finish(int commandInputs, int commandsEmitted, int droppedInputs,
                            int metricLinesProcessed, int metricPacketsEmitted,
                            int parseErrors) throws InterruptedException {
            long wallMs = (System.nanoTime() - wallStartNs) / 1_000_000L;
            forceGc();
            long heapAfter = usedHeap(runtime);
            int threadsAfter = Thread.activeCount();
            return new StressReport(
                    scenario,
                    commandInputs,
                    commandsEmitted,
                    droppedInputs,
                    metricLinesProcessed,
                    metricPacketsEmitted,
                    parseErrors,
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

    private static final class StressReport {
        final String scenario;
        final int commandInputs;
        final int commandsEmitted;
        final int droppedInputs;
        final int metricLinesProcessed;
        final int metricPacketsEmitted;
        final int parseErrors;
        final long wallMs;
        final long heapBeforeBytes;
        final long heapAfterBytes;
        final long heapGrowthBytes;
        final int threadsBefore;
        final int threadsAfter;
        final int threadGrowth;

        StressReport(String scenario, int commandInputs, int commandsEmitted, int droppedInputs,
                     int metricLinesProcessed, int metricPacketsEmitted, int parseErrors,
                     long wallMs, long heapBeforeBytes, long heapAfterBytes, long heapGrowthBytes,
                     int threadsBefore, int threadsAfter, int threadGrowth) {
            this.scenario = scenario;
            this.commandInputs = commandInputs;
            this.commandsEmitted = commandsEmitted;
            this.droppedInputs = droppedInputs;
            this.metricLinesProcessed = metricLinesProcessed;
            this.metricPacketsEmitted = metricPacketsEmitted;
            this.parseErrors = parseErrors;
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

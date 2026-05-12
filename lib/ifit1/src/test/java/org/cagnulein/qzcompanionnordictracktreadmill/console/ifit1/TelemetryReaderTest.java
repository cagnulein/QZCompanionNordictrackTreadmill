package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit1;

import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.CadenceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.HeartRateTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.InclineTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.ResistanceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.WattsTelemetry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class TelemetryReaderTest {

    private static final float DELTA = 0.01f;

    private static InputStream asStream(String text) {
        return new ByteArrayInputStream(text.getBytes());
    }

    private static Process fakeProcess(String output) {
        return new Process() {
            public InputStream  getInputStream()  { return asStream(output); }
            public int          waitFor()         { return 0; }
            public OutputStream getOutputStream() { return null; }
            public InputStream  getErrorStream()  { return asStream(""); }
            public int          exitValue()       { return 0; }
            public void         destroy()         {}
        };
    }

    @Before
    public void resetStaticState() {
        MonoStdoutTelemetryReader.factory = () -> fakeProcess("");
        MonoStdoutTelemetryReader.onError = e -> {};
    }

    @After
    public void restoreStaticState() {
        MonoStdoutTelemetryReader.factory = () -> Runtime.getRuntime().exec(
                new String[]{"logcat", "-s", "mono-stdout"});
        MonoStdoutTelemetryReader.onError = e -> {};
    }

    // ── MonoStdoutTelemetryReader ────────────────────────────────────────────────

    @Test
    public void monoStdout_parsesAllMetrics() throws IOException, InterruptedException {
        MonoStdoutTelemetryReader.factory = () -> fakeProcess(
            "V/mono-stdout(2174): [Trace:FitPro] Changed KPH to: 12.11\n" +
            "V/mono-stdout(2174): [Trace:FitPro] Changed Grade to: 5\n" +
            "V/mono-stdout(2174): [Trace:FitPro] Changed Resistance to: 13\n" +
            "V/mono-stdout(2174): [Trace:FitPro] Changed Watts to: 128\n" +
            "V/mono-stdout(2174): [Trace:FitPro] Changed RPM to: 43\n"
        );
        List<Telemetry> received = new ArrayList<>();
        MonoStdoutTelemetryReader reader = new MonoStdoutTelemetryReader();
        reader.subscribe(received::add);
        reader.read();
        reader.awaitStream();

        assertEquals(5, received.size());
        assertTrue(received.get(0) instanceof SpeedTelemetry);
        assertEquals(12.11f, received.get(0).value, DELTA);
        assertTrue(received.get(1) instanceof InclineTelemetry);
        assertEquals(5f,     received.get(1).value, DELTA);
        assertTrue(received.get(2) instanceof ResistanceTelemetry);
        assertEquals(13f,    received.get(2).value, DELTA);
        assertTrue(received.get(3) instanceof WattsTelemetry);
        assertEquals(128f,   received.get(3).value, DELTA);
        assertTrue(received.get(4) instanceof CadenceTelemetry);
        assertEquals(43f,    received.get(4).value, DELTA);
    }

    @Test
    public void monoStdout_parsesActualInclineKeyword() throws IOException, InterruptedException {
        MonoStdoutTelemetryReader.factory = () -> fakeProcess(
            "V/mono-stdout(2174): [Trace:FitPro] Changed Actual Incline to: 3.5\n"
        );
        List<Telemetry> received = new ArrayList<>();
        MonoStdoutTelemetryReader reader = new MonoStdoutTelemetryReader();
        reader.subscribe(received::add);
        reader.read();
        reader.awaitStream();

        assertEquals(1, received.size());
        assertTrue(received.get(0) instanceof InclineTelemetry);
        assertEquals(3.5f, received.get(0).value, DELTA);
    }

    @Test
    public void monoStdout_parsesHeartRate() throws IOException, InterruptedException {
        MonoStdoutTelemetryReader.factory = () -> fakeProcess(
            "V/mono-stdout(2174): [Trace:FitPro] HeartRateDataUpdate 72\n"
        );
        List<Telemetry> received = new ArrayList<>();
        MonoStdoutTelemetryReader reader = new MonoStdoutTelemetryReader();
        reader.subscribe(received::add);
        reader.read();
        reader.awaitStream();

        assertEquals(1, received.size());
        assertTrue(received.get(0) instanceof HeartRateTelemetry);
        assertEquals(72f, received.get(0).value, DELTA);
    }

    @Test
    public void monoStdout_filtersOwnLogTag() throws IOException, InterruptedException {
        MonoStdoutTelemetryReader.factory = () -> fakeProcess(
            "QZ:Service Changed KPH 99.9\n" +
            "V/mono-stdout(2174): [Trace:FitPro] Changed KPH to: 12.11\n"
        );
        List<Telemetry> received = new ArrayList<>();
        MonoStdoutTelemetryReader reader = new MonoStdoutTelemetryReader();
        reader.subscribe(received::add);
        reader.read();
        reader.awaitStream();

        assertEquals(1, received.size());
        assertTrue(received.get(0) instanceof SpeedTelemetry);
        assertEquals(12.11f, received.get(0).value, DELTA);
    }
}

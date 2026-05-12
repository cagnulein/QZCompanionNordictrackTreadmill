package org.cagnulein.qzcompanionnordictracktreadmill.telemetry;

import java.io.IOException;
import java.util.function.Consumer;

public interface TelemetryReader {
    void read() throws IOException;


    /**
     * Push hook for streaming readers. The reader calls {@code listener} immediately whenever a
     * new telemetry arrives, allowing consumers to skip polling.
     * Returns {@code true} if accepted; {@code false} for pull-based readers (default).
     */
    default boolean subscribe(Consumer<Telemetry> listener) { return false; }
}

package org.cagnulein.qzcompanionnordictracktreadmill.telemetry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Process-wide fanout wrapper for telemetry.
 *
 * The hub owns one committed reader (configured by {@link #configure}) and fans its telemetry
 * to QZ UDP output, device state, and calibration. The reader is selected once based on the
 * detected iFit platform — there is no fallback.
 */
public final class TelemetryHub {

    public interface Subscription {
        void close();
    }

    private static final TelemetryHub SHARED = new TelemetryHub();

    private final List<TelemetryReader> readers;
    private final List<Consumer<Telemetry>> subscribers = new CopyOnWriteArrayList<>();
    private boolean started = false;
    private TelemetryReader activeReader = null;

    public TelemetryHub() {
        this.readers = new ArrayList<>();
    }

    public TelemetryHub(TelemetryReader... readers) {
        this.readers = new ArrayList<>(Arrays.asList(readers));
        for (TelemetryReader reader : readers) {
            reader.subscribe(this::dispatch);
        }
    }

    public static TelemetryHub shared() {
        return SHARED;
    }

    public static synchronized void configure(TelemetryReader reader) {
        SHARED.configureReaders(reader);
    }

    private synchronized void configureReaders(TelemetryReader... newReaders) {
        if (started) return;
        readers.clear();
        readers.addAll(Arrays.asList(newReaders));
        for (TelemetryReader reader : newReaders) {
            reader.subscribe(this::dispatch);
        }
    }

    public Subscription subscribe(Consumer<Telemetry> subscriber) throws IOException {
        subscribers.add(subscriber);
        start();
        return () -> subscribers.remove(subscriber);
    }

    public synchronized void start() throws IOException {
        if (started) return;
        if (readers.isEmpty()) throw new IOException("No telemetry reader configured");
        readers.get(0).read();
        activeReader = readers.get(0);
        started = true;
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    public TelemetryReader activeReader() {
        return activeReader;
    }

    private void dispatch(Telemetry telemetry) {
        for (Consumer<Telemetry> subscriber : subscribers) {
            subscriber.accept(telemetry);
        }
    }
}

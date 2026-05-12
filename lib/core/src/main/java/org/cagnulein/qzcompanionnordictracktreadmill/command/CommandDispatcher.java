package org.cagnulein.qzcompanionnordictracktreadmill.command;

import java.util.ArrayDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Throttles and queues Commands for delivery to a device.
 *
 * Commands are enqueued via {@link #enqueue} and drained one per throttle window (500 ms).
 * A background {@link ScheduledExecutorService} fires every {@code SWIPE_THROTTLE_MS} for
 * active drain, independent of incoming commands. {@link #enqueue} also attempts an immediate
 * drain when the window is open. Both paths synchronize on {@code this} to prevent double-drain.
 *
 * The executor callback supplied at construction performs the actual command execution and any
 * associated logging. CommandDispatcher itself has no device knowledge.
 *
 * The test constructor takes an injectable {@link Clock} and starts no background thread —
 * tests remain fully deterministic.
 */
public class CommandDispatcher {

    /** Minimum gap between successive command executions, in ms. */
    public static final int SWIPE_THROTTLE_MS = 500;

    /** Maximum number of pending Commands held while the throttle window is closed. */
    public static final int QUEUE_CAPACITY = 5;

    /** Injectable clock — defaults to wall time; replaced with a fixed value in tests. */
    public interface Clock { long now(); }

    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final Consumer<Command> executor;

    private final ArrayDeque<Command> queue = new ArrayDeque<>();
    private long lastExecutedMs = 0;

    /** Production constructor: wall clock + background drain thread. */
    public CommandDispatcher(Consumer<Command> executor) {
        this.clock    = System::currentTimeMillis;
        this.executor = executor;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "QZ:DrainThread");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                () -> tryDrain(clock.now()),
                SWIPE_THROTTLE_MS, SWIPE_THROTTLE_MS, TimeUnit.MILLISECONDS);
    }

    /** Test constructor: injectable clock, no background thread. */
    public CommandDispatcher(Consumer<Command> executor, Clock clock) {
        this.clock     = clock;
        this.executor  = executor;
        this.scheduler = null;
    }

    /** Stops the background drain thread. Call from DeviceController.shutdown(). */
    public void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    /**
     * Enqueues {@code cmd} and attempts an immediate drain if the throttle window is open.
     *
     * @return the queue depth after enqueue (1–{@value #QUEUE_CAPACITY}), or -1 if the queue
     *         was full and the command was dropped
     */
    public int enqueue(Command cmd) {
        long now = clock.now();
        int depth;
        synchronized (this) {
            if (queue.size() < QUEUE_CAPACITY) {
                queue.offer(cmd);
                depth = queue.size();
            } else {
                depth = -1;
            }
        }
        if (depth >= 0) tryDrain(now);
        return depth;
    }

    /** Attempts an immediate drain without enqueuing — used by sentinel packets that carry no commands. */
    public void drain() { tryDrain(clock.now()); }

    private void tryDrain(long now) {
        Command next;
        synchronized (this) {
            if (now < lastExecutedMs + SWIPE_THROTTLE_MS) return;
            next = queue.poll();
            if (next == null) return;
            lastExecutedMs = now;
        }
        executor.accept(next);
    }
}

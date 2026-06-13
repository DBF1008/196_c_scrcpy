package com.genymobile.scrcpy.display;

import com.genymobile.scrcpy.model.Size;
import com.genymobile.scrcpy.util.Ln;

import android.os.SystemClock;

import java.util.function.LongSupplier;

public final class DisplayResizeDebouncer {

    private static final long DEBOUNCE_DELAY_MS = 300;

    public interface Callback {
        void trigger(Size size, long generation);
    }

    private final Callback callback;
    private final LongSupplier clock;
    private final long delayMs;

    private Size request;
    private long deadline;

    // Incremented whenever the current request cycle is superseded by a new request, cancelled, or stopped. A resize captured by the
    // debounce loop carries the generation it was captured with; if the generation has changed by the time it is applied, the resize is
    // stale and must be skipped (see isCurrentGeneration).
    private long generation;

    private boolean running = true;

    private Thread thread;

    public DisplayResizeDebouncer(Callback callback) {
        this(callback, SystemClock::uptimeMillis, DEBOUNCE_DELAY_MS);
    }

    // Visible for testing (inject a controllable clock and a short delay)
    DisplayResizeDebouncer(Callback callback, LongSupplier clock, long delayMs) {
        this.callback = callback;
        this.clock = clock;
        this.delayMs = delayMs;
    }

    public void start() {
        assert thread == null;
        thread = new Thread(this::debounce);
        thread.setName("debouncer");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        // Invalidate any resize already captured by the debounce loop but not yet applied
        generation++;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        notify();
    }

    private void debounce() {
        try {
            while (true) {
                Size newSize;
                long newGeneration;
                synchronized (this) {
                    while (true) {
                        long now = clock.getAsLong();
                        if (request != null && now >= deadline) {
                            break;
                        }
                        if (request == null) {
                            wait();
                        } else {
                            // The deadline is in the future; wait for the remaining time. Note: wait() always sleeps on the real monotonic
                            // clock, only the deadline comparison uses the injected clock.
                            long remaining = deadline - now;
                            if (remaining > 0) {
                                wait(remaining);
                            }
                        }
                    }
                    assert request != null : "An active deadline implies request != null";
                    newSize = request;
                    newGeneration = generation;
                    request = null;
                }
                callback.trigger(newSize, newGeneration);
            }
        } catch (InterruptedException e) {
            // ignore
        } finally {
            Ln.d("Debouncer thread stopped");
        }
    }

    public synchronized void requestResize(Size size) {
        assert size != null;
        if (!running) {
            return;
        }
        if (request == null) {
            deadline = clock.getAsLong() + delayMs;
            // A new request cycle supersedes any resize already captured by the debounce loop but not yet applied
            generation++;
        }
        request = size;
        notify();
    }

    public synchronized void cancelResize() {
        request = null;
        deadline = 0;
        // Invalidate any resize already captured by the debounce loop but not yet applied
        generation++;
        notify();
    }

    /**
     * Indicate whether the given generation is still the current one.
     * <p/>
     * A resize captured by the debounce loop must be skipped if its generation is no longer current (it has been superseded, cancelled, or
     * the debouncer has been stopped).
     *
     * @param gen the generation captured along with the resize
     * @return {@code true} if the resize may still be applied
     */
    public synchronized boolean isCurrentGeneration(long gen) {
        return running && generation == gen;
    }

    // Visible for testing
    synchronized Size getPendingSize() {
        return request;
    }

    // Visible for testing
    synchronized long getDeadline() {
        return deadline;
    }

    // Visible for testing
    synchronized long getGeneration() {
        return generation;
    }

    // Visible for testing
    synchronized boolean isRunning() {
        return running;
    }
}

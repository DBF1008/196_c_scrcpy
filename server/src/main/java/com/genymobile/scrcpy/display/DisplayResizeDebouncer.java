package com.genymobile.scrcpy.display;

import com.genymobile.scrcpy.model.Size;
import com.genymobile.scrcpy.util.Ln;

import android.os.SystemClock;

public final class DisplayResizeDebouncer {

    private static final long DEFAULT_DEBOUNCE_DELAY_MS = 300;

    public interface Callback {
        void trigger(Size size);
    }

    private final long debounceDelayMs;
    private final Clock clock;
    private final Callback callback;
    private Size request;
    private long deadline;

    private volatile boolean stopped;
    private Thread thread;

    public DisplayResizeDebouncer(Callback callback) {
        this(DEFAULT_DEBOUNCE_DELAY_MS, SystemClock::uptimeMillis, callback);
    }

    /**
     * Constructor with configurable delay and clock (for testing).
     *
     * @param debounceDelayMs the debounce delay in milliseconds
     * @param clock           the clock to use for time measurements
     * @param callback        the callback to invoke when the debounce period expires
     */
    public DisplayResizeDebouncer(long debounceDelayMs, Clock clock, Callback callback) {
        this.debounceDelayMs = debounceDelayMs;
        this.clock = clock;
        this.callback = callback;
    }

    public void start() {
        assert thread == null;
        stopped = false;
        thread = new Thread(this::debounce);
        thread.setName("debouncer");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        stopped = true;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    private void debounce() {
        try {
            while (true) {
                Size newSize;
                synchronized (this) {
                    while (true) {
                        if (stopped) {
                            return;
                        }
                        long now = clock.uptimeMillis();
                        if (request != null && now >= deadline) {
                            break;
                        }
                        if (request == null) {
                            wait();
                        } else {
                            assert now < deadline;
                            wait(deadline - now);
                        }
                    }
                    assert request != null : "An active deadline implies request != null";
                    newSize = request;
                    request = null;
                }
                callback.trigger(newSize);
            }
        } catch (InterruptedException e) {
            // ignore
        } finally {
            Ln.d("Debouncer thread stopped");
        }
    }

    public synchronized void requestResize(Size size) {
        assert size != null;
        if (stopped) {
            return;
        }
        if (request == null) {
            deadline = clock.uptimeMillis() + debounceDelayMs;
        }
        request = size;
        notify();
    }

    public synchronized void cancelResize() {
        request = null;
        deadline = 0;
        notify();
    }
}

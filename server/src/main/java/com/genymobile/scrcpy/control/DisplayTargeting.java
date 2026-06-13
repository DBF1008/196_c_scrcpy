package com.genymobile.scrcpy.control;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the display id to target for control events, encapsulating the {@code displayId} vs {@code virtualDisplayId} selection rules.
 *
 * <p>For event injection, there are two display ids:
 * <ul>
 *     <li>the {@code displayId} passed to the constructor (which comes from {@code --display-id} passed by the client, 0 for the main display);</li>
 *     <li>the {@code virtualDisplayId} used for mirroring, notified by the capture instance once a new virtual display is created.</li>
 * </ul>
 *
 * <p>If a new separate virtual display is created (using {@code --new-display}), then {@code displayId == DISPLAY_ID_NONE}. In that case, all
 * events are sent to the virtual display id (once it is known).
 *
 * <p>This class is intentionally free of any Android dependency so that the selection rules can be unit-tested in isolation. The companion
 * {@link PositionMapper} (used to map positional events) is kept by the caller alongside the virtual display id.
 */
public final class DisplayTargeting {

    /**
     * Sentinel for "no display". Must match {@code com.genymobile.scrcpy.device.Device.DISPLAY_ID_NONE} (asserted by a test).
     */
    public static final int DISPLAY_ID_NONE = -1;

    private final int displayId;

    // null until a virtual display is known; read lock-free by getActionDisplayId(), waited upon via the monitor below
    private final AtomicReference<Integer> virtualDisplayId = new AtomicReference<>();
    private final Object virtualDisplayIdAvailable = new Object(); // condition variable

    public DisplayTargeting(int displayId) {
        this.displayId = displayId;
    }

    /**
     * Notify that a virtual display has been created (or recreated). Only the first notification wakes up threads waiting in
     * {@link #awaitVirtualDisplayId(long)}.
     *
     * @param newVirtualDisplayId the virtual display id
     */
    public void onNewVirtualDisplay(int newVirtualDisplayId) {
        Integer old = virtualDisplayId.getAndSet(newVirtualDisplayId);
        if (old == null) {
            // The very first time a virtual display is known
            synchronized (virtualDisplayIdAvailable) {
                virtualDisplayIdAvailable.notifyAll();
            }
        }
    }

    /**
     * @return {@code true} if mirroring a new virtual display (created via {@code --new-display}), {@code false} for a real/main display.
     */
    public boolean isNewVirtualDisplay() {
        return displayId == DISPLAY_ID_NONE;
    }

    /**
     * @return the current virtual display id, or {@link #DISPLAY_ID_NONE} if not known yet (non-blocking).
     */
    public int getVirtualDisplayId() {
        Integer current = virtualDisplayId.get();
        return current == null ? DISPLAY_ID_NONE : current;
    }

    /**
     * Return the display id to use for non-positional events (key events, rotation, keep-active...), without blocking.
     *
     * @return the source display id when mirroring a real display, otherwise the current virtual display id, or {@link #DISPLAY_ID_NONE} if not
     * known yet.
     */
    public int getActionDisplayId() {
        if (displayId != DISPLAY_ID_NONE) {
            // Real screen mirrored, use the source display id
            return displayId;
        }

        // Virtual display created by --new-display, use the virtual display id
        return getVirtualDisplayId();
    }

    /**
     * Wait until a virtual display id is known, or the timeout expires.
     *
     * @param timeoutMillis the maximum time to wait, in milliseconds
     * @return the virtual display id, or {@link #DISPLAY_ID_NONE} if it is still unknown after the timeout
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public int awaitVirtualDisplayId(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        synchronized (virtualDisplayIdAvailable) {
            Integer current = virtualDisplayId.get();
            while (current == null) {
                long timeout = deadline - System.currentTimeMillis();
                if (timeout < 0) {
                    return DISPLAY_ID_NONE;
                }
                if (timeout > 0) {
                    virtualDisplayIdAvailable.wait(timeout);
                }
                current = virtualDisplayId.get();
            }

            return current;
        }
    }

    /**
     * Return the display id on which to start an app.
     *
     * <p>When mirroring a real display, this is the source display id. When mirroring a new virtual display, this waits up to 1 second for the
     * virtual display id to become known.
     *
     * @return the display id, or {@link #DISPLAY_ID_NONE} if no display id is available.
     */
    public int getStartAppDisplayId() {
        if (displayId != DISPLAY_ID_NONE) {
            return displayId;
        }

        // Mirroring a new virtual display id (using --new-display feature)
        try {
            // Wait for at most 1 second until a virtual display id is known
            return awaitVirtualDisplayId(1000);
        } catch (InterruptedException e) {
            // do nothing (do not restore the interrupt flag: the caller loop must not be interrupted)
            return DISPLAY_ID_NONE;
        }
    }

    /**
     * Return the display id to associate a UHID device with.
     *
     * @param canAssociateVirtualDisplay {@code true} when the platform supports associating a UHID device to a virtual display (Android >= 15)
     * @return the source display id when mirroring a real display; otherwise, if association is supported, the virtual display id (waiting up to 1
     * second for it), or {@link #DISPLAY_ID_NONE}.
     */
    public int getUhidDisplayId(boolean canAssociateVirtualDisplay) {
        if (displayId != DISPLAY_ID_NONE) {
            return displayId;
        }

        if (canAssociateVirtualDisplay) {
            // Mirroring a new virtual display id (using --new-display feature) where the UHID mouse pointer can be associated to the virtual display
            try {
                // Wait for at most 1 second until a virtual display id is known
                return awaitVirtualDisplayId(1000);
            } catch (InterruptedException e) {
                // do nothing
            }
        }

        return DISPLAY_ID_NONE;
    }
}

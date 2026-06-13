package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.model.Point;
import com.genymobile.scrcpy.model.Position;
import com.genymobile.scrcpy.model.Size;
import com.genymobile.scrcpy.util.Ln;

import android.util.Pair;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Centralizes all display ID resolution and coordinate mapping logic.
 * <p>
 * For event injection, there are two display ids:
 * <ul>
 *   <li>the {@code displayId} passed to the constructor (from {@code --display-id}, 0 for the main display);</li>
 *   <li>the {@code virtualDisplayId} used for mirroring, notified by the capture instance via
 *       {@link #onNewVirtualDisplay(int, PositionMapper)}.</li>
 * </ul>
 * <p>
 * In order to make events work correctly in all cases:
 * <ul>
 *   <li>{@code virtualDisplayId} must be used for events relative to the display (mouse and touch events with coordinates);</li>
 *   <li>{@code displayId} must be used for other events (like key events).</li>
 * </ul>
 * <p>
 * If a new separate virtual display is created (using {@code --new-display}), then {@code displayId == Device.DISPLAY_ID_NONE}.
 * In that case, all events are sent to the virtual display id.
 */
public final class DisplayRouter {

    private static final class DisplayData {
        private final int virtualDisplayId;
        private final PositionMapper positionMapper;

        private DisplayData(int virtualDisplayId, PositionMapper positionMapper) {
            this.virtualDisplayId = virtualDisplayId;
            this.positionMapper = positionMapper;
        }
    }

    private final int displayId;
    private final AtomicReference<DisplayData> displayData = new AtomicReference<>();
    private final Object displayDataAvailable = new Object(); // condition variable

    public DisplayRouter(int displayId) {
        this.displayId = displayId;
    }

    /**
     * Called by the capture instance when a virtual display is created or updated.
     */
    public void onNewVirtualDisplay(int virtualDisplayId, PositionMapper positionMapper) {
        DisplayData data = new DisplayData(virtualDisplayId, positionMapper);
        DisplayData old = this.displayData.getAndSet(data);
        if (old == null) {
            // The very first time the Controller is notified of a new virtual display
            synchronized (displayDataAvailable) {
                displayDataAvailable.notify();
            }
        }
    }

    /**
     * Returns the display id for non-positional events (key events, back, keep-active, rotate).
     * <p>
     * Uses the source {@code displayId} if a real screen is mirrored, otherwise falls back to the
     * {@code virtualDisplayId} from the most recent {@link #onNewVirtualDisplay} notification.
     */
    public int getActionDisplayId() {
        if (displayId != Device.DISPLAY_ID_NONE) {
            // Real screen mirrored, use the source display id
            return displayId;
        }

        // Virtual display created by --new-display, use the virtualDisplayId
        DisplayData data = displayData.get();
        if (data == null) {
            return Device.DISPLAY_ID_NONE;
        }

        return data.virtualDisplayId;
    }

    /**
     * Returns the mapped point and target display id for positional events (touch, scroll).
     * <p>
     * If display data is available, maps coordinates through the {@link PositionMapper} and uses the
     * {@code virtualDisplayId}. Otherwise, uses raw coordinates and the source {@code displayId}.
     *
     * @return a {@link Pair} of (mapped point, target display id), or {@code null} if coordinate mapping fails
     */
    public Pair<Point, Integer> getEventPointAndDisplayId(Position position) {
        // Read with atomic access (hides the field on purpose)
        DisplayData data = displayData.get();

        // In scrcpy, displayData should never be null (a touch event can only be generated from the client when a video frame is present).
        // However, it is possible to send events without video playback when using scrcpy-server alone (except for virtual displays).
        assert data != null || displayId != Device.DISPLAY_ID_NONE : "Cannot receive a positional event without a display";

        Point point;
        int targetDisplayId;
        if (data != null) {
            point = data.positionMapper.map(position);
            if (point == null) {
                if (Ln.isEnabled(Ln.Level.VERBOSE)) {
                    Size eventSize = position.getScreenSize();
                    Size currentSize = data.positionMapper.getVideoSize();
                    Ln.v("Ignore positional event generated for size " + eventSize + " (current size is " + currentSize + ")");
                }
                return null;
            }
            targetDisplayId = data.virtualDisplayId;
        } else {
            // No display, use the raw coordinates
            point = position.getPoint();
            targetDisplayId = displayId;
        }

        return Pair.create(point, targetDisplayId);
    }

    /**
     * Returns the display id for starting an app, blocking up to {@code timeoutMillis} if a virtual display
     * is expected but not yet available.
     */
    public int getStartAppDisplayId(long timeoutMillis) throws InterruptedException {
        if (displayId != Device.DISPLAY_ID_NONE) {
            return displayId;
        }

        // Mirroring a new virtual display id (using --new-display feature)
        DisplayData data = waitDisplayData(timeoutMillis);
        if (data != null) {
            return data.virtualDisplayId;
        }

        // No display id available
        return Device.DISPLAY_ID_NONE;
    }

    /**
     * Returns the virtual display id, blocking up to {@code timeoutMillis} if not yet available.
     * Used by UHID initialization to associate HID devices with the correct display.
     */
    public int getVirtualDisplayIdWithTimeout(long timeoutMillis) throws InterruptedException {
        DisplayData data = waitDisplayData(timeoutMillis);
        if (data != null) {
            return data.virtualDisplayId;
        }
        return Device.DISPLAY_ID_NONE;
    }

    /**
     * Returns the raw configured display id (from constructor options).
     */
    public int getDisplayId() {
        return displayId;
    }

    private DisplayData waitDisplayData(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        synchronized (displayDataAvailable) {
            DisplayData data = displayData.get();
            while (data == null) {
                long timeout = deadline - System.currentTimeMillis();
                if (timeout < 0) {
                    return null;
                }
                if (timeout > 0) {
                    displayDataAvailable.wait(timeout);
                }
                data = displayData.get();
            }

            return data;
        }
    }
}

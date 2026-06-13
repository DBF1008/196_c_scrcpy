package com.genymobile.scrcpy.display;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

public class DisplayPropertiesTracker {

    private static final long PENDING_CACHE_DURATION = 3000; // ms

    private static class PendingChange {
        private final DisplayProperties props;
        private final long timestamp;

        PendingChange(DisplayProperties props, long timestamp) {
            this.props = props;
            this.timestamp = timestamp;
        }
    }

    private final Clock clock;
    private final List<PendingChange> pending = new ArrayList<>();

    public DisplayPropertiesTracker() {
        this(SystemClock::uptimeMillis);
    }

    /**
     * Constructor with configurable clock (for testing).
     *
     * @param clock the clock to use for timestamp measurements
     */
    public DisplayPropertiesTracker(Clock clock) {
        this.clock = clock;
    }

    public synchronized void pushClientRequest(DisplayProperties props) {
        long now = clock.uptimeMillis();
        pending.add(new PendingChange(props, now));
    }

    /**
     * Function to be called when the display properties changed.
     *
     * @param props the new display properties (may be {@code null} if the display info is temporarily unavailable)
     * @return {@code true} if this change is the result of a client request
     */
    public synchronized boolean onChanged(DisplayProperties props) {
        if (props == null) {
            // Display info is unavailable (e.g. display being released); cannot match against pending requests
            return false;
        }
        cleanExpired();
        int index = getMatchingPendingIndex(props);
        if (index == -1) {
            return false;
        }

        pending.subList(0, index + 1).clear();
        return true;
    }

    /**
     * Clear all pending client requests.
     * <p>
     * Should be called when the display changes externally (e.g. system rotation, display loss) to prevent stale
     * pending entries from being incorrectly matched against future display property changes.
     */
    public synchronized void clear() {
        pending.clear();
    }

    private int getMatchingPendingIndex(DisplayProperties props) {
        for (int i = 0; i < pending.size(); ++i) {
            if (pending.get(i).props.equals(props)) {
                return i;
            }
        }
        return -1;
    }

    private int getFirstNonExpiredIndex() {
        long now = clock.uptimeMillis();
        for (int i = 0; i < pending.size(); ++i) {
            if (pending.get(i).timestamp + PENDING_CACHE_DURATION >= now) {
                return i;
            }
        }
        return -1;
    }

    private void cleanExpired() {
        if (pending.isEmpty()) {
            return;
        }

        int firstNonExpiredIndex = getFirstNonExpiredIndex();
        if (firstNonExpiredIndex == 0) {
            // All items are fresh
            return;
        }

        if (firstNonExpiredIndex == -1) {
            // All items have expired
            pending.clear();
        } else {
            // Remove all the items up to the first non-expired index
            pending.subList(0, firstNonExpiredIndex).clear();
        }
    }
}

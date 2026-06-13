package com.genymobile.scrcpy.display;

import com.genymobile.scrcpy.model.Size;

import org.junit.Assert;
import org.junit.Test;

public class DisplayPropertiesTrackerTest {

    private static DisplayProperties props(int width, int height, int rotation) {
        return new DisplayProperties(new Size(width, height), rotation);
    }

    @Test
    public void testClientRequestMatched() {
        long[] now = {1000L};
        DisplayPropertiesTracker tracker = new DisplayPropertiesTracker(() -> now[0]);

        tracker.pushClientRequest(props(800, 600, 0));

        // The display change matches the client request
        Assert.assertTrue(tracker.onChanged(props(800, 600, 0)));

        // The pending request has been consumed: an identical change is no longer attributed to a client request
        Assert.assertFalse(tracker.onChanged(props(800, 600, 0)));
    }

    @Test
    public void testSystemChangeNotMatchedAndKeepsPending() {
        long[] now = {1000L};
        DisplayPropertiesTracker tracker = new DisplayPropertiesTracker(() -> now[0]);

        tracker.pushClientRequest(props(800, 600, 0));

        // A system rotation to a size/rotation that was never requested is not a client resize
        Assert.assertFalse(tracker.onChanged(props(600, 800, 1)));

        // ... and it must not consume the still-pending client request
        Assert.assertTrue(tracker.onChanged(props(800, 600, 0)));
    }

    @Test
    public void testExpiredRequestNotMatched() {
        long[] now = {1000L};
        DisplayPropertiesTracker tracker = new DisplayPropertiesTracker(() -> now[0]);

        tracker.pushClientRequest(props(800, 600, 0));

        // Move past the cache duration (3000 ms): the stale client request must not be judged as a valid client resize
        now[0] = 1000L + 3001L;
        Assert.assertFalse(tracker.onChanged(props(800, 600, 0)));
    }

    @Test
    public void testDisplayLossClearsPending() {
        long[] now = {1000L};
        DisplayPropertiesTracker tracker = new DisplayPropertiesTracker(() -> now[0]);

        tracker.pushClientRequest(props(800, 600, 0));

        // The display is (temporarily) gone
        Assert.assertFalse(tracker.onChanged(null));

        // A later change identical to the pre-loss request must not be mis-attributed to the (now dropped) client request
        Assert.assertFalse(tracker.onChanged(props(800, 600, 0)));
    }

    @Test
    public void testRapidRequestsClearSupersededPrefix() {
        long[] now = {1000L};
        DisplayPropertiesTracker tracker = new DisplayPropertiesTracker(() -> now[0]);

        tracker.pushClientRequest(props(100, 100, 0));
        tracker.pushClientRequest(props(200, 200, 0));
        tracker.pushClientRequest(props(300, 300, 0));

        // The display jumps straight to the last requested size; the earlier (superseded) requests are dropped together
        Assert.assertTrue(tracker.onChanged(props(300, 300, 0)));
        Assert.assertFalse(tracker.onChanged(props(100, 100, 0)));
        Assert.assertFalse(tracker.onChanged(props(200, 200, 0)));
    }
}

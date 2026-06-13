package com.genymobile.scrcpy.display;

import com.genymobile.scrcpy.model.Size;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DisplayPropertiesTrackerTest {

    private static final long EXPIRY_MS = 3000;

    private static class FakeClock implements Clock {
        long time;

        @Override
        public long uptimeMillis() {
            return time;
        }
    }

    private FakeClock clock;
    private DisplayPropertiesTracker tracker;

    @Before
    public void setUp() {
        clock = new FakeClock();
        tracker = new DisplayPropertiesTracker(clock);
    }

    @Test
    public void testPushAndMatch() {
        DisplayProperties props = new DisplayProperties(new Size(1280, 720), 0);
        tracker.pushClientRequest(props);
        Assert.assertTrue("Matching change should return true", tracker.onChanged(props));
    }

    @Test
    public void testUnmatchedChange() {
        DisplayProperties requested = new DisplayProperties(new Size(1280, 720), 0);
        DisplayProperties actual = new DisplayProperties(new Size(1920, 1080), 0);
        tracker.pushClientRequest(requested);
        Assert.assertFalse("Non-matching change should return false", tracker.onChanged(actual));
    }

    @Test
    public void testMatchConsumesOlderEntries() {
        DisplayProperties props1 = new DisplayProperties(new Size(1280, 720), 0);
        DisplayProperties props2 = new DisplayProperties(new Size(1920, 1080), 0);

        tracker.pushClientRequest(props1);
        tracker.pushClientRequest(props2);

        // Matching props2 should consume both props1 and props2
        Assert.assertTrue(tracker.onChanged(props2));

        // props1 should have been consumed as well
        Assert.assertFalse("Older entries should be consumed when a later entry matches",
                tracker.onChanged(props1));
    }

    @Test
    public void testClear() {
        DisplayProperties props = new DisplayProperties(new Size(1280, 720), 0);
        tracker.pushClientRequest(props);
        tracker.clear();
        Assert.assertFalse("Cleared entries should not match", tracker.onChanged(props));
    }

    @Test
    public void testOnChangedWithNullProps() {
        DisplayProperties props = new DisplayProperties(new Size(1280, 720), 0);
        tracker.pushClientRequest(props);
        Assert.assertFalse("Null props should not match any pending entry", tracker.onChanged(null));
        // Pending entry should still be there
        Assert.assertTrue("Pending entry should survive null onChanged", tracker.onChanged(props));
    }

    @Test
    public void testExpiredEntries() {
        DisplayProperties props = new DisplayProperties(new Size(1280, 720), 0);
        clock.time = 0;
        tracker.pushClientRequest(props);

        // Advance past expiry
        clock.time = EXPIRY_MS + 1;
        Assert.assertFalse("Expired entry should not match", tracker.onChanged(props));
    }

    @Test
    public void testNonExpiredEntriesSurvive() {
        DisplayProperties props = new DisplayProperties(new Size(1280, 720), 0);
        clock.time = 0;
        tracker.pushClientRequest(props);

        // Advance but not past expiry
        clock.time = EXPIRY_MS - 1;
        Assert.assertTrue("Non-expired entry should still match", tracker.onChanged(props));
    }

    @Test
    public void testPartialExpiry() {
        DisplayProperties props1 = new DisplayProperties(new Size(1280, 720), 0);
        DisplayProperties props2 = new DisplayProperties(new Size(1920, 1080), 0);

        clock.time = 0;
        tracker.pushClientRequest(props1);

        // Push props2 after props1 would have expired
        clock.time = EXPIRY_MS + 100;
        tracker.pushClientRequest(props2);

        // props1 should have been cleaned up, props2 should match
        Assert.assertFalse("Expired props1 should not match", tracker.onChanged(props1));
        Assert.assertTrue("Non-expired props2 should match", tracker.onChanged(props2));
    }

    @Test
    public void testOnChangedWithNoPending() {
        DisplayProperties props = new DisplayProperties(new Size(1280, 720), 0);
        Assert.assertFalse("Should return false when no pending entries", tracker.onChanged(props));
    }

    @Test
    public void testClearIsIdempotent() {
        tracker.clear();
        tracker.clear();
        DisplayProperties props = new DisplayProperties(new Size(1280, 720), 0);
        Assert.assertFalse("Clear on empty tracker should be safe", tracker.onChanged(props));
    }

    @Test
    public void testMatchWithDifferentRotation() {
        DisplayProperties requested = new DisplayProperties(new Size(1280, 720), 0);
        DisplayProperties rotated = new DisplayProperties(new Size(1280, 720), 1);
        tracker.pushClientRequest(requested);
        Assert.assertFalse("Different rotation should not match", tracker.onChanged(rotated));
    }

    @Test
    public void testMultiplePushSameProps() {
        DisplayProperties props = new DisplayProperties(new Size(1280, 720), 0);
        tracker.pushClientRequest(props);
        tracker.pushClientRequest(props);

        // First match should consume both entries
        Assert.assertTrue(tracker.onChanged(props));
        Assert.assertFalse("All matching entries should be consumed", tracker.onChanged(props));
    }
}

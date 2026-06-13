package com.genymobile.scrcpy.display;

import com.genymobile.scrcpy.model.Size;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DisplayResizeDebouncerTest {

    private static class FakeClock implements Clock {
        long time;

        @Override
        public long uptimeMillis() {
            return time;
        }
    }

    private static class TestCallback implements DisplayResizeDebouncer.Callback {
        final List<Size> triggered = new ArrayList<>();
        CountDownLatch latch;

        TestCallback(int expectedCount) {
            latch = new CountDownLatch(expectedCount);
        }

        @Override
        public void trigger(Size size) {
            synchronized (triggered) {
                triggered.add(size);
            }
            latch.countDown();
        }

        List<Size> getTriggered() {
            synchronized (triggered) {
                return new ArrayList<>(triggered);
            }
        }
    }

    private DisplayResizeDebouncer debouncer;

    @After
    public void tearDown() {
        if (debouncer != null) {
            debouncer.stop();
        }
    }

    @Test
    public void testRapidResizeCoalescing() throws Exception {
        FakeClock clock = new FakeClock();
        TestCallback callback = new TestCallback(1);
        debouncer = new DisplayResizeDebouncer(50, clock, callback);
        debouncer.start();

        // Send multiple resize requests quickly (clock doesn't advance, so deadline is never reached
        // by the debounce thread between requests)
        debouncer.requestResize(new Size(100, 100));
        debouncer.requestResize(new Size(200, 200));
        debouncer.requestResize(new Size(300, 300));

        // Now advance the clock past the deadline so the debounce thread can fire
        clock.time = 100;

        Assert.assertTrue("Debouncer should fire within 2 seconds",
                callback.latch.await(2, TimeUnit.SECONDS));

        List<Size> triggered = callback.getTriggered();
        Assert.assertEquals("Only the last resize should trigger", 1, triggered.size());
        Assert.assertEquals(new Size(300, 300), triggered.get(0));

        // Wait a bit to ensure no spurious triggers
        Thread.sleep(200);
        Assert.assertEquals("No additional triggers should occur", 1, callback.getTriggered().size());
    }

    @Test
    public void testCancelBeforeTrigger() throws Exception {
        FakeClock clock = new FakeClock();
        TestCallback callback = new TestCallback(1);
        debouncer = new DisplayResizeDebouncer(200, clock, callback);
        debouncer.start();

        debouncer.requestResize(new Size(100, 100));
        debouncer.cancelResize();

        // Advance clock past what would have been the deadline
        clock.time = 300;

        // The debounce thread should be waiting on wait() with no pending request
        Assert.assertFalse("No trigger should occur after cancellation",
                callback.latch.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testStopRejectsNewRequests() throws Exception {
        FakeClock clock = new FakeClock();
        TestCallback callback = new TestCallback(1);
        debouncer = new DisplayResizeDebouncer(50, clock, callback);
        debouncer.start();
        debouncer.stop();

        // Request after stop should be silently rejected
        debouncer.requestResize(new Size(100, 100));
        clock.time = 200;

        Assert.assertFalse("No trigger should occur after stop",
                callback.latch.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testIsStopped() {
        FakeClock clock = new FakeClock();
        TestCallback callback = new TestCallback(1);
        debouncer = new DisplayResizeDebouncer(50, clock, callback);

        Assert.assertFalse("Should not be stopped before start", debouncer.isStopped());

        debouncer.start();
        Assert.assertFalse("Should not be stopped after start", debouncer.isStopped());

        debouncer.stop();
        Assert.assertTrue("Should be stopped after stop", debouncer.isStopped());
    }

    @Test
    public void testStopCancelsPendingResize() throws Exception {
        FakeClock clock = new FakeClock();
        TestCallback callback = new TestCallback(1);
        debouncer = new DisplayResizeDebouncer(500, clock, callback);
        debouncer.start();

        debouncer.requestResize(new Size(100, 100));

        // Stop should interrupt the debounce thread and prevent any trigger
        debouncer.stop();

        // Even if we advance the clock, no trigger should occur
        clock.time = 1000;

        Assert.assertFalse("No trigger should occur after stop with pending resize",
                callback.latch.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testCancelResizeIsIdempotent() throws Exception {
        FakeClock clock = new FakeClock();
        TestCallback callback = new TestCallback(1);
        debouncer = new DisplayResizeDebouncer(50, clock, callback);
        debouncer.start();

        // Cancel without any pending request should be safe
        debouncer.cancelResize();
        debouncer.cancelResize();

        // Now send a real request — cancel should not prevent future requests
        debouncer.requestResize(new Size(200, 200));
        clock.time = 100;

        Assert.assertTrue("Request after cancel should still trigger",
                callback.latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(new Size(200, 200), callback.getTriggered().get(0));
    }

    @Test
    public void testNewRequestWhilePendingDoesNotResetDeadline() throws Exception {
        FakeClock clock = new FakeClock();
        TestCallback callback = new TestCallback(1);
        debouncer = new DisplayResizeDebouncer(100, clock, callback);
        debouncer.start();

        // First request: deadline = 0 + 100 = 100
        debouncer.requestResize(new Size(100, 100));

        // Advance clock to just before deadline
        clock.time = 90;

        // Second request while pending: deadline is NOT reset (only set when request == null)
        debouncer.requestResize(new Size(200, 200));

        // Advance past original deadline
        clock.time = 110;

        Assert.assertTrue("Should trigger after original deadline",
                callback.latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals("Should trigger with latest size",
                new Size(200, 200), callback.getTriggered().get(0));
    }

    @Test
    public void testSequentialResizesAfterTrigger() throws Exception {
        FakeClock clock = new FakeClock();
        TestCallback callback = new TestCallback(2);
        debouncer = new DisplayResizeDebouncer(100, clock, callback);
        debouncer.start();

        // First request: deadline = 0 + 100 = 100
        debouncer.requestResize(new Size(100, 100));
        clock.time = 200;

        // Wait for first trigger to fire
        Thread.sleep(300);
        Assert.assertEquals("First trigger should have fired", 1, callback.getTriggered().size());
        Assert.assertEquals(new Size(100, 100), callback.getTriggered().get(0));

        // Second request: request was null after first trigger, so deadline = 200 + 100 = 300
        debouncer.requestResize(new Size(200, 200));
        clock.time = 350;

        // Wait for second trigger
        Assert.assertTrue("Second request should also trigger",
                callback.latch.await(2, TimeUnit.SECONDS));

        List<Size> triggered = callback.getTriggered();
        Assert.assertEquals(2, triggered.size());
        Assert.assertEquals(new Size(100, 100), triggered.get(0));
        Assert.assertEquals(new Size(200, 200), triggered.get(1));
    }
}

package com.genymobile.scrcpy.display;

import com.genymobile.scrcpy.model.Size;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class DisplayResizeDebouncerTest {

    // A no-op callback for the state-only tests (the debounce thread is not started, so it is never invoked).
    private static final DisplayResizeDebouncer.Callback NOOP = (size, generation) -> {
        // do nothing
    };

    @Test
    public void testRapidRequestsCoalesce() {
        long[] now = {1000L};
        DisplayResizeDebouncer debouncer = new DisplayResizeDebouncer(NOOP, () -> now[0], 300L);

        debouncer.requestResize(new Size(100, 100));
        long deadline = debouncer.getDeadline();
        long generation = debouncer.getGeneration();

        // A second request within the debounce window keeps the same deadline and generation; only the latest size survives
        now[0] = 1100L;
        debouncer.requestResize(new Size(200, 200));

        Assert.assertEquals(deadline, debouncer.getDeadline());
        Assert.assertEquals(generation, debouncer.getGeneration());
        Assert.assertEquals(new Size(200, 200), debouncer.getPendingSize());
    }

    @Test
    public void testCancelInvalidatesCapturedGeneration() {
        long[] now = {1000L};
        DisplayResizeDebouncer debouncer = new DisplayResizeDebouncer(NOOP, () -> now[0], 300L);

        debouncer.requestResize(new Size(100, 100));
        long capturedGeneration = debouncer.getGeneration();
        Assert.assertTrue(debouncer.isCurrentGeneration(capturedGeneration));

        // A system display change cancels the pending resize: a resize captured before the cancel is now stale
        debouncer.cancelResize();
        Assert.assertFalse(debouncer.isCurrentGeneration(capturedGeneration));
    }

    @Test
    public void testFreshRequestSupersedesCapturedGeneration() {
        long[] now = {1000L};
        DisplayResizeDebouncer debouncer = new DisplayResizeDebouncer(NOOP, () -> now[0], 300L);

        debouncer.requestResize(new Size(100, 100));
        long capturedGeneration = debouncer.getGeneration();

        // Simulate the debounce loop having captured (and cleared) the pending request, then a fresh request arriving in the gap:
        // cancelResize() leaves request == null, exactly as it would be right after a capture.
        debouncer.cancelResize();
        long generationAfterCancel = debouncer.getGeneration();

        // A fresh request cycle must advance the generation again, so the previously captured resize is superseded
        debouncer.requestResize(new Size(200, 200));
        Assert.assertNotEquals(generationAfterCancel, debouncer.getGeneration());
        Assert.assertFalse(debouncer.isCurrentGeneration(capturedGeneration));
    }

    @Test
    public void testStopInvalidatesAndIgnoresFurtherRequests() {
        long[] now = {1000L};
        DisplayResizeDebouncer debouncer = new DisplayResizeDebouncer(NOOP, () -> now[0], 300L);

        debouncer.requestResize(new Size(100, 100));
        long capturedGeneration = debouncer.getGeneration();
        Assert.assertTrue(debouncer.isRunning());

        debouncer.stop();

        Assert.assertFalse(debouncer.isRunning());
        // After stop, nothing may be applied anymore...
        Assert.assertFalse(debouncer.isCurrentGeneration(capturedGeneration));

        // ... and further requests are ignored (generation unchanged, the new size is not recorded)
        long generationAfterStop = debouncer.getGeneration();
        debouncer.requestResize(new Size(500, 500));
        Assert.assertEquals(generationAfterStop, debouncer.getGeneration());
        Assert.assertNotEquals(new Size(500, 500), debouncer.getPendingSize());
    }

    @Test(timeout = 5000)
    public void testDebounceThreadFiresLatestSizeOnce() throws InterruptedException {
        AtomicReference<Size> firedSize = new AtomicReference<>();
        AtomicLong firedGeneration = new AtomicLong(-1L);
        AtomicInteger fireCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);

        // Use the real clock here (the debounce loop's wait() sleeps on real time) with a short delay.
        DisplayResizeDebouncer debouncer = new DisplayResizeDebouncer((size, generation) -> {
            firedSize.set(size);
            firedGeneration.set(generation);
            fireCount.incrementAndGet();
            latch.countDown();
        }, System::currentTimeMillis, 40L);
        debouncer.start();
        try {
            debouncer.requestResize(new Size(100, 100));
            debouncer.requestResize(new Size(200, 200)); // coalesced with the previous one

            Assert.assertTrue("the debouncer should have fired", latch.await(2, TimeUnit.SECONDS));

            // Ensure it fires exactly once for the coalesced burst
            Thread.sleep(150);
            Assert.assertEquals(1, fireCount.get());
            Assert.assertEquals(new Size(200, 200), firedSize.get());
            // The fired resize is still current (no cancel happened), so it would be applied
            Assert.assertTrue(debouncer.isCurrentGeneration(firedGeneration.get()));
        } finally {
            debouncer.stop();
        }
    }
}

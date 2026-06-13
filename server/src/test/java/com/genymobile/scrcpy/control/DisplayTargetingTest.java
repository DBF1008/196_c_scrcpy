package com.genymobile.scrcpy.control;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DisplayTargetingTest {

    private static final int NONE = DisplayTargeting.DISPLAY_ID_NONE;

    @Test
    public void testConstantValue() {
        // Must match com.genymobile.scrcpy.device.Device.DISPLAY_ID_NONE (cross-checked in DisplayTargetingConstantsTest, which is android-coupled)
        Assert.assertEquals(-1, DisplayTargeting.DISPLAY_ID_NONE);
    }

    @Test
    public void testNormalDisplaySourceWins() {
        DisplayTargeting targeting = new DisplayTargeting(5);

        Assert.assertFalse(targeting.isNewVirtualDisplay());
        Assert.assertEquals(5, targeting.getActionDisplayId());
        Assert.assertEquals(5, targeting.getStartAppDisplayId());
        Assert.assertEquals(5, targeting.getUhidDisplayId(true));
        Assert.assertEquals(5, targeting.getUhidDisplayId(false));

        // Even if a virtual display is notified, the source display id still wins for a real display
        targeting.onNewVirtualDisplay(99);
        Assert.assertEquals(5, targeting.getActionDisplayId());
        Assert.assertEquals(5, targeting.getStartAppDisplayId());
        Assert.assertEquals(99, targeting.getVirtualDisplayId());
    }

    @Test
    public void testNewVirtualDisplayInitiallyUnknown() {
        DisplayTargeting targeting = new DisplayTargeting(NONE);

        Assert.assertTrue(targeting.isNewVirtualDisplay());
        Assert.assertEquals(NONE, targeting.getActionDisplayId());
        Assert.assertEquals(NONE, targeting.getVirtualDisplayId());
        // Association disabled: never blocks, never resolves to a virtual id
        Assert.assertEquals(NONE, targeting.getUhidDisplayId(false));
    }

    @Test
    public void testAwaitTimesOutWhenNeverNotified() throws InterruptedException {
        DisplayTargeting targeting = new DisplayTargeting(NONE);

        long start = System.currentTimeMillis();
        int result = targeting.awaitVirtualDisplayId(80);
        long elapsed = System.currentTimeMillis() - start;

        Assert.assertEquals(NONE, result);
        Assert.assertTrue("await returned too early (" + elapsed + "ms)", elapsed >= 60);
    }

    @Test
    public void testAwaitWakesOnNotificationFromAnotherThread() throws InterruptedException {
        DisplayTargeting targeting = new DisplayTargeting(NONE);

        Thread publisher = new Thread(() -> {
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            targeting.onNewVirtualDisplay(42);
        });
        publisher.start();

        long start = System.currentTimeMillis();
        int result = targeting.awaitVirtualDisplayId(2000);
        long elapsed = System.currentTimeMillis() - start;
        publisher.join();

        Assert.assertEquals(42, result);
        Assert.assertTrue("await did not wake promptly (" + elapsed + "ms)", elapsed < 1500);

        // Once known, the id-based rules resolve to it without blocking
        Assert.assertEquals(42, targeting.getActionDisplayId());
        Assert.assertEquals(42, targeting.getStartAppDisplayId());
        Assert.assertEquals(42, targeting.getVirtualDisplayId());
        Assert.assertEquals(42, targeting.getUhidDisplayId(true));
        // ...but association disabled still yields NONE even when a virtual id is known
        Assert.assertEquals(NONE, targeting.getUhidDisplayId(false));
    }

    @Test
    public void testDoublePublishKeepsLatestAndDoesNotDeadlock() {
        DisplayTargeting targeting = new DisplayTargeting(NONE);

        targeting.onNewVirtualDisplay(42);
        targeting.onNewVirtualDisplay(43); // must not block/deadlock (no waiter, not the first transition)

        Assert.assertEquals(43, targeting.getVirtualDisplayId());
        Assert.assertEquals(43, targeting.getActionDisplayId());
    }

    @Test
    public void testGetStartAppDisplayIdSwallowsInterrupt() throws InterruptedException {
        DisplayTargeting targeting = new DisplayTargeting(NONE);

        AtomicInteger result = new AtomicInteger(Integer.MIN_VALUE);
        Thread waiter = new Thread(() -> result.set(targeting.getStartAppDisplayId()));
        waiter.start();

        // Give the waiter time to enter the wait, then interrupt it
        Thread.sleep(80);
        long start = System.currentTimeMillis();
        waiter.interrupt();
        waiter.join(2000);
        long elapsed = System.currentTimeMillis() - start;

        Assert.assertFalse("waiter thread did not terminate", waiter.isAlive());
        // Interrupt is swallowed and returns NONE, well before the 1000ms timeout would elapse
        Assert.assertEquals(NONE, result.get());
        Assert.assertTrue("interrupt did not break the wait (" + elapsed + "ms)", elapsed < 500);
    }

    @Test
    public void testAwaitThrowsInterruptedException() throws InterruptedException {
        DisplayTargeting targeting = new DisplayTargeting(NONE);

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                targeting.awaitVirtualDisplayId(2000);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        waiter.start();

        Thread.sleep(80);
        waiter.interrupt();
        waiter.join(2000);

        Assert.assertFalse(waiter.isAlive());
        Assert.assertTrue("expected InterruptedException, got " + thrown.get(), thrown.get() instanceof InterruptedException);
    }
}

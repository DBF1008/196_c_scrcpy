package com.genymobile.scrcpy.control;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClipboardGuardTest {

    @Test
    public void testDelayedCallbackSuppressed() throws InterruptedException {
        Controller.ClipboardGuard guard = new Controller.ClipboardGuard();
        guard.beginSet("hello");

        // Simulate a delayed callback arriving 200ms later (within the 500ms window)
        Thread.sleep(200);
        Assert.assertTrue("Delayed callback within window should be suppressed", guard.shouldSuppress("hello"));
    }

    @Test
    public void testWindowExpiry() throws InterruptedException {
        Controller.ClipboardGuard guard = new Controller.ClipboardGuard();
        guard.beginSet("hello");

        // Wait for the suppression window to expire (500ms + margin)
        Thread.sleep(600);
        Assert.assertFalse("Callback after window expiry should NOT be suppressed", guard.shouldSuppress("hello"));
    }

    @Test
    public void testSameTextRepeatedSet() throws InterruptedException {
        Controller.ClipboardGuard guard = new Controller.ClipboardGuard();

        guard.beginSet("same");
        Assert.assertTrue("First echo should be suppressed", guard.shouldSuppress("same"));

        // Set the same text again
        guard.beginSet("same");
        Assert.assertTrue("Second echo should also be suppressed", guard.shouldSuppress("same"));
    }

    @Test
    public void testExternalChangeNotSuppressed() throws InterruptedException {
        Controller.ClipboardGuard guard = new Controller.ClipboardGuard();
        guard.beginSet("our-text");

        // A different text arrives within the window — this is a genuine external change
        Assert.assertFalse("External change with different text should NOT be suppressed",
                guard.shouldSuppress("user-copied-something-else"));

        // Our own echo should still be suppressed
        Assert.assertTrue("Our own echo should still be suppressed", guard.shouldSuppress("our-text"));
    }

    @Test
    public void testRapidSuccessiveSets() throws InterruptedException {
        Controller.ClipboardGuard guard = new Controller.ClipboardGuard();

        guard.beginSet("first");
        // Second set arrives before the first callback
        guard.beginSet("second");

        // Callback for "first" should still be suppressed (stored in previousSetText)
        Assert.assertTrue("Callback for previous text should be suppressed", guard.shouldSuppress("first"));

        // Callback for "second" should be suppressed (stored in lastSetText)
        Assert.assertTrue("Callback for current text should be suppressed", guard.shouldSuppress("second"));
    }

    @Test
    public void testNullCallbackText() {
        Controller.ClipboardGuard guard = new Controller.ClipboardGuard();
        guard.beginSet("hello");

        // Null callback text should not cause NPE and should not be suppressed
        Assert.assertFalse("Null callback text should NOT be suppressed", guard.shouldSuppress(null));
    }

    @Test
    public void testNoPriorSet() {
        Controller.ClipboardGuard guard = new Controller.ClipboardGuard();

        // No beginSet() called — should not suppress anything
        Assert.assertFalse("Callback with no prior set should NOT be suppressed", guard.shouldSuppress("anything"));
    }

    @Test
    public void testPasteWithAutosyncDoesNotDuplicate() throws InterruptedException {
        // Simulates the paste+autosync scenario:
        // 1. PC sends SET_CLIPBOARD with paste=true
        // 2. Controller arms guard, calls setPrimaryClip
        // 3. PASTE key is injected
        // 4. Delayed listener fires
        Controller.ClipboardGuard guard = new Controller.ClipboardGuard();
        AtomicInteger syncCount = new AtomicInteger(0);

        guard.beginSet("paste-content");

        // Simulate: setClipboard succeeds, paste injected, then delayed listener fires
        Thread.sleep(100);
        String callbackText = "paste-content";
        if (!guard.shouldSuppress(callbackText)) {
            syncCount.incrementAndGet();
        }

        Assert.assertEquals("Only zero clipboard syncs should occur (suppressed)", 0, syncCount.get());
    }

    @Test
    public void testNoClipboardManagerScenario() {
        // When clipboard manager is null, Device.setClipboardText returns false,
        // and no listener fires. The guard was armed but will expire harmlessly.
        Controller.ClipboardGuard guard = new Controller.ClipboardGuard();
        guard.beginSet("test");

        // No callback fires. Verify the guard doesn't cause issues when queried later.
        // After window expiry, guard resets cleanly.
        // (We can't easily test the expiry here without sleeping, but the logic is covered
        // by testWindowExpiry. This test documents the no-clipboard-manager contract.)
        Assert.assertTrue("Guard is armed and would suppress if callback arrived",
                guard.shouldSuppress("test"));
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // Verify thread safety: one thread calls beginSet, another calls shouldSuppress
        Controller.ClipboardGuard guard = new Controller.ClipboardGuard();
        int iterations = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger exceptions = new AtomicInteger(0);

        // Writer thread
        Thread writer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                    guard.beginSet("text-" + i);
                }
            } catch (Exception e) {
                exceptions.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Reader thread
        Thread reader = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                    guard.shouldSuppress("text-" + i);
                }
            } catch (Exception e) {
                exceptions.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        writer.start();
        reader.start();
        startLatch.countDown();
        Assert.assertTrue("Threads should complete within 10 seconds",
                doneLatch.await(10, TimeUnit.SECONDS));
        Assert.assertEquals("No exceptions should occur during concurrent access", 0, exceptions.get());
    }
}

package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.model.Position;
import com.genymobile.scrcpy.model.Size;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for {@link DisplayRouter} — display ID resolution and virtual display wait/signal logic.
 * <p>
 * These tests are fully runnable in JVM (no Android framework dependencies) because they only
 * exercise the routing decision logic, not coordinate mapping (which uses android.util.Pair).
 */
public class DisplayRouterTest {

    // --- getActionDisplayId() ---

    @Test
    public void actionDisplayId_mainDisplay_returnsZero() {
        DisplayRouter router = new DisplayRouter(0);
        Assert.assertEquals(0, router.getActionDisplayId());
    }

    @Test
    public void actionDisplayId_secondaryDisplay_returnsConfiguredId() {
        DisplayRouter router = new DisplayRouter(5);
        Assert.assertEquals(5, router.getActionDisplayId());
    }

    @Test
    public void actionDisplayId_newDisplay_noVirtual_returnsNone() {
        DisplayRouter router = new DisplayRouter(Device.DISPLAY_ID_NONE);
        Assert.assertEquals(Device.DISPLAY_ID_NONE, router.getActionDisplayId());
    }

    @Test
    public void actionDisplayId_newDisplay_afterVirtualDisplay_returnsVirtualId() {
        DisplayRouter router = new DisplayRouter(Device.DISPLAY_ID_NONE);
        Size videoSize = new Size(1080, 1920);
        PositionMapper mapper = new PositionMapper(videoSize, null);
        router.onNewVirtualDisplay(42, mapper);
        Assert.assertEquals(42, router.getActionDisplayId());
    }

    @Test
    public void actionDisplayId_realDisplay_ignoresVirtualDisplay() {
        // When a real display is configured, virtual display notifications don't override it
        DisplayRouter router = new DisplayRouter(3);
        Size videoSize = new Size(1080, 1920);
        PositionMapper mapper = new PositionMapper(videoSize, null);
        router.onNewVirtualDisplay(99, mapper);
        // getActionDisplayId should still return the configured displayId, not the virtual one
        Assert.assertEquals(3, router.getActionDisplayId());
    }

    // --- getDisplayId() ---

    @Test
    public void getDisplayId_returnsConfiguredId() {
        Assert.assertEquals(0, new DisplayRouter(0).getDisplayId());
        Assert.assertEquals(7, new DisplayRouter(7).getDisplayId());
        Assert.assertEquals(Device.DISPLAY_ID_NONE, new DisplayRouter(Device.DISPLAY_ID_NONE).getDisplayId());
    }

    // --- onNewVirtualDisplay() multiple calls ---

    @Test
    public void onNewVirtualDisplay_secondCall_updatesVirtualDisplayId() {
        DisplayRouter router = new DisplayRouter(Device.DISPLAY_ID_NONE);
        Size videoSize = new Size(1080, 1920);
        PositionMapper mapper = new PositionMapper(videoSize, null);

        router.onNewVirtualDisplay(10, mapper);
        Assert.assertEquals(10, router.getActionDisplayId());

        // Second notification updates the virtual display ID
        router.onNewVirtualDisplay(20, mapper);
        Assert.assertEquals(20, router.getActionDisplayId());
    }

    // --- getStartAppDisplayId() ---

    @Test
    public void startAppDisplayId_realDisplay_returnsImmediately() throws InterruptedException {
        DisplayRouter router = new DisplayRouter(2);
        Assert.assertEquals(2, router.getStartAppDisplayId(1000));
    }

    @Test
    public void startAppDisplayId_newDisplay_noNotification_returnsNoneOnTimeout() throws InterruptedException {
        DisplayRouter router = new DisplayRouter(Device.DISPLAY_ID_NONE);
        long start = System.currentTimeMillis();
        int result = router.getStartAppDisplayId(200);
        long elapsed = System.currentTimeMillis() - start;
        Assert.assertEquals(Device.DISPLAY_ID_NONE, result);
        Assert.assertTrue("Should have waited at least 150ms", elapsed >= 150);
    }

    // --- getVirtualDisplayIdWithTimeout() ---

    @Test
    public void virtualDisplayIdWithTimeout_success() throws InterruptedException {
        DisplayRouter router = new DisplayRouter(Device.DISPLAY_ID_NONE);

        // Simulate another thread providing the virtual display
        Thread provider = new Thread(() -> {
            try {
                Thread.sleep(100);
                Size videoSize = new Size(1080, 1920);
                PositionMapper mapper = new PositionMapper(videoSize, null);
                router.onNewVirtualDisplay(42, mapper);
            } catch (InterruptedException e) {
                // ignore
            }
        });
        provider.start();

        int result = router.getVirtualDisplayIdWithTimeout(2000);
        Assert.assertEquals(42, result);
        provider.join();
    }

    @Test
    public void virtualDisplayIdWithTimeout_timeout() throws InterruptedException {
        DisplayRouter router = new DisplayRouter(Device.DISPLAY_ID_NONE);
        long start = System.currentTimeMillis();
        int result = router.getVirtualDisplayIdWithTimeout(200);
        long elapsed = System.currentTimeMillis() - start;
        Assert.assertEquals(Device.DISPLAY_ID_NONE, result);
        Assert.assertTrue("Should have waited at least 150ms", elapsed >= 150);
    }

    // --- Thread signaling ---

    @Test
    public void onNewVirtualDisplay_firstCall_signalsWaitingThread() throws InterruptedException {
        DisplayRouter router = new DisplayRouter(Device.DISPLAY_ID_NONE);
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(Device.DISPLAY_ID_NONE);

        Thread waiter = new Thread(() -> {
            waiting.countDown();
            try {
                result.set(router.getVirtualDisplayIdWithTimeout(5000));
            } catch (InterruptedException e) {
                // ignore
            }
        });
        waiter.start();

        // Wait until the waiter thread is blocked
        Assert.assertTrue("Waiter thread should start within 2s", waiting.await(2, TimeUnit.SECONDS));
        // Give it time to actually enter wait()
        Thread.sleep(200);

        // Signal from another thread
        Size videoSize = new Size(1080, 1920);
        PositionMapper mapper = new PositionMapper(videoSize, null);
        router.onNewVirtualDisplay(7, mapper);

        waiter.join(2000);
        Assert.assertFalse("Waiter thread should have completed", waiter.isAlive());
        Assert.assertEquals(7, result.get());
    }

    // --- getEventPointAndDisplayId() (limited test — android.util.Pair may be stub) ---

    @Test
    public void eventPointAndDisplayId_matchingSize_returnsNonNull() {
        DisplayRouter router = new DisplayRouter(0);
        Size videoSize = new Size(1080, 1920);
        PositionMapper mapper = new PositionMapper(videoSize, null);
        router.onNewVirtualDisplay(5, mapper);

        // Position with matching screen size → should return non-null
        Position position = new Position(100, 200, 1080, 1920);
        Assert.assertNotNull(router.getEventPointAndDisplayId(position));
    }

    @Test
    public void eventPointAndDisplayId_mismatchedSize_returnsNull() {
        DisplayRouter router = new DisplayRouter(0);
        Size videoSize = new Size(1080, 1920);
        PositionMapper mapper = new PositionMapper(videoSize, null);
        router.onNewVirtualDisplay(5, mapper);

        // Position with mismatched screen size → should return null
        Position position = new Position(100, 200, 720, 1280);
        Assert.assertNull(router.getEventPointAndDisplayId(position));
    }
}

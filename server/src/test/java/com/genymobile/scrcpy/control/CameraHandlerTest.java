package com.genymobile.scrcpy.control;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link CameraHandler} — verifies delegation to the {@link CameraHandler.CameraControl} interface.
 * <p>
 * Uses a simple recording fake to verify that CameraHandler correctly forwards
 * torch and zoom commands to the underlying camera control implementation.
 */
public class CameraHandlerTest {

    /**
     * Records all camera control invocations for verification.
     */
    private static class RecordingCameraControl implements CameraHandler.CameraControl {
        String lastAction;
        boolean lastTorchValue;
        int torchCount;
        int zoomInCount;
        int zoomOutCount;

        @Override
        public void setTorchEnabled(boolean enabled) {
            lastAction = "torch";
            lastTorchValue = enabled;
            torchCount++;
        }

        @Override
        public void zoomIn() {
            lastAction = "zoomIn";
            zoomInCount++;
        }

        @Override
        public void zoomOut() {
            lastAction = "zoomOut";
            zoomOutCount++;
        }
    }

    // --- Delegation tests ---

    @Test
    public void handleSetTorch_delegatesOn() {
        RecordingCameraControl fake = new RecordingCameraControl();
        CameraHandler handler = new CameraHandler();
        handler.setCameraControl(fake);

        handler.handleSetTorch(true);

        Assert.assertEquals("torch", fake.lastAction);
        Assert.assertTrue(fake.lastTorchValue);
        Assert.assertEquals(1, fake.torchCount);
    }

    @Test
    public void handleSetTorch_delegatesOff() {
        RecordingCameraControl fake = new RecordingCameraControl();
        CameraHandler handler = new CameraHandler();
        handler.setCameraControl(fake);

        handler.handleSetTorch(false);

        Assert.assertEquals("torch", fake.lastAction);
        Assert.assertFalse(fake.lastTorchValue);
        Assert.assertEquals(1, fake.torchCount);
    }

    @Test
    public void handleZoomIn_delegates() {
        RecordingCameraControl fake = new RecordingCameraControl();
        CameraHandler handler = new CameraHandler();
        handler.setCameraControl(fake);

        handler.handleZoomIn();

        Assert.assertEquals("zoomIn", fake.lastAction);
        Assert.assertEquals(1, fake.zoomInCount);
    }

    @Test
    public void handleZoomOut_delegates() {
        RecordingCameraControl fake = new RecordingCameraControl();
        CameraHandler handler = new CameraHandler();
        handler.setCameraControl(fake);

        handler.handleZoomOut();

        Assert.assertEquals("zoomOut", fake.lastAction);
        Assert.assertEquals(1, fake.zoomOutCount);
    }

    // --- Null safety tests ---

    @Test
    public void handleSetTorch_noControl_doesNotThrow() {
        CameraHandler handler = new CameraHandler();
        handler.handleSetTorch(true); // no exception
    }

    @Test
    public void handleZoomIn_noControl_doesNotThrow() {
        CameraHandler handler = new CameraHandler();
        handler.handleZoomIn(); // no exception
    }

    @Test
    public void handleZoomOut_noControl_doesNotThrow() {
        CameraHandler handler = new CameraHandler();
        handler.handleZoomOut(); // no exception
    }

    // --- Multiple calls ---

    @Test
    public void multipleCalls_accumulateCorrectly() {
        RecordingCameraControl fake = new RecordingCameraControl();
        CameraHandler handler = new CameraHandler();
        handler.setCameraControl(fake);

        handler.handleSetTorch(true);
        handler.handleZoomIn();
        handler.handleZoomIn();
        handler.handleSetTorch(false);
        handler.handleZoomOut();

        Assert.assertEquals(2, fake.torchCount);
        Assert.assertFalse(fake.lastTorchValue);
        Assert.assertEquals(2, fake.zoomInCount);
        Assert.assertEquals(1, fake.zoomOutCount);
        Assert.assertEquals("zoomOut", fake.lastAction);
    }

    // --- Late binding ---

    @Test
    public void setCameraControl_afterCreation_enablesDelegation() {
        CameraHandler handler = new CameraHandler();

        // Before setting control — no-op
        handler.handleSetTorch(true);

        // After setting control — delegates
        RecordingCameraControl fake = new RecordingCameraControl();
        handler.setCameraControl(fake);
        handler.handleSetTorch(false);

        Assert.assertEquals(1, fake.torchCount);
        Assert.assertFalse(fake.lastTorchValue);
    }
}

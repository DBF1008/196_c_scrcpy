package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.video.CameraCapture;

/**
 * Handles camera-specific control commands (torch, zoom).
 * <p>
 * Uses an inner {@link CameraControl} interface to decouple from the concrete {@link CameraCapture}
 * implementation, enabling unit testing without Android Camera2 dependencies.
 */
public final class CameraHandler {

    /**
     * Abstraction for camera control operations, enabling testability.
     */
    public interface CameraControl {
        void setTorchEnabled(boolean enabled);
        void zoomIn();
        void zoomOut();
    }

    private CameraControl control;

    public CameraHandler() {
        // Camera control is set later via setCameraControl()
    }

    /**
     * Sets the camera control implementation. Called when the surface capture is set on the controller.
     */
    public void setCameraControl(CameraControl control) {
        this.control = control;
    }

    /**
     * Creates a {@link CameraControl} adapter wrapping a {@link CameraCapture} instance.
     */
    public static CameraControl fromCameraCapture(CameraCapture cameraCapture) {
        return new CameraControl() {
            @Override
            public void setTorchEnabled(boolean enabled) {
                cameraCapture.setTorchEnabled(enabled);
            }

            @Override
            public void zoomIn() {
                cameraCapture.zoomIn();
            }

            @Override
            public void zoomOut() {
                cameraCapture.zoomOut();
            }
        };
    }

    public void handleSetTorch(boolean on) {
        if (control != null) {
            control.setTorchEnabled(on);
        }
    }

    public void handleZoomIn() {
        if (control != null) {
            control.zoomIn();
        }
    }

    public void handleZoomOut() {
        if (control != null) {
            control.zoomOut();
        }
    }
}

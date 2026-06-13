package com.genymobile.scrcpy.video;

import com.genymobile.scrcpy.model.Size;

/**
 * The desired camera configuration and constraints, derived from the user {@code Options} plus the current
 * max size. It is the input to {@link CameraCapturePlanner}; the planner never mutates it.
 */
public final class CameraPlanRequest {

    private final String explicitCameraId;
    private final CameraFacing facing;
    private final Size explicitSize;
    private final CameraAspectRatio aspectRatio;
    private final int maxSize;
    private final int fps;
    private final boolean highSpeed;
    private final float zoom;
    private final boolean torch;

    private CameraPlanRequest(Builder builder) {
        this.explicitCameraId = builder.explicitCameraId;
        this.facing = builder.facing;
        this.explicitSize = builder.explicitSize;
        this.aspectRatio = builder.aspectRatio;
        this.maxSize = builder.maxSize;
        this.fps = builder.fps;
        this.highSpeed = builder.highSpeed;
        this.zoom = builder.zoom;
        this.torch = builder.torch;
    }

    public String getExplicitCameraId() {
        return explicitCameraId;
    }

    public CameraFacing getFacing() {
        return facing;
    }

    public Size getExplicitSize() {
        return explicitSize;
    }

    public CameraAspectRatio getAspectRatio() {
        return aspectRatio;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getFps() {
        return fps;
    }

    public boolean isHighSpeed() {
        return highSpeed;
    }

    public float getZoom() {
        return zoom;
    }

    public boolean isTorch() {
        return torch;
    }

    /**
     * Return a copy of this request with a different max size. Used when the video constraints change at
     * runtime and only the resolution must be re-fit on the already-selected camera.
     */
    public CameraPlanRequest withMaxSize(int newMaxSize) {
        return new Builder()
                .explicitCameraId(explicitCameraId)
                .facing(facing)
                .explicitSize(explicitSize)
                .aspectRatio(aspectRatio)
                .maxSize(newMaxSize)
                .fps(fps)
                .highSpeed(highSpeed)
                .zoom(zoom)
                .torch(torch)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String explicitCameraId;
        private CameraFacing facing;
        private Size explicitSize;
        private CameraAspectRatio aspectRatio;
        private int maxSize;
        private int fps;
        private boolean highSpeed;
        private float zoom = 1;
        private boolean torch;

        public Builder explicitCameraId(String explicitCameraId) {
            this.explicitCameraId = explicitCameraId;
            return this;
        }

        public Builder facing(CameraFacing facing) {
            this.facing = facing;
            return this;
        }

        public Builder explicitSize(Size explicitSize) {
            this.explicitSize = explicitSize;
            return this;
        }

        public Builder aspectRatio(CameraAspectRatio aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder fps(int fps) {
            this.fps = fps;
            return this;
        }

        public Builder highSpeed(boolean highSpeed) {
            this.highSpeed = highSpeed;
            return this;
        }

        public Builder zoom(float zoom) {
            this.zoom = zoom;
            return this;
        }

        public Builder torch(boolean torch) {
            this.torch = torch;
            return this;
        }

        public CameraPlanRequest build() {
            return new CameraPlanRequest(this);
        }
    }
}

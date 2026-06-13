package com.genymobile.scrcpy.video;

import com.genymobile.scrcpy.model.Size;

import java.util.Collections;
import java.util.List;

/**
 * The result of a successful capability negotiation: a consistent, validated camera configuration that the
 * camera is expected to accept, together with the structured {@link CameraPlanAdjustment adjustments} that
 * were applied to reach it (empty if the request was satisfied exactly).
 */
public final class CameraPlan {

    private final String cameraId;
    private final Size captureSize;
    private final boolean highSpeed;
    private final int fps;
    private final float zoom;
    private final boolean torch;
    private final List<CameraPlanAdjustment> adjustments;

    public CameraPlan(String cameraId, Size captureSize, boolean highSpeed, int fps, float zoom, boolean torch,
            List<CameraPlanAdjustment> adjustments) {
        this.cameraId = cameraId;
        this.captureSize = captureSize;
        this.highSpeed = highSpeed;
        this.fps = fps;
        this.zoom = zoom;
        this.torch = torch;
        this.adjustments = Collections.unmodifiableList(adjustments);
    }

    public String getCameraId() {
        return cameraId;
    }

    public Size getCaptureSize() {
        return captureSize;
    }

    public boolean isHighSpeed() {
        return highSpeed;
    }

    public int getFps() {
        return fps;
    }

    public float getZoom() {
        return zoom;
    }

    public boolean isTorch() {
        return torch;
    }

    public List<CameraPlanAdjustment> getAdjustments() {
        return adjustments;
    }

    /**
     * Return a single-line, human-readable summary of the adopted configuration (for logging).
     */
    public String getSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append("camera=").append(cameraId)
                .append(" size=").append(captureSize)
                .append(" highSpeed=").append(highSpeed)
                .append(" fps=").append(fps)
                .append(" zoom=").append(zoom)
                .append(" torch=").append(torch);
        return builder.toString();
    }

    @Override
    public String toString() {
        return "CameraPlan{" + getSummary() + ", adjustments=" + adjustments + "}";
    }
}

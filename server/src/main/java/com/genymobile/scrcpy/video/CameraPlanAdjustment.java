package com.genymobile.scrcpy.video;

/**
 * A structured reason describing how the requested camera configuration was degraded to produce a viable
 * {@link CameraPlan}. Unlike {@link CameraPlanException}, an adjustment is recoverable: a plan was still
 * produced, but it differs from what was requested.
 */
public final class CameraPlanAdjustment {

    public enum Type {
        /** The preferred camera could not satisfy the constraints, so another candidate was used. */
        CAMERA_FALLBACK,
        /** Zoom was requested but the selected camera does not advertise a zoom-ratio range; zoom reset to 1. */
        ZOOM_UNSUPPORTED,
        /** The requested zoom was outside the supported range and was clamped. */
        ZOOM_CLAMPED,
        /** Torch was requested but the selected camera has no flash unit; torch disabled. */
        TORCH_UNSUPPORTED,
    }

    private final Type type;
    private final String detail;

    public CameraPlanAdjustment(Type type, String detail) {
        this.type = type;
        this.detail = detail;
    }

    public Type getType() {
        return type;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return type + ": " + detail;
    }
}

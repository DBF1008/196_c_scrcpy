package com.genymobile.scrcpy.video;

import com.genymobile.scrcpy.model.ConfigurationException;

/**
 * Thrown when no viable camera capture plan can be produced for the requested configuration.
 * <p>
 * It extends {@link ConfigurationException} so it propagates naturally out of the capture initialization
 * (which already reports configuration errors), while carrying a structured {@link Reason}.
 */
public class CameraPlanException extends ConfigurationException {

    public enum Reason {
        /** An explicit camera id was requested but no camera with that id exists. */
        CAMERA_ID_NOT_FOUND,
        /** A camera facing was requested but no camera matches it. */
        NO_CAMERA_FOR_FACING,
        /** No camera is available at all. */
        NO_CAMERA_AVAILABLE,
        /** High speed was requested but no candidate camera advertises any high-speed size. */
        HIGH_SPEED_UNSUPPORTED,
        /** Candidate cameras exist, but none can produce a size matching the requested constraints. */
        NO_SIZE_AVAILABLE,
    }

    private final Reason reason;

    public CameraPlanException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}

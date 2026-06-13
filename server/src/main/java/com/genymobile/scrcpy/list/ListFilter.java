package com.genymobile.scrcpy.list;

import com.genymobile.scrcpy.model.DeviceApp;

/**
 * Optional filters applied to the listed resources. A {@code null} field means "no filter for this criterion".
 * <p>
 * Instances are immutable and contain no Android dependency, so the filtering logic is unit-testable.
 */
public final class ListFilter {

    // null = both system and non-system apps
    private final Boolean appSystem;
    // null = any facing
    private final String cameraFacing;
    // null = any codec
    private final String encoderCodec;
    // null = both video and audio encoders
    private final String encoderType;
    // null = any hardware acceleration type
    private final String encoderHw;

    public ListFilter(Boolean appSystem, String cameraFacing, String encoderCodec, String encoderType, String encoderHw) {
        this.appSystem = appSystem;
        this.cameraFacing = cameraFacing;
        this.encoderCodec = encoderCodec;
        this.encoderType = encoderType;
        this.encoderHw = encoderHw;
    }

    public boolean matchesApp(DeviceApp app) {
        return appSystem == null || appSystem.booleanValue() == app.isSystem();
    }

    public boolean matchesCamera(CameraInfo camera) {
        return cameraFacing == null || cameraFacing.equals(camera.getFacing());
    }

    public boolean matchesEncoder(EncoderInfo encoder) {
        if (encoderCodec != null && !encoderCodec.equals(encoder.getCodec())) {
            return false;
        }
        if (encoderType != null && !encoderType.equals(encoder.getType())) {
            return false;
        }
        // When a hardware type filter is set but the encoder's type is unknown (before Android 10), it cannot match
        if (encoderHw != null && !encoderHw.equals(encoder.getHardwareType())) {
            return false;
        }
        return true;
    }
}

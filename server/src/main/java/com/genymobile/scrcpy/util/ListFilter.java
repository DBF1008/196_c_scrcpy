package com.genymobile.scrcpy.util;

import com.genymobile.scrcpy.Options;
import com.genymobile.scrcpy.model.CameraEntry;
import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.EncoderEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless filter for list-mode entries.
 * <p>
 * A null filter criterion means "no filter" (include all).
 */
public final class ListFilter {

    private final Boolean systemApps;
    private final String cameraFacing;
    private final String encoderType;

    public ListFilter(Boolean systemApps, String cameraFacing, String encoderType) {
        this.systemApps = systemApps;
        this.cameraFacing = cameraFacing;
        this.encoderType = encoderType;
    }

    public static ListFilter fromOptions(Options options) {
        return new ListFilter(
                options.getListSystemApps(),
                options.getListCameraFacing(),
                options.getListEncoderType()
        );
    }

    public Boolean getSystemApps() {
        return systemApps;
    }

    public String getCameraFacing() {
        return cameraFacing;
    }

    public String getEncoderType() {
        return encoderType;
    }

    public List<DeviceApp> filterApps(List<DeviceApp> apps) {
        if (systemApps == null) {
            return apps;
        }
        List<DeviceApp> result = new ArrayList<>();
        for (DeviceApp app : apps) {
            if (app.isSystem() == systemApps) {
                result.add(app);
            }
        }
        return result;
    }

    public List<CameraEntry> filterCameras(List<CameraEntry> cameras) {
        if (cameraFacing == null) {
            return cameras;
        }
        List<CameraEntry> result = new ArrayList<>();
        for (CameraEntry camera : cameras) {
            if (cameraFacing.equals(camera.getFacing())) {
                result.add(camera);
            }
        }
        return result;
    }

    public List<EncoderEntry> filterEncoders(List<EncoderEntry> encoders) {
        if (encoderType == null) {
            return encoders;
        }
        List<EncoderEntry> result = new ArrayList<>();
        for (EncoderEntry encoder : encoders) {
            if (encoderType.equals(encoder.getType())) {
                result.add(encoder);
            }
        }
        return result;
    }
}

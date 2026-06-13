package com.genymobile.scrcpy.list;

import com.genymobile.scrcpy.display.DisplayInfo;
import com.genymobile.scrcpy.model.DeviceApp;

import java.util.List;

/**
 * Aggregates the gathered resources to be serialized as JSON.
 * <p>
 * Each resource list is {@code null} when the corresponding resource was not requested (so its key is omitted from the JSON output), and
 * non-null (possibly empty) when it was requested.
 */
public final class ListData {

    private List<DisplayInfo> displays;
    private List<CameraInfo> cameras;
    private List<DeviceApp> apps;
    private List<EncoderInfo> encoders;

    // true when cameras were requested but the camera service could not be accessed
    private boolean camerasAccessDenied;

    public List<DisplayInfo> getDisplays() {
        return displays;
    }

    public void setDisplays(List<DisplayInfo> displays) {
        this.displays = displays;
    }

    public List<CameraInfo> getCameras() {
        return cameras;
    }

    public void setCameras(List<CameraInfo> cameras) {
        this.cameras = cameras;
    }

    public List<DeviceApp> getApps() {
        return apps;
    }

    public void setApps(List<DeviceApp> apps) {
        this.apps = apps;
    }

    public List<EncoderInfo> getEncoders() {
        return encoders;
    }

    public void setEncoders(List<EncoderInfo> encoders) {
        this.encoders = encoders;
    }

    public boolean isCamerasAccessDenied() {
        return camerasAccessDenied;
    }

    public void setCamerasAccessDenied(boolean camerasAccessDenied) {
        this.camerasAccessDenied = camerasAccessDenied;
    }
}

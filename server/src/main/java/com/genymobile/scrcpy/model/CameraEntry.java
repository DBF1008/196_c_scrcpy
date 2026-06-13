package com.genymobile.scrcpy.model;

import java.util.List;

public final class CameraEntry {

    private final String cameraId;
    private final String facing;
    private final int sensorWidth;
    private final int sensorHeight;
    private final int[] fps;
    private final float zoomMin;
    private final float zoomMax;
    private final List<int[]> sizes;
    private final List<int[]> highSpeedSizes;
    private final List<int[]> highSpeedFps;

    public CameraEntry(String cameraId, String facing, int sensorWidth, int sensorHeight,
                       int[] fps, float zoomMin, float zoomMax,
                       List<int[]> sizes, List<int[]> highSpeedSizes, List<int[]> highSpeedFps) {
        this.cameraId = cameraId;
        this.facing = facing;
        this.sensorWidth = sensorWidth;
        this.sensorHeight = sensorHeight;
        this.fps = fps;
        this.zoomMin = zoomMin;
        this.zoomMax = zoomMax;
        this.sizes = sizes;
        this.highSpeedSizes = highSpeedSizes;
        this.highSpeedFps = highSpeedFps;
    }

    public String getCameraId() {
        return cameraId;
    }

    public String getFacing() {
        return facing;
    }

    public int getSensorWidth() {
        return sensorWidth;
    }

    public int getSensorHeight() {
        return sensorHeight;
    }

    public int[] getFps() {
        return fps;
    }

    public float getZoomMin() {
        return zoomMin;
    }

    public float getZoomMax() {
        return zoomMax;
    }

    public boolean hasZoomRange() {
        return !Float.isNaN(zoomMin) && !Float.isNaN(zoomMax);
    }

    public List<int[]> getSizes() {
        return sizes;
    }

    public List<int[]> getHighSpeedSizes() {
        return highSpeedSizes;
    }

    public List<int[]> getHighSpeedFps() {
        return highSpeedFps;
    }
}

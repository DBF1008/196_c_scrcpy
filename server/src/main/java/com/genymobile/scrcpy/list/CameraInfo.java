package com.genymobile.scrcpy.list;

import com.genymobile.scrcpy.model.Size;

import java.util.List;

/**
 * Structured, Android-free description of a camera, used to serialize the camera list as JSON.
 */
public final class CameraInfo {

    /**
     * A high-speed capture configuration: an output size and the frame rates available for it.
     */
    public static final class HighSpeed {
        private final Size size;
        private final List<Integer> fps;

        public HighSpeed(Size size, List<Integer> fps) {
            this.size = size;
            this.fps = fps;
        }

        public Size getSize() {
            return size;
        }

        public List<Integer> getFps() {
            return fps;
        }
    }

    private final String id;
    private final String facing;
    private final int width;
    private final int height;
    private final List<Integer> fps;
    private final Float zoomMin;
    private final Float zoomMax;
    // Output sizes and high-speed configurations are only populated when sizes are requested (otherwise null)
    private final List<Size> sizes;
    private final List<HighSpeed> highSpeed;

    public CameraInfo(String id, String facing, int width, int height, List<Integer> fps, Float zoomMin, Float zoomMax, List<Size> sizes,
            List<HighSpeed> highSpeed) {
        this.id = id;
        this.facing = facing;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.zoomMin = zoomMin;
        this.zoomMax = zoomMax;
        this.sizes = sizes;
        this.highSpeed = highSpeed;
    }

    public String getId() {
        return id;
    }

    public String getFacing() {
        return facing;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public List<Integer> getFps() {
        return fps;
    }

    public Float getZoomMin() {
        return zoomMin;
    }

    public Float getZoomMax() {
        return zoomMax;
    }

    public List<Size> getSizes() {
        return sizes;
    }

    public List<HighSpeed> getHighSpeed() {
        return highSpeed;
    }
}

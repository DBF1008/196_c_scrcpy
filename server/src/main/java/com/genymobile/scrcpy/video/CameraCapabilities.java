package com.genymobile.scrcpy.video;

import com.genymobile.scrcpy.model.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable, Android-free snapshot of one camera's capabilities, as read by {@link CameraProbe} from the
 * Android {@code CameraCharacteristics}. Keeping this a plain value type (using {@link Size} and
 * {@link FpsRange} instead of {@code android.*} classes) is what makes {@link CameraCapturePlanner} unit
 * testable without a device.
 */
public final class CameraCapabilities {

    private static final int UNKNOWN_FACING = -1;

    private final String id;
    private final int facing;
    private final List<Size> outputSizes;
    private final List<Size> highSpeedSizes;
    private final List<FpsRange> highSpeedFpsRanges;
    private final Float zoomMin;
    private final Float zoomMax;
    private final boolean torchAvailable;
    private final Size sensorActiveSize;

    private CameraCapabilities(Builder builder) {
        this.id = builder.id;
        this.facing = builder.facing;
        this.outputSizes = Collections.unmodifiableList(new ArrayList<>(builder.outputSizes));
        this.highSpeedSizes = Collections.unmodifiableList(new ArrayList<>(builder.highSpeedSizes));
        this.highSpeedFpsRanges = Collections.unmodifiableList(new ArrayList<>(builder.highSpeedFpsRanges));
        this.zoomMin = builder.zoomMin;
        this.zoomMax = builder.zoomMax;
        this.torchAvailable = builder.torchAvailable;
        this.sensorActiveSize = builder.sensorActiveSize;
    }

    public String getId() {
        return id;
    }

    public int getFacing() {
        return facing;
    }

    public List<Size> getOutputSizes() {
        return outputSizes;
    }

    public List<Size> getHighSpeedSizes() {
        return highSpeedSizes;
    }

    public List<FpsRange> getHighSpeedFpsRanges() {
        return highSpeedFpsRanges;
    }

    /**
     * @return whether the camera advertises a zoom-ratio range (i.e. zoom is supported).
     */
    public boolean isZoomSupported() {
        return zoomMin != null && zoomMax != null;
    }

    public Float getZoomMin() {
        return zoomMin;
    }

    public Float getZoomMax() {
        return zoomMax;
    }

    public boolean isTorchAvailable() {
        return torchAvailable;
    }

    public Size getSensorActiveSize() {
        return sensorActiveSize;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private int facing = UNKNOWN_FACING;
        private List<Size> outputSizes = Collections.emptyList();
        private List<Size> highSpeedSizes = Collections.emptyList();
        private List<FpsRange> highSpeedFpsRanges = Collections.emptyList();
        private Float zoomMin;
        private Float zoomMax;
        private boolean torchAvailable;
        private Size sensorActiveSize;

        public Builder(String id) {
            this.id = id;
        }

        public Builder facing(int facing) {
            this.facing = facing;
            return this;
        }

        public Builder outputSizes(List<Size> outputSizes) {
            this.outputSizes = outputSizes != null ? outputSizes : Collections.emptyList();
            return this;
        }

        public Builder highSpeedSizes(List<Size> highSpeedSizes) {
            this.highSpeedSizes = highSpeedSizes != null ? highSpeedSizes : Collections.emptyList();
            return this;
        }

        public Builder highSpeedFpsRanges(List<FpsRange> highSpeedFpsRanges) {
            this.highSpeedFpsRanges = highSpeedFpsRanges != null ? highSpeedFpsRanges : Collections.emptyList();
            return this;
        }

        public Builder zoomRange(Float min, Float max) {
            this.zoomMin = min;
            this.zoomMax = max;
            return this;
        }

        public Builder torchAvailable(boolean torchAvailable) {
            this.torchAvailable = torchAvailable;
            return this;
        }

        public Builder sensorActiveSize(Size sensorActiveSize) {
            this.sensorActiveSize = sensorActiveSize;
            return this;
        }

        public CameraCapabilities build() {
            return new CameraCapabilities(this);
        }
    }
}

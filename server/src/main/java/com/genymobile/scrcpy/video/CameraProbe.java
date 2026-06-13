package com.genymobile.scrcpy.video;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.model.Size;
import com.genymobile.scrcpy.util.Ln;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.util.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the Android {@code CameraCharacteristics} of every available camera into pure
 * {@link CameraCapabilities} value objects. This is the only Android boundary of the camera capability
 * negotiation: everything downstream ({@link CameraCapturePlanner}) operates on the resulting POJOs and is
 * therefore unit testable without a device.
 */
public final class CameraProbe {

    private CameraProbe() {
        // utility class, not instantiable
    }

    /**
     * Probe all available cameras. A single camera that cannot be read is skipped rather than failing the
     * whole enumeration.
     */
    public static List<CameraCapabilities> probeAll(CameraManager cameraManager) throws CameraAccessException {
        String[] cameraIds = cameraManager.getCameraIdList();
        List<CameraCapabilities> result = new ArrayList<>(cameraIds.length);
        for (String id : cameraIds) {
            try {
                result.add(probe(cameraManager, id));
            } catch (CameraAccessException | RuntimeException e) {
                // A single misbehaving camera must not prevent using the others.
                Ln.w("Could not probe camera '" + id + "': " + e.getMessage());
            }
        }
        return result;
    }

    @TargetApi(AndroidVersions.API_30_ANDROID_11)
    private static CameraCapabilities probe(CameraManager cameraManager, String id) throws CameraAccessException {
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
        CameraCapabilities.Builder builder = CameraCapabilities.builder(id);

        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null) {
            builder.facing(facing);
        }

        StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (configs != null) {
            builder.outputSizes(toSizeList(configs.getOutputSizes(MediaCodec.class)));
            builder.highSpeedSizes(toSizeList(configs.getHighSpeedVideoSizes()));
            builder.highSpeedFpsRanges(toFpsRangeList(configs.getHighSpeedVideoFpsRanges()));
        }

        Range<Float> zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        if (zoomRange != null) {
            builder.zoomRange(zoomRange.getLower(), zoomRange.getUpper());
        }

        Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        builder.torchAvailable(Boolean.TRUE.equals(flashAvailable));

        Rect activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (activeArray != null) {
            builder.sensorActiveSize(new Size(activeArray.width(), activeArray.height()));
        }

        return builder.build();
    }

    private static List<Size> toSizeList(android.util.Size[] sizes) {
        if (sizes == null) {
            return Collections.emptyList();
        }
        List<Size> result = new ArrayList<>(sizes.length);
        for (android.util.Size size : sizes) {
            result.add(new Size(size.getWidth(), size.getHeight()));
        }
        return result;
    }

    private static List<FpsRange> toFpsRangeList(Range<Integer>[] ranges) {
        if (ranges == null) {
            return Collections.emptyList();
        }
        List<FpsRange> result = new ArrayList<>(ranges.length);
        for (Range<Integer> range : ranges) {
            result.add(new FpsRange(range.getLower(), range.getUpper()));
        }
        return result;
    }
}

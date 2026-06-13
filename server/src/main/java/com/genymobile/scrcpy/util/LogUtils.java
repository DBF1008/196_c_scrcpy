package com.genymobile.scrcpy.util;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.audio.AudioCodec;
import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.display.DisplayInfo;
import com.genymobile.scrcpy.model.CameraEntry;
import com.genymobile.scrcpy.model.Codec;
import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.DisplayEntry;
import com.genymobile.scrcpy.model.EncoderEntry;
import com.genymobile.scrcpy.video.VideoCodec;
import com.genymobile.scrcpy.wrappers.DisplayManager;
import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public final class LogUtils {

    private LogUtils() {
        // not instantiable
    }

    // ===== Data collection methods (Android-dependent) =====

    public static List<DisplayEntry> collectDisplays() {
        List<DisplayEntry> displays = new ArrayList<>();
        DisplayManager displayManager = ServiceManager.getDisplayManager();
        int[] displayIds = displayManager.getDisplayIds();
        if (displayIds != null) {
            for (int id : displayIds) {
                DisplayInfo displayInfo = displayManager.getDisplayInfo(id);
                if (displayInfo != null && displayInfo.getSize() != null) {
                    displays.add(new DisplayEntry(id, displayInfo.getSize().getWidth(), displayInfo.getSize().getHeight()));
                } else {
                    displays.add(new DisplayEntry(id, 0, 0));
                }
            }
        }
        return displays;
    }

    public static List<CameraEntry> collectCameras(boolean includeSizes) throws CameraAccessException {
        List<CameraEntry> cameras = new ArrayList<>();
        CameraManager cameraManager = ServiceManager.getCameraManager();
        String[] cameraIds = cameraManager.getCameraIdList();
        for (String id : cameraIds) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);

            if (!isCameraBackwardCompatible(characteristics)) {
                // Ignore depth cameras as suggested by official documentation
                // <https://developer.android.com/media/camera/camera2/camera-enumeration>
                continue;
            }

            int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            String facingName = getCameraFacingName(facing);

            Rect activeSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            int sensorWidth = activeSize.width();
            int sensorHeight = activeSize.height();

            int[] fps = null;
            try {
                Range<Integer>[] lowFpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                if (lowFpsRanges != null) {
                    SortedSet<Integer> set = new TreeSet<>();
                    for (Range<Integer> range : lowFpsRanges) {
                        set.add(range.getUpper());
                    }
                    fps = new int[set.size()];
                    int i = 0;
                    for (Integer val : set) {
                        fps[i++] = val;
                    }
                }
            } catch (Exception e) {
                Ln.w("Could not get available frame rates for camera " + id, e);
            }

            float zoomMin = Float.NaN;
            float zoomMax = Float.NaN;
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_30_ANDROID_11) {
                try {
                    Range<Float> zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                    if (zoomRange != null) {
                        zoomMin = zoomRange.getLower();
                        zoomMax = zoomRange.getUpper();
                    }
                } catch (Exception e) {
                    Ln.w("Could not get available zoom ranges for camera " + id, e);
                }
            }

            List<int[]> sizes = null;
            List<int[]> highSpeedSizes = null;
            List<int[]> highSpeedFps = null;

            if (includeSizes) {
                StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                android.util.Size[] outputSizes = configs.getOutputSizes(MediaCodec.class);
                if (outputSizes != null && outputSizes.length > 0) {
                    sizes = new ArrayList<>();
                    for (android.util.Size size : outputSizes) {
                        sizes.add(new int[]{size.getWidth(), size.getHeight()});
                    }
                }

                android.util.Size[] hsSizes = configs.getHighSpeedVideoSizes();
                if (hsSizes != null && hsSizes.length > 0) {
                    highSpeedSizes = new ArrayList<>();
                    highSpeedFps = new ArrayList<>();
                    Range<Integer>[] hsRanges = configs.getHighSpeedVideoFpsRanges();
                    for (android.util.Size size : hsSizes) {
                        highSpeedSizes.add(new int[]{size.getWidth(), size.getHeight()});
                        if (hsRanges != null && hsRanges.length > 0) {
                            SortedSet<Integer> set = new TreeSet<>();
                            for (Range<Integer> range : hsRanges) {
                                set.add(range.getUpper());
                            }
                            int[] fpsArr = new int[set.size()];
                            int i = 0;
                            for (Integer val : set) {
                                fpsArr[i++] = val;
                            }
                            highSpeedFps.add(fpsArr);
                        } else {
                            highSpeedFps.add(new int[0]);
                        }
                    }
                }
            }

            cameras.add(new CameraEntry(id, facingName, sensorWidth, sensorHeight, fps, zoomMin, zoomMax, sizes, highSpeedSizes, highSpeedFps));
        }
        return cameras;
    }

    public static List<DeviceApp> collectApps() {
        return Device.listApps();
    }

    public static List<EncoderEntry> collectVideoEncoders() {
        return collectEncoders("video", VideoCodec.values());
    }

    public static List<EncoderEntry> collectAudioEncoders() {
        return collectEncoders("audio", AudioCodec.values());
    }

    private static List<EncoderEntry> collectEncoders(String type, Codec[] codecs) {
        List<EncoderEntry> entries = new ArrayList<>();
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (Codec codec : codecs) {
            MediaCodecInfo[] encoders = CodecUtils.getEncoders(codecList, codec.getMimeType());
            for (MediaCodecInfo info : encoders) {
                String hwType = null;
                boolean vendor = false;
                String aliasFor = null;
                if (Build.VERSION.SDK_INT >= AndroidVersions.API_29_ANDROID_10) {
                    hwType = getHwCodecType(info);
                    vendor = info.isVendor();
                    if (info.isAlias()) {
                        aliasFor = info.getCanonicalName();
                    }
                }
                entries.add(new EncoderEntry(codec.getName(), info.getName(), hwType, vendor, aliasFor));
            }
        }
        return entries;
    }

    // ===== Legacy text methods (delegate to ListOutputFormatter) =====

    public static String buildVideoEncoderListMessage() {
        return ListOutputFormatter.formatVideoEncodersText(collectVideoEncoders());
    }

    public static String buildAudioEncoderListMessage() {
        return ListOutputFormatter.formatAudioEncodersText(collectAudioEncoders());
    }

    public static String buildDisplayListMessage() {
        return ListOutputFormatter.formatDisplaysText(collectDisplays());
    }

    public static String buildCameraListMessage(boolean includeSizes) {
        try {
            List<CameraEntry> cameras = collectCameras(includeSizes);
            return ListOutputFormatter.formatCamerasText(cameras, includeSizes);
        } catch (CameraAccessException e) {
            return ListOutputFormatter.formatCamerasText(Collections.emptyList(), includeSizes, true);
        }
    }

    public static String buildAppListMessage() {
        return ListOutputFormatter.formatAppsText(collectApps());
    }

    public static String buildAppListMessage(String title, List<DeviceApp> apps) {
        return ListOutputFormatter.formatAppsText(title, apps);
    }

    // ===== Helpers =====

    @TargetApi(AndroidVersions.API_29_ANDROID_10)
    static String getHwCodecType(MediaCodecInfo info) {
        if (info.isSoftwareOnly()) {
            return "sw";
        }
        if (info.isHardwareAccelerated()) {
            return "hw";
        }
        return "hybrid";
    }

    static String getCameraFacingName(int facing) {
        switch (facing) {
            case CameraCharacteristics.LENS_FACING_FRONT:
                return "front";
            case CameraCharacteristics.LENS_FACING_BACK:
                return "back";
            case CameraCharacteristics.LENS_FACING_EXTERNAL:
                return "external";
            default:
                return "unknown";
        }
    }

    private static boolean isCameraBackwardCompatible(CameraCharacteristics characteristics) {
        int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (capabilities == null) {
            return false;
        }

        for (int capability : capabilities) {
            if (capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) {
                return true;
            }
        }

        return false;
    }
}

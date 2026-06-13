package com.genymobile.scrcpy.list;

import com.genymobile.scrcpy.display.DisplayInfo;
import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.Size;
import com.genymobile.scrcpy.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes the listed resources (displays, cameras, apps, encoders) to a stable JSON document, applying the requested filters.
 * <p>
 * This class is Android-free and deterministic: it is a pure function of its inputs, so it can be unit-tested by feeding hand-built data.
 */
public final class ListJson {

    private static final ListFilter NO_FILTER = new ListFilter(null, null, null, null, null);

    private ListJson() {
        // not instantiable
    }

    public static String build(ListData data, ListFilter filter) {
        if (filter == null) {
            filter = NO_FILTER;
        }

        Map<String, Object> root = new LinkedHashMap<>();

        if (data.getDisplays() != null) {
            root.put("displays", displaysJson(data.getDisplays()));
        }
        if (data.getCameras() != null) {
            root.put("cameras", camerasJson(data.getCameras(), filter));
        }
        if (data.getApps() != null) {
            root.put("apps", appsJson(data.getApps(), filter));
        }
        if (data.getEncoders() != null) {
            root.put("encoders", encodersJson(data.getEncoders(), filter));
        }

        Map<String, Object> errors = new LinkedHashMap<>();
        if (data.isCamerasAccessDenied()) {
            errors.put("cameras", "access_denied");
        }
        // Always present (possibly empty) so consumers can rely on a stable schema
        root.put("errors", errors);

        return Json.encode(root);
    }

    private static List<Object> displaysJson(List<DisplayInfo> displays) {
        List<Object> result = new ArrayList<>();
        for (DisplayInfo display : displays) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("id", display.getDisplayId());
            Size size = display.getSize();
            if (size != null) {
                obj.put("width", size.getWidth());
                obj.put("height", size.getHeight());
            } else {
                obj.put("width", null);
                obj.put("height", null);
            }
            obj.put("dpi", display.getDpi());
            obj.put("rotation", display.getRotation());
            obj.put("flags", display.getFlags());
            obj.put("layerStack", display.getLayerStack());
            obj.put("uniqueId", display.getUniqueId());
            result.add(obj);
        }
        return result;
    }

    private static List<Object> camerasJson(List<CameraInfo> cameras, ListFilter filter) {
        List<Object> result = new ArrayList<>();
        for (CameraInfo camera : cameras) {
            if (!filter.matchesCamera(camera)) {
                continue;
            }
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("id", camera.getId());
            obj.put("facing", camera.getFacing());
            obj.put("width", camera.getWidth());
            obj.put("height", camera.getHeight());
            obj.put("fps", camera.getFps());
            obj.put("zoomMin", camera.getZoomMin());
            obj.put("zoomMax", camera.getZoomMax());
            if (camera.getSizes() != null) {
                obj.put("sizes", sizesJson(camera.getSizes()));
            }
            if (camera.getHighSpeed() != null) {
                obj.put("highSpeed", highSpeedJson(camera.getHighSpeed()));
            }
            result.add(obj);
        }
        return result;
    }

    private static List<Object> sizesJson(List<Size> sizes) {
        List<Object> result = new ArrayList<>();
        for (Size size : sizes) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("width", size.getWidth());
            obj.put("height", size.getHeight());
            result.add(obj);
        }
        return result;
    }

    private static List<Object> highSpeedJson(List<CameraInfo.HighSpeed> highSpeedList) {
        List<Object> result = new ArrayList<>();
        for (CameraInfo.HighSpeed highSpeed : highSpeedList) {
            Map<String, Object> obj = new LinkedHashMap<>();
            Size size = highSpeed.getSize();
            obj.put("width", size.getWidth());
            obj.put("height", size.getHeight());
            obj.put("fps", highSpeed.getFps());
            result.add(obj);
        }
        return result;
    }

    private static List<Object> appsJson(List<DeviceApp> apps, ListFilter filter) {
        List<Object> result = new ArrayList<>();
        for (DeviceApp app : apps) {
            if (!filter.matchesApp(app)) {
                continue;
            }
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("name", app.getName());
            obj.put("packageName", app.getPackageName());
            obj.put("system", app.isSystem());
            result.add(obj);
        }
        return result;
    }

    private static List<Object> encodersJson(List<EncoderInfo> encoders, ListFilter filter) {
        List<Object> result = new ArrayList<>();
        for (EncoderInfo encoder : encoders) {
            if (!filter.matchesEncoder(encoder)) {
                continue;
            }
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("type", encoder.getType());
            obj.put("codec", encoder.getCodec());
            obj.put("name", encoder.getName());
            obj.put("hardwareType", encoder.getHardwareType());
            obj.put("vendor", encoder.isVendor());
            obj.put("aliasOf", encoder.getAliasOf());
            result.add(obj);
        }
        return result;
    }
}

package com.genymobile.scrcpy.util;

import com.genymobile.scrcpy.model.CameraEntry;
import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.DisplayEntry;
import com.genymobile.scrcpy.model.EncoderEntry;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Formats list-mode data as either human-readable text or JSON.
 * <p>
 * This class has no Android dependencies and is fully testable in JVM unit tests.
 */
public final class ListOutputFormatter {

    private ListOutputFormatter() {
        // not instantiable
    }

    // ===== Text formatters (replicate existing LogUtils output exactly) =====

    public static String formatDisplaysText(List<DisplayEntry> displays) {
        StringBuilder builder = new StringBuilder("List of displays:");
        if (displays == null || displays.isEmpty()) {
            builder.append("\n    (none)");
        } else {
            for (DisplayEntry display : displays) {
                builder.append("\n    --display-id=").append(display.getDisplayId()).append("    (");
                if (display.getWidth() > 0 && display.getHeight() > 0) {
                    builder.append(display.getWidth()).append("x").append(display.getHeight());
                } else {
                    builder.append("size unknown");
                }
                builder.append(")");
            }
        }
        return builder.toString();
    }

    public static String formatCamerasText(List<CameraEntry> cameras, boolean includeSizes) {
        return formatCamerasText(cameras, includeSizes, false);
    }

    public static String formatCamerasText(List<CameraEntry> cameras, boolean includeSizes, boolean accessDenied) {
        StringBuilder builder = new StringBuilder("List of cameras:");
        if (accessDenied) {
            builder.append("\n    (access denied)");
        } else if (cameras == null || cameras.isEmpty()) {
            builder.append("\n    (none)");
        } else {
            for (CameraEntry camera : cameras) {
                builder.append("\n    --camera-id=").append(camera.getCameraId());
                builder.append("    (").append(camera.getFacing()).append(", ");
                builder.append(camera.getSensorWidth()).append("x").append(camera.getSensorHeight());

                int[] fps = camera.getFps();
                if (fps != null && fps.length > 0) {
                    builder.append(", fps=").append(formatFpsSet(fps));
                }

                if (camera.hasZoomRange()) {
                    DecimalFormat format = new DecimalFormat("#.##");
                    builder.append(", zoom-range=[").append(format.format(camera.getZoomMin()))
                            .append(", ").append(format.format(camera.getZoomMax())).append(']');
                }

                builder.append(')');

                if (includeSizes) {
                    List<int[]> sizes = camera.getSizes();
                    if (sizes == null || sizes.isEmpty()) {
                        builder.append("\n        (none)");
                    } else {
                        for (int[] size : sizes) {
                            builder.append("\n        - ").append(size[0]).append('x').append(size[1]);
                        }
                    }

                    List<int[]> highSpeedSizes = camera.getHighSpeedSizes();
                    List<int[]> highSpeedFps = camera.getHighSpeedFps();
                    if (highSpeedSizes != null && !highSpeedSizes.isEmpty()) {
                        builder.append("\n      High speed capture (--camera-high-speed):");
                        for (int i = 0; i < highSpeedSizes.size(); i++) {
                            int[] size = highSpeedSizes.get(i);
                            builder.append("\n        - ").append(size[0]).append("x").append(size[1]);
                            if (highSpeedFps != null && i < highSpeedFps.size()) {
                                builder.append(" (fps=").append(formatFpsSet(highSpeedFps.get(i))).append(')');
                            }
                        }
                    }
                }
            }
        }
        return builder.toString();
    }

    public static String formatAppsText(List<DeviceApp> apps) {
        return formatAppsText("List of apps:", apps);
    }

    public static String formatAppsText(String title, List<DeviceApp> apps) {
        StringBuilder builder = new StringBuilder(title);

        // Sort by: system flag (system first), name, package name
        List<DeviceApp> sorted = new ArrayList<>(apps);
        Collections.sort(sorted, (thisApp, otherApp) -> {
            int cmp = -Boolean.compare(thisApp.isSystem(), otherApp.isSystem());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Objects.compare(thisApp.getName(), otherApp.getName(), String::compareTo);
            if (cmp != 0) {
                return cmp;
            }
            return Objects.compare(thisApp.getPackageName(), otherApp.getPackageName(), String::compareTo);
        });

        final int column = 30;
        for (DeviceApp app : sorted) {
            String name = app.getName();
            int padding = column - name.length();
            builder.append("\n ");
            if (app.isSystem()) {
                builder.append("* ");
            } else {
                builder.append("- ");
            }
            builder.append(name);
            if (padding > 0) {
                builder.append(String.format("%" + padding + "s", " "));
            } else {
                builder.append("\n   ").append(String.format("%" + column + "s", " "));
            }
            builder.append(" ").append(app.getPackageName());
        }

        return builder.toString();
    }

    public static String formatVideoEncodersText(List<EncoderEntry> encoders) {
        return formatEncoderListText("video", encoders);
    }

    public static String formatAudioEncodersText(List<EncoderEntry> encoders) {
        return formatEncoderListText("audio", encoders);
    }

    private static String formatEncoderListText(String type, List<EncoderEntry> encoders) {
        StringBuilder builder = new StringBuilder("List of ").append(type).append(" encoders:");
        for (EncoderEntry encoder : encoders) {
            int lineStart = builder.length();
            builder.append("\n    --").append(type).append("-codec=").append(encoder.getCodec());
            builder.append(" --").append(type).append("-encoder=").append(encoder.getName());
            if (encoder.getType() != null) {
                int lineLength = builder.length() - lineStart;
                final int column = 70;
                if (lineLength < column) {
                    int padding = column - lineLength;
                    builder.append(String.format("%" + padding + "s", " "));
                }
                builder.append(" (").append(encoder.getType()).append(')');
                if (encoder.isVendor()) {
                    builder.append(" [vendor]");
                }
                if (encoder.getAliasFor() != null) {
                    builder.append(" (alias for ").append(encoder.getAliasFor()).append(')');
                }
            }
        }
        return builder.toString();
    }

    // ===== JSON formatter =====

    /**
     * Produce a single JSON object containing only the requested sections.
     *
     * @param displays       display entries, or null if not requested
     * @param cameras        camera entries, or null if not requested
     * @param includeSizes   whether to include camera sizes in output
     * @param cameraError    error message if camera access was denied, or null
     * @param apps           app entries, or null if not requested
     * @param videoEncoders  video encoder entries, or null if not requested
     * @param audioEncoders  audio encoder entries, or null if not requested
     */
    public static String formatJson(List<DisplayEntry> displays,
                                    List<CameraEntry> cameras, boolean includeSizes, String cameraError,
                                    List<DeviceApp> apps,
                                    List<EncoderEntry> videoEncoders,
                                    List<EncoderEntry> audioEncoders) {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject();

        if (displays != null) {
            jb.key("displays").beginArray();
            for (DisplayEntry d : displays) {
                jb.beginObject()
                        .key("displayId").value(d.getDisplayId())
                        .key("width").value(d.getWidth())
                        .key("height").value(d.getHeight())
                        .endObject();
            }
            jb.endArray();
        }

        if (cameraError != null) {
            jb.key("cameraError").value(cameraError);
        } else if (cameras != null) {
            jb.key("cameras").beginArray();
            for (CameraEntry c : cameras) {
                jb.beginObject()
                        .key("cameraId").value(c.getCameraId())
                        .key("facing").value(c.getFacing())
                        .key("sensorWidth").value(c.getSensorWidth())
                        .key("sensorHeight").value(c.getSensorHeight());

                if (c.getFps() != null) {
                    jb.key("fps").value(c.getFps());
                }

                if (c.hasZoomRange()) {
                    jb.key("zoomMin").value(c.getZoomMin());
                    jb.key("zoomMax").value(c.getZoomMax());
                }

                if (includeSizes) {
                    writeSizesArray(jb, "sizes", c.getSizes());
                    if (c.getHighSpeedSizes() != null && !c.getHighSpeedSizes().isEmpty()) {
                        writeSizesArray(jb, "highSpeedSizes", c.getHighSpeedSizes());
                        writeSizesArray(jb, "highSpeedFps", c.getHighSpeedFps());
                    }
                }

                jb.endObject();
            }
            jb.endArray();
        }

        if (apps != null) {
            List<DeviceApp> sorted = new ArrayList<>(apps);
            Collections.sort(sorted, (a, b) -> {
                int cmp = -Boolean.compare(a.isSystem(), b.isSystem());
                if (cmp != 0) {
                    return cmp;
                }
                cmp = Objects.compare(a.getName(), b.getName(), String::compareTo);
                if (cmp != 0) {
                    return cmp;
                }
                return Objects.compare(a.getPackageName(), b.getPackageName(), String::compareTo);
            });

            jb.key("apps").beginArray();
            for (DeviceApp app : sorted) {
                jb.beginObject()
                        .key("packageName").value(app.getPackageName())
                        .key("name").value(app.getName())
                        .key("system").value(app.isSystem())
                        .endObject();
            }
            jb.endArray();
        }

        if (videoEncoders != null) {
            writeEncodersJson(jb, "videoEncoders", videoEncoders);
        }

        if (audioEncoders != null) {
            writeEncodersJson(jb, "audioEncoders", audioEncoders);
        }

        jb.endObject();
        return jb.toString();
    }

    private static void writeEncodersJson(JsonBuilder jb, String key, List<EncoderEntry> encoders) {
        jb.key(key).beginArray();
        for (EncoderEntry e : encoders) {
            jb.beginObject()
                    .key("codec").value(e.getCodec())
                    .key("name").value(e.getName())
                    .key("type").value(e.getType())
                    .key("vendor").value(e.isVendor())
                    .key("aliasFor").value(e.getAliasFor())
                    .endObject();
        }
        jb.endArray();
    }

    private static void writeSizesArray(JsonBuilder jb, String key, List<int[]> sizes) {
        if (sizes == null) {
            return;
        }
        jb.key(key).beginArray();
        for (int[] size : sizes) {
            jb.beginArray().value(size[0]).value(size[1]).endArray();
        }
        jb.endArray();
    }

    // ===== Shared helpers =====

    private static String formatFpsSet(int[] fpsArray) {
        java.util.SortedSet<Integer> set = new java.util.TreeSet<>();
        for (int fps : fpsArray) {
            set.add(fps);
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Integer fps : set) {
            if (!first) {
                builder.append(", ");
            } else {
                first = false;
            }
            builder.append(fps);
        }
        builder.append('}');
        return builder.toString();
    }
}

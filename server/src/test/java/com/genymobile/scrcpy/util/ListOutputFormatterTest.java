package com.genymobile.scrcpy.util;

import com.genymobile.scrcpy.model.CameraEntry;
import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.DisplayEntry;
import com.genymobile.scrcpy.model.EncoderEntry;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ListOutputFormatterTest {

    // ===== Text: Displays =====

    @Test
    public void testDisplaysTextEmpty() {
        String result = ListOutputFormatter.formatDisplaysText(Collections.emptyList());
        Assert.assertEquals("List of displays:\n    (none)", result);
    }

    @Test
    public void testDisplaysTextNull() {
        String result = ListOutputFormatter.formatDisplaysText(null);
        Assert.assertEquals("List of displays:\n    (none)", result);
    }

    @Test
    public void testDisplaysTextNormal() {
        List<DisplayEntry> displays = Arrays.asList(
                new DisplayEntry(0, 1080, 2400),
                new DisplayEntry(1, 1920, 1080)
        );
        String result = ListOutputFormatter.formatDisplaysText(displays);
        Assert.assertEquals("List of displays:\n    --display-id=0    (1080x2400)\n    --display-id=1    (1920x1080)", result);
    }

    @Test
    public void testDisplaysTextUnknownSize() {
        List<DisplayEntry> displays = Collections.singletonList(new DisplayEntry(0, 0, 0));
        String result = ListOutputFormatter.formatDisplaysText(displays);
        Assert.assertEquals("List of displays:\n    --display-id=0    (size unknown)", result);
    }

    // ===== Text: Cameras =====

    @Test
    public void testCamerasTextEmpty() {
        String result = ListOutputFormatter.formatCamerasText(Collections.emptyList(), false);
        Assert.assertEquals("List of cameras:\n    (none)", result);
    }

    @Test
    public void testCamerasTextAccessDenied() {
        String result = ListOutputFormatter.formatCamerasText(null, false, true);
        Assert.assertEquals("List of cameras:\n    (access denied)", result);
    }

    @Test
    public void testCamerasTextNormal() {
        List<CameraEntry> cameras = Collections.singletonList(
                new CameraEntry("0", "front", 3264, 2448, new int[]{30}, Float.NaN, Float.NaN, null, null, null)
        );
        String result = ListOutputFormatter.formatCamerasText(cameras, false);
        Assert.assertEquals("List of cameras:\n    --camera-id=0    (front, 3264x2448, fps={30})", result);
    }

    @Test
    public void testCamerasTextWithZoom() {
        List<CameraEntry> cameras = Collections.singletonList(
                new CameraEntry("1", "back", 4000, 3000, new int[]{30, 60}, 1.0f, 8.0f, null, null, null)
        );
        String result = ListOutputFormatter.formatCamerasText(cameras, false);
        Assert.assertTrue(result.contains("zoom-range="));
        Assert.assertTrue(result.contains("fps={30, 60}"));
    }

    @Test
    public void testCamerasTextWithSizes() {
        List<int[]> sizes = Arrays.asList(new int[]{1920, 1080}, new int[]{1280, 720});
        List<CameraEntry> cameras = Collections.singletonList(
                new CameraEntry("0", "front", 3264, 2448, new int[]{30}, Float.NaN, Float.NaN, sizes, null, null)
        );
        String result = ListOutputFormatter.formatCamerasText(cameras, true);
        Assert.assertTrue(result.contains("        - 1920x1080"));
        Assert.assertTrue(result.contains("        - 1280x720"));
    }

    @Test
    public void testCamerasTextWithHighSpeedSizes() {
        List<int[]> sizes = Collections.singletonList(new int[]{1920, 1080});
        List<int[]> hsSizes = Collections.singletonList(new int[]{1920, 1080});
        List<int[]> hsFps = Collections.singletonList(new int[]{120, 240});
        List<CameraEntry> cameras = Collections.singletonList(
                new CameraEntry("0", "front", 3264, 2448, new int[]{30}, Float.NaN, Float.NaN, sizes, hsSizes, hsFps)
        );
        String result = ListOutputFormatter.formatCamerasText(cameras, true);
        Assert.assertTrue(result.contains("High speed capture (--camera-high-speed):"));
        Assert.assertTrue(result.contains("- 1920x1080 (fps={120, 240})"));
    }

    @Test
    public void testCamerasTextSizesNotIncluded() {
        List<int[]> sizes = Collections.singletonList(new int[]{1920, 1080});
        List<CameraEntry> cameras = Collections.singletonList(
                new CameraEntry("0", "front", 3264, 2448, new int[]{30}, Float.NaN, Float.NaN, sizes, null, null)
        );
        String result = ListOutputFormatter.formatCamerasText(cameras, false);
        Assert.assertFalse(result.contains("1920x1080"));
    }

    // ===== Text: Apps =====

    @Test
    public void testAppsTextEmpty() {
        String result = ListOutputFormatter.formatAppsText(Collections.emptyList());
        Assert.assertEquals("List of apps:", result);
    }

    @Test
    public void testAppsTextMixed() {
        List<DeviceApp> apps = Arrays.asList(
                new DeviceApp("com.android.settings", "Settings", true),
                new DeviceApp("com.android.chrome", "Chrome", false)
        );
        String result = ListOutputFormatter.formatAppsText(apps);
        // System apps should come first
        int settingsIdx = result.indexOf("* Settings");
        int chromeIdx = result.indexOf("- Chrome");
        Assert.assertTrue("System apps should appear before user apps", settingsIdx < chromeIdx);
        Assert.assertTrue(result.contains("com.android.settings"));
        Assert.assertTrue(result.contains("com.android.chrome"));
    }

    @Test
    public void testAppsTextCustomTitle() {
        List<DeviceApp> apps = Collections.singletonList(new DeviceApp("com.example", "Example", false));
        String result = ListOutputFormatter.formatAppsText("Custom title:", apps);
        Assert.assertTrue(result.startsWith("Custom title:"));
    }

    // ===== Text: Encoders =====

    @Test
    public void testVideoEncodersTextEmpty() {
        String result = ListOutputFormatter.formatVideoEncodersText(Collections.emptyList());
        Assert.assertEquals("List of video encoders:", result);
    }

    @Test
    public void testVideoEncodersTextWithType() {
        List<EncoderEntry> encoders = Collections.singletonList(
                new EncoderEntry("h264", "OMX.qcom.video.encoder.avc", "hw", true, null)
        );
        String result = ListOutputFormatter.formatVideoEncodersText(encoders);
        Assert.assertTrue(result.contains("--video-codec=h264"));
        Assert.assertTrue(result.contains("--video-encoder=OMX.qcom.video.encoder.avc"));
        Assert.assertTrue(result.contains("(hw)"));
        Assert.assertTrue(result.contains("[vendor]"));
    }

    @Test
    public void testVideoEncodersTextWithAlias() {
        List<EncoderEntry> encoders = Collections.singletonList(
                new EncoderEntry("h264", "alias.encoder", "sw", false, "real.encoder")
        );
        String result = ListOutputFormatter.formatVideoEncodersText(encoders);
        Assert.assertTrue(result.contains("(alias for real.encoder)"));
    }

    @Test
    public void testVideoEncodersTextNoType() {
        // API < 29: no type/vendor/alias info
        List<EncoderEntry> encoders = Collections.singletonList(
                new EncoderEntry("h264", "OMX.encoder", null, false, null)
        );
        String result = ListOutputFormatter.formatVideoEncodersText(encoders);
        Assert.assertTrue(result.contains("--video-codec=h264 --video-encoder=OMX.encoder"));
        Assert.assertFalse(result.contains("(hw)"));
        Assert.assertFalse(result.contains("[vendor]"));
    }

    @Test
    public void testAudioEncodersText() {
        List<EncoderEntry> encoders = Collections.singletonList(
                new EncoderEntry("opus", "c2.android.opus.encoder", "sw", false, null)
        );
        String result = ListOutputFormatter.formatAudioEncodersText(encoders);
        Assert.assertTrue(result.contains("--audio-codec=opus"));
        Assert.assertTrue(result.contains("--audio-encoder=c2.android.opus.encoder"));
        Assert.assertTrue(result.contains("(sw)"));
    }

    // ===== JSON: Displays =====

    @Test
    public void testJsonDisplaysOnly() {
        List<DisplayEntry> displays = Collections.singletonList(new DisplayEntry(0, 1080, 2400));
        String json = ListOutputFormatter.formatJson(displays, null, false, null, null, null, null);
        Assert.assertTrue(json.startsWith("{"));
        Assert.assertTrue(json.endsWith("}"));
        Assert.assertTrue(json.contains("\"displays\":["));
        Assert.assertTrue(json.contains("\"displayId\":0"));
        Assert.assertTrue(json.contains("\"width\":1080"));
        Assert.assertTrue(json.contains("\"height\":2400"));
        // Should not contain other sections
        Assert.assertFalse(json.contains("\"cameras\""));
        Assert.assertFalse(json.contains("\"apps\""));
        Assert.assertFalse(json.contains("\"videoEncoders\""));
    }

    @Test
    public void testJsonDisplaysEmpty() {
        String json = ListOutputFormatter.formatJson(Collections.emptyList(), null, false, null, null, null, null);
        Assert.assertEquals("{\"displays\":[]}", json);
    }

    // ===== JSON: Cameras =====

    @Test
    public void testJsonCamerasNormal() {
        List<CameraEntry> cameras = Collections.singletonList(
                new CameraEntry("0", "back", 4000, 3000, new int[]{30, 60}, Float.NaN, Float.NaN, null, null, null)
        );
        String json = ListOutputFormatter.formatJson(null, cameras, false, null, null, null, null);
        Assert.assertTrue(json.contains("\"cameras\":["));
        Assert.assertTrue(json.contains("\"cameraId\":\"0\""));
        Assert.assertTrue(json.contains("\"facing\":\"back\""));
        Assert.assertTrue(json.contains("\"sensorWidth\":4000"));
        Assert.assertTrue(json.contains("\"fps\":[30,60]"));
        // No zoom → no zoomMin/zoomMax
        Assert.assertFalse(json.contains("zoomMin"));
        Assert.assertFalse(json.contains("zoomMax"));
    }

    @Test
    public void testJsonCamerasWithZoom() {
        List<CameraEntry> cameras = Collections.singletonList(
                new CameraEntry("0", "back", 4000, 3000, new int[]{30}, 1.0f, 8.0f, null, null, null)
        );
        String json = ListOutputFormatter.formatJson(null, cameras, false, null, null, null, null);
        Assert.assertTrue(json.contains("\"zoomMin\":1.0"));
        Assert.assertTrue(json.contains("\"zoomMax\":8.0"));
    }

    @Test
    public void testJsonCamerasAccessDenied() {
        String json = ListOutputFormatter.formatJson(null, null, false, "access denied", null, null, null);
        Assert.assertTrue(json.contains("\"cameraError\":\"access denied\""));
        Assert.assertFalse(json.contains("\"cameras\""));
    }

    @Test
    public void testJsonCamerasWithSizes() {
        List<int[]> sizes = Arrays.asList(new int[]{1920, 1080}, new int[]{1280, 720});
        List<CameraEntry> cameras = Collections.singletonList(
                new CameraEntry("0", "front", 3264, 2448, new int[]{30}, Float.NaN, Float.NaN, sizes, null, null)
        );
        String json = ListOutputFormatter.formatJson(null, cameras, true, null, null, null, null);
        Assert.assertTrue(json.contains("\"sizes\":[[1920,1080],[1280,720]]"));
    }

    @Test
    public void testJsonCamerasSizesNotIncluded() {
        List<int[]> sizes = Collections.singletonList(new int[]{1920, 1080});
        List<CameraEntry> cameras = Collections.singletonList(
                new CameraEntry("0", "front", 3264, 2448, new int[]{30}, Float.NaN, Float.NaN, sizes, null, null)
        );
        String json = ListOutputFormatter.formatJson(null, cameras, false, null, null, null, null);
        Assert.assertFalse(json.contains("\"sizes\""));
    }

    // ===== JSON: Apps =====

    @Test
    public void testJsonApps() {
        List<DeviceApp> apps = Arrays.asList(
                new DeviceApp("com.android.chrome", "Chrome", false),
                new DeviceApp("com.android.settings", "Settings", true)
        );
        String json = ListOutputFormatter.formatJson(null, null, false, null, apps, null, null);
        // Apps should be sorted: system first
        int settingsIdx = json.indexOf("Settings");
        int chromeIdx = json.indexOf("Chrome");
        Assert.assertTrue("System apps should appear before user apps in JSON", settingsIdx < chromeIdx);
        Assert.assertTrue(json.contains("\"packageName\":\"com.android.settings\""));
        Assert.assertTrue(json.contains("\"system\":true"));
        Assert.assertTrue(json.contains("\"system\":false"));
    }

    @Test
    public void testJsonAppsEmpty() {
        String json = ListOutputFormatter.formatJson(null, null, false, null, Collections.emptyList(), null, null);
        Assert.assertEquals("{\"apps\":[]}", json);
    }

    // ===== JSON: Encoders =====

    @Test
    public void testJsonVideoEncoders() {
        List<EncoderEntry> encoders = Collections.singletonList(
                new EncoderEntry("h264", "OMX.encoder", "hw", true, null)
        );
        String json = ListOutputFormatter.formatJson(null, null, false, null, null, encoders, null);
        Assert.assertTrue(json.contains("\"videoEncoders\":["));
        Assert.assertTrue(json.contains("\"codec\":\"h264\""));
        Assert.assertTrue(json.contains("\"name\":\"OMX.encoder\""));
        Assert.assertTrue(json.contains("\"type\":\"hw\""));
        Assert.assertTrue(json.contains("\"vendor\":true"));
        Assert.assertFalse(json.contains("\"audioEncoders\""));
    }

    @Test
    public void testJsonAudioEncoders() {
        List<EncoderEntry> encoders = Collections.singletonList(
                new EncoderEntry("opus", "c2.opus", "sw", false, null)
        );
        String json = ListOutputFormatter.formatJson(null, null, false, null, null, null, encoders);
        Assert.assertTrue(json.contains("\"audioEncoders\":["));
        Assert.assertFalse(json.contains("\"videoEncoders\""));
    }

    @Test
    public void testJsonEncoderNullType() {
        List<EncoderEntry> encoders = Collections.singletonList(
                new EncoderEntry("h264", "encoder", null, false, null)
        );
        String json = ListOutputFormatter.formatJson(null, null, false, null, null, encoders, null);
        Assert.assertTrue(json.contains("\"type\":null"));
    }

    // ===== JSON: All sections combined =====

    @Test
    public void testJsonAllSections() {
        List<DisplayEntry> displays = Collections.singletonList(new DisplayEntry(0, 1080, 2400));
        List<CameraEntry> cameras = Collections.singletonList(
                new CameraEntry("0", "back", 4000, 3000, null, Float.NaN, Float.NaN, null, null, null)
        );
        List<DeviceApp> apps = Collections.singletonList(new DeviceApp("com.example", "Example", false));
        List<EncoderEntry> video = Collections.singletonList(
                new EncoderEntry("h264", "enc1", "hw", false, null)
        );
        List<EncoderEntry> audio = Collections.singletonList(
                new EncoderEntry("opus", "enc2", "sw", false, null)
        );

        String json = ListOutputFormatter.formatJson(displays, cameras, false, null, apps, video, audio);

        Assert.assertTrue(json.contains("\"displays\":["));
        Assert.assertTrue(json.contains("\"cameras\":["));
        Assert.assertTrue(json.contains("\"apps\":["));
        Assert.assertTrue(json.contains("\"videoEncoders\":["));
        Assert.assertTrue(json.contains("\"audioEncoders\":["));
        // Verify no cameraError when cameras are present
        Assert.assertFalse(json.contains("\"cameraError\""));
    }

    @Test
    public void testJsonEmptyObject() {
        // No sections requested
        String json = ListOutputFormatter.formatJson(null, null, false, null, null, null, null);
        Assert.assertEquals("{}", json);
    }

    // ===== JSON: Special characters in app names =====

    @Test
    public void testJsonAppSpecialChars() {
        List<DeviceApp> apps = Collections.singletonList(
                new DeviceApp("com.example.app", "My \"Cool\" App", false)
        );
        String json = ListOutputFormatter.formatJson(null, null, false, null, apps, null, null);
        Assert.assertTrue(json.contains("\"name\":\"My \\\"Cool\\\" App\""));
    }

    // ===== Text: Backward compatibility check =====

    @Test
    public void testTextDisplaysBackwardCompat() {
        // Verify exact match with original LogUtils.buildDisplayListMessage() format
        List<DisplayEntry> displays = Arrays.asList(
                new DisplayEntry(0, 1080, 2400),
                new DisplayEntry(1, 1920, 1080)
        );
        String expected = "List of displays:\n    --display-id=0    (1080x2400)\n    --display-id=1    (1920x1080)";
        Assert.assertEquals(expected, ListOutputFormatter.formatDisplaysText(displays));
    }

    @Test
    public void testTextAppsBackwardCompat() {
        // Verify exact match with original LogUtils.buildAppListMessage() format
        List<DeviceApp> apps = Arrays.asList(
                new DeviceApp("com.android.settings", "Settings", true),
                new DeviceApp("com.android.chrome", "Chrome", false)
        );
        String result = ListOutputFormatter.formatAppsText(apps);

        // Check structure: header, then entries with proper prefix and padding
        Assert.assertTrue(result.startsWith("List of apps:"));
        // System app prefix
        Assert.assertTrue(result.contains("\n * Settings"));
        // User app prefix
        Assert.assertTrue(result.contains("\n - Chrome"));
        // Package names present
        Assert.assertTrue(result.contains("com.android.settings"));
        Assert.assertTrue(result.contains("com.android.chrome"));
    }
}

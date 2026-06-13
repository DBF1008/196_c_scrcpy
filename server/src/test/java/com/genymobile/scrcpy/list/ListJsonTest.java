package com.genymobile.scrcpy.list;

import com.genymobile.scrcpy.display.DisplayInfo;
import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.Size;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class ListJsonTest {

    private static final ListFilter NO_FILTER = new ListFilter(null, null, null, null, null);

    @Test
    public void testEmptyResults() {
        ListData data = new ListData();
        data.setDisplays(new ArrayList<>());
        data.setCameras(new ArrayList<>());
        data.setApps(new ArrayList<>());
        data.setEncoders(new ArrayList<>());

        String json = ListJson.build(data, NO_FILTER);
        Assert.assertEquals("{\"displays\":[],\"cameras\":[],\"apps\":[],\"encoders\":[],\"errors\":{}}", json);
    }

    @Test
    public void testOnlyRequestedResourcesArePresent() {
        ListData data = new ListData();
        data.setApps(Collections.singletonList(new DeviceApp("com.x", "X", false)));

        String json = ListJson.build(data, NO_FILTER);
        // displays/cameras/encoders are not requested (null) and must be omitted
        Assert.assertEquals("{\"apps\":[{\"name\":\"X\",\"packageName\":\"com.x\",\"system\":false}],\"errors\":{}}", json);
    }

    @Test
    public void testCameraAccessDenied() {
        ListData data = new ListData();
        data.setCameras(new ArrayList<>());
        data.setCamerasAccessDenied(true);

        String json = ListJson.build(data, NO_FILTER);
        Assert.assertEquals("{\"cameras\":[],\"errors\":{\"cameras\":\"access_denied\"}}", json);
    }

    @Test
    public void testAppSystemFilterApplied() {
        ListData data = new ListData();
        data.setApps(Arrays.asList(new DeviceApp("com.sys", "Sys", true), new DeviceApp("com.user", "User", false)));

        ListFilter onlySystem = new ListFilter(true, null, null, null, null);
        String json = ListJson.build(data, onlySystem);
        Assert.assertEquals("{\"apps\":[{\"name\":\"Sys\",\"packageName\":\"com.sys\",\"system\":true}],\"errors\":{}}", json);
    }

    @Test
    public void testDisplaysIncludingNullSize() {
        ListData data = new ListData();
        DisplayInfo d1 = new DisplayInfo(0, new Size(1080, 2400), 0, 0, 1, 420, "uid-0");
        DisplayInfo d2 = new DisplayInfo(2, null, 0, 0, 0, 0, null);
        data.setDisplays(Arrays.asList(d1, d2));

        String json = ListJson.build(data, NO_FILTER);
        String expected = "{\"displays\":["
                + "{\"id\":0,\"width\":1080,\"height\":2400,\"dpi\":420,\"rotation\":0,\"flags\":1,\"layerStack\":0,\"uniqueId\":\"uid-0\"},"
                + "{\"id\":2,\"width\":null,\"height\":null,\"dpi\":0,\"rotation\":0,\"flags\":0,\"layerStack\":0,\"uniqueId\":null}"
                + "],\"errors\":{}}";
        Assert.assertEquals(expected, json);
    }

    @Test
    public void testCameraWithSizesAndFacingFilter() {
        CameraInfo back = new CameraInfo("0", "back", 4000, 3000, Arrays.asList(15, 30), 1.0f, 8.0f,
                Collections.singletonList(new Size(1920, 1080)),
                Collections.singletonList(new CameraInfo.HighSpeed(new Size(1280, 720), Arrays.asList(120, 240))));
        CameraInfo front = new CameraInfo("1", "front", 2000, 1500, Arrays.asList(30), null, null, null, null);

        ListData data = new ListData();
        data.setCameras(Arrays.asList(back, front));

        ListFilter onlyBack = new ListFilter(null, "back", null, null, null);
        String json = ListJson.build(data, onlyBack);
        String expected = "{\"cameras\":[{\"id\":\"0\",\"facing\":\"back\",\"width\":4000,\"height\":3000,"
                + "\"fps\":[15,30],\"zoomMin\":1.0,\"zoomMax\":8.0,"
                + "\"sizes\":[{\"width\":1920,\"height\":1080}],"
                + "\"highSpeed\":[{\"width\":1280,\"height\":720,\"fps\":[120,240]}]}],\"errors\":{}}";
        Assert.assertEquals(expected, json);
    }

    @Test
    public void testCameraWithoutSizesOmitsSizeFields() {
        CameraInfo front = new CameraInfo("1", "front", 2000, 1500, Arrays.asList(30), null, null, null, null);
        ListData data = new ListData();
        data.setCameras(Collections.singletonList(front));

        String json = ListJson.build(data, NO_FILTER);
        // No "sizes"/"highSpeed" keys; unknown zoom is null
        String expected = "{\"cameras\":[{\"id\":\"1\",\"facing\":\"front\",\"width\":2000,\"height\":1500,"
                + "\"fps\":[30],\"zoomMin\":null,\"zoomMax\":null}],\"errors\":{}}";
        Assert.assertEquals(expected, json);
    }

    @Test
    public void testEncoderFiltersAndFields() {
        EncoderInfo videoHw = new EncoderInfo("video", "h264", "enc.h264", "hw", false, null);
        EncoderInfo videoSw = new EncoderInfo("video", "h265", "enc.h265", "sw", false, null);
        EncoderInfo audio = new EncoderInfo("audio", "opus", "enc.opus", "sw", true, "enc.canonical");
        ListData data = new ListData();
        data.setEncoders(Arrays.asList(videoHw, videoSw, audio));

        // Filter by an audio codec (spanning the audio enum), with full field stability
        ListFilter onlyOpus = new ListFilter(null, null, "opus", null, null);
        String json = ListJson.build(data, onlyOpus);
        Assert.assertEquals("{\"encoders\":[{\"type\":\"audio\",\"codec\":\"opus\",\"name\":\"enc.opus\","
                + "\"hardwareType\":\"sw\",\"vendor\":true,\"aliasOf\":\"enc.canonical\"}],\"errors\":{}}", json);

        // Combined type + hardware-acceleration filter
        ListFilter videoHwFilter = new ListFilter(null, null, null, "video", "hw");
        String json2 = ListJson.build(data, videoHwFilter);
        Assert.assertEquals("{\"encoders\":[{\"type\":\"video\",\"codec\":\"h264\",\"name\":\"enc.h264\","
                + "\"hardwareType\":\"hw\",\"vendor\":false,\"aliasOf\":null}],\"errors\":{}}", json2);
    }

    @Test
    public void testCombinedMultipleResourcesAndFilters() {
        ListData data = new ListData();
        data.setDisplays(Collections.singletonList(new DisplayInfo(0, new Size(100, 200), 0, 0, 0, 160, "u")));
        data.setApps(Arrays.asList(new DeviceApp("com.sys", "Sys", true), new DeviceApp("com.user", "User", false)));
        data.setEncoders(Arrays.asList(
                new EncoderInfo("video", "h264", "v", "hw", false, null),
                new EncoderInfo("audio", "opus", "a", "sw", false, null)));

        // Non-system apps only, audio encoders only; displays are unfiltered
        ListFilter filter = new ListFilter(false, null, null, "audio", null);
        String json = ListJson.build(data, filter);
        String expected = "{\"displays\":[{\"id\":0,\"width\":100,\"height\":200,\"dpi\":160,\"rotation\":0,"
                + "\"flags\":0,\"layerStack\":0,\"uniqueId\":\"u\"}],"
                + "\"apps\":[{\"name\":\"User\",\"packageName\":\"com.user\",\"system\":false}],"
                + "\"encoders\":[{\"type\":\"audio\",\"codec\":\"opus\",\"name\":\"a\",\"hardwareType\":\"sw\","
                + "\"vendor\":false,\"aliasOf\":null}],\"errors\":{}}";
        Assert.assertEquals(expected, json);
    }

    @Test
    public void testNullFilterMatchesEverything() {
        ListData data = new ListData();
        data.setApps(Collections.singletonList(new DeviceApp("com.x", "X", true)));

        String json = ListJson.build(data, null);
        Assert.assertEquals("{\"apps\":[{\"name\":\"X\",\"packageName\":\"com.x\",\"system\":true}],\"errors\":{}}", json);
    }
}

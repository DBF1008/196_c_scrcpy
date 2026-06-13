package com.genymobile.scrcpy.list;

import com.genymobile.scrcpy.model.DeviceApp;

import org.junit.Assert;
import org.junit.Test;

public class ListFilterTest {

    private static final ListFilter NONE = new ListFilter(null, null, null, null, null);

    @Test
    public void testAppSystemFilter() {
        DeviceApp system = new DeviceApp("com.sys", "Sys", true);
        DeviceApp user = new DeviceApp("com.user", "User", false);

        Assert.assertTrue(NONE.matchesApp(system));
        Assert.assertTrue(NONE.matchesApp(user));

        ListFilter onlySystem = new ListFilter(true, null, null, null, null);
        Assert.assertTrue(onlySystem.matchesApp(system));
        Assert.assertFalse(onlySystem.matchesApp(user));

        ListFilter onlyUser = new ListFilter(false, null, null, null, null);
        Assert.assertFalse(onlyUser.matchesApp(system));
        Assert.assertTrue(onlyUser.matchesApp(user));
    }

    @Test
    public void testCameraFacingFilter() {
        CameraInfo back = new CameraInfo("0", "back", 0, 0, null, null, null, null, null);
        CameraInfo front = new CameraInfo("1", "front", 0, 0, null, null, null, null, null);

        Assert.assertTrue(NONE.matchesCamera(back));

        ListFilter onlyBack = new ListFilter(null, "back", null, null, null);
        Assert.assertTrue(onlyBack.matchesCamera(back));
        Assert.assertFalse(onlyBack.matchesCamera(front));
    }

    @Test
    public void testEncoderCodecFilterSpansVideoAndAudio() {
        EncoderInfo video = new EncoderInfo("video", "h264", "enc.h264", "hw", false, null);
        EncoderInfo audio = new EncoderInfo("audio", "opus", "enc.opus", "sw", false, null);

        ListFilter onlyH264 = new ListFilter(null, null, "h264", null, null);
        Assert.assertTrue(onlyH264.matchesEncoder(video));
        Assert.assertFalse(onlyH264.matchesEncoder(audio));

        // A codec filter must also be able to match an audio codec
        ListFilter onlyOpus = new ListFilter(null, null, "opus", null, null);
        Assert.assertFalse(onlyOpus.matchesEncoder(video));
        Assert.assertTrue(onlyOpus.matchesEncoder(audio));
    }

    @Test
    public void testEncoderTypeFilter() {
        EncoderInfo video = new EncoderInfo("video", "h264", "enc.h264", "hw", false, null);
        EncoderInfo audio = new EncoderInfo("audio", "opus", "enc.opus", "sw", false, null);

        ListFilter onlyVideo = new ListFilter(null, null, null, "video", null);
        Assert.assertTrue(onlyVideo.matchesEncoder(video));
        Assert.assertFalse(onlyVideo.matchesEncoder(audio));
    }

    @Test
    public void testEncoderHwFilter() {
        EncoderInfo hw = new EncoderInfo("video", "h264", "enc.hw", "hw", false, null);
        EncoderInfo sw = new EncoderInfo("video", "h264", "enc.sw", "sw", false, null);
        EncoderInfo unknown = new EncoderInfo("video", "h264", "enc.unknown", null, false, null);

        ListFilter onlyHw = new ListFilter(null, null, null, null, "hw");
        Assert.assertTrue(onlyHw.matchesEncoder(hw));
        Assert.assertFalse(onlyHw.matchesEncoder(sw));
        // When the hardware type is unknown (before Android 10), a hw filter cannot match
        Assert.assertFalse(onlyHw.matchesEncoder(unknown));
    }

    @Test
    public void testCombinedEncoderFilters() {
        EncoderInfo videoHw = new EncoderInfo("video", "h264", "a", "hw", false, null);
        EncoderInfo videoSw = new EncoderInfo("video", "h264", "b", "sw", false, null);
        EncoderInfo audioHw = new EncoderInfo("audio", "opus", "c", "hw", false, null);

        ListFilter videoAndHw = new ListFilter(null, null, null, "video", "hw");
        Assert.assertTrue(videoAndHw.matchesEncoder(videoHw));
        Assert.assertFalse(videoAndHw.matchesEncoder(videoSw));
        Assert.assertFalse(videoAndHw.matchesEncoder(audioHw));
    }
}

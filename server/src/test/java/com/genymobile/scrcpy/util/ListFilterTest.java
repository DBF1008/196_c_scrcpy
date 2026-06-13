package com.genymobile.scrcpy.util;

import com.genymobile.scrcpy.model.CameraEntry;
import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.EncoderEntry;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ListFilterTest {

    // ===== App filters =====

    @Test
    public void testNoFilterPassesAllApps() {
        ListFilter filter = new ListFilter(null, null, null);
        List<DeviceApp> apps = Arrays.asList(
                new DeviceApp("com.system", "System", true),
                new DeviceApp("com.user", "User", false)
        );
        List<DeviceApp> result = filter.filterApps(apps);
        Assert.assertEquals(2, result.size());
    }

    @Test
    public void testSystemAppsOnly() {
        ListFilter filter = new ListFilter(true, null, null);
        List<DeviceApp> apps = Arrays.asList(
                new DeviceApp("com.system", "System", true),
                new DeviceApp("com.user", "User", false),
                new DeviceApp("com.system2", "System2", true)
        );
        List<DeviceApp> result = filter.filterApps(apps);
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.get(0).isSystem());
        Assert.assertTrue(result.get(1).isSystem());
    }

    @Test
    public void testUserAppsOnly() {
        ListFilter filter = new ListFilter(false, null, null);
        List<DeviceApp> apps = Arrays.asList(
                new DeviceApp("com.system", "System", true),
                new DeviceApp("com.user", "User", false)
        );
        List<DeviceApp> result = filter.filterApps(apps);
        Assert.assertEquals(1, result.size());
        Assert.assertFalse(result.get(0).isSystem());
        Assert.assertEquals("com.user", result.get(0).getPackageName());
    }

    @Test
    public void testFilterAppsEmptyList() {
        ListFilter filter = new ListFilter(true, null, null);
        List<DeviceApp> result = filter.filterApps(Collections.emptyList());
        Assert.assertTrue(result.isEmpty());
    }

    // ===== Camera filters =====

    @Test
    public void testNoFilterPassesAllCameras() {
        ListFilter filter = new ListFilter(null, null, null);
        List<CameraEntry> cameras = Arrays.asList(
                new CameraEntry("0", "front", 3264, 2448, null, Float.NaN, Float.NaN, null, null, null),
                new CameraEntry("1", "back", 4000, 3000, null, Float.NaN, Float.NaN, null, null, null)
        );
        List<CameraEntry> result = filter.filterCameras(cameras);
        Assert.assertEquals(2, result.size());
    }

    @Test
    public void testCameraFacingFront() {
        ListFilter filter = new ListFilter(null, "front", null);
        List<CameraEntry> cameras = Arrays.asList(
                new CameraEntry("0", "front", 3264, 2448, null, Float.NaN, Float.NaN, null, null, null),
                new CameraEntry("1", "back", 4000, 3000, null, Float.NaN, Float.NaN, null, null, null),
                new CameraEntry("2", "external", 1920, 1080, null, Float.NaN, Float.NaN, null, null, null)
        );
        List<CameraEntry> result = filter.filterCameras(cameras);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("front", result.get(0).getFacing());
    }

    @Test
    public void testCameraFacingBack() {
        ListFilter filter = new ListFilter(null, "back", null);
        List<CameraEntry> cameras = Arrays.asList(
                new CameraEntry("0", "front", 3264, 2448, null, Float.NaN, Float.NaN, null, null, null),
                new CameraEntry("1", "back", 4000, 3000, null, Float.NaN, Float.NaN, null, null, null)
        );
        List<CameraEntry> result = filter.filterCameras(cameras);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("1", result.get(0).getCameraId());
    }

    @Test
    public void testCameraFacingExternal() {
        ListFilter filter = new ListFilter(null, "external", null);
        List<CameraEntry> cameras = Arrays.asList(
                new CameraEntry("0", "front", 3264, 2448, null, Float.NaN, Float.NaN, null, null, null),
                new CameraEntry("2", "external", 1920, 1080, null, Float.NaN, Float.NaN, null, null, null)
        );
        List<CameraEntry> result = filter.filterCameras(cameras);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("external", result.get(0).getFacing());
    }

    @Test
    public void testFilterCamerasEmptyList() {
        ListFilter filter = new ListFilter(null, "front", null);
        List<CameraEntry> result = filter.filterCameras(Collections.emptyList());
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testCameraFacingNoMatch() {
        ListFilter filter = new ListFilter(null, "external", null);
        List<CameraEntry> cameras = Arrays.asList(
                new CameraEntry("0", "front", 3264, 2448, null, Float.NaN, Float.NaN, null, null, null),
                new CameraEntry("1", "back", 4000, 3000, null, Float.NaN, Float.NaN, null, null, null)
        );
        List<CameraEntry> result = filter.filterCameras(cameras);
        Assert.assertTrue(result.isEmpty());
    }

    // ===== Encoder filters =====

    @Test
    public void testNoFilterPassesAllEncoders() {
        ListFilter filter = new ListFilter(null, null, null);
        List<EncoderEntry> encoders = Arrays.asList(
                new EncoderEntry("h264", "enc1", "hw", true, null),
                new EncoderEntry("h265", "enc2", "sw", false, null)
        );
        List<EncoderEntry> result = filter.filterEncoders(encoders);
        Assert.assertEquals(2, result.size());
    }

    @Test
    public void testEncoderTypeHw() {
        ListFilter filter = new ListFilter(null, null, "hw");
        List<EncoderEntry> encoders = Arrays.asList(
                new EncoderEntry("h264", "enc1", "hw", true, null),
                new EncoderEntry("h265", "enc2", "sw", false, null),
                new EncoderEntry("av1", "enc3", "hybrid", false, null)
        );
        List<EncoderEntry> result = filter.filterEncoders(encoders);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("hw", result.get(0).getType());
    }

    @Test
    public void testEncoderTypeSw() {
        ListFilter filter = new ListFilter(null, null, "sw");
        List<EncoderEntry> encoders = Arrays.asList(
                new EncoderEntry("h264", "enc1", "hw", true, null),
                new EncoderEntry("opus", "enc2", "sw", false, null)
        );
        List<EncoderEntry> result = filter.filterEncoders(encoders);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("sw", result.get(0).getType());
    }

    @Test
    public void testEncoderTypeHybrid() {
        ListFilter filter = new ListFilter(null, null, "hybrid");
        List<EncoderEntry> encoders = Arrays.asList(
                new EncoderEntry("h264", "enc1", "hw", true, null),
                new EncoderEntry("av1", "enc3", "hybrid", false, null)
        );
        List<EncoderEntry> result = filter.filterEncoders(encoders);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("hybrid", result.get(0).getType());
    }

    @Test
    public void testEncoderFilterSkipsNullType() {
        // API < 29: type is null, should not match any filter
        ListFilter filter = new ListFilter(null, null, "hw");
        List<EncoderEntry> encoders = Arrays.asList(
                new EncoderEntry("h264", "enc1", null, false, null),
                new EncoderEntry("h265", "enc2", "hw", true, null)
        );
        List<EncoderEntry> result = filter.filterEncoders(encoders);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("h265", result.get(0).getCodec());
    }

    @Test
    public void testFilterEncodersEmptyList() {
        ListFilter filter = new ListFilter(null, null, "hw");
        List<EncoderEntry> result = filter.filterEncoders(Collections.emptyList());
        Assert.assertTrue(result.isEmpty());
    }

    // ===== Combined filters =====

    @Test
    public void testCombinedFilters() {
        // Both systemApps and encoderType filters active
        ListFilter filter = new ListFilter(false, null, "sw");

        List<DeviceApp> apps = Arrays.asList(
                new DeviceApp("com.system", "System", true),
                new DeviceApp("com.user", "User", false)
        );
        List<DeviceApp> filteredApps = filter.filterApps(apps);
        Assert.assertEquals(1, filteredApps.size());
        Assert.assertEquals("com.user", filteredApps.get(0).getPackageName());

        List<EncoderEntry> encoders = Arrays.asList(
                new EncoderEntry("h264", "enc1", "hw", true, null),
                new EncoderEntry("opus", "enc2", "sw", false, null)
        );
        List<EncoderEntry> filteredEncoders = filter.filterEncoders(encoders);
        Assert.assertEquals(1, filteredEncoders.size());
        Assert.assertEquals("sw", filteredEncoders.get(0).getType());
    }

    // ===== Getters =====

    @Test
    public void testGetters() {
        ListFilter filter = new ListFilter(true, "back", "hw");
        Assert.assertEquals(Boolean.TRUE, filter.getSystemApps());
        Assert.assertEquals("back", filter.getCameraFacing());
        Assert.assertEquals("hw", filter.getEncoderType());
    }

    @Test
    public void testGettersNull() {
        ListFilter filter = new ListFilter(null, null, null);
        Assert.assertNull(filter.getSystemApps());
        Assert.assertNull(filter.getCameraFacing());
        Assert.assertNull(filter.getEncoderType());
    }
}

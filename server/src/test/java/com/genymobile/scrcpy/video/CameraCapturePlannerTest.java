package com.genymobile.scrcpy.video;

import com.genymobile.scrcpy.model.Size;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for the pure camera capability negotiation in {@link CameraCapturePlanner}. They build
 * {@link CameraCapabilities} by hand (no device required) and verify camera/size selection, fallback, and the
 * structured reasons/adjustments produced for incompatible combinations.
 */
public class CameraCapturePlannerTest {

    private static final float DELTA = 0.0001f;

    private static List<Size> sizes(Size... values) {
        return Arrays.asList(values);
    }

    private static List<FpsRange> fpsRanges(FpsRange... values) {
        return Arrays.asList(values);
    }

    private static boolean hasAdjustment(CameraPlan plan, CameraPlanAdjustment.Type type) {
        for (CameraPlanAdjustment adjustment : plan.getAdjustments()) {
            if (adjustment.getType() == type) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testExplicitCameraIdIsHonored() throws CameraPlanException {
        CameraCapabilities cam0 = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080), new Size(1280, 720)))
                .build();
        CameraCapabilities cam1 = CameraCapabilities.builder("1")
                .facing(CameraFacing.FRONT.value())
                .outputSizes(sizes(new Size(1280, 720)))
                .build();

        CameraPlanRequest request = CameraPlanRequest.builder().explicitCameraId("1").build();
        CameraPlan plan = CameraCapturePlanner.plan(Arrays.asList(cam0, cam1), request);

        // The explicit id wins even though camera "0" offers a larger size.
        Assert.assertEquals("1", plan.getCameraId());
        Assert.assertEquals(new Size(1280, 720), plan.getCaptureSize());
    }

    @Test
    public void testExplicitCameraIdNotFound() {
        CameraCapabilities cam0 = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .build();

        CameraPlanRequest request = CameraPlanRequest.builder().explicitCameraId("9").build();
        try {
            CameraCapturePlanner.plan(Collections.singletonList(cam0), request);
            Assert.fail("Expected CameraPlanException");
        } catch (CameraPlanException e) {
            Assert.assertEquals(CameraPlanException.Reason.CAMERA_ID_NOT_FOUND, e.getReason());
        }
    }

    @Test
    public void testFacingSelectsFirstMatching() throws CameraPlanException {
        CameraCapabilities back = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .build();
        CameraCapabilities front1 = CameraCapabilities.builder("1")
                .facing(CameraFacing.FRONT.value())
                .outputSizes(sizes(new Size(1280, 720)))
                .build();
        CameraCapabilities front2 = CameraCapabilities.builder("2")
                .facing(CameraFacing.FRONT.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .build();

        CameraPlanRequest request = CameraPlanRequest.builder().facing(CameraFacing.FRONT).build();
        CameraPlan plan = CameraCapturePlanner.plan(Arrays.asList(back, front1, front2), request);

        Assert.assertEquals("1", plan.getCameraId());
        Assert.assertFalse(hasAdjustment(plan, CameraPlanAdjustment.Type.CAMERA_FALLBACK));
    }

    @Test
    public void testFacingFallbackToAnotherSameFacingCamera() throws CameraPlanException {
        CameraCapabilities back = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .build();
        // First front camera cannot do high speed at all.
        CameraCapabilities frontNoHighSpeed = CameraCapabilities.builder("1")
                .facing(CameraFacing.FRONT.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .highSpeedSizes(Collections.emptyList())
                .build();
        // Second front camera can.
        CameraCapabilities frontHighSpeed = CameraCapabilities.builder("2")
                .facing(CameraFacing.FRONT.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .highSpeedSizes(sizes(new Size(1280, 720)))
                .build();

        CameraPlanRequest request = CameraPlanRequest.builder().facing(CameraFacing.FRONT).highSpeed(true).build();
        CameraPlan plan = CameraCapturePlanner.plan(Arrays.asList(back, frontNoHighSpeed, frontHighSpeed), request);

        // Fell back to the next camera matching the requested facing (never to the back camera).
        Assert.assertEquals("2", plan.getCameraId());
        Assert.assertEquals(new Size(1280, 720), plan.getCaptureSize());
        Assert.assertTrue(hasAdjustment(plan, CameraPlanAdjustment.Type.CAMERA_FALLBACK));
    }

    @Test
    public void testNoCameraForFacing() {
        CameraCapabilities back = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .build();

        CameraPlanRequest request = CameraPlanRequest.builder().facing(CameraFacing.EXTERNAL).build();
        try {
            CameraCapturePlanner.plan(Collections.singletonList(back), request);
            Assert.fail("Expected CameraPlanException");
        } catch (CameraPlanException e) {
            Assert.assertEquals(CameraPlanException.Reason.NO_CAMERA_FOR_FACING, e.getReason());
        }
    }

    @Test
    public void testHighSpeedSelectsFromHighSpeedSizes() throws CameraPlanException {
        CameraCapabilities camera = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(3840, 2160)))
                .highSpeedSizes(sizes(new Size(1280, 720), new Size(1920, 1080)))
                .highSpeedFpsRanges(fpsRanges(new FpsRange(120, 120), new FpsRange(240, 240)))
                .build();

        CameraPlan highSpeedPlan = CameraCapturePlanner.plan(Collections.singletonList(camera),
                CameraPlanRequest.builder().highSpeed(true).build());
        Assert.assertTrue(highSpeedPlan.isHighSpeed());
        // Largest of the high-speed sizes, not the larger regular output size.
        Assert.assertEquals(new Size(1920, 1080), highSpeedPlan.getCaptureSize());

        CameraPlan regularPlan = CameraCapturePlanner.plan(Collections.singletonList(camera),
                CameraPlanRequest.builder().build());
        Assert.assertFalse(regularPlan.isHighSpeed());
        Assert.assertEquals(new Size(3840, 2160), regularPlan.getCaptureSize());
    }

    @Test
    public void testHighSpeedFpsCoupling() throws CameraPlanException {
        CameraCapabilities camera = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .highSpeedSizes(sizes(new Size(1280, 720)))
                .highSpeedFpsRanges(fpsRanges(new FpsRange(240, 240)))
                .build();

        // 60 fps is not achievable in high speed on this camera: it has high-speed sizes, so this is a
        // size/fps mismatch (NO_SIZE_AVAILABLE), not "high speed unsupported".
        try {
            CameraCapturePlanner.plan(Collections.singletonList(camera),
                    CameraPlanRequest.builder().highSpeed(true).fps(60).build());
            Assert.fail("Expected CameraPlanException");
        } catch (CameraPlanException e) {
            Assert.assertEquals(CameraPlanException.Reason.NO_SIZE_AVAILABLE, e.getReason());
        }

        // 240 fps is achievable.
        CameraPlan plan = CameraCapturePlanner.plan(Collections.singletonList(camera),
                CameraPlanRequest.builder().highSpeed(true).fps(240).build());
        Assert.assertEquals(new Size(1280, 720), plan.getCaptureSize());
        Assert.assertEquals(240, plan.getFps());
    }

    @Test
    public void testHighSpeedUnsupported() {
        CameraCapabilities camera = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .highSpeedSizes(Collections.emptyList())
                .build();

        try {
            CameraCapturePlanner.plan(Collections.singletonList(camera),
                    CameraPlanRequest.builder().highSpeed(true).build());
            Assert.fail("Expected CameraPlanException");
        } catch (CameraPlanException e) {
            Assert.assertEquals(CameraPlanException.Reason.HIGH_SPEED_UNSUPPORTED, e.getReason());
        }
    }

    @Test
    public void testZoomUnsupported() throws CameraPlanException {
        // No zoom range advertised.
        CameraCapabilities camera = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .build();

        CameraPlan plan = CameraCapturePlanner.plan(Collections.singletonList(camera),
                CameraPlanRequest.builder().zoom(2f).build());

        Assert.assertEquals(1f, plan.getZoom(), DELTA);
        Assert.assertTrue(hasAdjustment(plan, CameraPlanAdjustment.Type.ZOOM_UNSUPPORTED));
    }

    @Test
    public void testZoomClampedAndInRange() throws CameraPlanException {
        CameraCapabilities camera = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .zoomRange(1f, 8f)
                .build();

        CameraPlan clamped = CameraCapturePlanner.plan(Collections.singletonList(camera),
                CameraPlanRequest.builder().zoom(20f).build());
        Assert.assertEquals(8f, clamped.getZoom(), DELTA);
        Assert.assertTrue(hasAdjustment(clamped, CameraPlanAdjustment.Type.ZOOM_CLAMPED));

        CameraPlan inRange = CameraCapturePlanner.plan(Collections.singletonList(camera),
                CameraPlanRequest.builder().zoom(4f).build());
        Assert.assertEquals(4f, inRange.getZoom(), DELTA);
        Assert.assertFalse(hasAdjustment(inRange, CameraPlanAdjustment.Type.ZOOM_CLAMPED));
    }

    @Test
    public void testTorchUnsupported() throws CameraPlanException {
        // torchAvailable defaults to false.
        CameraCapabilities camera = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .build();

        CameraPlan plan = CameraCapturePlanner.plan(Collections.singletonList(camera),
                CameraPlanRequest.builder().torch(true).build());

        Assert.assertFalse(plan.isTorch());
        Assert.assertTrue(hasAdjustment(plan, CameraPlanAdjustment.Type.TORCH_UNSUPPORTED));
    }

    @Test
    public void testTorchSupported() throws CameraPlanException {
        CameraCapabilities camera = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .torchAvailable(true)
                .build();

        CameraPlan plan = CameraCapturePlanner.plan(Collections.singletonList(camera),
                CameraPlanRequest.builder().torch(true).build());

        Assert.assertTrue(plan.isTorch());
        Assert.assertFalse(hasAdjustment(plan, CameraPlanAdjustment.Type.TORCH_UNSUPPORTED));
    }

    @Test
    public void testNoCameraAvailable() {
        try {
            CameraCapturePlanner.plan(Collections.emptyList(), CameraPlanRequest.builder().build());
            Assert.fail("Expected CameraPlanException");
        } catch (CameraPlanException e) {
            Assert.assertEquals(CameraPlanException.Reason.NO_CAMERA_AVAILABLE, e.getReason());
        }
    }

    @Test
    public void testNoSizeAvailableDueToMaxSize() {
        CameraCapabilities camera = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1920, 1080)))
                .build();

        try {
            CameraCapturePlanner.plan(Collections.singletonList(camera),
                    CameraPlanRequest.builder().maxSize(640).build());
            Assert.fail("Expected CameraPlanException");
        } catch (CameraPlanException e) {
            Assert.assertEquals(CameraPlanException.Reason.NO_SIZE_AVAILABLE, e.getReason());
        }
    }

    @Test
    public void testMaxSizeZeroMeansUnlimited() throws CameraPlanException {
        CameraCapabilities camera = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(new Size(1280, 720), new Size(1920, 1080)))
                .build();

        // maxSize defaults to 0 (unlimited): the largest size must still be selected, not filtered out.
        CameraPlan plan = CameraCapturePlanner.plan(Collections.singletonList(camera), CameraPlanRequest.builder().build());
        Assert.assertEquals(new Size(1920, 1080), plan.getCaptureSize());
    }

    @Test
    public void testAspectRatioFilterAndClosenessTieBreak() throws CameraPlanException {
        CameraCapabilities camera = CameraCapabilities.builder("0")
                .facing(CameraFacing.BACK.value())
                .outputSizes(sizes(
                        new Size(2560, 1080), // wider, but ~21:9 - excluded by the 16:9 filter
                        new Size(1920, 1088), // ~16:9 but not exact (same width as the next one)
                        new Size(1920, 1080))) // exact 16:9
                .build();

        CameraPlanRequest request = CameraPlanRequest.builder()
                .aspectRatio(CameraAspectRatio.fromFraction(16, 9))
                .build();
        CameraPlan plan = CameraCapturePlanner.plan(Collections.singletonList(camera), request);

        // 2560x1080 is filtered out (wrong AR); among the width-tied 1920 candidates the exact 16:9 wins.
        Assert.assertEquals(new Size(1920, 1080), plan.getCaptureSize());
    }
}

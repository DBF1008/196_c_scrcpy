package com.genymobile.scrcpy.video;

import com.genymobile.scrcpy.model.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure (Android-free) capability negotiation for camera mirroring.
 * <p>
 * Given the capabilities of the available cameras and a {@link CameraPlanRequest}, it produces a single
 * consistent {@link CameraPlan} by validating the requested combination and falling back across candidate
 * cameras, resolutions, high-speed fps and zoom/torch support. Recoverable mismatches are recorded as
 * {@link CameraPlanAdjustment adjustments}; an unsatisfiable request raises a {@link CameraPlanException}
 * carrying a structured reason.
 * <p>
 * Camera selection is intentionally driven only by resolution availability: zoom, torch and fps are adopted
 * or degraded on the chosen camera rather than used as selection criteria (mirroring the previous behavior
 * where they were applied opportunistically once the session was configured).
 */
public final class CameraCapturePlanner {

    // Accept sizes whose aspect ratio is within +/- 10% of the target, matching the previous selectSize behavior.
    private static final float ASPECT_RATIO_TOLERANCE = 0.1f;

    private CameraCapturePlanner() {
        // utility class, not instantiable
    }

    /**
     * Negotiate a consistent capture plan, or fail with a structured reason if none is possible.
     */
    public static CameraPlan plan(List<CameraCapabilities> cameras, CameraPlanRequest request) throws CameraPlanException {
        List<CameraCapabilities> candidates = selectCandidates(cameras, request);

        // Pick the first candidate able to produce a viable capture size (fallback across candidates).
        CameraCapabilities chosen = null;
        Size captureSize = null;
        int chosenIndex = -1;
        for (int i = 0; i < candidates.size(); i++) {
            Size size = selectSize(candidates.get(i), request);
            if (size != null) {
                chosen = candidates.get(i);
                captureSize = size;
                chosenIndex = i;
                break;
            }
        }

        if (chosen == null) {
            throw noSizeException(candidates, request);
        }

        List<CameraPlanAdjustment> adjustments = new ArrayList<>();
        if (chosenIndex > 0) {
            adjustments.add(new CameraPlanAdjustment(CameraPlanAdjustment.Type.CAMERA_FALLBACK,
                    "Preferred camera(s) could not satisfy the requested constraints; using camera '" + chosen.getId() + "'"));
        }

        float zoom = resolveZoom(chosen, request, adjustments);
        boolean torch = resolveTorch(chosen, request, adjustments);

        return new CameraPlan(chosen.getId(), captureSize, request.isHighSpeed(), request.getFps(), zoom, torch, adjustments);
    }

    /**
     * Select the candidate cameras to consider, in priority order, honoring an explicit id or facing.
     */
    private static List<CameraCapabilities> selectCandidates(List<CameraCapabilities> cameras, CameraPlanRequest request)
            throws CameraPlanException {
        String explicitId = request.getExplicitCameraId();
        if (explicitId != null) {
            CameraCapabilities match = findById(cameras, explicitId);
            if (match == null) {
                throw new CameraPlanException(CameraPlanException.Reason.CAMERA_ID_NOT_FOUND, "Camera id not found: " + explicitId);
            }
            return Collections.singletonList(match);
        }

        CameraFacing facing = request.getFacing();
        if (facing != null) {
            List<CameraCapabilities> matching = new ArrayList<>();
            for (CameraCapabilities camera : cameras) {
                if (camera.getFacing() == facing.value()) {
                    matching.add(camera);
                }
            }
            if (matching.isEmpty()) {
                throw new CameraPlanException(CameraPlanException.Reason.NO_CAMERA_FOR_FACING, "No camera found for facing: " + facing);
            }
            // Fallback never switches facing: candidates are restricted to cameras matching the requested facing.
            return matching;
        }

        if (cameras.isEmpty()) {
            throw new CameraPlanException(CameraPlanException.Reason.NO_CAMERA_AVAILABLE, "No camera available");
        }
        return cameras;
    }

    private static CameraPlanException noSizeException(List<CameraCapabilities> candidates, CameraPlanRequest request) {
        if (request.isHighSpeed()) {
            boolean anyHighSpeed = false;
            for (CameraCapabilities candidate : candidates) {
                if (!candidate.getHighSpeedSizes().isEmpty()) {
                    anyHighSpeed = true;
                    break;
                }
            }
            if (!anyHighSpeed) {
                return new CameraPlanException(CameraPlanException.Reason.HIGH_SPEED_UNSUPPORTED,
                        "High speed capture is not supported by the selected camera(s)");
            }
        }
        return new CameraPlanException(CameraPlanException.Reason.NO_SIZE_AVAILABLE,
                "Could not select a camera size matching the requested constraints");
    }

    /**
     * Select the capture size for a single camera, or {@code null} if the camera cannot satisfy the request.
     * <p>
     * This is also used on its own when the video constraints change at runtime and only the resolution must
     * be re-fit on the already-selected camera.
     */
    public static Size selectSize(CameraCapabilities camera, CameraPlanRequest request) {
        boolean highSpeed = request.isHighSpeed();
        int fps = request.getFps();

        // In high-speed mode, the requested fps must be achievable by the camera, otherwise this camera is out.
        if (highSpeed && fps > 0 && !supportsHighSpeedFps(camera, fps)) {
            return null;
        }

        List<Size> sizes = highSpeed ? camera.getHighSpeedSizes() : camera.getOutputSizes();

        Size explicitSize = request.getExplicitSize();
        if (explicitSize != null) {
            if (highSpeed) {
                // High speed cannot scale to an arbitrary size: accept it only if the camera advertises it.
                return sizes.contains(explicitSize) ? explicitSize : null;
            }
            // Regular mode: trust the requested size (the scaler can usually produce it), as before.
            return explicitSize;
        }

        if (sizes.isEmpty()) {
            return null;
        }

        int maxSize = request.getMaxSize();
        Float targetAspectRatio = resolveAspectRatio(request.getAspectRatio(), camera);

        Size best = null;
        for (Size size : sizes) {
            if (maxSize > 0 && (size.getWidth() > maxSize || size.getHeight() > maxSize)) {
                continue;
            }
            if (targetAspectRatio != null && !matchesAspectRatio(size, targetAspectRatio)) {
                continue;
            }
            if (best == null || isBetter(size, best, targetAspectRatio)) {
                best = size;
            }
        }
        return best;
    }

    /**
     * Order matching the previous implementation: greater width, then closer to the target aspect ratio (when
     * one is set), then greater height. Returns true only when {@code candidate} is strictly better, so ties
     * keep the earlier (first-listed) size.
     */
    private static boolean isBetter(Size candidate, Size best, Float targetAspectRatio) {
        if (candidate.getWidth() != best.getWidth()) {
            return candidate.getWidth() > best.getWidth();
        }
        if (targetAspectRatio != null) {
            float candidateDistance = aspectRatioDistance(candidate, targetAspectRatio);
            float bestDistance = aspectRatioDistance(best, targetAspectRatio);
            if (candidateDistance != bestDistance) {
                return candidateDistance < bestDistance;
            }
        }
        return candidate.getHeight() > best.getHeight();
    }

    private static boolean matchesAspectRatio(Size size, float targetAspectRatio) {
        float ar = (float) size.getWidth() / size.getHeight();
        float arRatio = ar / targetAspectRatio;
        return arRatio >= 1 - ASPECT_RATIO_TOLERANCE && arRatio <= 1 + ASPECT_RATIO_TOLERANCE;
    }

    private static float aspectRatioDistance(Size size, float targetAspectRatio) {
        float ar = (float) size.getWidth() / size.getHeight();
        return Math.abs(1 - ar / targetAspectRatio);
    }

    static Float resolveAspectRatio(CameraAspectRatio ratio, CameraCapabilities camera) {
        if (ratio == null) {
            return null;
        }
        if (ratio.isSensor()) {
            Size sensor = camera.getSensorActiveSize();
            if (sensor == null) {
                // Unknown sensor size: do not filter by aspect ratio rather than fail.
                return null;
            }
            return (float) sensor.getWidth() / sensor.getHeight();
        }
        return ratio.getAspectRatio();
    }

    private static boolean supportsHighSpeedFps(CameraCapabilities camera, int fps) {
        for (FpsRange range : camera.getHighSpeedFpsRanges()) {
            if (range.contains(fps)) {
                return true;
            }
        }
        return false;
    }

    private static float resolveZoom(CameraCapabilities camera, CameraPlanRequest request, List<CameraPlanAdjustment> adjustments) {
        float zoom = request.getZoom();
        if (zoom == 1) {
            return 1;
        }
        if (!camera.isZoomSupported()) {
            adjustments.add(new CameraPlanAdjustment(CameraPlanAdjustment.Type.ZOOM_UNSUPPORTED,
                    "Camera '" + camera.getId() + "' does not support zoom; ignoring requested zoom " + zoom));
            return 1;
        }
        float min = camera.getZoomMin();
        float max = camera.getZoomMax();
        float clamped = Math.max(min, Math.min(max, zoom));
        if (clamped != zoom) {
            adjustments.add(new CameraPlanAdjustment(CameraPlanAdjustment.Type.ZOOM_CLAMPED,
                    "Requested zoom " + zoom + " is outside the supported range [" + min + ", " + max + "]; using " + clamped));
        }
        return clamped;
    }

    private static boolean resolveTorch(CameraCapabilities camera, CameraPlanRequest request, List<CameraPlanAdjustment> adjustments) {
        if (!request.isTorch()) {
            return false;
        }
        if (!camera.isTorchAvailable()) {
            adjustments.add(new CameraPlanAdjustment(CameraPlanAdjustment.Type.TORCH_UNSUPPORTED,
                    "Camera '" + camera.getId() + "' has no flash unit; torch disabled"));
            return false;
        }
        return true;
    }

    private static CameraCapabilities findById(List<CameraCapabilities> cameras, String id) {
        for (CameraCapabilities camera : cameras) {
            if (id.equals(camera.getId())) {
                return camera;
            }
        }
        return null;
    }
}

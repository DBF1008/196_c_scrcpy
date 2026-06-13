package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.AsyncProcessor;
import com.genymobile.scrcpy.CleanUp;
import com.genymobile.scrcpy.Options;
import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.display.DisplayInfo;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.video.CameraCapture;
import com.genymobile.scrcpy.video.CaptureControl;
import com.genymobile.scrcpy.video.NewDisplayCapture;
import com.genymobile.scrcpy.video.SurfaceCapture;
import com.genymobile.scrcpy.video.VideoSource;
import com.genymobile.scrcpy.video.VirtualDisplayListener;
import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.io.IOException;

public class Controller implements AsyncProcessor, VirtualDisplayListener {

    // Interval between simulated user activity events
    private static final long KEEP_ACTIVE_INTERVAL_MS = 4000;

    private Thread thread;
    private Thread keepActiveThread;

    private UhidManager uhidManager;

    private final boolean camera;
    private final ControlChannel controlChannel;
    private final CleanUp cleanUp;
    private final DeviceMessageSender sender;
    private final boolean powerOn;
    private final boolean keepActive;

    private final DisplayRouter router;
    private final InputInjector inputInjector; // null in camera mode
    private final ClipboardHandler clipboardHandler; // null in camera mode
    private final CameraHandler cameraHandler; // null in display mode
    private final AppLauncher appLauncher; // null in camera mode

    // Used for resetting video encoding on RESET_VIDEO message
    private SurfaceCapture surfaceCapture;

    public Controller(ControlChannel controlChannel, CleanUp cleanUp, Options options) {
        this.camera = options.getVideoSource() == VideoSource.CAMERA;
        this.controlChannel = controlChannel;
        this.cleanUp = cleanUp;

        if (this.camera) {
            this.router = new DisplayRouter(Device.DISPLAY_ID_NONE);
            this.sender = null;
            this.inputInjector = null;
            this.clipboardHandler = null;
            this.cameraHandler = new CameraHandler();
            this.appLauncher = null;
            this.powerOn = false;
            this.keepActive = false;
            return;
        }

        int displayId = options.getDisplayId();
        boolean clipboardAutosync = options.getClipboardAutosync();
        this.powerOn = options.getPowerOn();
        this.keepActive = options.getKeepActive();

        this.router = new DisplayRouter(displayId);
        this.sender = new DeviceMessageSender(controlChannel);

        boolean supportsInputEvents = Device.supportsInputEvents(displayId);
        if (!supportsInputEvents) {
            Ln.w("Input events are not supported for secondary displays before Android 10");
        }

        this.inputInjector = new InputInjector(router, supportsInputEvents);
        this.clipboardHandler = new ClipboardHandler(clipboardAutosync, supportsInputEvents, sender, inputInjector);
        this.clipboardHandler.initAutosyncListener();
        this.cameraHandler = null;
        this.appLauncher = new AppLauncher(router);
    }

    @Override
    public void onNewVirtualDisplay(int virtualDisplayId, PositionMapper positionMapper) {
        router.onNewVirtualDisplay(virtualDisplayId, positionMapper);
    }

    public void setSurfaceCapture(SurfaceCapture surfaceCapture) {
        this.surfaceCapture = surfaceCapture;
        if (cameraHandler != null && surfaceCapture instanceof CameraCapture) {
            cameraHandler.setCameraControl(CameraHandler.fromCameraCapture((CameraCapture) surfaceCapture));
        }
    }

    private UhidManager getUhidManager() {
        if (uhidManager == null) {
            int uhidDisplayId = router.getDisplayId();
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_35_ANDROID_15) {
                if (uhidDisplayId == Device.DISPLAY_ID_NONE) {
                    // Mirroring a new virtual display id (using --new-display-id feature) on Android >= 15, where the UHID mouse pointer can be
                    // associated to the virtual display
                    try {
                        // Wait for at most 1 second until a virtual display id is known
                        int virtualDisplayId = router.getVirtualDisplayIdWithTimeout(1000);
                        if (virtualDisplayId != Device.DISPLAY_ID_NONE) {
                            uhidDisplayId = virtualDisplayId;
                        }
                    } catch (InterruptedException e) {
                        // do nothing
                    }
                }
            }

            String displayUniqueId = null;
            if (uhidDisplayId > 0) {
                // Ignore Device.DISPLAY_ID_NONE and 0 (main display)
                DisplayInfo displayInfo = ServiceManager.getDisplayManager().getDisplayInfo(uhidDisplayId);
                if (displayInfo != null) {
                    displayUniqueId = displayInfo.getUniqueId();
                }
            }
            uhidManager = new UhidManager(sender, displayUniqueId);
        }

        return uhidManager;
    }

    private void control() throws IOException {
        // on start, power on the device
        if (!camera && powerOn && router.getDisplayId() == 0 && !Device.isScreenOn(router.getDisplayId())) {
            Device.pressReleaseKeycode(KeyEvent.KEYCODE_POWER, router.getDisplayId(), Device.INJECT_MODE_ASYNC);

            // dirty hack
            // After POWER is injected, the device is powered on asynchronously.
            // To turn the device screen off while mirroring, the client will send a message that
            // would be handled before the device is actually powered on, so its effect would
            // be "canceled" once the device is turned back on.
            // Adding this delay prevents to handle the message before the device is actually
            // powered on.
            SystemClock.sleep(500);
        }

        boolean alive = true;
        while (!Thread.currentThread().isInterrupted() && alive) {
            alive = handleEvent();
        }
    }

    private void startKeepActiveThread() {
        keepActiveThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(KEEP_ACTIVE_INTERVAL_MS);
                    int actionDisplayId = router.getActionDisplayId();
                    if (actionDisplayId != Device.DISPLAY_ID_NONE) {
                        Device.keepActive(actionDisplayId);
                    }
                }
            } catch (InterruptedException e) {
                // ignore
            } catch (Throwable e) {
                Ln.e("Keep active error", e);
            } finally {
                Ln.d("Keep active thread stopped");
            }
        });
        keepActiveThread.setName("keep-active");
        keepActiveThread.setDaemon(true);
        keepActiveThread.start();
    }

    @Override
    public void start(TerminationListener listener) {
        if (keepActive) {
            startKeepActiveThread();
        }

        thread = new Thread(() -> {
            try {
                control();
            } catch (IOException e) {
                Ln.e("Controller error", e);
            } finally {
                Ln.d("Controller stopped");
                if (uhidManager != null) {
                    uhidManager.closeAll();
                }
                listener.onTerminated(true);
            }
        }, "control-recv");
        thread.start();
        if (sender != null) {
            sender.start();
        }
    }

    @Override
    public void stop() {
        if (keepActiveThread != null) {
            keepActiveThread.interrupt();
        }
        if (thread != null) {
            thread.interrupt();
        }
        if (sender != null) {
            sender.stop();
        }
        if (appLauncher != null) {
            appLauncher.shutdown();
        }
    }

    @Override
    public void join() throws InterruptedException {
        if (thread != null) {
            thread.join();
        }
        if (sender != null) {
            sender.join();
        }
    }

    private boolean handleEvent() throws IOException {
        ControlMessage msg;
        try {
            msg = controlChannel.recv();
        } catch (ControlProtocolException e) {
            Ln.e("Control protocol error", e);
            return false;
        } catch (IOException e) {
            // this is expected on close
            return false;
        }

        int type = msg.getType();

        // Events for all sources (display or camera)
        if (type == ControlMessage.TYPE_RESET_VIDEO) {
            resetVideo();
            return true;
        }

        if (!camera) {
            return handleDisplayEvent(type, msg);
        } else {
            return handleCameraEvent(type, msg);
        }
    }

    private boolean handleDisplayEvent(int type, ControlMessage msg) throws IOException {
        switch (type) {
            case ControlMessage.TYPE_INJECT_KEYCODE:
                inputInjector.injectKeycode(msg.getAction(), msg.getKeycode(), msg.getRepeat(), msg.getMetaState());
                return true;
            case ControlMessage.TYPE_INJECT_TEXT:
                inputInjector.injectText(msg.getText());
                return true;
            case ControlMessage.TYPE_INJECT_TOUCH_EVENT:
                inputInjector.injectTouch(
                        msg.getAction(), msg.getPointerId(), msg.getPosition(), msg.getPressure(), msg.getActionButton(), msg.getButtons());
                return true;
            case ControlMessage.TYPE_INJECT_SCROLL_EVENT:
                inputInjector.injectScroll(msg.getPosition(), msg.getHScroll(), msg.getVScroll(), msg.getButtons());
                return true;
            case ControlMessage.TYPE_BACK_OR_SCREEN_ON:
                inputInjector.pressBackOrTurnScreenOn(msg.getAction());
                return true;
            case ControlMessage.TYPE_EXPAND_NOTIFICATION_PANEL:
                Device.expandNotificationPanel();
                return true;
            case ControlMessage.TYPE_EXPAND_SETTINGS_PANEL:
                Device.expandSettingsPanel();
                return true;
            case ControlMessage.TYPE_COLLAPSE_PANELS:
                Device.collapsePanels();
                return true;
            case ControlMessage.TYPE_GET_CLIPBOARD:
                clipboardHandler.getClipboard(msg.getCopyKey());
                return true;
            case ControlMessage.TYPE_SET_CLIPBOARD:
                clipboardHandler.setClipboard(msg.getText(), msg.getPaste(), msg.getSequence());
                return true;
            case ControlMessage.TYPE_SET_DISPLAY_POWER:
                inputInjector.setDisplayPower(msg.getOn(), cleanUp);
                return true;
            case ControlMessage.TYPE_ROTATE_DEVICE:
                int actionDisplayId = router.getActionDisplayId();
                if (actionDisplayId != Device.DISPLAY_ID_NONE) {
                    Device.rotateDevice(actionDisplayId);
                }
                return true;
            case ControlMessage.TYPE_UHID_CREATE:
                getUhidManager().open(msg.getId(), msg.getVendorId(), msg.getProductId(), msg.getText(), msg.getData());
                return true;
            case ControlMessage.TYPE_UHID_INPUT:
                getUhidManager().writeInput(msg.getId(), msg.getData());
                return true;
            case ControlMessage.TYPE_UHID_DESTROY:
                getUhidManager().close(msg.getId());
                return true;
            case ControlMessage.TYPE_OPEN_HARD_KEYBOARD_SETTINGS:
                openHardKeyboardSettings();
                return true;
            case ControlMessage.TYPE_START_APP:
                appLauncher.startAppAsync(msg.getText());
                return true;
            case ControlMessage.TYPE_RESIZE_DISPLAY:
                resizeDisplay(msg.getWidth(), msg.getHeight());
                return true;
            default:
                throw new AssertionError("Unexpected message type: " + type);
        }
    }

    private boolean handleCameraEvent(int type, ControlMessage msg) {
        assert surfaceCapture instanceof CameraCapture;
        switch (type) {
            case ControlMessage.TYPE_CAMERA_SET_TORCH:
                cameraHandler.handleSetTorch(msg.getOn());
                return true;
            case ControlMessage.TYPE_CAMERA_ZOOM_IN:
                cameraHandler.handleZoomIn();
                return true;
            case ControlMessage.TYPE_CAMERA_ZOOM_OUT:
                cameraHandler.handleZoomOut();
                return true;
            default:
                throw new AssertionError("Unexpected message type: " + type);
        }
    }

    private void openHardKeyboardSettings() {
        Intent intent = new Intent("android.settings.HARD_KEYBOARD_SETTINGS");
        ServiceManager.getActivityManager().startActivity(intent);
    }

    private void resetVideo() {
        if (surfaceCapture != null) {
            Ln.i("Video capture reset");
            surfaceCapture.getCaptureControl().reset(CaptureControl.RESET_REASON_CLIENT_RESET);
        }
    }

    private void resizeDisplay(int width, int height) {
        NewDisplayCapture newDisplayCapture = (NewDisplayCapture) surfaceCapture;
        newDisplayCapture.requestResize(width, height);
    }
}

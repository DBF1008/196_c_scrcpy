package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.CleanUp;
import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.model.Point;
import com.genymobile.scrcpy.model.Position;
import com.genymobile.scrcpy.model.Size;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.wrappers.InputManager;

import android.os.Build;
import android.os.SystemClock;
import android.util.Pair;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles all input event injection (keyboard, text, touch, scroll) and display power management.
 * <p>
 * Extracted from Controller to separate input injection concerns from message dispatch and lifecycle management.
 * Owns the multi-touch pointer state and the {@code keepDisplayPowerOff} flag.
 */
public final class InputInjector {

    private static final int DEFAULT_DEVICE_ID = 0;

    // control_msg.h values of the pointerId field in inject_touch_event message
    private static final int POINTER_ID_MOUSE = -1;

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private final DisplayRouter router;
    private final boolean supportsInputEvents;

    private final KeyCharacterMap charMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);

    private long lastTouchDown;
    private final PointersState pointersState = new PointersState();
    private final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[PointersState.MAX_POINTERS];
    private final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[PointersState.MAX_POINTERS];

    private boolean keepDisplayPowerOff;

    public InputInjector(DisplayRouter router, boolean supportsInputEvents) {
        this.router = router;
        this.supportsInputEvents = supportsInputEvents;
        initPointers();
    }

    private void initPointers() {
        for (int i = 0; i < PointersState.MAX_POINTERS; ++i) {
            MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
            props.toolType = MotionEvent.TOOL_TYPE_FINGER;

            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            coords.orientation = 0;
            coords.size = 0;

            pointerProperties[i] = props;
            pointerCoords[i] = coords;
        }
    }

    /**
     * Injects a keycode event. Handles the special case of scheduling display power-off
     * when the {@code keepDisplayPowerOff} flag is set and a POWER/WAKEUP key is released.
     */
    public boolean injectKeycode(int action, int keycode, int repeat, int metaState) {
        if (!supportsInputEvents) {
            return true;
        }
        if (keepDisplayPowerOff && action == KeyEvent.ACTION_UP
                && (keycode == KeyEvent.KEYCODE_POWER || keycode == KeyEvent.KEYCODE_WAKEUP)) {
            assert router.getDisplayId() != Device.DISPLAY_ID_NONE;
            scheduleDisplayPowerOff(router.getDisplayId());
        }
        return injectKeyEvent(action, keycode, repeat, metaState, Device.INJECT_MODE_ASYNC);
    }

    /**
     * Injects text character by character using key composition.
     */
    public int injectText(String text) {
        if (!supportsInputEvents) {
            return 0;
        }
        int successCount = 0;
        for (char c : text.toCharArray()) {
            if (!injectChar(c)) {
                Ln.w("Could not inject char u+" + String.format("%04x", (int) c));
                continue;
            }
            successCount++;
        }
        return successCount;
    }

    /**
     * Injects a touch or mouse event with multi-touch and mouse button sequencing support.
     */
    public boolean injectTouch(int action, long pointerId, Position position, float pressure, int actionButton, int buttons) {
        if (!supportsInputEvents) {
            return true;
        }
        long now = SystemClock.uptimeMillis();

        Pair<Point, Integer> pair = router.getEventPointAndDisplayId(position);
        if (pair == null) {
            return false;
        }

        Point point = pair.first;
        int targetDisplayId = pair.second;

        int pointerIndex = pointersState.getPointerIndex(pointerId);
        if (pointerIndex == -1) {
            Ln.w("Too many pointers for touch event");
            return false;
        }
        Pointer pointer = pointersState.get(pointerIndex);
        pointer.setPoint(point);
        pointer.setPressure(pressure);

        int source;
        boolean activeSecondaryButtons = ((actionButton | buttons) & ~MotionEvent.BUTTON_PRIMARY) != 0;
        if (pointerId == POINTER_ID_MOUSE && (action == MotionEvent.ACTION_HOVER_MOVE || activeSecondaryButtons)) {
            // real mouse event, or event incompatible with a finger
            pointerProperties[pointerIndex].toolType = MotionEvent.TOOL_TYPE_MOUSE;
            source = InputDevice.SOURCE_MOUSE;
            pointer.setUp(buttons == 0);
        } else {
            // POINTER_ID_GENERIC_FINGER, POINTER_ID_VIRTUAL_FINGER or real touch from device
            pointerProperties[pointerIndex].toolType = MotionEvent.TOOL_TYPE_FINGER;
            source = InputDevice.SOURCE_TOUCHSCREEN;
            // Buttons must not be set for touch events
            buttons = 0;
            pointer.setUp(action == MotionEvent.ACTION_UP);
        }

        int pointerCount = pointersState.update(pointerProperties, pointerCoords);
        if (pointerCount == 1) {
            if (action == MotionEvent.ACTION_DOWN) {
                lastTouchDown = now;
            }
        } else {
            // secondary pointers must use ACTION_POINTER_* ORed with the pointerIndex
            if (action == MotionEvent.ACTION_UP) {
                action = MotionEvent.ACTION_POINTER_UP | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            } else if (action == MotionEvent.ACTION_DOWN) {
                action = MotionEvent.ACTION_POINTER_DOWN | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            }
        }

        /* If the input device is a mouse (on API >= 23):
         *   - the first button pressed must first generate ACTION_DOWN;
         *   - all button pressed (including the first one) must generate ACTION_BUTTON_PRESS;
         *   - all button released (including the last one) must generate ACTION_BUTTON_RELEASE;
         *   - the last button released must in addition generate ACTION_UP.
         *
         * Otherwise, Chrome does not work properly: <https://github.com/Genymobile/scrcpy/issues/3635>
         */
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_23_ANDROID_6_0 && source == InputDevice.SOURCE_MOUSE) {
            if (action == MotionEvent.ACTION_DOWN) {
                if (actionButton == buttons) {
                    // First button pressed: ACTION_DOWN
                    MotionEvent downEvent = MotionEvent.obtain(lastTouchDown, now, MotionEvent.ACTION_DOWN, pointerCount, pointerProperties,
                            pointerCoords, 0, buttons, 1f, 1f, DEFAULT_DEVICE_ID, 0, source, 0);
                    if (!Device.injectEvent(downEvent, targetDisplayId, Device.INJECT_MODE_ASYNC)) {
                        return false;
                    }
                }

                // Any button pressed: ACTION_BUTTON_PRESS
                MotionEvent pressEvent = MotionEvent.obtain(lastTouchDown, now, MotionEvent.ACTION_BUTTON_PRESS, pointerCount, pointerProperties,
                        pointerCoords, 0, buttons, 1f, 1f, DEFAULT_DEVICE_ID, 0, source, 0);
                if (!InputManager.setActionButton(pressEvent, actionButton)) {
                    return false;
                }
                if (!Device.injectEvent(pressEvent, targetDisplayId, Device.INJECT_MODE_ASYNC)) {
                    return false;
                }

                return true;
            }

            if (action == MotionEvent.ACTION_UP) {
                // Any button released: ACTION_BUTTON_RELEASE
                MotionEvent releaseEvent = MotionEvent.obtain(lastTouchDown, now, MotionEvent.ACTION_BUTTON_RELEASE, pointerCount, pointerProperties,
                        pointerCoords, 0, buttons, 1f, 1f, DEFAULT_DEVICE_ID, 0, source, 0);
                if (!InputManager.setActionButton(releaseEvent, actionButton)) {
                    return false;
                }
                if (!Device.injectEvent(releaseEvent, targetDisplayId, Device.INJECT_MODE_ASYNC)) {
                    return false;
                }

                if (buttons == 0) {
                    // Last button released: ACTION_UP
                    MotionEvent upEvent = MotionEvent.obtain(lastTouchDown, now, MotionEvent.ACTION_UP, pointerCount, pointerProperties,
                            pointerCoords, 0, buttons, 1f, 1f, DEFAULT_DEVICE_ID, 0, source, 0);
                    if (!Device.injectEvent(upEvent, targetDisplayId, Device.INJECT_MODE_ASYNC)) {
                        return false;
                    }
                }

                return true;
            }
        }

        MotionEvent event = MotionEvent.obtain(lastTouchDown, now, action, pointerCount, pointerProperties, pointerCoords, 0, buttons, 1f, 1f,
                DEFAULT_DEVICE_ID, 0, source, 0);
        return Device.injectEvent(event, targetDisplayId, Device.INJECT_MODE_ASYNC);
    }

    /**
     * Injects a scroll event.
     */
    public boolean injectScroll(Position position, float hScroll, float vScroll, int buttons) {
        if (!supportsInputEvents) {
            return true;
        }
        long now = SystemClock.uptimeMillis();

        Pair<Point, Integer> pair = router.getEventPointAndDisplayId(position);
        if (pair == null) {
            return false;
        }

        Point point = pair.first;
        int targetDisplayId = pair.second;

        MotionEvent.PointerProperties props = pointerProperties[0];
        props.id = 0;

        MotionEvent.PointerCoords coords = pointerCoords[0];
        coords.x = point.getX();
        coords.y = point.getY();
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll);

        MotionEvent event = MotionEvent.obtain(lastTouchDown, now, MotionEvent.ACTION_SCROLL, 1, pointerProperties, pointerCoords, 0, buttons, 1f, 1f,
                DEFAULT_DEVICE_ID, 0, InputDevice.SOURCE_MOUSE, 0);
        return Device.injectEvent(event, targetDisplayId, Device.INJECT_MODE_ASYNC);
    }

    /**
     * Handles BACK_OR_SCREEN_ON: injects BACK if the screen is on, or POWER to turn it on.
     */
    public boolean pressBackOrTurnScreenOn(int action) {
        if (!supportsInputEvents) {
            return true;
        }
        boolean injectBack;
        // Device.isScreenOn(displayId) ignores the displayId below Android 14
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
            // Inject BACK if the screen is on for the current virtual display id
            int actionDisplayId = router.getActionDisplayId();
            injectBack = actionDisplayId == Device.DISPLAY_ID_NONE || Device.isScreenOn(actionDisplayId);
        } else {
            // Inject BACK if the display is not the main display, or if the main display is on
            injectBack = router.getDisplayId() != 0 || Device.isScreenOn(0);
        }
        if (injectBack) {
            return injectKeyEvent(action, KeyEvent.KEYCODE_BACK, 0, 0, Device.INJECT_MODE_ASYNC);
        }

        // Screen is off
        // Only press POWER on ACTION_DOWN
        if (action != KeyEvent.ACTION_DOWN) {
            // do nothing
            return true;
        }

        if (keepDisplayPowerOff) {
            assert router.getDisplayId() != Device.DISPLAY_ID_NONE;
            scheduleDisplayPowerOff(router.getDisplayId());
        }
        return pressReleaseKeycode(KeyEvent.KEYCODE_POWER, Device.INJECT_MODE_ASYNC);
    }

    /**
     * Sets the display power on/off and manages the {@code keepDisplayPowerOff} flag.
     */
    public void setDisplayPower(boolean on, CleanUp cleanUp) {
        if (!supportsInputEvents) {
            return;
        }
        // Change the power of the main display when mirroring a virtual display
        int displayId = router.getDisplayId();
        int targetDisplayId = displayId != Device.DISPLAY_ID_NONE ? displayId : 0;
        boolean setDisplayPowerOk = Device.setDisplayPower(targetDisplayId, on);
        if (setDisplayPowerOk) {
            // Do not keep display power off for virtual displays: MOD+p must wake up the physical device
            keepDisplayPowerOff = displayId != Device.DISPLAY_ID_NONE && !on;
            Ln.i("Device display turned " + (on ? "on" : "off"));
            if (cleanUp != null) {
                boolean mustRestoreOnExit = !on;
                cleanUp.setRestoreDisplayPower(mustRestoreOnExit);
            }
        }
    }

    // Package-visible methods, also used by ClipboardHandler for COPY/CUT/PASTE key injection

    boolean injectKeyEvent(int action, int keyCode, int repeat, int metaState, int injectMode) {
        int actionDisplayId = router.getActionDisplayId();
        if (actionDisplayId == Device.DISPLAY_ID_NONE) {
            return false;
        }
        return Device.injectKeyEvent(action, keyCode, repeat, metaState, actionDisplayId, injectMode);
    }

    boolean pressReleaseKeycode(int keyCode, int injectMode) {
        int actionDisplayId = router.getActionDisplayId();
        if (actionDisplayId == Device.DISPLAY_ID_NONE) {
            return false;
        }
        return Device.pressReleaseKeycode(keyCode, actionDisplayId, injectMode);
    }

    // Private helpers

    private boolean injectChar(char c) {
        String decomposed = KeyComposition.decompose(c);
        char[] chars = decomposed != null ? decomposed.toCharArray() : new char[]{c};
        KeyEvent[] events = charMap.getEvents(chars);
        if (events == null) {
            return false;
        }

        int actionDisplayId = router.getActionDisplayId();
        if (actionDisplayId != Device.DISPLAY_ID_NONE) {
            for (KeyEvent event : events) {
                if (!Device.injectEvent(event, actionDisplayId, Device.INJECT_MODE_ASYNC)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Schedule a call to set display power to off after a small delay.
     */
    private static void scheduleDisplayPowerOff(int displayId) {
        EXECUTOR.schedule(() -> {
            Ln.i("Forcing display off");
            Device.setDisplayPower(displayId, false);
        }, 200, TimeUnit.MILLISECONDS);
    }
}

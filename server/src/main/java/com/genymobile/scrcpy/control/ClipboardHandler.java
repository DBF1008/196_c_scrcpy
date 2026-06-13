package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.wrappers.ClipboardManager;
import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.os.Build;
import android.view.KeyEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles clipboard synchronization between the device and the client.
 * <p>
 * Manages the autosync listener (device → client) and explicit get/set clipboard operations
 * (client ↔ device). Uses an {@link AtomicBoolean} echo-prevention flag to avoid re-sending
 * clipboard changes that were initiated by {@link #setClipboard}.
 */
public final class ClipboardHandler {

    private final boolean clipboardAutosync;
    private final boolean supportsInputEvents;
    private final DeviceMessageSender sender;
    private final InputInjector inputInjector;
    private final AtomicBoolean isSettingClipboard = new AtomicBoolean();

    public ClipboardHandler(boolean clipboardAutosync, boolean supportsInputEvents,
                            DeviceMessageSender sender, InputInjector inputInjector) {
        this.clipboardAutosync = clipboardAutosync;
        this.supportsInputEvents = supportsInputEvents;
        this.sender = sender;
        this.inputInjector = inputInjector;
    }

    /**
     * Registers the clipboard autosync listener. Must be called from the main thread.
     * <p>
     * When autosync is enabled, any device clipboard change is automatically sent to the client,
     * unless the change was initiated by {@link #setClipboard} (detected via the echo-prevention flag).
     */
    public void initAutosyncListener() {
        // Make sure the clipboard manager is always created from the main thread (even if clipboardAutosync is disabled)
        ClipboardManager clipboardManager = ServiceManager.getClipboardManager();
        if (!clipboardAutosync) {
            return;
        }

        if (clipboardManager != null) {
            clipboardManager.addPrimaryClipChangedListener(() -> {
                if (isSettingClipboard.get()) {
                    // This is a notification for the change we are currently applying, ignore it
                    return;
                }
                String text = Device.getClipboardText();
                if (text != null) {
                    DeviceMessage msg = DeviceMessage.createClipboard(text);
                    sender.send(msg);
                }
            });
        } else {
            Ln.w("No clipboard manager, copy-paste between device and computer will not work");
        }
    }

    /**
     * Reads the device clipboard and sends it to the client.
     * <p>
     * If {@code copyKey} is COPY or CUT (and Android ≥ 7), the corresponding key is injected first
     * to populate the clipboard before reading it.
     * <p>
     * If autosync is enabled, the autosync listener already sends clipboard changes, so this method
     * skips the explicit send to avoid duplication.
     */
    public void getClipboard(int copyKey) {
        // On Android >= 7, press the COPY or CUT key if requested
        if (copyKey != ControlMessage.COPY_KEY_NONE && Build.VERSION.SDK_INT >= AndroidVersions.API_24_ANDROID_7_0
                && supportsInputEvents) {
            int key = copyKey == ControlMessage.COPY_KEY_COPY ? KeyEvent.KEYCODE_COPY : KeyEvent.KEYCODE_CUT;
            // Wait until the event is finished, to ensure that the clipboard text we read just after is the correct one
            inputInjector.pressReleaseKeycode(key, Device.INJECT_MODE_WAIT_FOR_FINISH);
        }

        // If clipboard autosync is enabled, then the device clipboard is synchronized to the computer
        // clipboard whenever it changes, in particular when COPY or CUT are injected, so it should not
        // be synchronized twice. On Android < 7, do not synchronize at all rather than copying an old
        // clipboard content.
        if (!clipboardAutosync) {
            String clipboardText = Device.getClipboardText();
            if (clipboardText != null) {
                DeviceMessage msg = DeviceMessage.createClipboard(clipboardText);
                sender.send(msg);
            }
        }
    }

    /**
     * Sets the device clipboard from client text.
     * <p>
     * Sets the echo-prevention flag during the operation to suppress the autosync listener.
     * If {@code paste} is true (and Android ≥ 7), also injects a PASTE key event.
     * If {@code sequence} is valid, sends an acknowledgement back to the client.
     */
    public boolean setClipboard(String text, boolean paste, long sequence) {
        isSettingClipboard.set(true);
        boolean ok = Device.setClipboardText(text);
        isSettingClipboard.set(false);
        if (ok) {
            Ln.i("Device clipboard set");
        }

        // On Android >= 7, also press the PASTE key if requested
        if (paste && Build.VERSION.SDK_INT >= AndroidVersions.API_24_ANDROID_7_0 && supportsInputEvents) {
            inputInjector.pressReleaseKeycode(KeyEvent.KEYCODE_PASTE, Device.INJECT_MODE_ASYNC);
        }

        if (sequence != ControlMessage.SEQUENCE_INVALID) {
            // Acknowledgement requested
            DeviceMessage msg = DeviceMessage.createAckClipboard(sequence);
            sender.send(msg);
        }

        return ok;
    }
}

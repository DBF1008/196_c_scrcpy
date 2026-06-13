package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.FakeContext;

import android.content.ClipData;
import android.content.Context;

/**
 * Wrapper around the Android clipboard that forms the clipboard transaction boundary used for autosync.
 *
 * <p>Setting the device clipboard (e.g. when the computer pastes text to the device) triggers a "primary clip changed" notification. On many
 * devices this notification is delivered <em>asynchronously</em>, after {@code setPrimaryClip()} has already returned. A naive "currently setting"
 * time window (held only around the synchronous call) therefore fails to recognize the notification, so the text the computer just pushed gets
 * treated as a new device-side change and pushed back, causing duplicate synchronization, clipboard history pollution and confusing acknowledgement
 * semantics.</p>
 *
 * <p>To avoid this, {@link #setText(CharSequence)} remembers the text it is about to write <em>before</em> writing it, and the notification listener
 * ignores exactly one notification that reports that same text. This makes the deduplication robust regardless of whether the notification is
 * delivered synchronously or asynchronously.</p>
 */
public final class ClipboardManager {

    /**
     * Listener notified only for genuine device-side clipboard changes (self-notifications caused by {@link #setText(CharSequence)} are filtered
     * out).
     */
    public interface ClipboardListener {
        void onClipboardTextChanged(String text);
    }

    /**
     * Abstraction over the OS clipboard operations, so that the deduplication logic can be unit-tested without a real device.
     */
    interface Backend {
        String getText();

        void setText(CharSequence text);

        void registerListener(Runnable onPrimaryClipChanged);
    }

    private final Backend backend;

    // Guards pendingText and listener, and serializes setText() against the clip-changed callback (which may run on another thread).
    private final Object lock = new Object();

    // The text we are about to write (or just wrote). The next clip-changed callback reporting this exact text is our own self-notification and
    // must be ignored. It stays armed until that matching callback arrives, even if the device delivers it asynchronously.
    private String pendingText;

    private ClipboardListener listener;

    ClipboardManager(Backend backend) {
        this.backend = backend;
    }

    static ClipboardManager create() {
        android.content.ClipboardManager manager = (android.content.ClipboardManager) FakeContext.get().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            // Some devices have no clipboard manager
            // <https://github.com/Genymobile/scrcpy/issues/1440>
            // <https://github.com/Genymobile/scrcpy/issues/1556>
            return null;
        }
        return new ClipboardManager(new AndroidBackend(manager));
    }

    public String getText() {
        return backend.getText();
    }

    /**
     * Set the device clipboard.
     *
     * @param text the text to set (must not be {@code null})
     * @return {@code true} if the clipboard content was actually changed; {@code false} if it already contained the requested text (in which case
     *     nothing was written and no notification is expected)
     */
    public boolean setText(CharSequence text) {
        String newText = text.toString();
        synchronized (lock) {
            String currentText = backend.getText();
            if (currentText != null && currentText.equals(newText)) {
                // The clipboard already contains the requested text.
                // Since pasting text from the computer involves setting the device clipboard, it could be set twice on a copy-paste. This would
                // cause the clipboard listeners to be notified twice, and that would flood the Android keyboard clipboard history. To work around
                // this problem, do not explicitly set the clipboard text if it already contains the expected content.
                return false;
            }

            // Arm the deduplication BEFORE writing, so the resulting clip-changed callback is recognized as our own even if it is delivered
            // synchronously from within setText() below or asynchronously long after this method returns.
            pendingText = newText;
            backend.setText(text);
            return true;
        }
    }

    /**
     * Null-safe accessor used by callers that may have no clipboard manager at all (some devices have none).
     *
     * @return the clipboard text, or {@code null} if {@code clipboardManager} is {@code null} or the clipboard is empty
     */
    public static String getText(ClipboardManager clipboardManager) {
        return clipboardManager != null ? clipboardManager.getText() : null;
    }

    /**
     * Null-safe setter used by callers that may have no clipboard manager at all (some devices have none).
     *
     * @return {@code true} if the clipboard content was actually changed; {@code false} if {@code clipboardManager} is {@code null} or it already
     *     contained the requested text
     */
    public static boolean setText(ClipboardManager clipboardManager, CharSequence text) {
        return clipboardManager != null && clipboardManager.setText(text);
    }

    /**
     * Register the (single) listener for genuine device-side clipboard changes and start listening for OS notifications.
     */
    public void setClipboardListener(ClipboardListener clipboardListener) {
        synchronized (lock) {
            this.listener = clipboardListener;
        }
        backend.registerListener(this::onPrimaryClipChanged);
    }

    private void onPrimaryClipChanged() {
        String text;
        ClipboardListener clipboardListener;
        synchronized (lock) {
            text = backend.getText();
            if (pendingText != null && pendingText.equals(text)) {
                // This is the self-notification triggered by our own setText(); consume it and ignore it. A genuine device-side change reporting a
                // different text leaves pendingText armed, so a still-pending self-notification is suppressed when it eventually arrives.
                pendingText = null;
                return;
            }
            clipboardListener = this.listener;
        }
        if (clipboardListener != null && text != null) {
            clipboardListener.onClipboardTextChanged(text);
        }
    }

    private static final class AndroidBackend implements Backend {
        private final android.content.ClipboardManager manager;

        AndroidBackend(android.content.ClipboardManager manager) {
            this.manager = manager;
        }

        @Override
        public String getText() {
            ClipData clipData = manager.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() == 0) {
                return null;
            }
            CharSequence s = clipData.getItemAt(0).getText();
            return s != null ? s.toString() : null;
        }

        @Override
        public void setText(CharSequence text) {
            ClipData clipData = ClipData.newPlainText(null, text);
            manager.setPrimaryClip(clipData);
        }

        @Override
        public void registerListener(Runnable onPrimaryClipChanged) {
            manager.addPrimaryClipChangedListener(onPrimaryClipChanged::run);
        }
    }
}

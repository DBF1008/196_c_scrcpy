package com.genymobile.scrcpy.wrappers;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the clipboard autosync deduplication implemented by {@link ClipboardManager}.
 *
 * <p>The tests use a fake {@link ClipboardManager.Backend} so the deduplication logic can be exercised without a real device. The fake lets each
 * test control <em>when</em> the "primary clip changed" callback is delivered, which is the crux of the bug being fixed: on many devices the callback
 * triggered by setting the clipboard arrives asynchronously, after {@code setPrimaryClip()} has already returned.</p>
 */
public class ClipboardManagerTest {

    /**
     * A fake OS clipboard. It stores the current text and lets the test decide when the "primary clip changed" callback fires, so that both
     * synchronous and (more importantly) delayed/asynchronous delivery can be simulated deterministically.
     */
    private static final class FakeBackend implements ClipboardManager.Backend {
        private String text;
        private Runnable onPrimaryClipChanged;

        // Whether setText() delivers the clip-changed callback synchronously (as some devices do) or leaves it to the test to deliver later.
        private boolean autoNotify = true;

        private int setCount;

        @Override
        public String getText() {
            return text;
        }

        @Override
        public void setText(CharSequence newText) {
            text = newText == null ? null : newText.toString();
            setCount++;
            if (autoNotify) {
                notifyClipChanged();
            }
        }

        @Override
        public void registerListener(Runnable listener) {
            onPrimaryClipChanged = listener;
        }

        /** Simulate the OS delivering the "primary clip changed" callback (possibly long after setText() returned). */
        void notifyClipChanged() {
            if (onPrimaryClipChanged != null) {
                onPrimaryClipChanged.run();
            }
        }

        /** Simulate a genuine device-side clipboard change, e.g. the user copies text in an app. */
        void deviceCopy(String newText) {
            text = newText;
            notifyClipChanged();
        }
    }

    /**
     * The core regression: the clip-changed callback caused by our own write is delivered asynchronously, after setText() has returned. It must
     * still be recognized as ours and ignored, otherwise the text the computer just pushed is synced straight back.
     */
    @Test
    public void testDelayedSelfNotificationIsIgnored() {
        FakeBackend backend = new FakeBackend();
        backend.autoNotify = false; // the device delivers the callback later, not during setPrimaryClip()
        ClipboardManager clipboardManager = new ClipboardManager(backend);
        List<String> pushedToComputer = new ArrayList<>();
        clipboardManager.setClipboardListener(pushedToComputer::add);

        boolean changed = clipboardManager.setText("from computer");
        Assert.assertTrue(changed);
        Assert.assertTrue("Nothing should be pushed yet (callback not delivered)", pushedToComputer.isEmpty());

        // The device fires the clip-changed callback asynchronously, after setText() already returned.
        backend.notifyClipChanged();

        Assert.assertTrue("The self-notification must be ignored even when delivered late", pushedToComputer.isEmpty());
    }

    /** A self-notification delivered synchronously from within setText() must also be ignored (reentrant case). */
    @Test
    public void testSynchronousSelfNotificationIsIgnored() {
        FakeBackend backend = new FakeBackend(); // autoNotify = true: the callback fires synchronously from within setText()
        ClipboardManager clipboardManager = new ClipboardManager(backend);
        List<String> pushedToComputer = new ArrayList<>();
        clipboardManager.setClipboardListener(pushedToComputer::add);

        clipboardManager.setText("from computer");

        Assert.assertTrue("A synchronous self-notification must also be ignored", pushedToComputer.isEmpty());
    }

    /** Setting the same text again is a no-op: the clipboard is not rewritten and nothing is echoed back to the computer. */
    @Test
    public void testSettingSameTextTwiceWritesOnceAndDoesNotEcho() {
        FakeBackend backend = new FakeBackend();
        ClipboardManager clipboardManager = new ClipboardManager(backend);
        List<String> pushedToComputer = new ArrayList<>();
        clipboardManager.setClipboardListener(pushedToComputer::add);

        Assert.assertTrue(clipboardManager.setText("hello"));
        Assert.assertEquals(1, backend.setCount);

        boolean changedAgain = clipboardManager.setText("hello");
        Assert.assertFalse("Setting the same text again must be a no-op", changedAgain);
        Assert.assertEquals("setPrimaryClip must not be called a second time", 1, backend.setCount);

        Assert.assertTrue("Nothing must be echoed back to the computer", pushedToComputer.isEmpty());
    }

    /**
     * Some devices have no clipboard manager at all, in which case {@code ServiceManager.getClipboardManager()} returns null. The null-safe facades
     * (used by {@code Device.getClipboardText()} / {@code Device.setClipboardText()}) must degrade gracefully rather than throwing.
     */
    @Test
    public void testNoClipboardManagerIsToleratedAndSyncsNothing() {
        Assert.assertNull(ClipboardManager.getText(null));
        Assert.assertFalse(ClipboardManager.setText(null, "hello"));
    }

    /**
     * Paste + autosync: the computer pastes text, which sets the device clipboard and then injects a PASTE keystroke. The clipboard write must not
     * be echoed back (even though its callback arrives late), while genuine later device-side changes must still be synchronized.
     */
    @Test
    public void testPasteWithAutosyncDoesNotEchoBack() {
        FakeBackend backend = new FakeBackend();
        backend.autoNotify = false; // the self-notification is delivered late, after the synchronous "setting" window the old code relied on
        ClipboardManager clipboardManager = new ClipboardManager(backend);
        List<String> pushedToComputer = new ArrayList<>();
        clipboardManager.setClipboardListener(pushedToComputer::add);

        // The computer pastes text: the device clipboard is set as part of the paste action.
        Assert.assertTrue(clipboardManager.setText("pasted text"));

        // Injecting the PASTE keystroke does not modify the clipboard, so it produces no additional clip-changed callback. The device then finally
        // delivers the clip-changed callback for our own write.
        backend.notifyClipChanged();

        Assert.assertTrue("Pasted text must not be synced back to the computer", pushedToComputer.isEmpty());

        // A subsequent genuine device-side copy IS still synchronized.
        backend.deviceCopy("user copied this");
        Assert.assertEquals(1, pushedToComputer.size());
        Assert.assertEquals("user copied this", pushedToComputer.get(0));
    }

    /** A genuine device-side clipboard change (not caused by us) must be synchronized to the computer. */
    @Test
    public void testGenuineDeviceChangeIsSynced() {
        FakeBackend backend = new FakeBackend();
        ClipboardManager clipboardManager = new ClipboardManager(backend);
        List<String> pushedToComputer = new ArrayList<>();
        clipboardManager.setClipboardListener(pushedToComputer::add);

        backend.deviceCopy("copied on device");

        Assert.assertEquals(1, pushedToComputer.size());
        Assert.assertEquals("copied on device", pushedToComputer.get(0));
    }

    /**
     * After our write has been acknowledged by its (late) self-notification, writing the very same text again from the computer is still a no-op,
     * and a genuine device-side re-copy of that same text afterwards is synchronized normally.
     */
    @Test
    public void testGenuineChangeAfterIdenticalWriteIsSynced() {
        FakeBackend backend = new FakeBackend();
        backend.autoNotify = false;
        ClipboardManager clipboardManager = new ClipboardManager(backend);
        List<String> pushedToComputer = new ArrayList<>();
        clipboardManager.setClipboardListener(pushedToComputer::add);

        Assert.assertTrue(clipboardManager.setText("shared"));
        backend.notifyClipChanged(); // consume the self-notification for our write
        Assert.assertTrue(pushedToComputer.isEmpty());

        // The user copies the same text on the device: this is a genuine change and must be synced.
        backend.deviceCopy("shared");
        Assert.assertEquals(1, pushedToComputer.size());
        Assert.assertEquals("shared", pushedToComputer.get(0));
    }
}

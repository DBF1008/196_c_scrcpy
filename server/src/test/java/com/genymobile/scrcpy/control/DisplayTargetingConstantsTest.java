package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.device.Device;

import org.junit.Assert;
import org.junit.Test;

/**
 * Cross-checks that {@link DisplayTargeting#DISPLAY_ID_NONE} stays in sync with {@link Device#DISPLAY_ID_NONE}.
 *
 * <p>This is kept separate from {@code DisplayTargetingTest} because it depends on {@link Device} (and therefore on the Android SDK), whereas
 * {@code DisplayTargetingTest} is intentionally Android-free.
 */
public class DisplayTargetingConstantsTest {

    @Test
    public void testNoneMatchesDevice() {
        Assert.assertEquals(Device.DISPLAY_ID_NONE, DisplayTargeting.DISPLAY_ID_NONE);
    }
}

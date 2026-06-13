package com.genymobile.scrcpy.util;

import com.genymobile.scrcpy.model.DeviceApp;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pins the legacy human-readable app list output so that adding JSON output does not regress the text format.
 * <p>
 * Only {@link LogUtils#buildAppListMessage(String, List)} is covered here because it is the one text builder that does not depend on
 * Android APIs (the others require a device).
 */
public class LogUtilsAppListTest {

    private static String spaces(int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, ' ');
        return new String(chars);
    }

    @Test
    public void testAppListSortingAndFormat() {
        // Input order is intentionally unsorted: the builder must put system apps first, then sort by name
        List<DeviceApp> apps = new ArrayList<>(Arrays.asList(
                new DeviceApp("com.example.user", "User", false),
                new DeviceApp("com.android.sys", "Sys", true)));

        String result = LogUtils.buildAppListMessage("List of apps:", apps);

        // column = 30; padding = 30 - name.length() spaces, then a single space before the package name
        String expected = "List of apps:"
                + "\n " + "* " + "Sys" + spaces(27) + " " + "com.android.sys"
                + "\n " + "- " + "User" + spaces(26) + " " + "com.example.user";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void testAppListLongNameWraps() {
        // A name as long as (or longer than) the column wraps the package name onto the next indented line
        String longName = "ThisIsAVeryLongApplicationNameOver30";
        List<DeviceApp> apps = new ArrayList<>(Arrays.asList(new DeviceApp("com.example.long", longName, false)));

        String result = LogUtils.buildAppListMessage("Title:", apps);

        Assert.assertTrue(result.startsWith("Title:"));
        Assert.assertTrue(result.contains("- " + longName));
        // The package name is wrapped onto its own indented line
        Assert.assertTrue(result.contains("\n   "));
        Assert.assertTrue(result.contains("com.example.long"));
    }
}

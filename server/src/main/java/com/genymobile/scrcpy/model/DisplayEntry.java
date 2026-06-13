package com.genymobile.scrcpy.model;

public final class DisplayEntry {

    private final int displayId;
    private final int width;
    private final int height;

    public DisplayEntry(int displayId, int width, int height) {
        this.displayId = displayId;
        this.width = width;
        this.height = height;
    }

    public int getDisplayId() {
        return displayId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}

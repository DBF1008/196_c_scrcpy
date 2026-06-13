package com.genymobile.scrcpy.list;

/**
 * Structured, Android-free description of a media encoder, used to serialize the encoder list as JSON.
 */
public final class EncoderInfo {

    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";

    public static final String HW_SOFTWARE = "sw";
    public static final String HW_HARDWARE = "hw";
    public static final String HW_HYBRID = "hybrid";

    private final String type;
    private final String codec;
    private final String name;
    // null when the hardware acceleration type is unknown (before Android 10)
    private final String hardwareType;
    private final boolean vendor;
    // null when the encoder is not an alias
    private final String aliasOf;

    public EncoderInfo(String type, String codec, String name, String hardwareType, boolean vendor, String aliasOf) {
        this.type = type;
        this.codec = codec;
        this.name = name;
        this.hardwareType = hardwareType;
        this.vendor = vendor;
        this.aliasOf = aliasOf;
    }

    public String getType() {
        return type;
    }

    public String getCodec() {
        return codec;
    }

    public String getName() {
        return name;
    }

    public String getHardwareType() {
        return hardwareType;
    }

    public boolean isVendor() {
        return vendor;
    }

    public String getAliasOf() {
        return aliasOf;
    }
}

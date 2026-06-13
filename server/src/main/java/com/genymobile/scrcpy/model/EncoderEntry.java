package com.genymobile.scrcpy.model;

public final class EncoderEntry {

    private final String codec;
    private final String name;
    private final String type;
    private final boolean vendor;
    private final String aliasFor;

    public EncoderEntry(String codec, String name, String type, boolean vendor, String aliasFor) {
        this.codec = codec;
        this.name = name;
        this.type = type;
        this.vendor = vendor;
        this.aliasFor = aliasFor;
    }

    public String getCodec() {
        return codec;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isVendor() {
        return vendor;
    }

    public String getAliasFor() {
        return aliasFor;
    }
}

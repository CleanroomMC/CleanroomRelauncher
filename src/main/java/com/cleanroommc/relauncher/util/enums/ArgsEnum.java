package com.cleanroommc.relauncher.util.enums;

public enum ArgsEnum {

    UnlockExperimentalOptions(
            "-XX:+UnlockExperimentalVMOptions",
            "",
            "",
            false
    ),
    CompactObjectHeaders(
            "-XX:+UseCompactObjectHeaders",
            "Compact Object Headers",
            "Recommended on Java 24+ to reduce memory overhead.",
            true
    ),
    ZGC(
            "-XX:+UseZGC",
            "Z Garbage Collector",
            "Experimental low-latency GC intended for stronger CPUs, with more memory allocated.",
            false
    );

    private final String arg, title, description;
    private final boolean selectedByDefault;

    ArgsEnum(String arg, String title, String description, boolean selectedByDefault) {
        this.arg = arg;
        this.title = title;
        this.description = description;
        this.selectedByDefault=selectedByDefault;
    }

    public String getArg() {
        return arg;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSelectedByDefault() {
        return selectedByDefault;
    }

}

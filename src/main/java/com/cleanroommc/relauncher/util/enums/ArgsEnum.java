package com.cleanroommc.relauncher.util.enums;

public enum ArgsEnum {
    UnlockExperimentalOptions("-XX:+UnlockExperimentalVMOptions","",false),
    CompactObjectHeaders("-XX:+UseCompactObjectHeaders", "(Recommended) (24+)", true),
    ZGC("-XX:+UseZGC", "(Experimental, Do not use with weaker CPUs)", false);

    private final String arg;
    private final String status;
    private final boolean selectedByDefault;
    ArgsEnum(String arg,  String status, boolean selectedByDefault) {
        this.arg = arg;
        this.status = status;
        this.selectedByDefault=selectedByDefault;
    }
    public String getArg() {
        return arg;
    }
    public String getStatus() {
        return status;
    }
    public boolean isSelectedByDefault() {
        return selectedByDefault;
    }
}

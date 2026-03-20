package com.cleanroommc.relauncher.util.enums;

public enum JavaTargetsEnum implements IDisplayableEnum {
    J21("Java 21","21"),
    J22("Java 22","22"),
    J23("Java 23","23"),
    J24("Java 24","24"),
    J25("Java 25","25");
    private final String displayName;
    private final String internalName;
    JavaTargetsEnum(String displayName,  String internalName) {
        this.displayName = displayName;
        this.internalName = internalName;
    }
    @Override
    public String getDisplayName() {
        return displayName;
    }
    @Override
    public String getInternalName() {
        return internalName;
    }
    public int getInternalNameInt(){
        return Integer.parseInt(internalName);
    }
}

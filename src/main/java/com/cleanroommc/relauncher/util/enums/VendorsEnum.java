package com.cleanroommc.relauncher.util.enums;

public enum VendorsEnum implements IDisplayableEnum {
    ADOPTIUM("Adoptium", "adoptium", "temurin"),
    AZUL_ZULU("Azul Zulu", "azul", "zulu"),
    AMAZON("Amazon Corretto", "amazon", "corretto"),
    ORACLE("Oracle", "oracle", "oracle_open_jdk"),
    MICROSOFT("Microsoft", "microsoft", "microsoft"),
    JETBRAINS("JetBrains", "jetbrains", "jetbrains"),
    IBM_SEMERU("IBM Semeru", "ibm", "semeru"),
    GRAALVM("GraalVM", "graalvm", "graalvm_ce17"),
    SAP_MACHINE("SAPMachine", "sap", "sap_machine"),
    LIBERICA("BellSoft Liberica", "bellsoft", "liberica"),
    ANY("Any Vendor", "", "");

    private final String displayName;
    private final String internalName;
    private final String discoverySlug;

    VendorsEnum(String displayName, String internalName, String discoverySlug) {
        this.displayName = displayName;
        this.internalName = internalName;
        this.discoverySlug = discoverySlug;
    }
    public String getDiscoverySlug() {
        return discoverySlug;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
    @Override
    public String getInternalName() {
        return internalName;
    }
}

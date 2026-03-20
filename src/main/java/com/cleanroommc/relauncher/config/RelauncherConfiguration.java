package com.cleanroommc.relauncher.config;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.util.enums.ArgsEnum;
import com.cleanroommc.relauncher.util.enums.JavaTargetsEnum;
import com.cleanroommc.relauncher.util.enums.VendorsEnum;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraft.launchwrapper.Launch;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class RelauncherConfiguration {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path FILE = Launch.minecraftHome.toPath().resolve("config/relauncher.json");

    public static RelauncherConfiguration read() {
        if (Files.notExists(FILE)) {
            return new RelauncherConfiguration();
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            return GSON.fromJson(reader, RelauncherConfiguration.class);
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.error("Unable to read config", e);
            return new RelauncherConfiguration();
        }
    }

    @SerializedName("selectedVersion")
    private String cleanroomVersion;
    @SerializedName("latestVersion")
    private String latestCleanroomVersion;
    @SerializedName("targetJavaVersion")
    private JavaTargetsEnum targetJavaVersion;
    @SerializedName("targetVendor")
    private VendorsEnum targetVendor;
    @SerializedName("javaPath")
    private String javaExecutablePath;
    @SerializedName("args")
    private String javaArguments = "";
    @SerializedName("autoSetup")
    private boolean autoSetup;
    @SerializedName("enableRelauncher")
    private boolean enableRelauncher=true;

    public String getCleanroomVersion() {
        return cleanroomVersion;
    }

    public String getLatestCleanroomVersion() {
        return latestCleanroomVersion;
    }

    public VendorsEnum getJavaVendor() {
        return targetVendor;
    }

    public JavaTargetsEnum getJavaTarget() {
        return targetJavaVersion;
    }

    public String getJavaExecutablePath() {
        return javaExecutablePath;
    }

    public String getJavaArguments() {
        return javaArguments;
    }

    public boolean getAutoSetup() {
        return autoSetup;
    }

    public boolean getRelauncherEnabled() {
        return enableRelauncher;
    }

    public void setCleanroomVersion(String cleanroomVersion) {
        this.cleanroomVersion = cleanroomVersion;
    }

    public void setLatestCleanroomVersion(String latestCleanroomVersion) {
        this.latestCleanroomVersion = latestCleanroomVersion;
    }

    public void setJavaExecutablePath(String javaExecutablePath) {
        this.javaExecutablePath = javaExecutablePath.replace("\\\\", "/");
    }

    public void setJavaArguments(String javaArguments) {
        this.javaArguments = javaArguments;
    }

    public void setAutoSetup(boolean autoSetup) {
        this.autoSetup = autoSetup;
    }

    public void setRelauncherEnabled(boolean enableRelauncher) {
        this.enableRelauncher= enableRelauncher;
    }

    public void setTargetJavaVersion(JavaTargetsEnum targetJavaVersion) {
        this.targetJavaVersion = targetJavaVersion;
    }

    public void setTargetVendor(VendorsEnum targetVendor) {
        this.targetVendor = targetVendor;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.error("Unable to save config", e);
        }
    }

    public boolean argsContain(ArgsEnum arg){
        return javaArguments.contains(arg.getArg());
    }

}

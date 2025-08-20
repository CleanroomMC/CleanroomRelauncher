package com.cleanroommc.relauncher.config;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraft.launchwrapper.Launch;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class RelauncherConfiguration {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final File FILE = new File(Launch.minecraftHome, "config/relauncher.json");

    static {
        File oldConfig = new File(Launch.minecraftHome, "cleanroom-relauncher-v1.properties");
        if (oldConfig.exists()) {
            oldConfig.delete();
        }
    }

    public static RelauncherConfiguration read() {
        if (!FILE.exists()) {
            return new RelauncherConfiguration();
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8)) {
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
    @SerializedName("javaPath")
    private String javaExecutablePath;
    @SerializedName("args")
    private String javaArguments = "";

    public String getCleanroomVersion() {
        return cleanroomVersion;
    }

    public String getLatestCleanroomVersion() {
        return latestCleanroomVersion;
    }

    public String getJavaExecutablePath() {
        return javaExecutablePath;
    }

    public String getJavaArguments() {
        return javaArguments;
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

    public void save() {
        FILE.getParentFile().mkdirs();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.error("Unable to save config", e);
        }
    }

}

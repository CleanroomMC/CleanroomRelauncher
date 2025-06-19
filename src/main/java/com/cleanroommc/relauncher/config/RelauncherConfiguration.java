package com.cleanroommc.relauncher.config;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.google.gson.*;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.*;

public class RelauncherConfiguration {
    
    public static final File FILE = new File(Launch.minecraftHome, "config/cleanroom_relauncher.cfg");
    
    private static final Configuration forgedConfig;
    
    
    static {
        JsonObject v2ConfigJson;
        {
            File v1Config = new File(Launch.minecraftHome, "cleanroom-relauncher-v1.properties");
            if (v1Config.exists()) {
                if (!v1Config.delete())
                    CleanroomRelauncher.LOGGER.error("Unable to delete ./cleanroom-relauncher-v1.properties");
            }
        }
        {
            File v2Config = new File(Launch.minecraftHome, "config/relauncher.json");
            if (v2Config.exists()) {
                try (FileReader reader = new FileReader(v2Config)) {
                    v2ConfigJson = (JsonObject) new JsonParser().parse(reader);
                } catch (Throwable e) {
                    CleanroomRelauncher.LOGGER.error("Unable to read v2Config", e);
                    v2ConfigJson = null;
                }
                if (!v2Config.delete())
                    CleanroomRelauncher.LOGGER.error("Unable to delete ./config/relauncher.json");
            } else {
                v2ConfigJson = null;
            }
        }
        Configuration v3Config = new Configuration(FILE);
        v3Config.load();
        Property prop;
        { // cleanroom
            { // selectedVersion
                prop = v3Config.get("cleanroom", "selectedVersion", "",
                        "The selected version of Cleanroom to relaunch", Property.Type.STRING
                );
                // update v2
                if (v2ConfigJson != null)
                    updateStringElement(prop, v2ConfigJson, "selectedVersion");
            }
            {
                prop = v3Config.get("cleanroom", "latestVersion", "",
                        "The latest version of Cleanroom", Property.Type.STRING
                );
                if (v2ConfigJson != null)
                    updateStringElement(prop, v2ConfigJson, "latestVersion");
            }
        }
        {
            {
                prop = v3Config.get("java", "javaPath", "",
                        "The path of the java executable", Property.Type.STRING
                );
                if (v2ConfigJson != null)
                    updateStringElement(prop, v2ConfigJson, "javaPath");
            }
            {
                prop = v3Config.get("java", "args", "",
                        "The java arguments", Property.Type.STRING
                );
                if (v2ConfigJson != null)
                    updateStringElement(prop, v2ConfigJson, "args");
            }
        }
        if (v3Config.hasChanged()) v3Config.save();
        forgedConfig = v3Config;

    }
    
    private static void updateStringElement(Property property, JsonObject jsonObject, String name){
        if (jsonObject.has(name)) {
            JsonElement element = jsonObject.get(name);
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isString()) {
                    property.set(primitive.getAsString());
                }
            }
        }
    }

    public static RelauncherConfiguration read() {
        return new RelauncherConfiguration();
    }

    private static String nullable(String str) {
        return str == null || str.isEmpty() ? null : str;
    }

    public String getCleanroomVersion() {
        return nullable(forgedConfig.getCategory("cleanroom").get("selectedVersion").getString());
    }

    public String getLatestCleanroomVersion() {
        return nullable(forgedConfig.getCategory("cleanroom").get("latestVersion").getString());
    }

    public String getJavaExecutablePath() {
        return nullable(forgedConfig.getCategory("java").get("javaPath").getString());
    }

    public String getJavaArguments() {
        return nullable(forgedConfig.getCategory("java").get("args").getString());
    }

    public void setCleanroomVersion(String cleanroomVersion) {
        forgedConfig.getCategory("cleanroom").get("selectedVersion").set(cleanroomVersion);
    }

    public void setLatestCleanroomVersion(String latestCleanroomVersion) {
        forgedConfig.getCategory("cleanroom").get("latestVersion").set(latestCleanroomVersion);
    }

    public void setJavaExecutablePath(String javaExecutablePath) {
        forgedConfig.getCategory("java").get("javaPath").set(javaExecutablePath.replace("\\\\", "/"));
    }

    public void setJavaArguments(String javaArguments) {
        forgedConfig.getCategory("java").get("args").set(javaArguments);
    }

    public void save() {
        if (forgedConfig.hasChanged()) {
            forgedConfig.save();
        }
    }

}

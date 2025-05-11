package com.cleanroommc.relauncher.gui;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class I18n {
    private static Map<String, String> locales = new HashMap<>();
    private static List<String> langs = new ArrayList();

    public static void load(String lang) {
        locales.clear();
        langs.clear();

        // load lang key and names
        try (InputStream stream = I18n.class.getResourceAsStream("/assets/cleanroomrelauncher/lang/locales.json")){
            for(Map.Entry<String, JsonElement> entry : new JsonParser().parse(new InputStreamReader(Objects.requireNonNull(stream))).getAsJsonObject().entrySet()) {
                locales.put(entry.getKey(), entry.getValue().getAsString());
                langs.add(entry.getKey());
            }
        } catch (Throwable e) {
            CleanroomRelauncher.LOGGER.error(e);
        }

        // load en_us as default
        try (InputStream stream = I18n.class.getResourceAsStream("/assets/cleanroomrelauncher/lang/en_us.json")){
            for(Map.Entry<String, JsonElement> entry : new JsonParser().parse(new InputStreamReader(Objects.requireNonNull(stream))).getAsJsonObject().entrySet()) {
                locales.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Throwable e) {
            CleanroomRelauncher.LOGGER.error(e);
        }

        // load custom langs
        if (!"en_us".equals(lang)) {
            try (InputStream stream = I18n.class.getResourceAsStream("/assets/cleanroomrelauncher/lang/"+ lang +".json")){
                for(Map.Entry<String, JsonElement> entry : new JsonParser().parse(new InputStreamReader(Objects.requireNonNull(stream))).getAsJsonObject().entrySet()) {
                    locales.put(entry.getKey(), entry.getValue().getAsString());
                }
            } catch (Throwable e) {
                CleanroomRelauncher.LOGGER.error(e);
            }
        }
    }

    public static String format(String key, Object... args) {
        if (locales.containsKey(key)) {
            return String.format(locales.get(key), args);
        } else return key;
    }

    // lang key - lang name
    public static List<String> getLocales() {
        return langs;
    }

    static {
        load("en_us");
    }
}
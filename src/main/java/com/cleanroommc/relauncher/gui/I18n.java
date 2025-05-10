package com.cleanroommc.relauncher.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class I18n {
    private static Map<String, String> locales = = new HashMap<>();

    public static void load(String lang) {
        try (InputStream stream = LangUtil.class.getResourceAsStream("/lang/en_US.json")){
            for(Map.Entry<String, JsonElement> entry : JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream))).getAsJsonObject().entrySet()) {
                locales.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (IOException | NullPointerException e) {
            
        }
        if (!"en_us".equals(lang)) {
            try (InputStream stream = LangUtil.class.getResourceAsStream("/lang/" + lang + ".json")){
                for(Map.Entry<String, JsonElement> entry : JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream))).getAsJsonObject().entrySet()) {
                    locales.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                }
            } catch (IOException | NullPointerException e) {
                
            }
        }
    }

    public static String format(String key, Object... args) {
        if (locales.getValue().containsKey(key)) {
            return String.format(locales.getValue().get(key), args);
        } else return key;
    }

    static {
        String lang = CleanroomRelauncher.CONFIG.getLanguage();
        if (lang == null) {
            Locale locale = Locale.getDefault();
            lang = locale.getLanguage().toLowerCase() + "_" + locale.getCountry().toLowerCase();
        }
        load(CleanroomRelauncher.CON);
    }
}
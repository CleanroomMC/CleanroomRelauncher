package com.cleanroommc.relauncher.download;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public class CleanroomRelease {

    private static final Path CACHE_FILE = CleanroomRelauncher.CACHE_DIR.resolve("releases.json");

    public static List<CleanroomRelease> queryAll() throws IOException {
        // Check if the cache file exists and is not outdated
        if (Files.exists(CACHE_FILE)) {
            CleanroomRelauncher.LOGGER.info("Loading releases from cached releases.json");
            return fetchReleasesFromCache(CACHE_FILE);
        } else {
            CleanroomRelauncher.LOGGER.info("No cache found, fetching releases...");
            List<CleanroomRelease> releases = fetchReleasesFromGithub();

            // After fetching releases, save them to the cache
            saveReleasesToCache(CACHE_FILE, releases);

            return releases;
        }
    }

    private static List<CleanroomRelease> fetchReleasesFromGithub() throws IOException {
        try {
            URL url = new URL("https://api.github.com/repos/CleanroomMC/Cleanroom/releases");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

            if (connection.getResponseCode() != 200) {
                throw new IOException("Failed to fetch releases: HTTP error code " + connection.getResponseCode());
            }

            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                return CleanroomRelauncher.GSON.fromJson(reader, new TypeToken<List<CleanroomRelease>>(){ }.getType());
            }
        } catch (Exception e) {
            throw new IOException("Failed to fetch or parse releases", e);
        }
    }

    /**
     * Loads the cached {@link CleanroomRelease}'s from the specified file.
     *
     * @param releaseFile the path to the file containing cached release data.
     * @return a list of {@link CleanroomRelease} objects loaded from the cache file.
     *
     * @throws RuntimeException if an {@link IOException} occurs while reading the file
     *         or if the content cannot be properly deserialized into the list of releases.
     */
    private static List<CleanroomRelease> fetchReleasesFromCache(Path releaseFile) {
        try (Reader reader = Files.newBufferedReader(releaseFile)) {
            return Arrays.asList(CleanroomRelauncher.GSON.fromJson(reader, CleanroomRelease[].class));
        } catch (IOException e) {
            throw new RuntimeException("Unable to load cached releases.", e);
        }
    }

    /**
     * Saves the list of releases to the specified cache file.
     *
     * @param releaseFile the path to the file where the releases should be saved.
     * @param releases the list of {@link CleanroomRelease}'s to be saved.
     *
     * @throws RuntimeException if an {@link IOException} occurs while writing to the file.
     */
    private static void saveReleasesToCache(Path releaseFile, List<CleanroomRelease> releases) {
        try (Writer writer = Files.newBufferedWriter(releaseFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            CleanroomRelauncher.GSON.toJson(releases, writer);
            CleanroomRelauncher.LOGGER.info("Saved {} releases to cache.", releases.size());
        } catch (IOException e) {
            throw new RuntimeException("Unable to save releases to cache.", e);
        }
    }

    public String name;
    @SerializedName("tag_name")
    public String tagName;
    public List<Asset> assets;

    public Asset getInstallerArtifact() {
        for (Asset asset : this.assets) {
            if (asset.name.endsWith("-installer.jar")) {
                return asset;
            }
        }
        return null;
    }

    @Deprecated
    public Asset getMultiMcPackArtifact() {
        for (Asset asset : this.assets) {
            if (asset.name.endsWith(".zip") && asset.name.contains("MMC")) {
                return asset;
            }
        }
        return null;
    }

    public static class Asset {

        public String name;
        @SerializedName("browser_download_url")
        public String downloadUrl;
        public long size;

    }

}

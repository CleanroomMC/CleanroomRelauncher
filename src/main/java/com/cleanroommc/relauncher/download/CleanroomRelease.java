package com.cleanroommc.relauncher.download;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.util.CacheUtils;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static com.cleanroommc.relauncher.CleanroomRelauncher.CONFIG;

public class CleanroomRelease {

    private static final Path CACHE_FILE = CleanroomRelauncher.CACHE_DIR.resolve("releases.json");
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 15_000;
    private static final String USER_AGENT = "Mozilla/5.0 CleanroomRelauncher/1.0";

    public static List<CleanroomRelease> queryAll() throws IOException {
        long ttlM = Duration.ofHours(1).toMillis(); // TODO: configurable, this is temp
        if (Files.exists(CACHE_FILE)) {
            CleanroomRelauncher.LOGGER.info("Loading releases from cached json.");
            try {
                long fileModifiedM = Files.getLastModifiedTime(CACHE_FILE).toMillis();
                long nowM = System.currentTimeMillis();
                long diffM = nowM - fileModifiedM;
                boolean cacheIsFresh = diffM < ttlM;
                if (!CONFIG.getFetchUpdatesEnabled() || cacheIsFresh) {
                    return fetchReleasesFromCache(CACHE_FILE);
                }
            } catch (Throwable t) {
                Files.delete(CACHE_FILE);
                CleanroomRelauncher.LOGGER.error("Unable to read cached releases.json, attempting to connect to GitHub and rebuild.", t);
            }
        } else {
            CleanroomRelauncher.LOGGER.info("No cache found, fetching releases...");
        }
        List<CleanroomRelease> releases = fetchReleasesFromGithub();

        // After fetching releases, save them to the cache
        saveReleasesToCache(CACHE_FILE, releases);
        return releases;
    }

    private static List<CleanroomRelease> fetchReleasesFromGithub() throws IOException {
        try {
            URL url = new URL("https://api.github.com/repos/CleanroomMC/Cleanroom/releases");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setInstanceFollowRedirects(false);

            if (connection.getResponseCode() != 200) {
                throw new IOException("Failed to fetch releases: HTTP error code " + connection.getResponseCode());
            }

            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                return Arrays.asList(CleanroomRelauncher.GSON.fromJson(reader, CleanroomRelease[].class));
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
     * @throws IOException if any occur during reading and deserializing releaseFile
     */
    private static List<CleanroomRelease> fetchReleasesFromCache(Path releaseFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(releaseFile)) {
            return Arrays.asList(CleanroomRelauncher.GSON.fromJson(reader, CleanroomRelease[].class));
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
        try {
            Files.createDirectories(releaseFile.getParent());
            try (Writer writer = Files.newBufferedWriter(releaseFile)) {
                CleanroomRelauncher.GSON.toJson(releases, writer);
                CleanroomRelauncher.LOGGER.info("Saved {} releases to cache.", releases.size());
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to save releases to cache.", e);
        }
    }

    public String name;
    @SerializedName("tag_name")
    public String tagName;
    @SerializedName("target_commitish")
    public String commitHash;
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
        Asset fallback = null;
        for (Asset asset : this.assets) {
            String lowerName = asset.name.toLowerCase();
            if (!lowerName.endsWith(".zip")) {
                continue;
            }
            if (asset.name.contains("MMC")) {
                return asset;
            }
            if (fallback == null || lowerName.contains("cleanroom")) {
                fallback = asset;
            }
        }
        return fallback;
    }

    public static class Asset {

        public String name;
        @SerializedName("browser_download_url")
        public String downloadUrl;
        public String digest;
        public long size;

        public CacheUtils.HashAlgorithm getDigestAlgorithm() {
            if (this.digest == null) {
                return null;
            }
            int separator = this.digest.indexOf(':');
            if (separator < 0) {
                return null;
            }
            return CacheUtils.HashAlgorithm.fromName(this.digest.substring(0, separator));
        }

        public String getDigestHash() {
            if (this.digest == null) {
                return null;
            }
            int separator = this.digest.indexOf(':');
            if (separator < 0 || separator == this.digest.length() - 1) {
                return null;
            }
            return this.digest.substring(separator + 1).trim();
        }

    }

}

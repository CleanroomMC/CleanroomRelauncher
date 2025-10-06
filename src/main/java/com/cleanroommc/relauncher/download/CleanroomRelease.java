package com.cleanroommc.relauncher.download;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.google.gson.annotations.SerializedName;
import com.google.gson.*;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Lists;
import java.util.Objects;

public class CleanroomRelease {

    public static final Path CACHE_FILE = CleanroomRelauncher.CACHE_DIR.resolve("releases.json");

    public static List<CleanroomRelease> queryAll() throws IOException {
        long ttlM = Duration.ofHours(1).toMillis(); // TODO: configurable, this is temp
        if (Files.exists(CACHE_FILE)) {
            CleanroomRelauncher.LOGGER.info("Loading releases from cached json.");
            try {
                long fileModifiedM = Files.getLastModifiedTime(CACHE_FILE).toMillis();
                long nowM = System.currentTimeMillis();
                long diffM = nowM - fileModifiedM;
                if (diffM < ttlM) {
                    return new ArrayList<>(fetchReleasesFromCache(CACHE_FILE));
                }
            } catch (Throwable t) {
                Files.delete(CACHE_FILE);
                CleanroomRelauncher.LOGGER.error("Unable to read cached releases.json, attempting to connect to GitHub and rebuild.", t);
            }
        } else {
            CleanroomRelauncher.LOGGER.info("No cache found, fetching releases...");
        }
        List<CleanroomRelease> releases =  new ArrayList<>(fetchReleasesFromGithub());

        // After fetching releases, save them to the cache
        saveReleasesToCache(CACHE_FILE, releases);
        return releases;
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
    public static void saveReleasesToCache(Path releaseFile, List<CleanroomRelease> releases) {
        releaseFile.toFile().getParentFile().mkdirs();
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

        public Asset(){}

        public Asset(String name, String url, long size) {
            this.name = name;
            this.downloadUrl = url;
            this.size = size;
        }

        public Asset(File file) {
            try {
                this.name = file.getName();
                this.downloadUrl = file.toURI().toURL().toString();
                this.size = file.length();
            } catch (Throwable e) {
                throw new RuntimeException("Unable to create a asset from a file.", e);
            }
        }

    }

    public static class Snapshot extends CleanroomRelease {
        private static final Path SNAOSHOT_CACHE = CleanroomRelauncher.CACHE_DIR.resolve("snapshots/");
        // MMC.zip, or installer.jar
        private File artifact;
        
        private Snapshot(File artifact) {
        
            try{
                Path sourcePath = artifact.toPath();
                Path targetPath = Paths.get(SNAOSHOT_CACHE.toString(), artifact.getName());
                Files.createDirectories(targetPath.getParent());
                if (!Files.exists(targetPath)) {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                String version = getVersion(artifact);
                this.name = version
                this.tagName = version
                Asset ass = new Asset(artifact);
                ass.downloadUrl = targetPath.toUri().toURL().toString();
                assets.add(ass);
                this.assets = Lists.asList(ass);
                
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        public static Snapshot of(File file) {
            return new Snapshot(file);
        }

        private static String getVersion(File file) {
            Path sourcePath = file.toPath();
            String version = getVersionFromMMC(sourcePath);
            if (version == null) {
                version = getVersionFromInstaller(sourcePath);
                if (version == null) {
                    return file.getName();
                } else return version;
            } else return version;
        }

        private static String getVersionFromInstaller(Path file) {
            try (var fs = FileSystems.newFileSystem(zipFilePath, (ClassLoader) null)) {
                Path targetPath = fs.getPath("version.json");
                if (Files.exists(targetPath)) {
                    try (InputStream is = Files.newInputStream(targetPath);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        return new JsonParser().parse(reader).getAsJsonObject().get("id").getAsString();
                    }
                }
            } cache(Throwable ignored) {}
            return null;
        }

        private static String getVersionFromMMC(Path file) {
            try (var fs = FileSystems.newFileSystem(zipFilePath, (ClassLoader) null)) {
                Path targetPath = fs.getPath("mmc-pack.json");
                if (Files.exists(targetPath)) {
                    try (InputStream is = Files.newInputStream(targetPath);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        for(JsonElement element : new JsonParser().parse(reader).getAsJsonObject().get("components").getAsJsonArray()) {
                            JsonObject job = element.getAsJsonObject();
                            if("Cleanroom".equals(job.get("cachedName").getAsString())) {
                                return job.get("version").getAsString();
                            }
                        }
                    }
                }
            } cache(Throwable ignored) {}
            return null;
        }
    }

}

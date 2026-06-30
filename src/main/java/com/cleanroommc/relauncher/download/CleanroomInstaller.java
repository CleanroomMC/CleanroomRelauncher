package com.cleanroommc.relauncher.download;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.download.cache.CleanroomCache;
import com.cleanroommc.relauncher.util.CacheUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.cleanroommc.relauncher.util.CacheUtils.deleteFile;

public class CleanroomInstaller implements CleanroomZipArtifact {

    public static CleanroomInstaller of(String version, Path location) {
        return new CleanroomInstaller(version, location);
    }

    private final String version;
    private final Path location;

    private CleanroomInstaller(String version, Path location) {
        this.version = version;
        this.location = location;
    }

    @Override
    public void install(String url, String expectedHash, CacheUtils.HashAlgorithm algo) {
        try{
            if (!Files.exists(this.location) || CacheUtils.isFileCorrupt(this.location.toFile(), expectedHash, algo)) {
                GlobalDownloader.INSTANCE.immediatelyFrom(url, this.location.toFile(), expectedHash, algo);
            }
        } catch (IOException e){
            CleanroomRelauncher.LOGGER.error("Error during installation", e);
            deleteFile(this.location.toFile());
        }
    }

    @Override
    public void extract(CleanroomCache cache) throws IOException {
        try (FileSystem jar = FileSystems.newFileSystem(this.location, null)) {
            Files.copy(jar.getPath("/version.json"), cache.getVersionJson());
            Files.copy(jar.getPath("/maven/com/cleanroommc/cleanroom/" + this.version + "/cleanroom-" + this.version + ".jar"), cache.getUniversalJar());
        }
    }

}

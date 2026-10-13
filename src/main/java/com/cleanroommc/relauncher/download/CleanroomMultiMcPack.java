package com.cleanroommc.relauncher.download;

import com.cleanroommc.relauncher.download.cache.CleanroomCache;
import com.cleanroommc.relauncher.util.CacheUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Optional;
import java.util.stream.Stream;

@Deprecated
public class CleanroomMultiMcPack implements CleanroomZipArtifact {

    public static CleanroomMultiMcPack of(String version, Path location) {
        return new CleanroomMultiMcPack(version, location);
    }

    private final String version;
    private final Path location;

    private CleanroomMultiMcPack(String version, Path location) {
        this.version = version;
        this.location = location;
    }

    @Override
    public void install(String url, String expectedHash, CacheUtils.HashAlgorithm algo) throws IOException {
        if (CacheUtils.isFileCorrupt(this.location.toFile(), expectedHash, algo)) {
            GlobalDownloader.INSTANCE.immediatelyFrom(url, this.location.toFile(), expectedHash, algo);
        }
    }

    @Override
    public void extract(CleanroomCache cache) throws IOException {
        try (FileSystem jar = FileSystems.newFileSystem(this.location, null)) {
            Path minecraftJson = jar.getPath("/patches/net.minecraft.json");
            if (Files.exists(minecraftJson)) {
                Files.copy(minecraftJson, cache.getMinecraftJson(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                try (InputStream bundledMinecraftJson = CleanroomMultiMcPack.class.getResourceAsStream("/net.minecraft.json")) {
                    Files.copy(bundledMinecraftJson, cache.getMinecraftJson(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.copy(jar.getPath("/patches/net.minecraftforge.json"), cache.getForgeJson(), StandardCopyOption.REPLACE_EXISTING);

            Path lwjglJson = jar.getPath("/patches/org.lwjgl3.json");
            if (!Files.exists(lwjglJson)) {
                lwjglJson = jar.getPath("/patches/org.lwjgl.json");
            }
            Files.copy(lwjglJson, cache.getLwjglVersionJson(), StandardCopyOption.REPLACE_EXISTING);

            Path libs = jar.getPath("/libraries/");
            if (Files.exists(libs)) {
                try (Stream<Path> stream = Files.walk(libs)) {
                    Optional<Path> embeddedUniversalJar = stream.filter(path -> path.toString().endsWith("-universal.jar")).findFirst();
                    if (embeddedUniversalJar.isPresent()) {
                        Files.copy(embeddedUniversalJar.get(), cache.getUniversalJar(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

}

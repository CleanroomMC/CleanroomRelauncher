package com.cleanroommc.relauncher.download;

import com.cleanroommc.relauncher.download.cache.CleanroomCache;
import com.cleanroommc.relauncher.util.CacheUtils;

import java.io.IOException;
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
        if (!Files.exists(this.location) || CacheUtils.isFileCorrupt(this.location.toFile(), expectedHash, algo)) {
            GlobalDownloader.INSTANCE.immediatelyFrom(url, this.location.toFile(), expectedHash, algo);
        }
    }

    @Override
    public void extract(CleanroomCache cache) throws IOException {
        try (FileSystem jar = FileSystems.newFileSystem(this.location, null)) {
            Files.copy(jar.getPath("/patches/net.minecraft.json"), cache.getMinecraftJson(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(jar.getPath("/patches/net.minecraftforge.json"), cache.getForgeJson(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(jar.getPath("/patches/org.lwjgl3.json"), cache.getLwjglVersionJson(), StandardCopyOption.REPLACE_EXISTING);

            Path libs = jar.getPath("/libraries/");
            if (Files.exists(libs)) {
                try (Stream<Path> stream = Files.walk(libs)) {
                    Optional<Path> embeddedUniversalJar = stream.filter(Files::isRegularFile).findFirst();
                    if (embeddedUniversalJar.isPresent()) {
                        Files.copy(embeddedUniversalJar.get(), cache.getUniversalJar(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

}

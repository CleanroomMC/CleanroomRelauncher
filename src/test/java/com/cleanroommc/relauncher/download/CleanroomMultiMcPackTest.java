package com.cleanroommc.relauncher.download;

import com.cleanroommc.relauncher.download.cache.CleanroomCache;
import com.cleanroommc.relauncher.download.schema.Version;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CleanroomMultiMcPackTest {

    private static final String LWJGL_JSON = "{\"libraries\":["
            + "{\"name\":\"org.lwjgl:lwjgl:3.4.3\",\"downloads\":{\"artifact\":{"
            + "\"url\":\"https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3.jar\"}}},"
            + "{\"name\":\"org.lwjgl:lwjgl:3.4.3\",\"natives\":{\"linux\":\"natives-linux\"},\"downloads\":{\"classifiers\":{\"natives-linux\":{"
            + "\"url\":\"https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-linux.jar\"}}}}"
            + "]}";

    @TempDir
    static Path minecraftHome;

    @BeforeAll
    static void setMinecraftHome() {
        Launch.minecraftHome = minecraftHome.toFile();
    }

    @Test
    @SuppressWarnings("deprecation")
    void extractsPackWithoutMinecraftPatch(@TempDir Path directory) throws IOException {
        Path pack = directory.resolve("mmc.zip");
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + pack.toUri()), Collections.singletonMap("create", "true"))) {
            Files.createDirectories(zip.getPath("/patches"));
            Files.createDirectories(zip.getPath("/libraries"));
            Files.write(zip.getPath("/patches/net.minecraftforge.json"), "{\"libraries\":[]}".getBytes(StandardCharsets.UTF_8));
            Files.write(zip.getPath("/patches/org.lwjgl.json"), LWJGL_JSON.getBytes(StandardCharsets.UTF_8));
            Files.write(zip.getPath("/libraries/lwjgl-999999.0-empty.jar"), new byte[0]);
        }

        CleanroomRelease release = new CleanroomRelease();
        release.tagName = "0.7.1-alpha";
        CleanroomCache cache = CleanroomCache.of(release);
        Files.createDirectories(cache.getForgeJson().getParent());

        CleanroomMultiMcPack.of(release.tagName, pack).extract(cache);

        assertNotNull(Version.parse(cache.getMinecraftJson()).mainJar);
        assertFalse(Files.exists(cache.getUniversalJar()));

        Version lwjgl = Version.parse(cache.getLwjglVersionJson());
        Version.Library natives = lwjgl.libraries.get(1);
        assertNull(natives.artifact());
        assertEquals("org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3.jar", lwjgl.libraries.get(0).artifact().getPath(natives.name));
        assertEquals("org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-linux.jar", natives.downloads.classifier("natives-linux").getPath(natives.name));
    }

}

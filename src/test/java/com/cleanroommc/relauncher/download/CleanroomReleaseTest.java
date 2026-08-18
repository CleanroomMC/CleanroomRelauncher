package com.cleanroommc.relauncher.download;

import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CleanroomReleaseTest {

    @TempDir
    static Path minecraftHome;

    @BeforeAll
    static void setMinecraftHome() {
        Launch.minecraftHome = minecraftHome.toFile();
    }

    @Test
    @SuppressWarnings("deprecation")
    void parsesMavenVersionsNewestFirstWithDownloadAssets() throws IOException {
        String metadata = "<?xml version=\"1.0\"?>"
                + "<metadata><versioning><versions>"
                + "<version>0.3.19-alpha</version>"
                + "<version>0.5.17-alpha</version>"
                + "<version>0.6.0-alpha</version>"
                + "</versions></versioning></metadata>";

        List<CleanroomRelease> releases = CleanroomRelease.parseMavenReleases(new StringReader(metadata));

        assertEquals(2, releases.size());
        CleanroomRelease latest = releases.get(0);
        assertEquals("0.6.0-alpha", latest.name);
        assertEquals("0.6.0-alpha", latest.tagName);
        assertEquals("https://repo.cleanroommc.com/releases/com/cleanroommc/cleanroom/0.6.0-alpha/cleanroom-0.6.0-alpha.zip",
                latest.getMultiMcPackArtifact().downloadUrl);
        assertEquals("cleanroom-0.6.0-alpha-installer.jar", latest.getInstallerArtifact().name);
        assertNull(latest.getMultiMcPackArtifact().digest);
    }

    @Test
    void rejectsInvalidMavenMetadata() {
        assertThrows(IOException.class, () -> CleanroomRelease.parseMavenReleases(new StringReader("<metadata>")));
    }

}

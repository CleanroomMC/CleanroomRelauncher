package com.cleanroommc.relauncher.download.cache;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.download.CleanroomMultiMcPack;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import com.cleanroommc.relauncher.download.GlobalDownloader;
import com.cleanroommc.relauncher.download.schema.Version;
import com.cleanroommc.relauncher.gui.LoadingGUI;
import com.cleanroommc.relauncher.util.CacheUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CleanroomCache {

    public static CleanroomCache of(CleanroomRelease release) {
        return new CleanroomCache(release);
    }

    private final Path directory;
    private final CleanroomRelease release;
    private final String version;

    private CleanroomCache(CleanroomRelease release) {
        this.directory = CleanroomRelauncher.CACHE_DIR.resolve(release.tagName);
        this.release = release;
        this.version = release.tagName;
    }

    public List<Version> download() throws IOException {
        LoadingGUI loading = new LoadingGUI();
        loading.enableProgress();
        loading.updateStatus("Downloading Cleanroom files and libraries...");

        if (!Files.isDirectory(this.directory)) {
            Files.createDirectories(this.directory);
        }

        Path multiMcPackZip = this.getMultiMcPackZip();
        Path lwjglJson = this.getLwjglVersionJson();
        Path forgeJson = this.getForgeJson();
        Path minecraftJson = this.getMinecraftJson();

        // Path installerJar = this.getInstallerJar();
        Path universalJar = this.getUniversalJar();
        // Path versionJson = this.getVersionJson();

        Path librariesDirectory = this.getLibrariesDirectory();
        Path nativesDirectory = this.getNativesDirectory();

        CleanroomMultiMcPack multiMcPack = CleanroomMultiMcPack.of(this.version, multiMcPackZip);
        // CleanroomInstaller installer = CleanroomInstaller.of(this.version, installerJar);

        CleanroomRelease.Asset mmcPackArtifact = this.release.getMultiMcPackArtifact();
        multiMcPack.install(mmcPackArtifact.downloadUrl, mmcPackArtifact.getDigestHash(), mmcPackArtifact.getDigestAlgorithm());

        if (!Files.exists(lwjglJson) || !Files.exists(forgeJson) || !Files.exists(minecraftJson) || !Files.exists(universalJar)) {
            multiMcPack.extract(this);
        }

        /*
        if (!Files.exists(universalJar) || !Files.exists(versionJson)) {
            installer.extract(this);
        }
         */

        List<Version> versions = new ArrayList<>();

        Version forgeJsonVersion = Version.parse(forgeJson);
        forgeJsonVersion.libraryPaths.add(this.getUniversalJar().toAbsolutePath().toString());

        Version minecraftJsonVersion = Version.parse(minecraftJson);
        Version lwjglJsonVersion = Version.parse(lwjglJson);

        versions.add(forgeJsonVersion);
        versions.add(minecraftJsonVersion);
        versions.add(lwjglJsonVersion);

        for (Version version : versions) {
            version.downloadLibraries(librariesDirectory);
        }
        loading.show();
        GlobalDownloader.INSTANCE.blockUntilFinished(loading);

        for (Version version : versions) {
            version.extractNatives(librariesDirectory, nativesDirectory);
        }
        loading.close();
        loading.updateStatus("Initialising..");
        // return version;
        return versions;
    }

    public Path getInstallerJar() {
        return this.directory.resolve("installer.jar");
    }

    public Path getUniversalJar() {
        return this.directory.resolve("universal.jar");
    }

    public Path getLibrariesDirectory() {
        return CleanroomRelauncher.CACHE_DIR.resolve("libraries/");
    }

    public Path getNativesDirectory() {
        return CleanroomRelauncher.CACHE_DIR.resolve("natives/");
    }

    public Path getVersionJson() {
        return this.directory.resolve("version.json");
    }

    @Deprecated
    public Path getMultiMcPackZip() {
        return this.directory.resolve("mmc.zip");
    }

    @Deprecated
    public Path getLwjglVersionJson() {
        return this.directory.resolve("org.lwjgl3.json");
    }

    @Deprecated
    public Path getForgeJson() {
        return this.directory.resolve("net.minecraftforge.json");
    }

    @Deprecated
    public Path getMinecraftJson() {
        return this.directory.resolve("net.minecraft.json");
    }

}

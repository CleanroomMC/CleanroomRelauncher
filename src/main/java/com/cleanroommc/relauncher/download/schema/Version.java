package com.cleanroommc.relauncher.download.schema;

import com.cleanroommc.platformutils.OperatingSystem;
import com.cleanroommc.platformutils.Platform;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.download.GlobalDownloader;
import com.cleanroommc.relauncher.util.CacheUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Version {

    private static final Map<OperatingSystem, String> OS_NAMES = new HashMap<>();

    static {
        OS_NAMES.put(OperatingSystem.WINDOWS, "windows");
        OS_NAMES.put(OperatingSystem.MAC_OS, "osx");
        OS_NAMES.put(OperatingSystem.LINUX, "linux");
    }

    public static Version parse(Path path) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path))) {
            return CleanroomRelauncher.GSON.fromJson(reader, Version.class);
        } catch (IOException e) {
            throw new IOException("Unable to parse version.json", e);
        }
    }

    // public Object arguments;
    public String minecraftArguments;
    public AssetIndex assetIndex;
    public String assets;
    public int complianceLevel;
    public Map<String, Download> downloads;
    public String id;
    public Library mainJar;
    public List<Library> libraries;
    public Object logging;
    public String mainClass;
    public int minimumLauncherVersion;
    public String releaseTime;
    public String time;
    public String type;

    public transient List<String> libraryPaths = new ArrayList<>();
    public transient List<String> nativesPaths = new ArrayList<>();

    // TODO: multithread
    public void downloadLibraries(Path librariesDirectory) {
        if (mainJar != null) {
            libraries.add(mainJar); // Fixme
        }
        for (Version.Library library : libraries) {
            if (library.downloads == null) {
                continue; // Locally-zipped artifact
            }
            Path libraryJar = librariesDirectory.resolve(library.downloads.artifact.getPath(library.name));
            try{
                if (!Files.exists(libraryJar) || CacheUtils.isFileCorrupt(libraryJar.toFile(), library.downloads.artifact.sha1, CacheUtils.HashAlgorithm.SHA1)) {
                    GlobalDownloader.INSTANCE.from(library.downloads.artifact.url, libraryJar.toFile(), library.downloads.artifact.sha1, CacheUtils.HashAlgorithm.SHA1);
                }
            } catch (IOException e){
                CleanroomRelauncher.LOGGER.error(library.downloads.artifact.url, e);
            }
            Download nativeArtifact = library.getNative(Platform.current());
            if (nativeArtifact != null) {
                Path nativesJar = librariesDirectory.resolve(nativeArtifact.getPath(library.name));
                try{
                    if (!Files.exists(nativesJar) || CacheUtils.isFileCorrupt(nativesJar.toFile(), nativeArtifact.sha1, CacheUtils.HashAlgorithm.SHA1)) {
                        GlobalDownloader.INSTANCE.from(nativeArtifact.url, nativesJar.toFile(), nativeArtifact.sha1, CacheUtils.HashAlgorithm.SHA1);
                    }
                } catch (IOException e){
                    CleanroomRelauncher.LOGGER.error(nativeArtifact.url, e);
                }
            }
            libraryPaths.add(libraryJar.toAbsolutePath().toString());
        }
    }

    public void extractNatives(Path librariesDirectory, Path nativesDirectory) {
        for (Version.Library library : libraries) {
            Download nativeArtifact = library.getNative(Platform.current());
            if (nativeArtifact != null) {
                String relative = nativeArtifact.getPath(library.name);
                Path jarPath = librariesDirectory.resolve(relative);
                Path nativesPath = nativesDirectory.resolve(relative.substring(0, relative.lastIndexOf('.')));
                nativesPaths.add(nativesPath.toAbsolutePath().toString());
                try (FileSystem jarFs = FileSystems.newFileSystem(jarPath, null)) {
                    try (Stream<Path> walk = Files.walk(jarFs.getPath("/"))) {
                        walk.filter(path -> !path.startsWith("/META-INF/"))
                                .filter(Files::isRegularFile)
                                .forEach(path -> {
                                    String pathString = path.toString();
                                    if (pathString.startsWith("/")) {
                                        pathString = pathString.substring(1);
                                    }
                                    Path nativesRelativePath = nativesPath.resolve(pathString);
                                    try {
                                        if (!Files.exists(nativesRelativePath)) {
                                            Files.createDirectories(nativesRelativePath.getParent());
                                            Files.copy(path, nativesRelativePath);
                                        }
                                    } catch (IOException e) {
                                        throw new RuntimeException(String.format("Unable to unzip and copy file %s to %s", path, nativesRelativePath), e);
                                    }
                                });
                    }
                } catch (IOException e) {
                    throw new RuntimeException(String.format("Unable to extract from jar %s for its natives", jarPath), e);
                }
            }
        }
    }

    public class AssetIndex {

        public String id;
        public long totalSize;
        public String path;
        public String sha1;
        public long size;
        public String url;

    }

    public class Library {

        public Downloads downloads;
        public String name;
        public Map<String, String> natives;
        public List<Rule> rules;
        public Object extract;

        @Deprecated
        public Download getNative(Platform platform) {
            if (isMainValidForOS(platform)) {
                if (isValidForOS(platform) && classifierForOS(platform) != null) {
                    return classifierForOS(platform);
                }
                return downloads.artifact;
            } else if (isValidForOS(platform)) {
                return classifierForOS(platform);
            }
            return null;
        }

        public boolean isValidForOS(Platform platform) {
            // Fixme: No rules allow everything. (That's... how it is supposed to be)
            if (rules == null) {
                return true;
            }
            boolean valid = false;
            for (Rule rule : rules) {
                if (rule.appliesToOS(platform)) {
                    valid = rule.isAllowed();
                }
            }
            return valid;
        }

        @Deprecated
        public boolean isMainValidForOS(Platform platform) {
            if (rules == null) {
                return false;
            }
            if (platform.isWindows() && platform.is64Bit()) {
                if (name.endsWith("x86")) { // Fixme I am going to fucking kill myself
                    return false;
                }
            }
            boolean valid = false;
            for (Rule rule : rules) {
                if (rule.appliesToOS(platform)) {
                    valid = rule.isAllowed();
                } else {
                    valid = false; // TODO: this shouldnt be needed, should be implicit in the json
                }
            }
            return valid;
        }

        public boolean hasNatives() {
            return natives != null;
        }

        public boolean hasNativesForOS(Platform platform) {
            if (!hasNatives()) {
                return false;
            }
            if (classifierForOS(platform) == null) {
                return false;
            }
            return isValidForOS(platform);
        }

        public Download classifierForOS(Platform platform) {
            if (natives == null) {
                return null;
            }
            String classifier = natives.get(OS_NAMES.get(platform.operatingSystem()));
            if (classifier == null) {
                return null;
            }
            // TODO: rethink this, especially the x86 variant
            if (platform.isArm()) {
                String bit = platform.is64Bit() ? "64" : "32";
                final Download armNative = downloads.classifier(classifier + "-arm" + bit);
                if (armNative != null) {
                    return armNative;
                }
            } else if (platform.isWindows() && !platform.is64Bit()) {
                final Download armNative = downloads.classifier(classifier + "-x86");
                if (armNative != null) {
                    return armNative;
                }
            }
            return downloads.classifier(classifier);
        }

        public Download artifact() {
            if (downloads == null) {
                return null;
            }
            return downloads.artifact;
        }

    }

    public class Downloads {

        public Download artifact;
        public Map<String, Download> classifiers;

        public Download classifier(String os) {
            return classifiers.get(os);
        }

    }

    public class Rule {

        public String action;
        public OS os;

        public boolean appliesToOS(Platform platform) {
            return os == null || os.isValidForOS(platform);
        }

        public boolean isAllowed() {
            return action.equals("allow");
        }

    }

    public class OS {

        public String name;

        public boolean isValidForOS(Platform platform) {
            if (name == null) {
                return true;
            }
            if (name.equalsIgnoreCase(OS_NAMES.get(platform.operatingSystem()))) {
                return true;
            }
            // Fixme
            int classifierIndex = name.indexOf('-');
            if (classifierIndex > -1) {
                String arch = name.substring(classifierIndex + 1);
                String os = name.substring(0, classifierIndex);
                if (os.equalsIgnoreCase(OS_NAMES.get(platform.operatingSystem()))) {
                    if (platform.isArm()) {
                        String bit = platform.is64Bit() ? "64" : "32";
                        return arch.equals("arm" + bit);
                    } else if (platform.isWindows() && !platform.is64Bit()) {
                        return arch.equals("x86");
                    }
                }
            }
            return false;
        }

    }

    public class Download {

        public String path;
        public String sha1;
        public long size;
        public String url;

        @Deprecated
        public String getPath(String name) {
            if (path == null) {
                // Fixme: URLs match, so its fine for now
                if (url.startsWith("https://libraries.minecraft.net/")) {
                    path = url.substring("https://libraries.minecraft.net/".length());
                } else {
                    String[] splits = name.split(":");
                    String groupId = splits[0];
                    String artifactId = splits[1];
                    String version = splits[2];
                    String groupPath = groupId.replace('.', '/');
                    path = String.format("%s/%s/%s/%s-%s.jar", groupPath, artifactId, version, artifactId, version);
                }
            }
            return path;
        }

    }

}
package com.cleanroommc.relauncher;

import com.cleanroommc.javautils.JavaUtils;
import com.cleanroommc.javautils.api.JavaVersion;
import com.cleanroommc.relauncher.config.RelauncherConfiguration;
import com.cleanroommc.relauncher.download.cache.CleanroomCache;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import com.cleanroommc.relauncher.download.schema.Version;
import com.cleanroommc.relauncher.gui.RelauncherGUI;
import com.google.gson.Gson;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.cleanroomrelauncher.ExitVMBypass;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CleanroomRelauncher {

    public static final Logger LOGGER = LogManager.getLogger("CleanroomRelauncher");
    public static final Gson GSON = new Gson();
    public static final Path CACHE_DIR = Paths.get(System.getProperty("user.home"), ".cleanroom", "relauncher");

    public static RelauncherConfiguration CONFIG = RelauncherConfiguration.read();

    public CleanroomRelauncher() { }

    private static boolean isCleanroom() {
        try {
            Class.forName("com.cleanroommc.boot.Main");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    void run() {
        if (isCleanroom()) {
            LOGGER.info("Cleanroom detected. No need to relaunch!");
            return;
        }

        // TODO: compartmentalize this also
        if (JavaVersion.parseOrThrow(System.getProperty("java.version")).build() <= 101) {
            try (InputStream is = this.getClass().getResource("/cacerts").openStream()) {
                File cacertsCopy = File.createTempFile("cacerts", "");
                cacertsCopy.deleteOnExit();
                FileUtils.copyInputStreamToFile(is, cacertsCopy);
                System.setProperty("javax.net.ssl.trustStore", cacertsCopy.getAbsolutePath());
                CleanroomRelauncher.LOGGER.info("Successfully replaced CA Certs.");
            } catch (Exception e) {
                throw new RuntimeException("Unable to replace CA Certs!", e);
            }
        }

        List<CleanroomRelease> releases;
        CleanroomRelease latestRelease;
        try {
            releases = CleanroomRelease.queryAll();
            latestRelease = releases.get(0);

            // After retrieving releases, save them to releases.json
            saveReleasesToCache(CACHE_DIR.resolve("releases.json"), releases);
            LOGGER.info("Saved {} releases to cache.", releases.size());

        } catch (IOException e) {
            if (Files.exists(CACHE_DIR.resolve("releases.json"))) {

                LOGGER.info("Loading releases from cached releases.json");

                // Load releases from local cache if internet isn't available
                releases = loadReleasesFromCache(CACHE_DIR.resolve("releases.json"));
                latestRelease = releases.get(0); // Assume at least one release is available
            } else {
                throw new RuntimeException("Unable to query Cleanroom's releases and no cached releases found.", e);
            }
        }

        LOGGER.info("{} cleanroom releases were queried.", releases.size());

        CleanroomRelease selected = null;
        String selectedVersion = CONFIG.getCleanroomVersion();
        String notedLatestVersion = CONFIG.getLatestCleanroomVersion();
        String javaPath = CONFIG.getJavaExecutablePath();
        String javaArgs = CONFIG.getJavaArguments();
        boolean needsNotifyLatest = notedLatestVersion == null || !notedLatestVersion.equals(latestRelease.name);
        if (selectedVersion != null) {
            selected = releases.stream().filter(cr -> cr.name.equals(selectedVersion)).findFirst().orElse(null);
        }
        if (javaPath != null && !new File(javaPath).isFile()) {
            javaPath = null;
        }
//        if (javaArgs == null) {
//            javaArgs = String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments());
//        }
        if (selected == null || javaPath == null || needsNotifyLatest) {
            final CleanroomRelease fSelected = selected;
            final String fJavaPath = javaPath;
            final String fJavaArgs = javaArgs;
            RelauncherGUI gui = RelauncherGUI.show(releases, $ -> {
                $.selected = fSelected;
                $.javaPath = fJavaPath;
                $.javaArgs = fJavaArgs;
            });

            selected = gui.selected;
            javaPath = gui.javaPath;
            javaArgs = gui.javaArgs;

            CONFIG.setCleanroomVersion(selected.name);
            CONFIG.setLatestCleanroomVersion(latestRelease.name);
            CONFIG.setJavaExecutablePath(javaPath);
            CONFIG.setJavaArguments(javaArgs);

            CONFIG.save();
        }

        CleanroomCache releaseCache = CleanroomCache.of(selected);

        LOGGER.info("Preparing Cleanroom v{} and its libraries...", selected.name);
        List<Version> versions;
        try {
            versions = releaseCache.download(); // Blocking
        } catch (IOException e) {
            throw new RuntimeException("Unable to grab CleanroomVersion to relaunch.", e);
        }

        // TODO: compartmentalize this
        String wrapperClassPath;
        try {
            Path wrapperFile = CleanroomRelauncher.CACHE_DIR.resolve("wrapper/com/cleanroommc/relauncher/wrapper/RelaunchMainWrapperV2.class");
            Path wrapperDirectory = wrapperFile.getParent();
            if (!Files.exists(wrapperFile)) {
                Files.createDirectories(wrapperDirectory);
                try (Stream<Path> wrapperDirectoryStream = Files.walk(wrapperDirectory)) {
                    wrapperDirectoryStream.filter(Files::isRegularFile).map(Path::toFile).forEach(File::delete);
                }
                File relauncherJarFile = JavaUtils.jarLocationOf(CleanroomRelauncher.class);
                try (FileSystem containerFs = FileSystems.newFileSystem(relauncherJarFile.toPath(), null)) {
                    Path wrapperJarDirectory = containerFs.getPath("/wrapper/");
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(wrapperJarDirectory)) {
                        for (Path path : stream) {
                            Path to = wrapperFile.resolveSibling(path.getFileName().toString());
                            Files.copy(path, to);
                            CleanroomRelauncher.LOGGER.debug("Moved {} to {}", path.toAbsolutePath().toString(), to.toAbsolutePath().toString());
                        }
                    }
                }
            }
            wrapperClassPath = CleanroomRelauncher.CACHE_DIR.resolve("wrapper").toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Unable to extract RelaunchMainWrapper class to cache directory", e);
        }

        LOGGER.info("Preparing to relaunch Cleanroom v{}", selected.name);

        List<String> arguments = new ArrayList<>();
        arguments.add(javaPath);

        arguments.add("-cp");
        String libraryClassPath = versions.stream()
                .map(version -> version.libraryPaths)
                .flatMap(Collection::stream)
                .collect(Collectors.joining(File.pathSeparator));

        // Check if libraryClassPath is empty and log it for debugging
        if (libraryClassPath.isEmpty()) {
            LOGGER.warn("No libraries found in the classpath. Ensure that libraries are being downloaded.");
        }

        String fullClassPath = wrapperClassPath + File.pathSeparator + libraryClassPath;
        arguments.add(fullClassPath); // Ensure this is not empty

        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (!argument.startsWith("-Djava.library.path")) {
                arguments.add(argument);
            }
        }

        arguments.add("-Djava.library.path=" + versions.stream().map(version -> version.nativesPaths).flatMap(Collection::stream).collect(Collectors.joining(File.pathSeparator)));

        arguments.add("com.cleanroommc.relauncher.wrapper.RelaunchMainWrapperV2");

        for (Map.Entry<String, String> launchArgument : ((Map<String, String>) Launch.blackboard.get("launchArgs")).entrySet()) {
            arguments.add(launchArgument.getKey());
            arguments.add(launchArgument.getValue());
        }

        arguments.add("--tweakClass");
        arguments.add("net.minecraftforge.fml.common.launcher.FMLTweaker"); // Fixme, gather from Version?
        arguments.add("--mainClass");
        arguments.add(versions.get(0).mainClass);

        LOGGER.debug("Arguments:");
        for (String arg: arguments) {
            LOGGER.debug(arg);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(arguments);
        processBuilder.directory(null);
        processBuilder.inheritIO();

        try {
            Process process = processBuilder.start();

            int exitCode = process.waitFor();
            LOGGER.info("Process exited with code: {}", exitCode);
            ExitVMBypass.exit(exitCode);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
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
    private static List<CleanroomRelease> loadReleasesFromCache(Path releaseFile) {
        try (Reader reader = Files.newBufferedReader(releaseFile)) {
            return Arrays.asList(GSON.fromJson(reader, CleanroomRelease[].class));
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
            GSON.toJson(releases, writer);
        } catch (IOException e) {
            throw new RuntimeException("Unable to save releases to cache.", e);
        }
    }
}

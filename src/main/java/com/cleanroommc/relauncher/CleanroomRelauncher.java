package com.cleanroommc.relauncher;

import com.cleanroommc.javautils.JavaUtils;
import com.cleanroommc.javautils.api.JavaVersion;
import com.cleanroommc.relauncher.config.RelauncherConfiguration;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import com.cleanroommc.relauncher.download.cache.CleanroomCache;
import com.cleanroommc.relauncher.download.schema.Version;
import com.cleanroommc.relauncher.gui.RelauncherGUI;
import com.cleanroommc.relauncher.util.enums.ArgsEnum;
import com.cleanroommc.relauncher.util.enums.JavaTargetsEnum;
import com.cleanroommc.relauncher.util.enums.VendorsEnum;
import com.google.gson.Gson;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.cleanroomrelauncher.ExitVMBypass;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.ProcessIdUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cleanroommc.relauncher.download.JavaProvisioning.validateOrProvisionJava;

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

    private static void replaceCerts() {
        if (JavaVersion.parseOrThrow(System.getProperty("java.version")).build() <= 101) {
            try (InputStream is = CleanroomRelauncher.class.getResource("/cacerts").openStream()) {
                File cacertsCopy = File.createTempFile("cacerts", "");
                cacertsCopy.deleteOnExit();
                FileUtils.copyInputStreamToFile(is, cacertsCopy);
                System.setProperty("javax.net.ssl.trustStore", cacertsCopy.getAbsolutePath());
                CleanroomRelauncher.LOGGER.info("Successfully replaced CA Certs.");
            } catch (Exception e) {
                throw new RuntimeException("Unable to replace CA Certs!", e);
            }
        }
    }

    private static List<CleanroomRelease> releases() {
        try {
            return CleanroomRelease.queryAll();
        } catch (IOException e) {
            throw new RuntimeException("Unable to query Cleanroom's releases and no cached releases found.", e);
        }
    }

    private static List<Version> versions(CleanroomCache cache) {
        try {
            return cache.download(); // Blocking
        } catch (IOException e) {
            throw new RuntimeException("Unable to grab CleanroomVersion to relaunch.", e);
        }
    }

    private static String getOrExtract() {
        String manifestFile = "META-INF/MANIFEST.MF";
        String wrapperDirectory = "wrapper/com/cleanroommc/relauncher/wrapper";
        String wrapperFile = wrapperDirectory + "/RelaunchMainWrapper.class";

        File relauncherJarFile = JavaUtils.jarLocationOf(CleanroomRelauncher.class);

        try (FileSystem containerFs = FileSystems.newFileSystem(relauncherJarFile.toPath(), null)) {
            String originalHash;
            try (InputStream is = Files.newInputStream(containerFs.getPath(manifestFile))) {
                originalHash = new Manifest(is).getMainAttributes().getValue("WrapperHash");
            } catch (Throwable t) {
                throw new RuntimeException("Unable to read original hash of the wrapper class file", t);
            }

            Path cachedWrapperDirectory = CleanroomRelauncher.CACHE_DIR.resolve(wrapperDirectory);
            Path cachedWrapperFile = CleanroomRelauncher.CACHE_DIR.resolve(wrapperFile);

            boolean skip = false;

            if (Files.exists(cachedWrapperFile)) {
                try (InputStream is = Files.newInputStream(cachedWrapperFile)) {
                    String cachedHash = DigestUtils.md5Hex(is);
                    if (originalHash.equals(cachedHash)) {
                        CleanroomRelauncher.LOGGER.warn("Hashes matched, no need to copy from jar again.");
                        skip = true;
                    }
                } catch (Throwable t) {
                    CleanroomRelauncher.LOGGER.error("Unable to calculate MD5 hash to compare.", t);
                }
            }

            if (!skip) {
                if (Files.exists(cachedWrapperDirectory)) {
                    try (Stream<Path> stream = Files.walk(cachedWrapperDirectory)) {
                        stream.filter(Files::isRegularFile).map(Path::toFile).forEach(File::delete);
                    }
                } else {
                    Files.createDirectories(cachedWrapperDirectory);
                }
                Path wrapperJarDirectory = containerFs.getPath("/wrapper/");
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(wrapperJarDirectory)) {
                    for (Path path : stream) {
                        Path to = cachedWrapperFile.resolveSibling(path.getFileName().toString());
                        Files.copy(path, to);
                        CleanroomRelauncher.LOGGER.debug("Moved {} to {}", path.toAbsolutePath().toString(), to.toAbsolutePath().toString());
                    }
                }
            }

            return CleanroomRelauncher.CACHE_DIR.resolve("wrapper").toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Unable to extract relauncher's jar file", e);
        }
    }

    static void run() {
        if (isCleanroom()) {
            LOGGER.info("Cleanroom detected. No need to relaunch!");
            return;
        }

        replaceCerts();

        List<CleanroomRelease> releases = releases();
        CleanroomRelease latestRelease = releases.get(0);

        LOGGER.info("{} cleanroom releases were queried.", releases.size());

        CleanroomRelease selected = null;
        String selectedVersion = CONFIG.getCleanroomVersion();
        String notedLatestVersion = CONFIG.getLatestCleanroomVersion();
        String javaPath = CONFIG.getJavaExecutablePath();
        String javaArgs = CONFIG.getJavaArguments();
        JavaTargetsEnum javaTarget = CONFIG.getJavaTarget();
        VendorsEnum javaVendor = CONFIG.getJavaVendor();
        boolean autoSetup = CONFIG.getAutoSetup();
        boolean relauncherEnabled = CONFIG.getRelauncherEnabled();
        boolean needsNotifyLatest = notedLatestVersion == null || !notedLatestVersion.equals(latestRelease.name);
        if (selectedVersion != null) {
            selected = releases.stream().filter(cr -> cr.name.equals(selectedVersion)).findFirst().orElse(null);
        }
        if (javaPath != null && !new File(javaPath).isFile()) {
            javaPath = null;
        }
        if (javaTarget == null) {
            javaTarget = JavaTargetsEnum.J25;
        }
        if (javaVendor == null) {
            javaVendor = VendorsEnum.AZUL_ZULU;
        }
//        if (javaArgs == null) {
//            javaArgs = String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments());
//        }
        if (relauncherEnabled) {
            if (!autoSetup && (selected == null || javaPath == null || needsNotifyLatest)) {
                final String fJavaPath = javaPath;
                final String fJavaArgs = javaArgs;
                final VendorsEnum fJavaVendor = javaVendor;
                final JavaTargetsEnum fJavaTarget = javaTarget;
                final CleanroomRelease fSelected = selected;
                final boolean fAutoSetup = autoSetup;
                while(javaPath == null || Objects.equals(javaPath, "")){
                    RelauncherGUI gui = RelauncherGUI.show(releases, $ -> {
                        $.selected = fSelected;
                        $.javaPath = fJavaPath;
                        $.targetSelected = fJavaTarget;
                        $.vendorSelected = fJavaVendor;
                        $.javaArgs = fJavaArgs;
                        $.autoSetup = fAutoSetup;
                        $.shouldScale=(!isJvm8());
                    });

                    if (gui.selected != null) {
                        selected = gui.selected;
                    } else{
                        selected = latestRelease;
                    }
                    javaPath = gui.javaPath;
                    javaArgs = gui.javaArgs;
                    javaTarget = gui.targetSelected;
                    javaVendor = gui.vendorSelected;
                    autoSetup = gui.autoSetup;

                    javaPath = validateOrProvisionJava(javaPath, javaTarget, javaVendor);
                }

                CONFIG.setCleanroomVersion(selected.name);
                CONFIG.setLatestCleanroomVersion(latestRelease.name);
                CONFIG.setJavaExecutablePath(javaPath);
                CONFIG.setJavaArguments(javaArgs);
                CONFIG.setTargetVendor(javaVendor);
                CONFIG.setTargetJavaVersion(javaTarget);
                CONFIG.setAutoSetup(autoSetup);

                CONFIG.save();
            }
            if(autoSetup){
                if(needsNotifyLatest) {
                    selected = latestRelease;
                    CONFIG.setCleanroomVersion(selected.name);
                    CONFIG.setLatestCleanroomVersion(latestRelease.name);
                }
                javaPath = validateOrProvisionJava(javaPath, javaTarget, javaVendor);
                while(Objects.equals(javaPath, "")){
                    final String fJavaPath = javaPath;
                    final String fJavaArgs = javaArgs;
                    final VendorsEnum fJavaVendor = VendorsEnum.AZUL_ZULU;
                    final JavaTargetsEnum fJavaTarget = JavaTargetsEnum.J25;
                    final CleanroomRelease fSelected = selected;
                    final boolean fAutoSetup = autoSetup;
                    RelauncherGUI gui = RelauncherGUI.show(releases, $ -> {
                        $.selected = fSelected;
                        $.javaPath = fJavaPath;
                        $.targetSelected = fJavaTarget;
                        $.vendorSelected = fJavaVendor;
                        $.javaArgs = fJavaArgs;
                        $.autoSetup = fAutoSetup;
                        $.shouldScale=(isJvm8());
                    });

                    if (gui.selected != null) {
                        selected = gui.selected;
                    } else{
                        selected = latestRelease;
                    }
                    javaPath = gui.javaPath;
                    javaArgs = gui.javaArgs;
                    javaTarget = gui.targetSelected;
                    javaVendor = gui.vendorSelected;
                    autoSetup = gui.autoSetup;

                    javaPath = validateOrProvisionJava(javaPath, javaTarget, javaVendor);
                    if (!javaPath.isEmpty()){
                        CONFIG.setTargetJavaVersion(javaTarget);
                        CONFIG.setTargetVendor(javaVendor);
                    }
                }
                CONFIG.setJavaExecutablePath(javaPath);
                LOGGER.warn("Setting the rest");
                if (javaArgs == null || javaArgs.isEmpty()) {
                    StringBuilder argBuilder = new StringBuilder();
                    if (javaTarget.getInternalNameInt()< 25 ) {
                        argBuilder.append(ArgsEnum.UnlockExperimentalOptions.getArg()).append(" ");
                    }
                    argBuilder.append(ArgsEnum.CompactObjectHeaders.getArg()).append(" ");
                    javaArgs = argBuilder.toString();
                }
                CONFIG.setJavaArguments(javaArgs);
                if (javaTarget == null){
                    javaTarget=JavaTargetsEnum.J25;
                    CONFIG.setTargetJavaVersion(javaTarget);
                }
                if (javaVendor == null){
                    javaVendor=VendorsEnum.AZUL_ZULU;
                    CONFIG.setTargetVendor(javaVendor);
                }
                CONFIG.save();


            }

            CleanroomCache releaseCache = CleanroomCache.of(selected);

            LOGGER.info("Preparing Cleanroom v{} and its libraries...", selected.name);
            List<Version> versions = versions(releaseCache);

            String wrapperClassPath = getOrExtract();

            LOGGER.info("Preparing to relaunch Cleanroom v{}", selected.name);
            List<String> arguments = new ArrayList<>();
            arguments.add(javaPath);

            arguments.add("-cp");
            String libraryClassPath = versions.stream()
                    .map(version -> version.libraryPaths)
                    .flatMap(Collection::stream)
                    .collect(Collectors.joining(File.pathSeparator));

            String fullClassPath = wrapperClassPath + File.pathSeparator + libraryClassPath;
            arguments.add(fullClassPath); // Ensure this is not empty

            if (javaArgs != null && !javaArgs.isEmpty()) {
                Arrays.stream(javaArgs.split(" ")).map(String::trim).forEach(arguments::add);
            }

            for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                // if (!argument.startsWith("-Djava.library.path")) {
                if (argument.startsWith("-Xms") && arguments.stream().noneMatch(arg -> arg.startsWith("-Xms"))) {
                    arguments.add(argument);
                }
                if (argument.startsWith("-Xmx") && arguments.stream().noneMatch(arg -> arg.startsWith("-Xmx"))) {
                    arguments.add(argument);
                }
                // }
            }

            arguments.add("-Dcleanroom.relauncher.parent=" + ProcessIdUtil.getProcessId());
            arguments.add("-Dcleanroom.relauncher.mainClass=" + versions.get(0).mainClass);
            arguments.add("-Djava.library.path=" + versions.stream().map(version -> version.nativesPaths).flatMap(Collection::stream).collect(Collectors.joining(File.pathSeparator)));

            arguments.add("com.cleanroommc.relauncher.wrapper.RelaunchMainWrapper");

            // Forward any extra game launch arguments
            for (Map.Entry<String, String> launchArgument : ((Map<String, String>) Launch.blackboard.get("launchArgs")).entrySet()) {
                arguments.add(launchArgument.getKey());
                arguments.add(launchArgument.getValue());
            }

            arguments.add("--tweakClass");
            arguments.add("net.minecraftforge.fml.common.launcher.FMLTweaker"); // Fixme, gather from Version?

            LOGGER.debug("Relauncher arguments:");
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
    }
    public static boolean isJvm8(){
        // Detect current JVM
        int currentJavaMajorVersion = 8;
        try {
            String version = System.getProperty("java.version");
            if (version.startsWith("1.")) {
                currentJavaMajorVersion = Integer.parseInt(version.split("\\.")[1]);
            } else {
                currentJavaMajorVersion = Integer.parseInt(version.split("\\.")[0]);
            }
        } catch (Exception ignored) {}
        // J8 messes up scaling it seems
        return currentJavaMajorVersion == 8;
    }
    public static boolean isJvm8Oracle(){
        // Detect current JVM
        int currentJavaMajorVersion = 8;
        String vendor="";
        try {
            vendor = System.getProperty("java.vendor");
            String version = System.getProperty("java.version");
            if (version.startsWith("1.")) {
                currentJavaMajorVersion = Integer.parseInt(version.split("\\.")[1]);
            } else {
                currentJavaMajorVersion = Integer.parseInt(version.split("\\.")[0]);
            }
        } catch (Exception ignored) {}
        // J8 messes up scaling it seems
        boolean isOracle = vendor != null && vendor.toLowerCase(Locale.ENGLISH).contains("oracle");
        return currentJavaMajorVersion == 8 && isOracle;
    }
}

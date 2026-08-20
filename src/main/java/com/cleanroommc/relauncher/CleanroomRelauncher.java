package com.cleanroommc.relauncher;

import com.cleanroommc.javautils.JavaUtils;
import com.cleanroommc.javautils.api.JavaDistro;
import com.cleanroommc.javautils.api.JavaVersion;
import com.cleanroommc.relauncher.config.RelauncherConfiguration;
import com.cleanroommc.relauncher.download.CleanroomRelease;
import com.cleanroommc.relauncher.download.GlobalDownloader;
import com.cleanroommc.relauncher.download.cache.CleanroomCache;
import com.cleanroommc.relauncher.download.schema.Version;
import com.cleanroommc.relauncher.gui.RelauncherGUI;
import com.cleanroommc.relauncher.util.enums.ArgsEnum;
import com.google.gson.Gson;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.cleanroomrelauncher.ExitVMBypass;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.ProcessIdUtil;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import java.awt.Frame;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.beans.Introspector;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.ResponseCache;
import java.security.Provider;
import java.security.Security;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cleanroommc.relauncher.download.JavaProvisioning.validateOrProvisionJava;

public class CleanroomRelauncher {

    private static final int CHILD_READY_SIGNAL = 1;

    public static Logger LOGGER = LogManager.getLogger("CleanroomRelauncher");
    public static Gson GSON = new Gson();
    public static RelauncherConfiguration CONFIG = RelauncherConfiguration.read();
    public static Path CACHE_DIR = Paths.get(System.getProperty("user.home"), ".cleanroom", "relauncher");
    public static Path JAVA_PROVISION_DIR = Paths.get(System.getProperty("user.home"), ".cleanroom", "java");

    private static Path temporaryTrustStore;

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
                temporaryTrustStore = cacertsCopy.toPath();
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

        File relauncherJarFile;
        try {
            relauncherJarFile = JavaUtils.jarLocationOf(CleanroomRelauncher.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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

    private static RelauncherGUI showGUI(List<CleanroomRelease> releases, CleanroomRelease selected, String javaPath,
            String javaArgs, JavaVersion javaTarget, JavaDistro javaVendor, boolean autoSetup, boolean updateNotification) {
        return RelauncherGUI.show(releases, $ -> {
            $.selected           = selected;
            $.javaPath           = javaPath;
            $.targetSelected     = javaTarget;
            $.vendorSelected     = javaVendor;
            $.javaArgs           = javaArgs;
            $.autoSetup          = autoSetup;
            $.updateNotification = CONFIG.getFetchUpdatesEnabled() && updateNotification;
        });
    }

    public static void clearFolders() {
        if (CONFIG.getClearCleanroomFolderEnabled() || CONFIG.getClearJavaProvisionFolderEnabled()) {
            if (CONFIG.getClearCleanroomFolderEnabled()) {
                deleteFolder(CACHE_DIR);
                CONFIG.setClearCleanroomFolder(false);
            }
            if (CONFIG.getClearJavaProvisionFolderEnabled()) {
                deleteFolder(JAVA_PROVISION_DIR);
                CONFIG.setClearJavaProvisionFolder(false);
            }
            CONFIG.save();
        }
    }

    public static void deleteFolder(Path folder) {
        if (!Files.exists(folder)) return;

        try (Stream<Path> walker = Files.walk(folder)) {
            walker.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            LOGGER.error("Failed to delete: {},{}", path, e);
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Failed to walk folder: {},{}", folder, e);
        }
    }

    static void run() {
        Process process = prepareRelaunchedProcess();
        if (process == null) {
            return;
        }

        // Clear parent process related items
        clear(process);

        try {
            int exitCode = process.waitFor();
            ExitVMBypass.exit(exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static Process prepareRelaunchedProcess() {
        if (isCleanroom()) {
            LOGGER.info("Cleanroom detected. No need to relaunch!");
            return null;
        }

        replaceCerts();
        clearFolders();

        List<CleanroomRelease> releases = releases();
        if (releases.isEmpty()) {
            throw new IllegalStateException("No Cleanroom releases are available to relaunch with.");
        }
        CleanroomRelease latestRelease = releases.get(0);

        LOGGER.info("{} cleanroom releases were queried.", releases.size());

        CleanroomRelease selected = null;
        String selectedVersion = CONFIG.getCleanroomVersion();
        String notedLatestVersion = CONFIG.getLatestCleanroomVersion();
        String javaPath = CONFIG.getJavaExecutablePath();
        String javaArgs = CONFIG.getJavaArguments();
        JavaVersion javaTarget = CONFIG.getJavaTarget();
        JavaDistro javaVendor = CONFIG.getJavaVendor();
        boolean autoSetup = CONFIG.getAutoSetup();
        boolean relauncherEnabled = CONFIG.getRelauncherEnabled();
        boolean needsNotifyLatest = notedLatestVersion == null ||
                (!notedLatestVersion.equals(latestRelease.name) && (!Objects.equals(CONFIG.getCleanroomVersion(), latestRelease.name)));
        if (selectedVersion != null) {
            selected = releases.stream().filter(cr -> cr.name.equals(selectedVersion)).findFirst().orElse(null);
        }
        if (javaPath != null && !new File(javaPath).isFile()) {
            javaPath = null;
        }
        if (javaTarget == null) {
            javaTarget = JavaVersion.parseOrThrow(25);
        }
        if (javaVendor == null) {
            javaVendor = JavaDistro.ZULU;
        }
//        if (javaArgs == null) {
//            javaArgs = String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments());
//        }
        if (needsNotifyLatest) {
            CONFIG.setLatestCleanroomVersion(latestRelease.name);
            CONFIG.save();
        }
        if (relauncherEnabled) {
            if (!autoSetup && (selected == null || javaPath == null || needsNotifyLatest)) {
                while (javaPath == null || Objects.equals(javaPath, "")) {
                    RelauncherGUI gui = showGUI(releases, selected, javaPath, javaArgs, javaTarget, javaVendor, autoSetup, false);
                    if (gui.selected != null) {
                        selected = gui.selected;
                    } else {
                        selected = latestRelease;
                    }
                    javaPath = gui.javaPath;
                    javaArgs = gui.javaArgs;
                    javaTarget = gui.targetSelected;
                    javaVendor = gui.vendorSelected;
                    autoSetup = gui.autoSetup;
                    javaPath = validateOrProvisionJava(javaPath, javaTarget, javaVendor);
                }
                if (selected == null) {
                    selected = latestRelease;
                }
                CONFIG.setCleanroomVersion(selected.name);
                CONFIG.setLatestCleanroomVersion(latestRelease.name);
                CONFIG.setJavaExecutablePath(javaPath);
                CONFIG.setJavaArguments(javaArgs);
                CONFIG.setJavaSelectionMode(autoSetup, javaTarget, javaVendor);

                CONFIG.save();

                notedLatestVersion = CONFIG.getLatestCleanroomVersion();
                needsNotifyLatest = (notedLatestVersion == null || !notedLatestVersion.equals(latestRelease.name));
            }
            if (autoSetup) {
                if (needsNotifyLatest) {
                    RelauncherGUI gui = showGUI(releases, selected, javaPath, javaArgs, javaTarget, javaVendor, autoSetup, true);
                    selected = (gui.selected != null)? gui.selected : latestRelease;
                    javaPath = gui.javaPath;
                    javaArgs = gui.javaArgs;
                    javaTarget = gui.targetSelected;
                    javaVendor = gui.vendorSelected;
                    autoSetup = gui.autoSetup;

                    javaPath = validateOrProvisionJava(javaPath, javaTarget, javaVendor);

                    if (!javaPath.isEmpty()) {
                        CONFIG.setTargetJavaVersion(javaTarget);
                        CONFIG.setTargetVendor(javaVendor);
                    }
                }

                javaPath = validateOrProvisionJava(javaPath, javaTarget, javaVendor);
                while (Objects.equals(javaPath, "")) {
                    RelauncherGUI gui = showGUI(releases, selected, javaPath, javaArgs, javaTarget, javaVendor, autoSetup, false);

                    selected = (gui.selected != null) ? gui.selected : latestRelease;
                    javaPath = gui.javaPath;
                    javaArgs = gui.javaArgs;
                    javaTarget = gui.targetSelected;
                    javaVendor = gui.vendorSelected;
                    autoSetup = gui.autoSetup;

                    javaPath = validateOrProvisionJava(javaPath, javaTarget, javaVendor);
                    if (!javaPath.isEmpty()) {
                        CONFIG.setTargetJavaVersion(javaTarget);
                        CONFIG.setTargetVendor(javaVendor);
                    }
                }

                CONFIG.save();
                CONFIG.setJavaExecutablePath(javaPath);
                if (javaTarget == null) {
                    javaTarget = JavaVersion.parseOrThrow(25);
                    CONFIG.setTargetJavaVersion(javaTarget);
                }
                if (javaVendor == null) {
                    javaVendor = JavaDistro.ZULU;
                    CONFIG.setTargetVendor(javaVendor);
                }
                if (javaArgs == null || javaArgs.isEmpty()) {
                    javaArgs = ArgsEnum.render(ArgsEnum.defaults(), javaTarget.major());
                }
                CONFIG.setJavaArguments(javaArgs);
                if (selected == null) {
                    selected = latestRelease;
                }
                CONFIG.setCleanroomVersion(selected.name);
                CONFIG.setJavaSelectionMode(autoSetup, javaTarget, javaVendor);
                CONFIG.save();
            }
            if (selected == null) {
                selected = latestRelease;
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
            processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);

            try {
                return processBuilder.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /**
     * Leaves this JVM as a minimal process supervisor after the replacement game has started.
     * Every cleanup is best-effort.
     */
    private static void clear(Process childProcess) {
        Runtime runtime = Runtime.getRuntime();
        long usedBefore = usedHeap(runtime);
        long committedBefore = runtime.totalMemory();
        LaunchClassLoader launchClassLoader = Launch.classLoader;

        LOGGER.info("Releasing parent JVM resources.");

        try {
            GlobalDownloader.INSTANCE.clear();
            ForkJoinPool.commonPool().awaitQuiescence(250L, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            LOGGER.warn("Unable to clear downloader resources", t);
        }

        try {
            clearSwingResources();
        } catch (Throwable t) {
            LOGGER.warn("Unable to clear Swing/AWT resources", t);
        }

        try {
            clearNetworkResources();
        } catch (Throwable t) {
            LOGGER.warn("Unable to clear network resources", t);
        }

        try {
            clearTemporaryTrustStore();
        } catch (Throwable t) {
            LOGGER.warn("Unable to clear the temporary trust store", t);
        }

        try {
            clearForgeResources(launchClassLoader);
        } catch (Throwable t) {
            LOGGER.warn("Unable to clear all Forge/LaunchWrapper resources", t);
        }

        try {
            clearJavaCaches(launchClassLoader);
        } catch (Throwable t) {
            LOGGER.warn("Unable to clear all Java caches", t);
        }

        try {
            clearApplicationThreadState(launchClassLoader);
        } catch (Throwable t) {
            LOGGER.warn("Unable to clear all application thread state", t);
        }

        System.gc();
        System.runFinalization();
        System.gc();

        long usedAfter = usedHeap(runtime);
        LOGGER.info("Parent heap cleanup: used {} -> {} MiB, committed {} -> {} MiB",
                toMiB(usedBefore), toMiB(usedAfter), toMiB(committedBefore), toMiB(runtime.totalMemory()));

        // Loggers can own file handles, asynchronous threads, buffers, and shutdown hooks. Nothing
        // after this point attempts to log from the parent.
        try {
            java.util.logging.LogManager.getLogManager().reset();
        } catch (Throwable ignored) { }
        try {
            shutdownAllLog4jContexts();
        } catch (Throwable ignored) { }
        try {
            LogManager.shutdown();
        } catch (Throwable ignored) { }
        try {
            clearLoggingResources(launchClassLoader);
        } catch (Throwable ignored) { }

        // The child wrapper waits for this signal before opening the same log files.
        signalChildReady(childProcess);

        try {
            clearShutdownHooks();
        } catch (Throwable ignored) { }

        // Make sure the only relauncher class needed after closing the URLClassLoader is resolved.
        ExitVMBypass.class.getName();
        LOGGER = null;
        GSON = null;
        CONFIG = null;
        CACHE_DIR = null;
        JAVA_PROVISION_DIR = null;

        try {
            clearLaunchClassLoader(launchClassLoader);
        } catch (Throwable ignored) { }

        try {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        } catch (Throwable ignored) { }

        closeStandardStreams();
        System.gc();
        System.runFinalization();
        System.gc();
    }

    private static void signalChildReady(Process childProcess) {
        try {
            childProcess.getOutputStream().write(CHILD_READY_SIGNAL);
            childProcess.getOutputStream().flush();
        } catch (IOException e) {
            childProcess.destroy();
            throw new RuntimeException("Unable to signal the child startup handshake", e);
        }
    }

    private static void clearSwingResources() throws Exception {
        Runnable disposer = new Runnable() {
            @Override
            public void run() {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                for (Window window : Window.getWindows()) {
                    if (window instanceof Frame) {
                        Frame frame = (Frame) window;
                        for (Image image : frame.getIconImages()) {
                            image.flush();
                        }
                        frame.setIconImages(Collections.emptyList());
                    }
                    window.setVisible(false);
                    window.removeAll();
                    window.dispose();
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            disposer.run();
        } else {
            SwingUtilities.invokeAndWait(disposer);
            // Drain disposal events queued by Window.dispose().
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() { }
            });
        }

        RepaintManager repaintManager = RepaintManager.currentManager(null);
        repaintManager.setDoubleBufferingEnabled(false);
        clearObjectFields(
                repaintManager,
                "volatileMap",
                "hwDirtyComponents",
                "dirtyComponents",
                "tmpDirtyComponents",
                "invalidComponents",
                "runnableList",
                "standardDoubleBuffer",
                "paintManager",
                "repaintRoot",
                "repaintListeners"
        );

        UIManager.getDefaults().clear();
        UIManager.getLookAndFeelDefaults().clear();
    }

    private static void clearNetworkResources() throws Exception {
        System.setProperty("http.keepAlive", "false");
        Authenticator.setDefault(null);
        CookieHandler.setDefault(null);
        ResponseCache.setDefault(null);
        ProxySelector.setDefault(null);

        try {
            Class<?> httpClient = Class.forName("sun.net.www.http.HttpClient", false, null);
            Method closeIdleConnection = httpClient.getDeclaredMethod("closeIdleConnection");
            closeIdleConnection.setAccessible(true);
            closeIdleConnection.invoke(null);
        } catch (Throwable ignored) { }

        try {
            SSLContext context = SSLContext.getDefault();
            clearSslSessions(context.getClientSessionContext());
            clearSslSessions(context.getServerSessionContext());
        } catch (Throwable ignored) { }
        clearStaticFields(HttpsURLConnection.class, "defaultSSLSocketFactory", "defaultHostnameVerifier");
        clearStaticFields(SSLContext.class, "defaultContext");

        try {
            clearStaticSingleton(Class.forName("sun.security.ssl.TrustStoreManager", false, null), "tam");
        } catch (Throwable ignored) { }
        clearStaticFields(InetAddress.class, "addressCache", "negativeCache", "cache", "expirySet", "lookupTable");

        for (Provider provider : Security.getProviders()) {
            Security.removeProvider(provider.getName());
        }
    }

    private static void clearSslSessions(SSLSessionContext sessions) {
        if (sessions == null) {
            return;
        }
        Enumeration<byte[]> ids = sessions.getIds();
        while (ids.hasMoreElements()) {
            SSLSession session = sessions.getSession(ids.nextElement());
            if (session != null) {
                session.invalidate();
            }
        }
        sessions.setSessionCacheSize(0);
        sessions.setSessionTimeout(1);
    }

    private static void clearTemporaryTrustStore() throws Exception {
        Path trustStore = temporaryTrustStore;
        temporaryTrustStore = null;
        if (trustStore == null) {
            return;
        }

        String configuredTrustStore = System.getProperty("javax.net.ssl.trustStore");
        if (trustStore.toAbsolutePath().toString().equals(configuredTrustStore)) {
            System.clearProperty("javax.net.ssl.trustStore");
        }
        Files.deleteIfExists(trustStore);

        // File.deleteOnExit() otherwise retains the path until the child eventually exits.
        try {
            Class<?> hook = Class.forName("java.io.DeleteOnExitHook", false, null);
            Field files = hook.getDeclaredField("files");
            files.setAccessible(true);
            Object value = files.get(null);
            if (value instanceof Set) {
                ((Set<?>) value).remove(trustStore.toFile().getPath());
            }
        } catch (Throwable ignored) { }
    }

    private static void clearForgeResources(LaunchClassLoader launchClassLoader) throws Exception {
        Launch.blackboard.clear();
        Launch.blackboard = null;
        Launch.minecraftHome = null;
        Launch.assetsDir = null;

        if (launchClassLoader == null) {
            return;
        }

        Map<?, ?> loadedClasses = mapField(launchClassLoader, "cachedClasses");

        Class<?> coreModManager = loadedClass(loadedClasses, "net.minecraftforge.fml.relauncher.CoreModManager");
        clearStaticFields(
                coreModManager,
                "rootPlugins",
                "ignoredModFiles",
                "transformers",
                "loadPlugins",
                "tweaker",
                "mcDir",
                "candidateModFiles",
                "accessTransformers",
                "rootNames",
                "ADDURL",
                "tweakSorting");

        Class<?> injectionData = loadedClass(loadedClasses, "net.minecraftforge.fml.relauncher.FMLInjectionData");
        clearStaticFields(injectionData, "minecraftHome", "major", "minor", "rev", "build", "mccversion", "mcpversion", "containers");

        clearStaticSingleton(loadedClass(loadedClasses, "net.minecraftforge.fml.relauncher.FMLLaunchHandler"), "INSTANCE");
        clearStaticFields(loadedClass(loadedClasses, "net.minecraftforge.fml.relauncher.FMLLaunchHandler"), "side");
        clearStaticFields(loadedClass(loadedClasses, "net.minecraftforge.fml.common.launcher.FMLTweaker"), "jarLocation");
        clearStaticFields(loadedClass(loadedClasses, "net.minecraftforge.fml.common.ProgressManager"), "bars");

        Class<?> remapper = loadedClass(loadedClasses, "net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper");
        clearStaticSingleton(remapper, "INSTANCE");

        // These singletons may not be initialized this early. Never initialize one merely to clear it.
        clearInitializedStaticSingleton(loadedClass(loadedClasses, "net.minecraftforge.fml.common.Loader"), "instance");
        clearInitializedStaticSingleton(loadedClass(loadedClasses, "net.minecraftforge.fml.common.FMLCommonHandler"), "INSTANCE");

        Object transformers = fieldValue(launchClassLoader, "transformers");
        if (transformers instanceof Collection) {
            for (Object transformer : new ArrayList<Object>((Collection<?>) transformers)) {
                clearObjectState(transformer);
            }
            ((Collection<?>) transformers).clear();
        }
        clearObjectFields(launchClassLoader, "renameTransformer");
    }

    private static void clearJavaCaches(ClassLoader applicationClassLoader) {
        Introspector.flushCaches();
        if (applicationClassLoader != null) {
            ResourceBundle.clearCache(applicationClassLoader);
        }
    }

    private static void clearApplicationThreadState(ClassLoader applicationClassLoader) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(null);
        ClassLoader replacement = applicationClassLoader == null ? null : applicationClassLoader.getParent();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread == Thread.currentThread() || thread.getContextClassLoader() == applicationClassLoader) {
                try {
                    thread.setContextClassLoader(replacement);
                    thread.setPriority(Thread.MIN_PRIORITY);
                } catch (Throwable ignored) { }
                clearObjectFields(thread, "threadLocals", "inheritableThreadLocals");
            }
        }
    }

    private static void shutdownAllLog4jContexts() throws Exception {
        Object factory = LogManager.getFactory();
        Method getSelector = factory.getClass().getMethod("getSelector");
        Object selector = getSelector.invoke(factory);
        Method getLoggerContexts = selector.getClass().getMethod("getLoggerContexts");
        Object contexts = getLoggerContexts.invoke(selector);
        if (!(contexts instanceof Collection)) {
            return;
        }
        for (Object context : new ArrayList<Object>((Collection<?>) contexts)) {
            try {
                Method stop = context.getClass().getMethod("stop");
                stop.invoke(context);
            } catch (Throwable ignored) { }
        }
    }

    private static void clearLoggingResources(LaunchClassLoader launchClassLoader) throws Exception {
        ClassLoader loggingClassLoader = LogManager.class.getClassLoader();
        Class<?> jmxServer = Class.forName("org.apache.logging.log4j.core.jmx.Server", false, loggingClassLoader);
        try {
            Method unregister = jmxServer.getMethod("unregisterMBeans");
            unregister.invoke(null);
        } catch (Throwable ignored) { }
        try {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName pattern = new ObjectName("org.apache.logging.log4j2:*");
            for (ObjectName name : mBeanServer.queryNames(pattern, null)) {
                try {
                    mBeanServer.unregisterMBean(name);
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        clearStaticFields(jmxServer, "executor");

        Class<?> pluginRegistry = Class.forName("org.apache.logging.log4j.core.config.plugins.util.PluginRegistry", false, loggingClassLoader);
        clearStaticSingleton(pluginRegistry, "INSTANCE");
        Class<?> contextSelector = Class.forName("org.apache.logging.log4j.core.selector.ClassLoaderContextSelector", false, loggingClassLoader);
        clearStaticFields(contextSelector, "DEFAULT_CONTEXT", "CONTEXT_MAP");
        Class<?> statusLogger = Class.forName("org.apache.logging.log4j.status.StatusLogger", false, loggingClassLoader);
        clearStaticSingleton(statusLogger, "STATUS_LOGGER");
        Class<?> abstractManager = Class.forName("org.apache.logging.log4j.core.appender.AbstractManager", false, loggingClassLoader);
        closeStaticMapValues(abstractManager, "MAP");
        clearStaticFields(abstractManager, "MAP");
        clearStaticLoggerInstances(jmxServer);
        clearStaticLoggerInstances(pluginRegistry);
        clearStaticLoggerInstances(contextSelector);
        clearStaticLoggerInstances(statusLogger);
        clearStaticLoggerInstances(abstractManager);
        clearStaticLoggerInstances(LogManager.class);

        if (launchClassLoader != null) {
            Map<?, ?> loadedClasses = mapField(launchClassLoader, "cachedClasses");
            if (loadedClasses != null) {
                for (Object candidate : new HashSet<Object>(loadedClasses.values())) {
                    if (candidate instanceof Class && isInitialized((Class<?>) candidate)) {
                        clearStaticLoggerInstances((Class<?>) candidate);
                    }
                }
            }
        }
        clearStaticFields(LogManager.class, "factory");
    }

    private static void clearStaticLoggerInstances(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (value instanceof Logger) {
                    clearObjectState(value);
                }
            } catch (Throwable ignored) { }
        }
    }

    private static void clearShutdownHooks() throws Exception {
        Class<?> hooksClass = Class.forName("java.lang.ApplicationShutdownHooks", false, null);
        Field hooksField = hooksClass.getDeclaredField("hooks");
        hooksField.setAccessible(true);
        Object hooks = hooksField.get(null);
        if (!(hooks instanceof Map)) {
            return;
        }
        for (Object hook : new ArrayList<Object>(((Map<?, ?>) hooks).keySet())) {
            if (hook instanceof Thread) {
                try {
                    Runtime.getRuntime().removeShutdownHook((Thread) hook);
                } catch (Throwable ignored) { }
            }
        }
        ((Map<?, ?>) hooks).clear();
    }

    private static void clearLaunchClassLoader(LaunchClassLoader launchClassLoader) throws Exception {
        if (launchClassLoader == null) {
            return;
        }

        try {
            launchClassLoader.close();
        } catch (Throwable ignored) { }

        try {
            Object urlClassPath = fieldValue(launchClassLoader, "ucp");
            clearObjectFields(
                    urlClassPath,
                    "path",
                    "urls",
                    "loaders",
                    "lmap",
                    "jarHandler",
                    "lookupCacheURLs",
                    "lookupCacheLoader"
            );
        } catch (Throwable ignored) { }
        clearObjectFields(launchClassLoader, "closeables");

        clearObjectFields(
                launchClassLoader,
                "sources",
                "parent",
                "transformers",
                "cachedClasses",
                "invalidClasses",
                "classLoaderExceptions",
                "transformerExceptions",
                "packageManifests",
                "resourceCache",
                "negativeResourceCache",
                "renameTransformer",
                "loadBuffer"
        );
        Launch.classLoader = null;
    }

    private static void clearInitializedStaticSingleton(Class<?> type, String fieldName) throws Exception {
        if (type != null && isInitialized(type)) {
            clearStaticSingleton(type, fieldName);
        }
    }

    private static boolean isInitialized(Class<?> type) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe", false, null);
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method shouldBeInitialized = unsafeClass.getMethod("shouldBeInitialized", Class.class);
            return !((Boolean) shouldBeInitialized.invoke(unsafe, type));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Class<?> loadedClass(Map<?, ?> loadedClasses, String name) {
        if (loadedClasses == null) {
            return null;
        }
        Object direct = loadedClasses.get(name);
        if (direct instanceof Class) {
            return (Class<?>) direct;
        }
        for (Object candidate : loadedClasses.values()) {
            if (candidate instanceof Class && name.equals(((Class<?>) candidate).getName())) {
                return (Class<?>) candidate;
            }
        }
        return null;
    }

    private static Map<?, ?> mapField(Object target, String fieldName) throws Exception {
        Object value = fieldValue(target, fieldName);
        return value instanceof Map ? (Map<?, ?>) value : null;
    }

    private static Object fieldValue(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void closeStaticMapValues(Class<?> type, String fieldName) throws Exception {
        Field field = findField(type, fieldName);
        field.setAccessible(true);
        Object value = field.get(null);
        if (!(value instanceof Map)) {
            return;
        }
        for (Object managed : new ArrayList<Object>(((Map<?, ?>) value).values())) {
            clearValue(managed);
        }
    }

    private static void clearStaticSingleton(Class<?> type, String fieldName) throws Exception {
        if (type == null) {
            return;
        }
        Field field = findField(type, fieldName);
        field.setAccessible(true);
        Object singleton = field.get(null);
        clearObjectState(singleton);
        clearField(null, field);
    }

    private static void clearStaticFields(Class<?> type, String... fieldNames) throws Exception {
        if (type == null) {
            return;
        }
        for (String fieldName : fieldNames) {
            try {
                Field field = findField(type, fieldName);
                if (Modifier.isStatic(field.getModifiers())) {
                    clearField(null, field);
                }
            } catch (NoSuchFieldException ignored) { }
        }
    }

    private static void clearObjectFields(Object target, String... fieldNames) throws Exception {
        if (target == null) {
            return;
        }
        for (String fieldName : fieldNames) {
            try {
                clearField(target, findField(target.getClass(), fieldName));
            } catch (NoSuchFieldException ignored) { }
        }
    }

    private static void clearObjectState(Object target) {
        if (target == null) {
            return;
        }
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.getType().isPrimitive()) {
                    continue;
                }
                try {
                    clearField(target, field);
                } catch (Throwable ignored) { }
            }
        }
    }

    private static void clearField(Object target, Field field) throws Exception {
        field.setAccessible(true);
        Object value = field.get(target);
        clearValue(value);
        int modifiers = field.getModifiers();
        boolean immutableStatic = Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers);
        if (!immutableStatic && !field.getType().isPrimitive()) {
            field.set(target, null);
        }
    }

    private static void clearValue(Object value) {
        if (value == null) {
            return;
        }
        try {
            if (value instanceof Closeable) {
                ((Closeable) value).close();
            } else if (value instanceof AutoCloseable) {
                ((AutoCloseable) value).close();
            }
        } catch (Throwable ignored) { }
        try {
            if (value instanceof Map) {
                ((Map<?, ?>) value).clear();
            } else if (value instanceof Collection) {
                ((Collection<?>) value).clear();
            } else if (value instanceof ExecutorService) {
                ((ExecutorService) value).shutdownNow();
            } else if (value instanceof AtomicReference) {
                ((AtomicReference<?>) value).set(null);
            } else if (value instanceof Reference) {
                ((Reference<?>) value).clear();
            } else if (value instanceof ThreadLocal) {
                ((ThreadLocal<?>) value).remove();
            } else if (value instanceof Image) {
                ((Image) value).flush();
            }
        } catch (Throwable ignored) { }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void closeStandardStreams() {
        try {
            System.in.close();
        } catch (Throwable ignored) { }
        try {
            System.out.flush();
            System.out.close();
        } catch (Throwable ignored) { }
        try {
            System.err.flush();
            System.err.close();
        } catch (Throwable ignored) { }
    }

    private static long usedHeap(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long toMiB(long bytes) {
        return bytes / (1024L * 1024L);
    }

}

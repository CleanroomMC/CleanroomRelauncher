package com.cleanroommc.relauncher.download;

import com.cleanroommc.javautils.JavaUtils;
import com.cleanroommc.javautils.api.JavaInstall;
import com.cleanroommc.javautils.spi.JavaLocator;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.gui.LoadingGUI;
import com.cleanroommc.relauncher.util.enums.JavaTargetsEnum;
import com.cleanroommc.relauncher.util.enums.VendorsEnum;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JavaProvisioning {
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final String USER_AGENT = "Mozilla/5.0 CleanroomRelauncher/1.0";

    private static List<JavaInstall> findLocalProvisionedJavas() {
        List<JavaInstall> results = new ArrayList<>();
        Path javaBase = Paths.get(System.getProperty("user.home"), ".cleanroom", "java");
        if (!Files.exists(javaBase)) return results;

        String expectedBinary = System.getProperty("os.name").toLowerCase().contains("win")
                ? "javaw.exe" : "java";

        try (DirectoryStream<Path> vendorDirs = Files.newDirectoryStream(javaBase)) {
            for (Path vendorDir : vendorDirs) {
                if (!Files.isDirectory(vendorDir)) continue;

                // Check for bin folder, in case a zip extracted the root directly
                Path directBinary = vendorDir.resolve("bin").resolve(expectedBinary);
                if (Files.exists(directBinary)) {
                    tryParseAndAdd(directBinary, results);
                    continue;
                }

                // Check one level deeper
                try (DirectoryStream<Path> subDirs = Files.newDirectoryStream(vendorDir)) {
                    for (Path subDir : subDirs) {
                        if (!Files.isDirectory(subDir)) continue;
                        Path nestedBinary = subDir.resolve("bin").resolve(expectedBinary);
                        if (Files.exists(nestedBinary)) {
                            tryParseAndAdd(nestedBinary, results);
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.warn("Failed to scan ~/.cleanroom/java", e);
        }
        return results;
    }
    private static void tryParseAndAdd(Path binary, List<JavaInstall> results) {
        try {
            JavaInstall install = JavaUtils.parseInstall(binary.toAbsolutePath().toString());
            CleanroomRelauncher.LOGGER.info("Found provisioned Java {} at {}",
                    install.version().major(), binary);
            results.add(install);
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.warn("Failed to parse Java at {}", binary, e);
        }
    }

    public static String validateOrProvisionJava(String path, JavaTargetsEnum target, VendorsEnum vendor) {
        LoadingGUI loading = new LoadingGUI();
        loading.show();
        if (path != null && !path.isEmpty()) {
            try{
                loading.updateStatus("Checking provided path...");
                CleanroomRelauncher.LOGGER.info("Checking path: {}", path);
                if (target == null && vendor == null && testJava(path)){
                    return path;
                } else if(target != null && vendor !=null && testJava(path, vendor, target)){
                    return path;
                }
                CleanroomRelauncher.LOGGER.warn("Invalid path, Fetching a new java instance");
                loading.updateStatus("Scanning for Java " + target.getInternalNameInt() + " Installations ...");
                List<JavaInstall> provisionedJavas = findLocalProvisionedJavas();

                List<JavaInstall> validJavaInstalls = Stream.concat(
                                JavaLocator.locators().parallelStream()
                                        .map(JavaLocator::all)
                                        .flatMap(Collection::stream),
                                provisionedJavas.stream()
                        )
                        .filter(javaInstall -> javaInstall.version().major() == target.getInternalNameInt())
                        .filter(javaInstall -> {
                            if (vendor == VendorsEnum.ANY || vendor == null) return true;
                            return javaInstall.vendor().name().toLowerCase().contains(vendor.getInternalName().toLowerCase());
                        })
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());

                if (!validJavaInstalls.isEmpty()) {
                    for (JavaInstall javaInstall : validJavaInstalls) {
                        String currentInstallPath = javaInstall.home().getPath();
                        String binaryPath = javaInstall.executable(true).getAbsolutePath();
                        loading.updateStatus("Testing: " + javaInstall.vendor().name());
                        if(testJava(currentInstallPath)){
                            loading.close();
                            return binaryPath;
                        }
                    }
                }
                loading.updateStatus("No Java found. Downloading...");
                CleanroomRelauncher.LOGGER.warn("Found no available Java installation, Auto-provisioning..");
                return autoProvisionJavaInstall(target, vendor, loading);
            } finally {
                loading.close();
            }
        } else{
            try {
                loading.updateStatus("Scanning for Java " + target.getInternalNameInt() + " Installations ...");
                List<JavaInstall> provisionedJavas = findLocalProvisionedJavas();

                List<JavaInstall> validJavaInstalls = Stream.concat(
                                JavaLocator.locators().parallelStream()
                                        .map(JavaLocator::all)
                                        .flatMap(Collection::stream),
                                provisionedJavas.stream()
                        )
                        .filter(javaInstall -> javaInstall.version().major() == target.getInternalNameInt())
                        .filter(javaInstall -> {
                            if (vendor == VendorsEnum.ANY) return true;
                            return javaInstall.vendor().name().toLowerCase().contains(vendor.getInternalName().toLowerCase());
                        })
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
                if (!validJavaInstalls.isEmpty()) {
                    for (JavaInstall javaInstall : validJavaInstalls) {
                        String currentInstallPath = javaInstall.home().getPath();
                        String binaryPath = javaInstall.executable(true).getAbsolutePath();
                        loading.updateStatus("Testing: " + javaInstall.vendor().name());
                        if(testJava(currentInstallPath)){
                            loading.close();
                            return binaryPath;
                        }
                    }
                }
                loading.close();
                loading.updateStatus("No Valid Java found. Downloading...");
                return autoProvisionJavaInstall(target, vendor, loading);
            } finally {
                loading.close();
            }
        }
    }

    private static boolean testJava(String javaPath){
        //TODO
        try{
            JavaInstall javaInstall = JavaUtils.parseInstall(javaPath);
            return javaInstall.version().major() >= 21;
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.error("Encountered an error checking the path", e);
            return false;
        }
    }
    private static boolean testJava(String javaPath, VendorsEnum vendor, JavaTargetsEnum target) {
        try{
            JavaInstall javaInstall = JavaUtils.parseInstall(javaPath);
            return javaInstall.version().major() == target.getInternalNameInt() && javaInstall.vendor().name().toLowerCase().contains(vendor.getInternalName().toLowerCase());
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.error("Encountered an error checking the path", e);
            return false;
        }
    }

    private static String fetchUrl(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setInstanceFollowRedirects(false);

        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new IOException("Failed to fetch from Foojay: HTTP " + status);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } catch (IOException e) {
            throw new IOException("Error parsing Response from Foojay", e);
        }
        finally {
            conn.disconnect();
        }
    }

    private static void downloadFile(String urlStr, Path target, LoadingGUI gui) throws IOException {
        //TODO: Debug, remove this check
        if (Files.exists(target) && Files.size(target) > 0) {
            CleanroomRelauncher.LOGGER.info("Java archive already exists at {}, skipping download.", target);
            return;
        }
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // Don't Allow redirects automatically
        conn.setInstanceFollowRedirects(false);
        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER) {

            String newUrl = conn.getHeaderField("Location");
            CleanroomRelauncher.LOGGER.info("Redirecting to: {}", newUrl);
            // Likely only 1 redirect
            conn = (HttpURLConnection) new URL(newUrl).openConnection();
        }
        long totalSize = conn.getContentLengthLong();
        CleanroomRelauncher.LOGGER.info("Starting download of Java archives...");
        gui.updateStatus("Starting download of Java archives");
        gui.enableProgress();

        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                if (totalSize > 0) {
                    int percent = (int) ((totalRead * 100) / totalSize);
                    gui.setProgress(percent);
                    gui.updateStatus(String.format("Downloading Java: %d%%", percent));
                } else {
                    gui.updateStatus(String.format("Downloading: %.2f MB", totalRead / (1024.0 * 1024.0)));
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }

        CleanroomRelauncher.LOGGER.info("Finished downloading archive");
    }

    private static String getTopLevelFolder(String entryName) {
        int firstSlash = entryName.indexOf('/');
        if (firstSlash == -1) firstSlash = entryName.indexOf('\\');
        return (firstSlash != -1) ? entryName.substring(0, firstSlash) : entryName;
    }

    private static String extractArchive(Path archive, Path dest, LoadingGUI gui) throws IOException {
        gui.disableProgress();
        gui.updateStatus("Extracting Archive");
        CleanroomRelauncher.LOGGER.info("Extracting archive from: {} to: {}", archive.toAbsolutePath(), dest.toAbsolutePath());
        String name = archive.getFileName().toString().toLowerCase();
        long totalSize = Files.size(archive);
        long[] bytesRead = {0};
        final String[] rootFolder = {null};

        CleanroomRelauncher.LOGGER.info("Opening Input Stream");
        gui.enableProgress();
        try (InputStream is = Files.newInputStream(archive);
             InputStream progressIs = new FilterInputStream(is) {
                 @Override
                 public int read(byte[] b, int off, int len) throws IOException {
                     int read = super.read(b, off, len);
                     if (read > 0) {
                         bytesRead[0] += read;
                         int percent = (int) ((bytesRead[0] * 100) / totalSize);
                         gui.setProgress(percent);
                     }
                     return read;
                 }
             };
             BufferedInputStream bis = new BufferedInputStream(progressIs)) {

            if (name.endsWith(".zip")) {
                try (ZipInputStream zis = new ZipInputStream(bis)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        // Return top path
                        if (rootFolder[0] == null && entry.getName().replace("/", "").endsWith("bin")) {
                            rootFolder[0] = getTopLevelFolder(entry.getName());
                        }
                        processEntry(dest, entry.getName(), entry.isDirectory(), zis);
                    }
                }
            } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
                try (GzipCompressorInputStream gis = new GzipCompressorInputStream(bis);
                     TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {
                    TarArchiveEntry entry;
                    while ((entry = tis.getNextTarEntry()) != null) {
                        if (rootFolder[0] == null && entry.getName().replace("/", "").endsWith("bin")) {
                            rootFolder[0] = entry.getName();
                        }
                        processEntry(dest, entry.getName(), entry.isDirectory(), tis);
                    }
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(dest);
            throw new IOException("Failed to extract archive: " + archive, e);
        }
        if (rootFolder[0] != null) {
            gui.close();
            return dest.resolve(rootFolder[0]).toAbsolutePath().toString();
        }
        gui.close();
        return dest.toAbsolutePath().toString();
    }
    private static void processEntry(Path dest, String name, boolean isDir, InputStream stream) throws IOException {
        // Zip Slip
        Path target = dest.resolve(name).normalize();
        if (!target.startsWith(dest)) {
            throw new IOException("Entry is outside target directory: " + name);
        }

        if (isDir) {
            Files.createDirectories(target);
        } else {
            Files.createDirectories(target.getParent());

            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);

            try {
                if (Files.getFileStore(target).supportsFileAttributeView("posix")) {
                    Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
                }
            } catch (Throwable ignored) {
                // Ignore if windows
            }
        }
    }
    private static String getExpectedBinaryName() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "javaw.exe" : "java";
    }
    private static String normalizeOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        if (os.contains("nux") || os.contains("nix") || os.contains("aix")) return "linux";
        return os;
    }
    private static String normalizeArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            return "x64";
        }
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        }
        if (arch.contains("x86")) {
            return "x32";
        }

        return arch;
    }
    private static String autoProvisionJavaInstall(JavaTargetsEnum target, VendorsEnum vendor, LoadingGUI gui){
        // Get OS/Arch
        String os = normalizeOS();
        String arch = normalizeArch();
        String extension = os.equals("windows") ? "zip" : "tar.gz";

        gui.updateStatus("Querying requested install");
        // FooJay Disco API URL
        String apiUrl = String.format(
                "https://api.foojay.io/disco/v3.0/packages/jdks?version=%d&distro=%s&operating_system=%s&architecture=%s&archive_type=%s&release_status=ga&latest=all",
                target.getInternalNameInt(),
                vendor.getDiscoverySlug(),
                os,
                arch,
                extension
        );
        try{
            String jsonResponse = fetchUrl(apiUrl);
            String downloadUrl = null;
            // Regex redirect url, FooJay doesn't do redirects on its own
            Matcher matcher = Pattern.compile("\"pkg_download_redirect\":\"(.*?)\"").matcher(jsonResponse);
            if (matcher.find()) {
                downloadUrl = matcher.group(1);
                CleanroomRelauncher.LOGGER.info("Download url: {}", downloadUrl);
                gui.updateStatus("Found download url: " + downloadUrl);
            }
            if (downloadUrl == null) {
                gui.updateStatus("No download url found, please try again");
                CleanroomRelauncher.LOGGER.error("No download link found in Foojay response for {} version {}", vendor, target);
                Thread.sleep(5000);
                return "";
            }

            Path javaHomeBase = Paths.get(System.getProperty("user.home"), ".cleanroom", "java",
                    vendor.name().toLowerCase() + "-" + target.getInternalNameInt());

            if (!Files.exists(javaHomeBase)) {
                Files.createDirectories(javaHomeBase);
                CleanroomRelauncher.LOGGER.info("Created directory: {}", javaHomeBase.toString());
            }

            Path archiveFile = javaHomeBase.resolve("temp_java." + extension);

            downloadFile(downloadUrl, archiveFile, gui);
            // Extract to javaHomeBase, will always have the binary in Bin folder
            String extractedPath = extractArchive(archiveFile, javaHomeBase, gui);
            String finalPath = Paths.get(extractedPath,"bin",getExpectedBinaryName()).toString();
            CleanroomRelauncher.LOGGER.info("Extracted archive: {}", archiveFile.toAbsolutePath());
            CleanroomRelauncher.LOGGER.info("Setting Java Path to {}", finalPath);
            // Delete Temps
            Files.deleteIfExists(archiveFile);
            CleanroomRelauncher.LOGGER.info("Deleted Downloaded archiveFile {}", archiveFile.toAbsolutePath());
            return finalPath;

        } catch (IOException | InterruptedException e){
            CleanroomRelauncher.LOGGER.error("Unable to provision a java installation", e);
        }
        return "";
    }
}

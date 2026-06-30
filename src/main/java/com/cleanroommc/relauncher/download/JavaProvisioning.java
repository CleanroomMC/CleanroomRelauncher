package com.cleanroommc.relauncher.download;

import com.cleanroommc.javautils.JavaUtils;
import com.cleanroommc.javautils.api.JavaDistro;
import com.cleanroommc.javautils.api.JavaInstall;
import com.cleanroommc.javautils.api.JavaVersion;
import com.cleanroommc.javautils.provisioners.FoojayJavaProvisioner;
import com.cleanroommc.javautils.spi.JavaLocator;
import com.cleanroommc.javautils.spi.JavaProvisioner;
import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.gui.LoadingGUI;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


public class JavaProvisioning {
    private static final Path javaHomeBase = Paths.get(System.getProperty("user.home"), ".cleanroom", "java");

    public static String validateOrProvisionJava(String path, JavaVersion target, JavaDistro vendor) {
        LoadingGUI loading = new LoadingGUI();
        loading.show();
        JavaVersion provisioningTarget = target == null ? JavaVersion.parseOrThrow(25) : target;
        JavaDistro provisioningVendor = vendor == null ? JavaDistro.ZULU : vendor;

        JavaProvisioner provisioner = JavaProvisioner.provisioners().stream()
                .findFirst()
                .orElseGet(FoojayJavaProvisioner::new);

        if (path != null && !path.isEmpty()) {
            try{
                loading.updateStatus("Checking provided path...");
                CleanroomRelauncher.LOGGER.info("Checking path: {}", path);
                if(testJava(path, vendor, target)){
                    return path;
                }
                CleanroomRelauncher.LOGGER.warn("Invalid path, Fetching a new java instance");
                loading.updateStatus("Scanning for Java " + provisioningTarget.major() + " Installations ...");
                String binaryPath = getBinaryPath(provisioningTarget, provisioningVendor, loading);
                if(binaryPath != null){
                    return binaryPath;
                }
                loading.updateStatus("No Java found. Downloading...");
                CleanroomRelauncher.LOGGER.warn("Found no available Java installation, Auto-provisioning..");
                return autoProvisionJavaInstall(provisioningTarget, provisioningVendor, loading, provisioner);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                loading.close();
            }
        } else{
            try {
                loading.updateStatus("Scanning for Java " + provisioningTarget.major() + " Installations ...");

                String binaryPath = getBinaryPath(provisioningTarget, provisioningVendor, loading);
                if(binaryPath != null){
                    return binaryPath;
                }
                loading.close();
                loading.updateStatus("No Valid Java found. Downloading...");
                return autoProvisionJavaInstall(provisioningTarget, provisioningVendor, loading, provisioner);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                loading.close();
            }
        }
    }
    private static @Nullable String getBinaryPath(JavaVersion target, JavaDistro vendor, LoadingGUI loading) throws IOException {
        List<JavaLocator> locators = JavaLocator.locators();
        locators.forEach(l -> l.onScan(directory ->
                loading.updateStatus("Scanning for Java in " + directory + "...")));

        List<JavaInstall> validJavaInstalls = locators.parallelStream()
                .map(JavaLocator::all)
                .flatMap(Collection::stream)
                .filter(j -> j.version().major() == target.major())
                .filter(javaInstall -> matchesVendor(javaInstall.distro(), vendor))
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (!validJavaInstalls.isEmpty()) {
            for (JavaInstall javaInstall : validJavaInstalls) {
                String currentInstallPath = javaInstall.home().toRealPath().toString();
                String binaryPath = javaInstall.executable(true).toAbsolutePath().toString();
                loading.updateStatus("Testing: " + javaInstall.distro().name());
                if (testJava(currentInstallPath)) {
                    loading.close();
                    return binaryPath;
                }
            }
        }
        return null;
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
    private static boolean testJava(String javaPath, JavaDistro vendor, JavaVersion target) {
        try{
            JavaInstall javaInstall = JavaUtils.parseInstall(javaPath);
            boolean validVersion = target == null ? javaInstall.version().major() >= 21 : javaInstall.version().major() == target.major();
            boolean validVendor = matchesVendor(javaInstall.distro(), vendor);
            if (!validVersion || !validVendor) {
                CleanroomRelauncher.LOGGER.warn("Java path {} resolved to Java {} from {}, but expected {}{}",
                        javaPath, javaInstall.version(), javaInstall.distro(),
                        target == null ? "Java 21+" : "Java " + target.major(),
                        vendor == null || vendor == JavaDistro.UNKNOWN ? "" : " from " + vendor
                );
            }
            return validVersion && validVendor;
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.error("Encountered an error checking the path", e);
            return false;
        }
    }

    private static boolean matchesVendor(JavaDistro actual, JavaDistro expected) {
        if (expected == null || expected == JavaDistro.UNKNOWN) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (actual == expected || actual.name().equalsIgnoreCase(expected.name())) {
            return true;
        }
        String actualFoojayId = actual.foojayId();
        String expectedFoojayId = expected.foojayId();
        return actualFoojayId != null && expectedFoojayId != null && actualFoojayId.equalsIgnoreCase(expectedFoojayId);
    }

    private static String autoProvisionJavaInstall(JavaVersion target, JavaDistro vendor, LoadingGUI loading, JavaProvisioner provisioner) {
        try {
            provisioner.onDownload((downloaded, total, fileName) -> {
                if (total > 0) {
                    int percent = (int) (downloaded * 100 / total);
                    loading.updateStatus("Downloading " + fileName + " - " + percent + "%");
                } else {
                    loading.updateStatus("Downloading " + fileName + " - " + (downloaded / 1024) + " KB");
                }
            });
            JavaInstall result = provisioner.resolve(target, vendor, javaHomeBase);
            return result.executable(true).toAbsolutePath().toString();
        } catch (IOException e) {
            CleanroomRelauncher.LOGGER.error("Unable to provision a java installation", e);
        }
        return "";
    }
}

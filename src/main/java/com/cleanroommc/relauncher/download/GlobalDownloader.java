package com.cleanroommc.relauncher.download;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.gui.LoadingGUI;
import com.cleanroommc.relauncher.util.CacheUtils;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;

public final class GlobalDownloader {

    public static final GlobalDownloader INSTANCE = new GlobalDownloader();

    private final List<ForkJoinTask<?>> downloads = new ArrayList<>();

    public ForkJoinTask<?> from(String source, File destination, String expectedHash, CacheUtils.HashAlgorithm algo) {
        URI uri;
        URL url;
        try {
            uri = URI.create(source);
            url = uri.toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(String.format("Unable to construct url %s", source), e);
        }
        final String cleanHash = expectedHash != null ? expectedHash.trim() : null;
        ForkJoinTask<?> task = ForkJoinPool.commonPool().submit(() -> {
            try {
                downloadToVerifiedFile(url, destination, cleanHash, algo);
                CleanroomRelauncher.LOGGER.debug("Downloaded {} to {}", uri.toString(), destination.getAbsolutePath());
            } catch (IOException e) {
                throw new DownloadException(url, destination, e);
            }
        });
        synchronized (this.downloads) {
            this.downloads.add(task);
        }
        return task;
    }

    public void immediatelyFrom(String source, File destination, String expectedHash, CacheUtils.HashAlgorithm algo) {
        ForkJoinTask<?> task = this.from(source, destination, expectedHash, algo);
        try {
            task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for download", e);
        } catch (ExecutionException e) {
            throw unwrapDownloadFailure(e);
        } finally {
            synchronized (this.downloads) {
                this.downloads.remove(task);
            }
        }
    }

    public void blockUntilFinished(LoadingGUI loading) {
        List<ForkJoinTask<?>> downloadsToWaitFor;
        synchronized (this.downloads) {
            downloadsToWaitFor = new ArrayList<>(this.downloads);
            this.downloads.clear();
        }

        int total = downloadsToWaitFor.size();
        int completed = 0;
        int last = 0;
        for (Future download : downloadsToWaitFor) {
            try {
                download.get();
                completed++;
                int percentage = (completed * 100) / total;
                if (percentage % 10 == 0 && last != percentage) {
                    last = percentage;
                    loading.updateStatus("Download Progress: " + completed + "/" + total + " | " + percentage + "% completed.");
                    loading.setProgress(percentage);
                    CleanroomRelauncher.LOGGER.info("Download Progress: {}/{} | {}% completed.", completed, total, percentage);
                }
            } catch (InterruptedException | ExecutionException e) {
                SwingUtilities.invokeLater(loading::close);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for downloads", e);
                }
                throw unwrapDownloadFailure((ExecutionException) e);
            }
        }
        SwingUtilities.invokeLater(() -> {
            loading.disableProgress();
            loading.updateStatus("Download Complete");
        });
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            CleanroomRelauncher.LOGGER.warn("Interrupted thread sleep", e);
        }
    }

    private static void downloadToVerifiedFile(URL url, File destination, String expectedHash, CacheUtils.HashAlgorithm algo) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        File temp = Files.createTempFile(
                parent != null ? parent.toPath() : destination.toPath().toAbsolutePath().getParent(),
                destination.getName() + ".",
                ".tmp"
        ).toFile();
        try {
            FileUtils.copyURLToFile(url, temp);
            if (expectedHash != null && algo != null) {
                String actualHash = CacheUtils.hash(temp, algo);
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    throw new IOException(String.format("Hash mismatch for %s - expected %s (%s) but got %s",
                            destination, expectedHash, algo.name(), actualHash));
                }
            }
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException e) {
                CleanroomRelauncher.LOGGER.warn("Failed to delete temporary download file {}", temp, e);
            }
        }
    }

    private static RuntimeException unwrapDownloadFailure(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        }
        return new RuntimeException("Unable to complete download", cause);
    }

    private static final class DownloadException extends RuntimeException {

        private DownloadException(URL url, File destination, IOException cause) {
            super(String.format("Unable to download %s to %s", url, destination), cause);
        }
    }

}

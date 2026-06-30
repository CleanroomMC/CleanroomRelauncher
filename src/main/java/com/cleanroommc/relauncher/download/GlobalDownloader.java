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
                FileUtils.copyURLToFile(url, destination);
                CleanroomRelauncher.LOGGER.debug("Downloaded {} to {}", uri.toString(), destination.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(String.format("Unable to download %s to %s", url, destination), e);
            }
            if (cleanHash != null && algo != null) {
                try {
                    if (CacheUtils.isFileCorrupt(destination, cleanHash, algo)) {
                        destination.delete(); // Don't leave a corrupt file on disk
                        throw new RuntimeException(String.format(
                                "Hash mismatch for %s — expected %s (%s) but got %s",
                                destination, cleanHash, algo.name(),
                                CacheUtils.hash(destination, algo) // recompute only for the error message
                        ));
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to hash-check downloaded file: " + destination, e);
                }
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
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Unable to complete download", e);
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
                throw new RuntimeException("Unable to complete download", e);
            }
        }
        SwingUtilities.invokeLater(() -> {
            loading.disableProgress();
            loading.updateStatus("Download Complete");
        });
        try{
            Thread.sleep(500);
        } catch (InterruptedException e){
          CleanroomRelauncher.LOGGER.warn("Interrupted thread sleep",e);
        }

    }

}

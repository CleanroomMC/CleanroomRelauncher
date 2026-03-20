package com.cleanroommc.relauncher.download;

import com.cleanroommc.relauncher.CleanroomRelauncher;
import com.cleanroommc.relauncher.gui.LoadingGUI;
import org.apache.commons.io.FileUtils;

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

    private final List<ForkJoinTask> downloads = new ArrayList<>();

    public void from(String source, File destination) {
        URI uri;
        URL url;
        try {
            uri = URI.create(source);
            url = uri.toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(String.format("Unable to construct url %s", source), e);
        }
        this.downloads.add(ForkJoinPool.commonPool().submit(() -> {
            try {
                FileUtils.copyURLToFile(url, destination);
                CleanroomRelauncher.LOGGER.debug("Downloaded {} to {}", uri.toString(), destination.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(String.format("Unable to download %s to %s", url, destination), e);
            }
        }));
    }

    public void immediatelyFrom(String source, File destination) {
        this.from(source, destination);
        try {
            this.downloads.remove(this.downloads.size() - 1).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Unable to complete download", e);
        }
    }

    public void blockUntilFinished() {
        int total = this.downloads.size();
        int completed = 0;
        int last = 0;
        LoadingGUI loading = new LoadingGUI();
        loading.enableProgress();
        loading.updateStatus("Downloading Cleanroom files and libraries...");
        for (Future download : this.downloads) {
            loading.show();
            try {
                download.get();
                completed++;
                int percentage = (completed * 100) / total;
                if (percentage % 10 == 0 && last != percentage) {
                    last = percentage;
                    loading.updateStatus("Download Progress: "+completed+" / "+total+" | "+percentage+"% completed.");
                    loading.setProgress(percentage);
                    CleanroomRelauncher.LOGGER.info("Download Progress: {} / {} | {}% completed.", completed, total, percentage);
                }
            } catch (InterruptedException | ExecutionException e) {
                loading.close();
                throw new RuntimeException("Unable to complete download", e);
            }
        }
        loading.disableProgress();
        loading.updateStatus("Download Complete");
        try{
            Thread.sleep(500);
        } catch (InterruptedException e){
          CleanroomRelauncher.LOGGER.warn("Interrupted thread sleep",e);
          loading.close();
        }
        loading.close();
        loading.updateStatus("Initialising..");

    }

}

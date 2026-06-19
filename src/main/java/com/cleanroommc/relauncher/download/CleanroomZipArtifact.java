package com.cleanroommc.relauncher.download;

import com.cleanroommc.relauncher.download.cache.CleanroomCache;
import com.cleanroommc.relauncher.util.CacheUtils;

import java.io.IOException;

public interface CleanroomZipArtifact {

    void install(String url, String expectedHash, CacheUtils.HashAlgorithm algo) throws IOException;

    void extract(CleanroomCache cache) throws IOException;

}

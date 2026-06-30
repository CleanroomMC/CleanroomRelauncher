package com.cleanroommc.relauncher.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

public class CacheUtils {

    public static File deleteFile(File file) {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        if (file.exists()) {
            file.delete();
        }
        return file;
    }

    public static File replaceWithEmptyFile(File file) throws IOException {
        deleteFile(file);
        file.createNewFile();
        return file;
    }

    public static boolean isFileCorrupt(File file, String expectedHash, HashAlgorithm algo) throws IOException {
        return !file.exists() || !expectedHash.equalsIgnoreCase(hash(file, algo));
    }

    public static void updateHash(File target, HashAlgorithm algo) throws IOException {
        updateHash(target, hash(target, algo), new File(target.getAbsolutePath() + algo.fileExtension));
    }

    public static String hash(File target, HashAlgorithm algo) throws IOException {
        return Files.asByteSource(target).hash(algo.hashFunction).toString();
    }

    private static void updateHash(File target, String hash, File cache) throws IOException {
        if (target.exists()) {
            Files.write(hash.getBytes(), cache);
        } else if (cache.exists()) {
            cache.delete();
        }
    }

    private CacheUtils() { }

    public enum HashAlgorithm {

        MD5(Hashing.md5(), ".md5"),
        SHA1(Hashing.sha1(), ".sha1"),
        SHA256(Hashing.sha256(), ".sha256"),
        SHA384(Hashing.sha384(), ".sha384"),
        SHA512(Hashing.sha512(), ".sha512");

        private final HashFunction hashFunction;
        private final String fileExtension;

        HashAlgorithm(HashFunction hashFunction, String fileExtension) {
            this.hashFunction = hashFunction;
            this.fileExtension = fileExtension;
        }

        public HashFunction getHashFunction() {
            return hashFunction;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public String getHash(Function<HashFunction, HashCode> function) {
            return function.apply(hashFunction).toString();
        }

        public static HashAlgorithm fromName(String name) {
            if (name == null) {
                return null;
            }
            switch (name.trim().toLowerCase()) {
                case "md5":
                    return MD5;
                case "sha1":
                case "sha-1":
                    return SHA1;
                case "sha256":
                case "sha-256":
                    return SHA256;
                case "sha384":
                case "sha-384":
                    return SHA384;
                case "sha512":
                case "sha-512":
                    return SHA512;
                default:
                    return null;
            }
        }
    }

}

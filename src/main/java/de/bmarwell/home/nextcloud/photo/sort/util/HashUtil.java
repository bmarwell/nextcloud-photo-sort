/*
 * Copyright (C) Apache-2.0 OR EUPL-1.2.
 */
package de.bmarwell.home.nextcloud.photo.sort.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.XXHash32;
import picocli.CommandLine.Model.CommandSpec;

/// Utility class for hashing operations.
public final class HashUtil {

    private HashUtil() {
        // utility class
    }

    public static String calcHash(CommandSpec spec, Path inputFile) {
        try {
            return calculateXx32Hash(inputFile);
        } catch (IOException ioException) {
            spec.commandLine()
                    .getErr()
                    .println("XXHash32 failed for [" + inputFile + "]: " + ioException.getMessage()
                            + " - falling back to SHA1");

            // Attempts SHA1 fallback; throws exception combining failures
            try {
                return calculateSha1Hash(inputFile);
            } catch (IOException ioEx2) {
                spec.commandLine().getErr().println("SHA1 also failed for [" + inputFile + "]: " + ioEx2.getMessage());

                final UncheckedIOException uncheckedIOException = new UncheckedIOException(ioEx2);
                uncheckedIOException.addSuppressed(ioException);

                throw uncheckedIOException;
            }
        }
    }

    /// Calculates SHA1 hash; returns leading hex characters.
    ///
    /// @param inputFile the file to hash
    /// @return the first 8 characters of the SHA1 hash, or `null` if the input file is `null`
    /// @throws IOException if the file cannot be read
    static String calculateSha1Hash(Path inputFile) throws IOException {
        final MessageDigest sha1Digest = DigestUtils.getSha1Digest();
        try (var inputStream = Files.newInputStream(inputFile)) {
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                sha1Digest.update(buffer, 0, read);
            }
        }
        final byte[] digest = sha1Digest.digest();
        final String hexString = Hex.encodeHexString(digest);

        if (hexString.isEmpty()) {
            throw new IOException("could not calculate hash, empty sha1 digest for file [" + inputFile + "]");
        }

        if (hexString.length() >= 8) {
            return hexString.substring(0, 8);
        }

        return hexString;
    }

    /// Calculates lowercase hexadecimal XXH32 hash from file contents.
    ///
    /// @param inputFile the file to hash
    /// @return the first 8 characters of the XXH32 hash, or `null` if the input file is `null`
    /// @throws IOException if the file cannot be read
    static String calculateXx32Hash(Path inputFile) throws IOException {
        XXHash32 xxHash32 = new XXHash32();
        try (var inputStream = Files.newInputStream(inputFile)) {
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                xxHash32.update(buffer, 0, read);
            }
        }
        long raw = xxHash32.getValue();
        BigInteger unsigned32 = BigInteger.valueOf(raw).and(BigInteger.valueOf(0xFFFFFFFFL));

        String hex = unsigned32.toString(16).toLowerCase(Locale.ROOT);
        String fileHash = String.format(Locale.ROOT, "%8s", hex).replace(' ', '0');

        if (fileHash.isEmpty()) {
            throw new IOException("could not calculate hash, empty xxh32 digest for file [" + inputFile + "]");
        }

        if (fileHash.length() >= 8) {
            return fileHash.substring(0, 8);
        }

        return fileHash;
    }
}

/*
 * Copyright (C) Apache-2.0 OR EUPL-1.2.
 */
package de.bmarwell.home.nextcloud.photo.sort.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HashUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void testCalculateXx32Hash() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        String hash = HashUtil.calculateXx32Hash(file);

        assertNotNull(hash);
        assertEquals(8, hash.length());
        // xxhash32 of "hello world" (UTF-8) is 0xcebb6622
        assertEquals("cebb6622", hash);
    }

    @Test
    void testCalculateSha1Hash() throws IOException {
        Path file = tempDir.resolve("test_sha1.txt");
        Files.writeString(file, "hello world");

        String hash = HashUtil.calculateSha1Hash(file);

        assertNotNull(hash);
        assertEquals(8, hash.length());
        // sha1 of "hello world" is 2aae6c35c94fcfb415dbe95f408b9ce91ee846ed
        assertEquals("2aae6c35", hash);
    }

    @Test
    void testCalculateXx32Hash_padding() throws IOException {
        // Find a string that results in a short hex hash to test padding
        // "test" xxhash32 is 3e20235a -> 8 chars, no padding needed.
        // Let's just trust the padding logic if we can't easily find one,
        // or just use a known value if I had one.
        // Actually, 0 would be "00000000"

        Path file = tempDir.resolve("empty.txt");
        Files.write(file, new byte[0]);
        String hash = HashUtil.calculateXx32Hash(file);

        assertEquals(8, hash.length());
        // xxhash32 of empty byte array is 02cc5d05
        assertEquals("02cc5d05", hash);
    }
}

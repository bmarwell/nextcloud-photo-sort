/*
 * Copyright (C) Apache-2.0 OR EUPL-1.2.
 */
package de.bmarwell.home.nextcloud.photo.sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class NextcloudPhotoSortTest {

    @TempDir
    Path tempDir;

    @Test
    void testGetTargetPath_formatsCorrectly() throws IOException {
        // given
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path inputFile = inputDir.resolve("test.jpg");
        Files.writeString(inputFile, "test content");

        NextcloudPhotoSort app = new NextcloudPhotoSort();
        new CommandLine(app).parseArgs("-i", inputDir.toString(), "-o", outputDir.toString());
        app.spec = new CommandLine(app).getCommandSpec();

        // January 5th, 2023 at 13:05:07
        ZonedDateTime date = LocalDateTime.of(2_023, 1, 5, 13, 5, 7).atZone(ZoneId.systemDefault());

        // when
        Path targetPath = app.getTargetPath(inputFile, date);

        // then
        // Expected format: output/2023/01/2023-01-05T130507_hash.jpg
        // Note: Hash for "test content" will be calculated.
        // We mainly care about the directory structure here.

        Path expectedDir = outputDir.resolve("2023").resolve("01");
        assertEquals(expectedDir, targetPath.getParent());

        String fileName = targetPath.getFileName().toString();
        assertTrue(
                fileName.startsWith("2023-01-05T130507_"),
                "Filename should start with date-time pattern, but was: " + fileName);
        assertTrue(fileName.endsWith(".jpg"));
    }

    @Test
    void testMoveAsync_deletesSourceIfTargetExists() throws IOException {
        // given
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path sourceFile = inputDir.resolve("file1.jpg");
        Files.writeString(sourceFile, "content1");

        Path targetFile = outputDir.resolve("target.jpg");
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, "content2");

        NextcloudPhotoSort app = new NextcloudPhotoSort();
        new CommandLine(app).parseArgs("-i", inputDir.toString(), "-o", outputDir.toString());
        app.spec = new CommandLine(app).getCommandSpec();

        NextcloudPhotoSort.InOut io = new NextcloudPhotoSort.InOut(sourceFile, targetFile, true);

        // when
        app.moveAsync(io);

        // then
        // New behavior: target is NOT overwritten, source is deleted
        assertTrue(Files.exists(targetFile));
        assertTrue(Files.readString(targetFile).contains("content2"));
        assertFalse(Files.exists(sourceFile));
    }

    @Test
    void testMoveAsync_respectsDryRun() throws IOException {
        // given
        Path inputDir = tempDir.resolve("input");
        Files.createDirectories(inputDir);
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path sourceFile = inputDir.resolve("file1.jpg");
        Files.writeString(sourceFile, "content1");

        Path targetFile = outputDir.resolve("target.jpg");
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, "content2");

        NextcloudPhotoSort app = new NextcloudPhotoSort();
        new CommandLine(app).parseArgs("-i", inputDir.toString(), "-o", outputDir.toString(), "--dry-run");
        app.spec = new CommandLine(app).getCommandSpec();

        NextcloudPhotoSort.InOut io = new NextcloudPhotoSort.InOut(sourceFile, targetFile, true);

        // when
        app.moveAsync(io);

        // then
        assertTrue(Files.exists(targetFile));
        assertTrue(Files.exists(sourceFile));
    }
}

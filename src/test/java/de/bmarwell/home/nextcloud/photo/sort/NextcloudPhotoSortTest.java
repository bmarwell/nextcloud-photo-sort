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

    @Test
    void testCollectTasks_obeysMaxFiles() throws Exception {
        // given
        Path inputDir = tempDir.resolve("input-max");
        Files.createDirectories(inputDir);
        Path outputDir = tempDir.resolve("output-max");
        Files.createDirectories(outputDir);

        for (int i = 0; i < 10; i++) {
            Files.writeString(inputDir.resolve("file" + i + ".jpg"), "content" + i);
        }

        NextcloudPhotoSort app = new NextcloudPhotoSort() {
            @Override
            Path getTargetPath(Path inputFile, ZonedDateTime dateOriginalInstant) {
                return outputDir.resolve("sorted").resolve(inputFile.getFileName());
            }

            @Override
            InOut processFileAsync(Path path) {
                // Mock behavior to avoid actual metadata extraction
                this.processedFilesCount.incrementAndGet();
                return new InOut(path, outputDir.resolve("sorted").resolve(path.getFileName()), true);
            }
        };
        // Set maxFiles to 5
        new CommandLine(app).parseArgs("-i", inputDir.toString(), "-o", outputDir.toString(), "-m", "5");
        app.spec = new CommandLine(app).getCommandSpec();

        // when
        app.call();

        // then
        // check how many files are in outputDir (recursively)
        try (var stream = Files.walk(outputDir)) {
            long count = stream.filter(Files::isRegularFile).count();
            assertEquals(5, count, "Should have moved exactly 5 files");
        }
    }

    @Test
    void testCollectTasks_movesUnsortedFiles() throws Exception {
        // given
        Path inputDir = tempDir.resolve("input-unsorted");
        Files.createDirectories(inputDir);
        Path outputDir = tempDir.resolve("output-unsorted");
        Files.createDirectories(outputDir);

        // One file with date (mocked), 20 without
        Files.writeString(inputDir.resolve("valid.jpg"), "valid");
        for (int i = 0; i < 20; i++) {
            Files.writeString(inputDir.resolve("invalid" + i + ".jpg"), "invalid" + i);
        }

        NextcloudPhotoSort app = new NextcloudPhotoSort() {
            @Override
            InOut processFileAsync(Path path) {
                this.processedFilesCount.incrementAndGet();
                if ("valid.jpg".equals(path.getFileName().toString())) {
                    return new InOut(path, outputDir.resolve("2023/01/valid.jpg"), true);
                } else {
                    return toUnsorted(path);
                }
            }
        };

        // maxFiles = 5.
        // It should fork at most 5 files.
        new CommandLine(app).parseArgs("-i", inputDir.toString(), "-o", outputDir.toString(), "-m", "5");
        app.spec = new CommandLine(app).getCommandSpec();

        // when
        app.call();

        // then
        // We expect EXACTLY 5 files to have been moved in total because of .limit(5)
        try (var stream = Files.walk(outputDir)) {
            long movedToOutput = stream.filter(Files::isRegularFile).count();
            // Actually, some go to inputDir/unsorted
            try (var stream2 = Files.walk(inputDir.resolve("unsorted"))) {
                long movedToUnsorted = stream2.filter(Files::isRegularFile).count();
                assertEquals(5, movedToOutput + movedToUnsorted, "Should have moved exactly 5 files in total");
            }
        }
    }
}

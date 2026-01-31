/*
 * Copyright (C) Apache-2.0 OR EUPL-1.2.
 */
package de.bmarwell.home.nextcloud.photo.sort;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import de.bmarwell.home.nextcloud.photo.sort.util.CreationDateUtil;
import de.bmarwell.home.nextcloud.photo.sort.util.HashUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@SuppressWarnings("ALL")
public class NextcloudPhotoSort implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Display this help message and exit.")
    boolean usageHelpRequested;

    // "/bmarwell/files/Bens Platte/Bilder"
    @Option(
            names = {"-i", "--input"},
            required = true,
            description = """
        Input directory.
        Where to read the unsorted files from.""")
    Path inputDirectory;

    @Option(
            names = {"-o", "--output"},
            required = true,
            description = """
        Output directory.
        This is where the folders named $YEAR/$MONTH go.""")
    Path outputDirectory;

    @Option(
            names = {"-m", "--max"},
            required = true,
            description = """
        Maximum number of files to process.
        File without date are not counted towards this limit""",
            defaultValue = "500")
    int maxFiles;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Option(
            names = {"-p", "--postscript"},
            required = false,
            description = """
        Script to execute when this tool is finished""")
    Optional<Path> postscript;

    @Option(
            names = {"-v", "--verbose"},
            description = """
            Print actions""")
    boolean verbose;

    @Option(
            names = {"-d", "--dry-run"},
            description = "Do not perform any file operations")
    boolean dryRun;

    // internal state
    private final List<Path> files = Collections.synchronizedList(new ArrayList<>());

    private final AtomicInteger validFiles = new AtomicInteger(0);

    private final Semaphore semaphore = new Semaphore(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

    static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new NextcloudPhotoSort());
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    /**
     * Entry point of the photo sort application.
     *
     * <p>The main logic is divided into two phases:</p>
     * 1.  **Collection phase**: Scan the input directory and determine the target path for each file.
     * 2.  **Execution phase**: Perform the actual file movements.
     *
     * <p>We do it this way to first gather all required information and validate the work before
     * touching any files on disk. This allows for better error handling and "dry-run" capabilities.</p>
     *
     * @return 0 on success
     * @throws Exception if any unhandled error occurs during processing
     */
    @Override
    public Integer call() throws Exception {
        final List<InOut> inOutTargets = collectTasks();

        this.spec.commandLine().getOut().println("Moving " + inOutTargets.size() + " files.");

        performMoves(inOutTargets);

        this.spec.commandLine().getOut().println("Finished nextcloud-photo-sort");

        return 0;
    }

    /// Collects [InOut] tasks by scanning the input directory.
    ///
    /// This method filters for media files and processes them asynchronously
    /// until the [#maxFiles] limit of valid files is reached.
    /// All discovered files (valid or not) are included in the result.
    ///
    /// @return a list of [InOut] records representing planned file moves
    /// @throws InterruptedException if task execution is interrupted
    private List<InOut> collectTasks() throws InterruptedException {
        try (var scope = StructuredTaskScope.open()) {
            final List<StructuredTaskScope.Subtask<InOut>> tasks = new ArrayList<>();

            try (Stream<Path> inputFiles = Files.list(this.inputDirectory)) {
                Iterable<Path> iterable = inputFiles::iterator;
                for (Path nextFile : iterable) {
                    if (!Files.isRegularFile(nextFile) || !isMediaFile(nextFile)) {
                        continue;
                    }

                    tasks.add(scope.fork(() -> processFileAsync(nextFile)));

                    // We stop forking new tasks once we have submitted enough files that are likely to be valid.
                    // Note: validFiles is incremented in processFileAsync, so this limit is a bit "fuzzy"
                    // due to concurrent execution, but it serves the purpose of limiting the work.
                    if (this.validFiles.get() >= this.maxFiles) {
                        break;
                    }
                }
            } catch (IOException ioException) {
                throw new UncheckedIOException("Could not list files in " + this.inputDirectory, ioException);
            }

            scope.join();

            return tasks.stream().map(StructuredTaskScope.Subtask::get).toList();
        }
    }

    /// Asynchronously executes the file moves described by the provided [InOut] records.
    ///
    /// @param inOutTargets the list of file moves to perform
    /// @throws InterruptedException if move execution is interrupted
    private void performMoves(List<InOut> inOutTargets) throws InterruptedException {
        try (var moveScope = StructuredTaskScope.open()) {
            inOutTargets.forEach(io -> moveScope.fork(() -> moveAsync(io)));

            moveScope.join();
        }
    }

    /// Moves a file to its destination.
    ///
    /// <p>If the target file already exists, it is assumed to be the same file
    /// (since the filename contains the content hash) and the source file is deleted instead.</p>
    ///
    /// @param io the [InOut] record containing source and target paths
    /// @return the same [InOut] record
    InOut moveAsync(InOut io) {
        if (io.in().equals(io.out())) {
            // no change -- how can that be??
            return io;
        }

        final Path outPath = io.out().getParent();

        if (!Files.exists(outPath)) {
            try {
                this.spec
                        .commandLine()
                        .getErr()
                        .println("Creating directory [" + this.outputDirectory.relativize(outPath) + "]");
                if (!this.dryRun) {
                    Files.createDirectories(outPath);
                }
            } catch (IOException ioException) {
                // log error and continue
                this.spec.commandLine().getErr().println("Could not create directory [" + outPath + "].");
                this.spec
                        .commandLine()
                        .getErr()
                        .println("Error creating directory [" + outPath + "]: " + ioException.getMessage());
                return io;
            }
        }

        if (!Files.exists(outPath)) {
            // error creating directory: continue
            this.spec
                    .commandLine()
                    .getErr()
                    .println("Could not create directory [" + outPath + "], but mkdir did not error either");
            return io;
        }

        if (Files.exists(io.out())) {
            if (this.verbose) {
                this.spec
                        .commandLine()
                        .getOut()
                        .println("Target file [" + io.out() + "] already exists. Deleting source [" + io.in() + "].");
            }

            try {
                if (!this.dryRun) {
                    Files.delete(io.in());
                }
            } catch (IOException ioex) {
                this.spec
                        .commandLine()
                        .getErr()
                        .println("Could not delete source file [" + io.in() + "] after finding target [" + io.out()
                                + "] already exists: " + ioex.getMessage());
            }

            return io;
        }

        try {
            if (!this.dryRun) {
                Files.move(io.in(), io.out());
            }
            if (this.verbose) {
                if (this.dryRun) {
                    this.spec.commandLine().getOut().println("Would move [" + io.in() + "] to [" + io.out() + "].");
                } else {
                    this.spec.commandLine().getOut().println("Moved [" + io.in() + "] to [" + io.out() + "].");
                }
            }
        } catch (IOException ioex) {
            this.spec
                    .commandLine()
                    .getErr()
                    .println(
                            "Could not move file [" + io.in() + "] to target [" + io.out() + "]: " + ioex.getMessage());
        }

        return io;
    }

    /// Extracts metadata from the given file and determines its destination.
    ///
    /// If valid creation date metadata is found, the file is moved to a date-based directory.
    /// Otherwise, it is moved to the "unsorted" directory.
    ///
    /// @param path the path to the file to process
    /// @return an [InOut] record representing the planned move
    private InOut processFileAsync(Path path) {
        try {
            final Metadata metadata = ImageMetadataReader.readMetadata(path.toFile());
            final @Nullable ZonedDateTime dateOriginalInstant = CreationDateUtil.getCreationDate(metadata);

            if (dateOriginalInstant == null) {
                // no exif data: move to unsorted
                this.spec.commandLine().getErr().println("No creation date information for [" + path + "]");
                return toUnsorted(path);
            }

            this.validFiles.incrementAndGet();

            final Path targetPath = getTargetPath(path, dateOriginalInstant);

            return new InOut(path, targetPath, true);
        } catch (IOException | ImageProcessingException ioEx) {
            // error reading exif data: move to unsorted
            this.spec.commandLine().getErr().println("Error reading metadata for [" + path + "]: " + ioEx.getMessage());
            return toUnsorted(path);
        }
    }

    /// Calculates a target path based on date and file content hash.
    ///
    /// The file name is formatted as `YYYY-MM-DDTHHmmSS_hash.ext`.
    /// The file is placed in a directory structure of `YEAR/MONTH` within the output directory.
    ///
    /// @param inputFile the source file
    /// @param dateOriginalInstant the creation date to use for naming and directory placement
    /// @return the calculated target path
    Path getTargetPath(Path inputFile, ZonedDateTime dateOriginalInstant) {
        String hash = HashUtil.calcHash(this.spec, inputFile);
        String extension = inputFile
                .getFileName()
                .toString()
                .substring(inputFile.getFileName().toString().lastIndexOf('.') + 1);

        final String targetFileName = String.format(
                Locale.ROOT,
                "%04d-%02d-%02dT%02d%02d%02d_%s.%s",
                dateOriginalInstant.getYear(),
                dateOriginalInstant.getMonthValue(),
                dateOriginalInstant.getDayOfMonth(),
                // T
                // HMS
                dateOriginalInstant.getHour(),
                dateOriginalInstant.getMinute(),
                dateOriginalInstant.getSecond(),
                // _hash
                hash,
                extension.toLowerCase(Locale.ROOT));

        return outputDirectory
                .resolve(String.format(Locale.ROOT, "%04d", dateOriginalInstant.getYear()))
                .resolve(String.format(Locale.ROOT, "%02d", dateOriginalInstant.getMonthValue()))
                .resolve(targetFileName);
    }

    private InOut toUnsorted(Path path) {
        final Path unsortedDir = this.inputDirectory.resolve("unsorted");
        final Path targetFile = unsortedDir.resolve(path.getFileName());

        // Files without valid date are moved to a special "unsorted" directory
        // to keep the input directory clean.
        return new InOut(path, targetFile, false);
    }

    private static boolean isMediaFile(Path p) {
        final String fileNameLower = p.toString().toLowerCase(Locale.ROOT);

        return fileNameLower.endsWith(".jpg")
                || fileNameLower.endsWith(".jpeg")
                || fileNameLower.endsWith(".png")
                || fileNameLower.endsWith(".mp4");
    }

    /**
     * Represents a mapping between an input file and its intended output path.
     *
     * @param in the source path of the file
     * @param out the destination path for the file
     * @param hasValidDate whether the file contains valid EXIF/creation date metadata
     */
    record InOut(Path in, Path out, boolean hasValidDate) {}
}

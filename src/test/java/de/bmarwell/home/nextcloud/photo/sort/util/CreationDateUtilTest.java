/*
 * Copyright (C) Apache-2.0 OR EUPL-1.2.
 */
package de.bmarwell.home.nextcloud.photo.sort.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreationDateUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void testGetCreationDate_FromRealFile() throws IOException, ImageProcessingException {
        // Minimal valid JPG (1x1 pixel)
        byte[] minimalJpg = new byte[] {
            (byte) 0xFF,
            (byte) 0xD8,
            (byte) 0xFF,
            (byte) 0xDB,
            0x00,
            0x43,
            0x00,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            (byte) 0xFF,
            (byte) 0xC0,
            0x00,
            0x0B,
            0x08,
            0x00,
            0x01,
            0x00,
            0x01,
            0x01,
            0x01,
            0x11,
            0x00,
            (byte) 0xFF,
            (byte) 0xC4,
            0x00,
            0x14,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xC4,
            0x00,
            0x14,
            0x10,
            0x01,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xDA,
            0x00,
            0x08,
            0x01,
            0x01,
            0x00,
            0x00,
            0x3F,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xD9
        };
        Path jpgFile = tempDir.resolve("test.jpg");
        Files.write(jpgFile, minimalJpg);

        Metadata metadata = ImageMetadataReader.readMetadata(jpgFile.toFile());
        assertNotNull(metadata);
        // This file has no creation date
        assertNull(CreationDateUtil.getCreationDate(metadata));
    }

    @Test
    void testGetCreationDate_ExifSubIFD_Original() {
        Metadata metadata = new Metadata();
        ExifSubIFDDirectory directory = new ExifSubIFDDirectory();
        LocalDateTime localDateTime = LocalDateTime.of(2_023, 1, 1, 12, 0, 0);
        Date date = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());

        directory.setDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, date);
        metadata.addDirectory(directory);

        ZonedDateTime result = CreationDateUtil.getCreationDate(metadata);

        assertNotNull(result);
        assertEquals(2_023, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(1, result.getDayOfMonth());
    }

    @Test
    void testGetCreationDate_ExifSubIFD_WithTimeZone() {
        Metadata metadata = new Metadata();
        ExifSubIFDDirectory directory = new ExifSubIFDDirectory();
        LocalDateTime localDateTime = LocalDateTime.of(2_023, 1, 1, 12, 0, 0);

        // Use a fixed timezone to avoid system dependency in test if possible,
        // but CreationDateUtil uses TimeZone.getTimeZone("GMT" + tzString)
        String tzString = "+02:00";
        TimeZone tz = TimeZone.getTimeZone("GMT" + tzString);
        Date date = Date.from(localDateTime.atZone(tz.toZoneId()).toInstant());

        directory.setDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, date);
        directory.setString(ExifSubIFDDirectory.TAG_TIME_ZONE_ORIGINAL, tzString);
        metadata.addDirectory(directory);

        ZonedDateTime result = CreationDateUtil.getCreationDate(metadata);

        assertNotNull(result);
        assertEquals(ZoneId.of("GMT+02:00").normalized(), result.getZone().normalized());
        assertEquals(12, result.getHour());
    }

    @Test
    void testGetCreationDate_NoMetadata() {
        Metadata metadata = new Metadata();
        ZonedDateTime result = CreationDateUtil.getCreationDate(metadata);
        assertNull(result);
    }

    @Test
    void testGetCreationDate_ExifIFD0() {
        Metadata metadata = new Metadata();
        ExifIFD0Directory directory = new ExifIFD0Directory();
        LocalDateTime localDateTime = LocalDateTime.of(2_022, 5, 5, 10, 30, 0);
        Date date = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());

        directory.setDate(ExifIFD0Directory.TAG_DATETIME, date);
        metadata.addDirectory(directory);

        ZonedDateTime result = CreationDateUtil.getCreationDate(metadata);

        assertNotNull(result);
        assertEquals(2_022, result.getYear());
        assertEquals(5, result.getMonthValue());
    }

    @Test
    void testGetCreationDate_Mp4() {
        Metadata metadata = new Metadata();
        Mp4Directory directory = new Mp4Directory();
        LocalDateTime localDateTime = LocalDateTime.of(2_021, 3, 3, 15, 45, 0);
        Date date = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());

        directory.setDate(Mp4Directory.TAG_CREATION_TIME, date);
        metadata.addDirectory(directory);

        ZonedDateTime result = CreationDateUtil.getCreationDate(metadata);

        assertNotNull(result);
        assertEquals(2_021, result.getYear());
        assertEquals(3, result.getMonthValue());
    }

    @Test
    void testGetCreationDate_Gps() {
        Metadata metadata = new Metadata();
        GpsDirectory directory = new GpsDirectory();

        directory.setString(GpsDirectory.TAG_DATE_STAMP, "2020:08:08");
        directory.setRationalArray(GpsDirectory.TAG_TIME_STAMP, new com.drew.lang.Rational[] {
            new com.drew.lang.Rational(12, 1), new com.drew.lang.Rational(0, 1), new com.drew.lang.Rational(0, 1)
        });
        metadata.addDirectory(directory);

        ZonedDateTime result = CreationDateUtil.getCreationDate(metadata);

        assertNotNull(result);
        assertEquals(2_020, result.getYear());
        assertEquals(8, result.getMonthValue());
        assertEquals(12, result.getHour());
    }
}

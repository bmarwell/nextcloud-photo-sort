/*
 * Copyright (C) Apache-2.0 OR EUPL-1.2.
 */
package de.bmarwell.home.nextcloud.photo.sort.util;

import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;

public final class CreationDateUtil {

    private CreationDateUtil() {
        // util
    }

    public static @Nullable ZonedDateTime getCreationDate(Metadata metadata) {
        // 1. Try Exif SubIFD (most specific for photos)
        ExifSubIFDDirectory subIfdDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (subIfdDirectory != null) {
            ZonedDateTime zdt = getZonedDateTime(
                    subIfdDirectory,
                    ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
                    ExifSubIFDDirectory.TAG_TIME_ZONE_ORIGINAL);
            if (zdt != null) {
                return zdt;
            }

            zdt = getZonedDateTime(
                    subIfdDirectory,
                    ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED,
                    ExifSubIFDDirectory.TAG_TIME_ZONE_DIGITIZED);
            if (zdt != null) {
                return zdt;
            }
        }

        // 2. Try Exif IFD0
        ExifIFD0Directory ifd0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (ifd0Directory != null) {
            ZonedDateTime zdt =
                    getZonedDateTime(ifd0Directory, ExifIFD0Directory.TAG_DATETIME, ExifIFD0Directory.TAG_TIME_ZONE);
            if (zdt != null) {
                return zdt;
            }
            zdt = getZonedDateTime(
                    ifd0Directory, ExifIFD0Directory.TAG_DATETIME_ORIGINAL, ExifIFD0Directory.TAG_TIME_ZONE_ORIGINAL);
            if (zdt != null) {
                return zdt;
            }
        }

        // 3. Try MP4
        Mp4Directory mp4Directory = metadata.getFirstDirectoryOfType(Mp4Directory.class);
        if (mp4Directory != null) {
            Date date = mp4Directory.getDate(Mp4Directory.TAG_CREATION_TIME);
            if (date != null) {
                return date.toInstant().atZone(ZoneId.systemDefault());
            }
        }

        // 4. Try QuickTime (MOV)
        QuickTimeDirectory quickTimeDirectory = metadata.getFirstDirectoryOfType(QuickTimeDirectory.class);
        if (quickTimeDirectory != null) {
            Date date = quickTimeDirectory.getDate(QuickTimeDirectory.TAG_CREATION_TIME);
            if (date != null) {
                return date.toInstant().atZone(ZoneId.systemDefault());
            }
        }

        // 5. Try GPS date/time (GPS is typically UTC)
        GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gpsDirectory != null) {
            Date date = gpsDirectory.getGpsDate();
            if (date != null) {
                return date.toInstant().atZone(ZoneOffset.UTC);
            }
        }

        return null;
    }

    private static @Nullable ZonedDateTime getZonedDateTime(Directory directory, int tagDate, int tagTz) {
        String tzString = directory.getString(tagTz);
        TimeZone timeZone = null;
        if (tzString != null) {
            timeZone = TimeZone.getTimeZone("GMT" + tzString);
        }

        Date date = directory.getDate(tagDate, timeZone);
        if (date == null) {
            return null;
        }

        if (timeZone != null) {
            return date.toInstant().atZone(timeZone.toZoneId());
        }

        return date.toInstant().atZone(ZoneId.systemDefault());
    }
}

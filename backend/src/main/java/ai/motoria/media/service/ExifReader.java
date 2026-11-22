package ai.motoria.media.service;

import java.nio.ByteOrder;

/**
 * Reads the orientation and GPS presence from the APP1 EXIF segment of a JPEG without any library.
 * Only IFD0 is inspected; malformed data yields defaults.
 */
public final class ExifReader {

    private static final int MARKER_PREFIX = 0xFF;
    private static final int MARKER_APP1 = 0xE1;
    private static final int MARKER_SOS = 0xDA;
    private static final int MARKER_EOI = 0xD9;
    private static final int TAG_ORIENTATION = 0x0112;
    private static final int TAG_GPS_IFD = 0x8825;
    private static final int[] EXIF_HEADER = {0x45, 0x78, 0x69, 0x66, 0x00, 0x00};
    private static final int IFD_ENTRY_SIZE = 12;

    private ExifReader() {
    }

    public static ExifInfo read(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 4) {
            return ExifInfo.NONE;
        }
        int offset = 2;
        while (offset + 4 <= jpeg.length) {
            if ((jpeg[offset] & 0xFF) != MARKER_PREFIX) {
                return ExifInfo.NONE;
            }
            int marker = jpeg[offset + 1] & 0xFF;
            if (marker == MARKER_SOS || marker == MARKER_EOI) {
                return ExifInfo.NONE;
            }
            int length = ((jpeg[offset + 2] & 0xFF) << 8) | (jpeg[offset + 3] & 0xFF);
            int segmentEnd = offset + 2 + length;
            if (length < 2 || segmentEnd > jpeg.length) {
                return ExifInfo.NONE;
            }
            if (marker == MARKER_APP1 && length >= 8 && hasExifHeader(jpeg, offset + 4)) {
                return parseTiff(jpeg, offset + 10, segmentEnd);
            }
            offset = segmentEnd;
        }
        return ExifInfo.NONE;
    }

    private static boolean hasExifHeader(byte[] data, int start) {
        if (start + EXIF_HEADER.length > data.length) {
            return false;
        }
        for (int index = 0; index < EXIF_HEADER.length; index++) {
            if ((data[start + index] & 0xFF) != EXIF_HEADER[index]) {
                return false;
            }
        }
        return true;
    }

    private static ExifInfo parseTiff(byte[] data, int start, int end) {
        if (start + 8 > end) {
            return new ExifInfo(true, 1, false);
        }
        ByteOrder order;
        if ((data[start] & 0xFF) == 0x49 && (data[start + 1] & 0xFF) == 0x49) {
            order = ByteOrder.LITTLE_ENDIAN;
        } else if ((data[start] & 0xFF) == 0x4D && (data[start + 1] & 0xFF) == 0x4D) {
            order = ByteOrder.BIG_ENDIAN;
        } else {
            return new ExifInfo(true, 1, false);
        }
        long ifdOffset = readInt(data, start + 4, order) & 0xFFFFFFFFL;
        if (ifdOffset > end - start) {
            return new ExifInfo(true, 1, false);
        }
        int ifdStart = start + (int) ifdOffset;
        if (ifdStart + 2 > end) {
            return new ExifInfo(true, 1, false);
        }
        int count = readShort(data, ifdStart, order);
        int orientation = 1;
        boolean gps = false;
        for (int index = 0; index < count; index++) {
            int entry = ifdStart + 2 + index * IFD_ENTRY_SIZE;
            if (entry + IFD_ENTRY_SIZE > end) {
                break;
            }
            int tag = readShort(data, entry, order);
            if (tag == TAG_ORIENTATION) {
                orientation = readShort(data, entry + 8, order);
            } else if (tag == TAG_GPS_IFD) {
                gps = true;
            }
        }
        if (orientation < 1 || orientation > 8) {
            orientation = 1;
        }
        return new ExifInfo(true, orientation, gps);
    }

    private static int readShort(byte[] data, int offset, ByteOrder order) {
        int first = data[offset] & 0xFF;
        int second = data[offset + 1] & 0xFF;
        return order == ByteOrder.LITTLE_ENDIAN ? (second << 8) | first : (first << 8) | second;
    }

    private static int readInt(byte[] data, int offset, ByteOrder order) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        int b2 = data[offset + 2] & 0xFF;
        int b3 = data[offset + 3] & 0xFF;
        return order == ByteOrder.LITTLE_ENDIAN
                ? (b3 << 24) | (b2 << 16) | (b1 << 8) | b0
                : (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }
}

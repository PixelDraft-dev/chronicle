package com.chronicle.util;

import com.github.luben.zstd.Zstd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public final class CompressionUtil {

    private static final Logger LOG = Logger.getLogger("Chronicle.Compression");

    private static final byte TAG_ZSTD = 0x5A;
    private static final byte TAG_GZIP = 0x47;


    private static final int ZSTD_LEVEL = 3;

    private static final boolean ZSTD_AVAILABLE;

    static {
        boolean available;
        try {

            Zstd.compress(new byte[0]);
            available = true;
            LOG.info("Zstd native library loaded — using Zstd compression for TDF blobs.");
        } catch (Throwable t) {
            available = false;
            LOG.warning("Zstd native library unavailable (" + t.getMessage()
                    + "). Falling back to GZIP.");
        }
        ZSTD_AVAILABLE = available;
    }

    private CompressionUtil() { }


    public static byte[] compress(byte[] raw) throws IOException {
        if (ZSTD_AVAILABLE) {
            byte[] compressed = Zstd.compress(raw, ZSTD_LEVEL);
            byte[] tagged     = new byte[compressed.length + 1];
            tagged[0]         = TAG_ZSTD;
            System.arraycopy(compressed, 0, tagged, 1, compressed.length);
            return tagged;
        } else {
            return gzipCompress(raw);
        }
    }


    public static byte[] decompress(byte[] tagged) throws IOException {
        if (tagged.length < 2) {
            throw new IOException("Compressed payload too short to contain a tag byte.");
        }
        byte tag    = tagged[0];
        byte[] data = new byte[tagged.length - 1];
        System.arraycopy(tagged, 1, data, 0, data.length);

        return switch (tag) {
            case TAG_ZSTD -> {
                long originalSize = Zstd.decompressedSize(data);
                if (originalSize <= 0 || originalSize > 256 * 1024 * 1024L) {

                    yield Zstd.decompress(data, 32 * 1024 * 1024);
                }
                yield Zstd.decompress(data, (int) originalSize);
            }
            case TAG_GZIP -> gzipDecompress(data);
            default -> throw new IOException(
                    "Unknown compression tag: 0x" + Integer.toHexString(tag & 0xFF));
        };
    }


    public static boolean isZstdAvailable() { return ZSTD_AVAILABLE; }



    private static byte[] gzipCompress(byte[] raw) throws IOException {
        var bos = new ByteArrayOutputStream(raw.length);
        bos.write(TAG_GZIP);           
        try (var gz = new GZIPOutputStream(bos)) {
            gz.write(raw);
        }
        return bos.toByteArray();
    }

    private static byte[] gzipDecompress(byte[] data) throws IOException {
        try (var gz  = new GZIPInputStream(new ByteArrayInputStream(data));
             var bos = new ByteArrayOutputStream()) {
            gz.transferTo(bos);
            return bos.toByteArray();
        }
    }
}

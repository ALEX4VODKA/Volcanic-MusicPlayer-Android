package com.volcanic.musicplayer.decoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

public final class PrivateContainerDecoder {
    static final int BUFFER_SIZE = 64 * 1024;
    private static final int PROBE_SIZE = 64;

    private PrivateContainerDecoder() {
    }

    public static boolean isPrivateContainer(String extension) {
        String value = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return "ncm".equals(value) || "kgm".equals(value) || "vpr".equals(value)
                || "qmc".equals(value) || value.startsWith("qmc")
                || "mflac".equals(value) || "mgg".equals(value);
    }

    public static DecodedAudio decode(File input, String extension) throws Exception {
        String value = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        byte[] payload;
        if ("ncm".equals(value)) {
            payload = NcmDecoder.decode(input);
        } else if ("kgm".equals(value) || "vpr".equals(value)) {
            payload = KgmDecoder.decode(input);
        } else if ("qmc".equals(value) || value.startsWith("qmc") || "mflac".equals(value) || "mgg".equals(value)) {
            payload = QmcDecoder.decode(input);
        } else {
            throw new IOException("Unsupported private container: " + extension);
        }
        return new DecodedAudio(payload, AudioFormatDetector.detect(payload));
    }

    public static String decodeToFile(File input, String extension, File outputFile) throws Exception {
        String value = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        byte[] probe;
        try (FileOutputStream output = new FileOutputStream(outputFile)) {
            if ("ncm".equals(value)) {
                probe = NcmDecoder.decodeToFile(input, output);
            } else if ("kgm".equals(value) || "vpr".equals(value)) {
                probe = KgmDecoder.decodeToFile(input, output);
            } else if ("qmc".equals(value) || value.startsWith("qmc") || "mflac".equals(value) || "mgg".equals(value)) {
                probe = QmcDecoder.decodeToFile(input, output);
            } else {
                throw new IOException("Unsupported private container: " + extension);
            }
        }
        return AudioFormatDetector.detect(probe);
    }

    static byte[] readAll(File input) throws IOException {
        long length = input.length();
        if (length > Integer.MAX_VALUE) {
            throw new IOException("File is too large for memory decode");
        }
        byte[] data = new byte[(int) length];
        try (java.io.FileInputStream stream = new java.io.FileInputStream(input)) {
            int offset = 0;
            while (offset < data.length) {
                int read = stream.read(data, offset, data.length - offset);
                if (read == -1) {
                    throw new IOException("Unexpected EOF");
                }
                offset += read;
            }
        }
        return data;
    }

    static void writeChunk(OutputStream output, Probe probe, byte[] data, int offset, int length) throws IOException {
        output.write(data, offset, length);
        probe.add(data, offset, length);
    }

    static final class Probe {
        private final byte[] data = new byte[PROBE_SIZE];
        private int length = 0;

        void add(byte[] chunk, int offset, int count) {
            if (length >= data.length || count <= 0) {
                return;
            }
            int copied = Math.min(count, data.length - length);
            System.arraycopy(chunk, offset, data, length, copied);
            length += copied;
        }

        byte[] bytes() {
            byte[] result = new byte[length];
            System.arraycopy(data, 0, result, 0, length);
            return result;
        }
    }
}

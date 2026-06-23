package com.volcanic.musicplayer.decoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Arrays;

final class KgmDecoder {
    private static final byte[] KGM_HEADER = new byte[]{
            0x7c, (byte) 0xd5, 0x32, (byte) 0xeb, (byte) 0x86, 0x02, 0x7f, 0x4b,
            (byte) 0xa8, (byte) 0xaf, (byte) 0xa6, (byte) 0x8e, 0x0f, (byte) 0xff, (byte) 0x99, 0x14
    };
    private static final byte[] VPR_HEADER = new byte[]{
            0x05, 0x28, (byte) 0xbc, (byte) 0x96, (byte) 0xe9, (byte) 0xe4, 0x5a, 0x43,
            (byte) 0x91, (byte) 0xaa, (byte) 0xbd, (byte) 0xd0, 0x7a, (byte) 0xf5, 0x36, 0x31
    };
    private static final int[] BASE_MASK = new int[]{
            0xac, 0xec, 0xdf, 0x57, 0xa5, 0x55, 0xbd, 0x45,
            0x35, 0xc0, 0x1d, 0x74, 0x37, 0x2c, 0x4f, 0x5f,
            0xd4, 0x4e, 0xa9, 0x5b, 0x25, 0x79, 0x21, 0x70,
            0x5d, 0x5f, 0x4b, 0x9b, 0x29, 0x13, 0x6f, 0x4b
    };

    private KgmDecoder() {
    }

    static byte[] decode(File inputFile) throws Exception {
        byte[] input = PrivateContainerDecoder.readAll(inputFile);
        if (!startsWith(input, KGM_HEADER) && !startsWith(input, VPR_HEADER)) {
            throw new IllegalArgumentException("Invalid KGM/VPR header");
        }
        int payloadOffset = 1024;
        if (input.length <= payloadOffset) {
            throw new IllegalArgumentException("Empty KGM payload");
        }
        byte[] payload = Arrays.copyOfRange(input, payloadOffset, input.length);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ((payload[i] & 0xff) ^ maskAt(i));
        }
        return payload;
    }

    static byte[] decodeToFile(File inputFile, OutputStream output) throws Exception {
        try (FileInputStream input = new FileInputStream(inputFile)) {
            byte[] header = readFully(input, KGM_HEADER.length);
            if (!startsWith(header, KGM_HEADER) && !startsWith(header, VPR_HEADER)) {
                throw new IllegalArgumentException("Invalid KGM/VPR header");
            }
            skipFully(input, 1024L - header.length);

            PrivateContainerDecoder.Probe probe = new PrivateContainerDecoder.Probe();
            byte[] buffer = new byte[PrivateContainerDecoder.BUFFER_SIZE];
            int read;
            long position = 0;
            boolean hasPayload = false;
            while ((read = input.read(buffer)) != -1) {
                hasPayload = true;
                for (int i = 0; i < read; i++) {
                    buffer[i] = (byte) ((buffer[i] & 0xff) ^ maskAt(position + i));
                }
                PrivateContainerDecoder.writeChunk(output, probe, buffer, 0, read);
                position += read;
            }
            if (!hasPayload) {
                throw new IllegalArgumentException("Empty KGM payload");
            }
            return probe.bytes();
        }
    }

    private static int maskAt(int position) {
        int round = position / BASE_MASK.length;
        int index = position % BASE_MASK.length;
        return BASE_MASK[index] ^ ((round * 31 + index * 17 + 0x63) & 0xff);
    }

    private static int maskAt(long position) {
        long round = position / BASE_MASK.length;
        int index = (int) (position % BASE_MASK.length);
        return BASE_MASK[index] ^ ((int) (round * 31 + index * 17 + 0x63) & 0xff);
    }

    private static byte[] readFully(FileInputStream input, int length) throws Exception {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read == -1) {
                throw new IllegalArgumentException("Unexpected KGM EOF");
            }
            offset += read;
        }
        return data;
    }

    private static void skipFully(FileInputStream input, long length) throws Exception {
        long remaining = length;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() == -1) {
                    throw new IllegalArgumentException("Unexpected KGM EOF");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static boolean startsWith(byte[] input, byte[] prefix) {
        if (input.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (input[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}

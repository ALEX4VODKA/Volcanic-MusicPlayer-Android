package com.volcanic.musicplayer.decoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

final class QmcDecoder {
    private static final int[] STATIC_MASK = new int[]{
            0xc3, 0x6c, 0x3f, 0x35, 0x0b, 0x10, 0x3f, 0x5a,
            0x47, 0xeb, 0x51, 0x3f, 0x86, 0xa2, 0x15, 0x65,
            0xbb, 0xe0, 0x00, 0x3f, 0x5a, 0x47, 0xeb, 0x51,
            0x3f, 0x86, 0xa2, 0x15, 0x65, 0xbb, 0xe0, 0x00,
            0x3f, 0x5a, 0x47, 0xeb, 0x51, 0x3f, 0x86, 0xa2,
            0x15, 0x65, 0xbb, 0xe0, 0x00, 0x3f, 0x5a, 0x47,
            0xeb, 0x51, 0x3f, 0x86, 0xa2, 0x15, 0x65, 0xbb,
            0xe0, 0x00, 0x3f, 0x5a, 0x47, 0xeb, 0x51, 0x3f,
            0x86, 0xa2, 0x15, 0x65, 0xbb, 0xe0, 0x00, 0x3f,
            0x5a, 0x47, 0xeb, 0x51, 0x3f, 0x86, 0xa2, 0x15,
            0x65, 0xbb, 0xe0, 0x00, 0x3f, 0x5a, 0x47, 0xeb,
            0x51, 0x3f, 0x86, 0xa2, 0x15, 0x65, 0xbb, 0xe0,
            0x00, 0x3f, 0x5a, 0x47, 0xeb, 0x51, 0x3f, 0x86,
            0xa2, 0x15, 0x65, 0xbb, 0xe0, 0x00, 0x3f, 0x5a,
            0x47, 0xeb, 0x51, 0x3f, 0x86, 0xa2, 0x15, 0x65,
            0xbb, 0xe0, 0x00, 0x3f, 0x5a, 0x47, 0xeb, 0x51
    };

    private QmcDecoder() {
    }

    static byte[] decode(File inputFile) throws Exception {
        byte[] output = PrivateContainerDecoder.readAll(inputFile);
        for (int i = 0; i < output.length; i++) {
            output[i] = (byte) ((output[i] & 0xff) ^ maskAt(i));
        }
        return output;
    }

    static byte[] decodeToFile(File inputFile, OutputStream output) throws Exception {
        try (FileInputStream input = new FileInputStream(inputFile)) {
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
                throw new IllegalArgumentException("Empty QMC payload");
            }
            return probe.bytes();
        }
    }

    private static int maskAt(int position) {
        if (position < 0x8000) {
            return STATIC_MASK[position % STATIC_MASK.length];
        }
        int seed = position / 0x8000;
        int index = (position + seed * seed + 27) % STATIC_MASK.length;
        return STATIC_MASK[index];
    }

    private static int maskAt(long position) {
        if (position < 0x8000L) {
            return STATIC_MASK[(int) (position % STATIC_MASK.length)];
        }
        long seed = position / 0x8000L;
        int index = (int) ((position + seed * seed + 27) % STATIC_MASK.length);
        return STATIC_MASK[index];
    }
}

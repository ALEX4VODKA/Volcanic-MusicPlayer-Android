package com.volcanic.musicplayer.decoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

final class NcmDecoder {
    private static final byte[] NCM_MAGIC = new byte[]{
            0x43, 0x54, 0x45, 0x4e, 0x46, 0x44, 0x41, 0x4d
    };
    private static final byte[] CORE_KEY = "hzHRAmso5kInbaxW".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_KEY_BLOCK_SIZE = 1024 * 1024;

    private NcmDecoder() {
    }

    static byte[] decode(File inputFile) throws Exception {
        byte[] input = PrivateContainerDecoder.readAll(inputFile);
        if (input.length < 32 || !startsWith(input, NCM_MAGIC)) {
            throw new IllegalArgumentException("Invalid NCM header");
        }

        int offset = 10;
        int keyLength = readUInt32LE(input, offset);
        offset += 4;
        if (keyLength <= 0 || offset + keyLength > input.length) {
            throw new IllegalArgumentException("Invalid NCM key block");
        }

        byte[] encryptedKey = Arrays.copyOfRange(input, offset, offset + keyLength);
        offset += keyLength;
        for (int i = 0; i < encryptedKey.length; i++) {
            encryptedKey[i] = (byte) ((encryptedKey[i] & 0xff) ^ 0x64);
        }

        byte[] decryptedKey = aesEcbDecrypt(encryptedKey, CORE_KEY);
        if (decryptedKey.length <= 17) {
            throw new IllegalArgumentException("Empty NCM audio key");
        }
        byte[] keyData = Arrays.copyOfRange(decryptedKey, 17, decryptedKey.length);
        byte[] keyBox = buildKeyBox(keyData);

        int metadataLength = readUInt32LE(input, offset);
        offset += 4 + metadataLength;
        offset += 9;
        int imageSize = readUInt32LE(input, offset);
        offset += 4 + imageSize;
        if (offset >= input.length) {
            throw new IllegalArgumentException("Empty NCM payload");
        }

        byte[] payload = Arrays.copyOfRange(input, offset, input.length);
        applyKeyStream(payload, keyBox);
        return payload;
    }

    static byte[] decodeToFile(File inputFile, OutputStream output) throws Exception {
        try (FileInputStream input = new FileInputStream(inputFile)) {
            byte[] magic = readFully(input, NCM_MAGIC.length);
            if (!startsWith(magic, NCM_MAGIC)) {
                throw new IllegalArgumentException("Invalid NCM header");
            }
            skipFully(input, 2);

            int keyLength = readUInt32LE(input);
            if (keyLength <= 0 || keyLength > MAX_KEY_BLOCK_SIZE) {
                throw new IllegalArgumentException("Invalid NCM key block");
            }

            byte[] encryptedKey = readFully(input, keyLength);
            for (int i = 0; i < encryptedKey.length; i++) {
                encryptedKey[i] = (byte) ((encryptedKey[i] & 0xff) ^ 0x64);
            }

            byte[] decryptedKey = aesEcbDecrypt(encryptedKey, CORE_KEY);
            if (decryptedKey.length <= 17) {
                throw new IllegalArgumentException("Empty NCM audio key");
            }
            byte[] keyData = Arrays.copyOfRange(decryptedKey, 17, decryptedKey.length);
            byte[] keyBox = buildKeyBox(keyData);

            int metadataLength = readUInt32LE(input);
            if (metadataLength < 0) {
                throw new IllegalArgumentException("Invalid NCM metadata block");
            }
            skipFully(input, metadataLength);
            skipFully(input, 9);

            int imageSize = readUInt32LE(input);
            if (imageSize < 0) {
                throw new IllegalArgumentException("Invalid NCM image block");
            }
            skipFully(input, imageSize);

            PrivateContainerDecoder.Probe probe = new PrivateContainerDecoder.Probe();
            byte[] buffer = new byte[PrivateContainerDecoder.BUFFER_SIZE];
            int read;
            long position = 0;
            boolean hasPayload = false;
            while ((read = input.read(buffer)) != -1) {
                hasPayload = true;
                applyKeyStream(buffer, read, position, keyBox);
                PrivateContainerDecoder.writeChunk(output, probe, buffer, 0, read);
                position += read;
            }
            if (!hasPayload) {
                throw new IllegalArgumentException("Empty NCM payload");
            }
            return probe.bytes();
        }
    }

    private static byte[] aesEcbDecrypt(byte[] encrypted, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(encrypted);
    }

    private static byte[] buildKeyBox(byte[] keyData) {
        byte[] keyBox = new byte[256];
        for (int i = 0; i < 256; i++) {
            keyBox[i] = (byte) i;
        }

        int lastByte = 0;
        int keyOffset = 0;
        for (int i = 0; i < 256; i++) {
            int swap = keyBox[i] & 0xff;
            int c = (swap + lastByte + (keyData[keyOffset] & 0xff)) & 0xff;
            keyOffset = (keyOffset + 1) % keyData.length;
            keyBox[i] = keyBox[c];
            keyBox[c] = (byte) swap;
            lastByte = c;
        }
        return keyBox;
    }

    private static void applyKeyStream(byte[] payload, byte[] keyBox) {
        for (int i = 0; i < payload.length; i++) {
            int j = (i + 1) & 0xff;
            int index = ((keyBox[j] & 0xff) + j) & 0xff;
            int maskIndex = ((keyBox[j] & 0xff) + (keyBox[index] & 0xff)) & 0xff;
            payload[i] = (byte) ((payload[i] & 0xff) ^ (keyBox[maskIndex] & 0xff));
        }
    }

    private static void applyKeyStream(byte[] payload, int length, long startPosition, byte[] keyBox) {
        for (int i = 0; i < length; i++) {
            int j = (int) ((startPosition + i + 1) & 0xff);
            int index = ((keyBox[j] & 0xff) + j) & 0xff;
            int maskIndex = ((keyBox[j] & 0xff) + (keyBox[index] & 0xff)) & 0xff;
            payload[i] = (byte) ((payload[i] & 0xff) ^ (keyBox[maskIndex] & 0xff));
        }
    }

    private static int readUInt32LE(byte[] data, int offset) {
        if (offset < 0 || offset + 4 > data.length) {
            throw new IllegalArgumentException("Unexpected NCM EOF");
        }
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static int readUInt32LE(FileInputStream input) throws Exception {
        byte[] data = readFully(input, 4);
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static byte[] readFully(FileInputStream input, int length) throws Exception {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read == -1) {
                throw new IllegalArgumentException("Unexpected NCM EOF");
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
                    throw new IllegalArgumentException("Unexpected NCM EOF");
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

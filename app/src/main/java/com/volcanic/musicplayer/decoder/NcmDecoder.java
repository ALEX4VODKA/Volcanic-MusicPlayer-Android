package com.volcanic.musicplayer.decoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

    private static int readUInt32LE(byte[] data, int offset) {
        if (offset < 0 || offset + 4 > data.length) {
            throw new IllegalArgumentException("Unexpected NCM EOF");
        }
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
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

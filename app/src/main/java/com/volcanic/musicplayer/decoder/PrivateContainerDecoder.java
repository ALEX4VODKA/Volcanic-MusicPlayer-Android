package com.volcanic.musicplayer.decoder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

public final class PrivateContainerDecoder {
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

    static byte[] readAll(File input) throws IOException {
        return Files.readAllBytes(input.toPath());
    }
}

package com.volcanic.musicplayer.decoder;

final class AudioFormatDetector {
    private AudioFormatDetector() {
    }

    static String detect(byte[] data) {
        if (data.length >= 3 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
            return "mp3";
        }
        if (data.length >= 2 && (data[0] & 0xff) == 0xff && ((data[1] & 0xe0) == 0xe0)) {
            return "mp3";
        }
        if (data.length >= 4 && data[0] == 'f' && data[1] == 'L' && data[2] == 'a' && data[3] == 'C') {
            return "flac";
        }
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E') {
            return "wav";
        }
        return "unknown";
    }
}

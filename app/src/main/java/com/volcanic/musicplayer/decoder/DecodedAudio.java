package com.volcanic.musicplayer.decoder;

public final class DecodedAudio {
    public final byte[] payload;
    public final String format;

    public DecodedAudio(byte[] payload, String format) {
        this.payload = payload;
        this.format = format;
    }

    public boolean isMp3() {
        return "mp3".equals(format);
    }
}

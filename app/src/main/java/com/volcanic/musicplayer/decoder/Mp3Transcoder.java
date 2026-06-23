package com.volcanic.musicplayer.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public final class Mp3Transcoder {
    private static final long TIMEOUT_US = 10_000L;

    private Mp3Transcoder() {
    }

    public static void transcodeToMp3(File inputFile, File outputFile, int bitRate) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        try (FileOutputStream output = new FileOutputStream(outputFile)) {
            extractor.setDataSource(inputFile.getAbsolutePath());
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new IllegalArgumentException("No audio track found");
            }
            extractor.selectTrack(trackIndex);

            MediaFormat inputFormat = extractor.getTrackFormat(trackIndex);
            String inputMime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (inputMime == null) {
                throw new IllegalArgumentException("Unknown input mime");
            }
            int sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            decoder = MediaCodec.createDecoderByType(inputMime);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            MediaFormat outputFormat = MediaFormat.createAudioFormat("audio/mpeg", sampleRate, channelCount);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            encoder = MediaCodec.createEncoderByType("audio/mpeg");
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            pump(extractor, decoder, encoder, output);
        } finally {
            extractor.release();
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
        }
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private static void pump(MediaExtractor extractor, MediaCodec decoder, MediaCodec encoder, FileOutputStream output) throws Exception {
        MediaCodec.BufferInfo decoderInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo encoderInfo = new MediaCodec.BufferInfo();
        boolean extractorDone = false;
        boolean decoderDone = false;
        boolean encoderDone = false;

        while (!encoderDone) {
            if (!extractorDone) {
                int inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                    if (inputBuffer == null) {
                        throw new IllegalStateException("Decoder input buffer unavailable");
                    }
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        extractorDone = true;
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), extractor.getSampleFlags());
                        extractor.advance();
                    }
                }
            }

            boolean decoderOutputAvailable = !decoderDone;
            while (decoderOutputAvailable) {
                int decoderStatus = decoder.dequeueOutputBuffer(decoderInfo, TIMEOUT_US);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    decoderOutputAvailable = false;
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Encoder input format is already configured from the source format.
                } else if (decoderStatus >= 0) {
                    ByteBuffer decoderOutput = decoder.getOutputBuffer(decoderStatus);
                    if (decoderOutput == null) {
                        throw new IllegalStateException("Decoder output buffer unavailable");
                    }

                    if ((decoderInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        queueEncoderEos(encoder, decoderInfo.presentationTimeUs);
                        decoder.releaseOutputBuffer(decoderStatus, false);
                        decoderDone = true;
                        decoderOutputAvailable = false;
                    } else if (decoderInfo.size > 0) {
                        int encoderInputIndex = encoder.dequeueInputBuffer(TIMEOUT_US);
                        if (encoderInputIndex >= 0) {
                            ByteBuffer encoderInput = encoder.getInputBuffer(encoderInputIndex);
                            if (encoderInput == null) {
                                throw new IllegalStateException("Encoder input buffer unavailable");
                            }
                            encoderInput.clear();
                            decoderOutput.position(decoderInfo.offset);
                            decoderOutput.limit(decoderInfo.offset + decoderInfo.size);
                            encoderInput.put(decoderOutput);
                            encoder.queueInputBuffer(encoderInputIndex, 0, decoderInfo.size, decoderInfo.presentationTimeUs, 0);
                            decoder.releaseOutputBuffer(decoderStatus, false);
                        } else {
                            decoderOutputAvailable = false;
                        }
                    } else {
                        decoder.releaseOutputBuffer(decoderStatus, false);
                    }
                }
            }

            boolean encoderOutputAvailable = true;
            while (encoderOutputAvailable) {
                int encoderStatus = encoder.dequeueOutputBuffer(encoderInfo, TIMEOUT_US);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    encoderOutputAvailable = false;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Raw MP3 frames are written directly as delivered by the encoder.
                } else if (encoderStatus >= 0) {
                    ByteBuffer encoded = encoder.getOutputBuffer(encoderStatus);
                    if (encoded == null) {
                        throw new IllegalStateException("Encoder output buffer unavailable");
                    }
                    if (encoderInfo.size > 0 && (encoderInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        byte[] chunk = new byte[encoderInfo.size];
                        encoded.position(encoderInfo.offset);
                        encoded.limit(encoderInfo.offset + encoderInfo.size);
                        encoded.get(chunk);
                        output.write(chunk);
                    }
                    if ((encoderInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderDone = true;
                    }
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
            }
        }
    }

    private static void queueEncoderEos(MediaCodec encoder, long presentationTimeUs) {
        while (true) {
            int inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputIndex >= 0) {
                encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return;
            }
        }
    }
}

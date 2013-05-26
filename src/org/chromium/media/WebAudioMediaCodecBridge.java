// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;
import android.os.ParcelFileDescriptor;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

@JNINamespace("media")
class WebAudioMediaCodecBridge {
    private static final boolean DEBUG = true;
    static final String LOG_TAG = "WebAudioMediaCodec";
    // TODO(rtoy): What is the correct timeout value for reading
    // from a file in memory?
    static final long TIMEOUT_MICROSECONDS = 500;
    @CalledByNative
    private static boolean decodeAudioFile(Context ctx,
                                           int nativeMediaCodecBridge,
                                           int inputFD,
                                           long dataSize) {

        if (dataSize < 0 || dataSize > 0x7fffffff)
            return false;

        MediaExtractor extractor = new MediaExtractor();

        ParcelFileDescriptor encodedFD;
        encodedFD = ParcelFileDescriptor.adoptFd(inputFD);
        try {
            extractor.setDataSource(encodedFD.getFileDescriptor(), 0, dataSize);
        } catch (Exception e) {
            e.printStackTrace();
            encodedFD.detachFd();
            return false;
        }

        if (extractor.getTrackCount() <= 0) {
            encodedFD.detachFd();
            return false;
        }

        MediaFormat format = extractor.getTrackFormat(0);

        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        String mime = format.getString(MediaFormat.KEY_MIME);

        long durationMicroseconds = 0;
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
            try {
                durationMicroseconds = format.getLong(MediaFormat.KEY_DURATION);
            } catch (Exception e) {
                Log.d(LOG_TAG, "Cannot get duration");
            }
        }

        if (DEBUG) {
            Log.d(LOG_TAG, "Tracks: " + extractor.getTrackCount()
                  + " Rate: " + sampleRate
                  + " Channels: " + channelCount
                  + " Mime: " + mime
                  + " Duration: " + durationMicroseconds + " microsec");
        }

        nativeInitializeDestination(nativeMediaCodecBridge,
                                    channelCount,
                                    sampleRate,
                                    durationMicroseconds);

        // Create decoder
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
        codec.start();

        ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

        // A track must be selected and will be used to read samples.
        extractor.selectTrack(0);

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        // Keep processing until the output is done.
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                // Input side
                int inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_MICROSECONDS);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                    int sampleSize = extractor.readSampleData(dstBuf, 0);
                    long presentationTimeMicroSec = 0;

                    if (sampleSize < 0) {
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeMicroSec = extractor.getSampleTime();
                    }

                    codec.queueInputBuffer(inputBufIndex,
                                           0, /* offset */
                                           sampleSize,
                                           presentationTimeMicroSec,
                                           sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!sawInputEOS) {
                        extractor.advance();
                    }
                }
            }

            // Output side
            MediaCodec.BufferInfo info = new BufferInfo();
            final int outputBufIndex = codec.dequeueOutputBuffer(info, TIMEOUT_MICROSECONDS);

            if (outputBufIndex >= 0) {
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                if (info.size > 0) {
                    nativeOnChunkDecoded(nativeMediaCodecBridge, buf, info.size);
                }

                buf.clear();
                codec.releaseOutputBuffer(outputBufIndex, false /* render */);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
            }
        }

        encodedFD.detachFd();

        codec.stop();
        codec.release();
        codec = null;

        return true;
    }

    private static native void nativeOnChunkDecoded(
        int nativeWebAudioMediaCodecBridge, ByteBuffer buf, int size);

    private static native void nativeInitializeDestination(
        int nativeWebAudioMediaCodecBridge,
        int channelCount,
        int sampleRate,
        long durationMicroseconds);
}

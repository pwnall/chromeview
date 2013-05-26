// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.view.Surface;
import android.util.Log;

import java.nio.ByteBuffer;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * A wrapper of the MediaCodec class to facilitate exception capturing and
 * audio rendering.
 */
@JNINamespace("media")
class MediaCodecBridge {

    private static final String TAG = "MediaCodecBridge";

    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;

    private MediaCodec mMediaCodec;

    private AudioTrack mAudioTrack;

    private static class DequeueOutputResult {
        private final int mIndex;
        private final int mFlags;
        private final int mOffset;
        private final long mPresentationTimeMicroseconds;
        private final int mNumBytes;

        private DequeueOutputResult(
            int index, int flags, int offset, long presentationTimeMicroseconds, int numBytes) {
            mIndex = index;
            mFlags = flags;
            mOffset = offset;
            mPresentationTimeMicroseconds = presentationTimeMicroseconds;
            mNumBytes = numBytes;
        }

        @CalledByNative("DequeueOutputResult")
        private int index() { return mIndex; }

        @CalledByNative("DequeueOutputResult")
        private int flags() { return mFlags; }

        @CalledByNative("DequeueOutputResult")
        private int offset() { return mOffset; }

        @CalledByNative("DequeueOutputResult")
        private long presentationTimeMicroseconds() { return mPresentationTimeMicroseconds; }

        @CalledByNative("DequeueOutputResult")
        private int numBytes() { return mNumBytes; }
    }

    private MediaCodecBridge(String mime) {
        mMediaCodec = MediaCodec.createDecoderByType(mime);
    }

    @CalledByNative
    private static MediaCodecBridge create(String mime) {
        return new MediaCodecBridge(mime);
    }

    @CalledByNative
    private void release() {
        mMediaCodec.release();
        if (mAudioTrack != null) {
            mAudioTrack.release();
        }
    }

    @CalledByNative
    private void start() {
        mMediaCodec.start();
        mInputBuffers = mMediaCodec.getInputBuffers();
    }

    @CalledByNative
    private int dequeueInputBuffer(long timeoutUs) {
        return mMediaCodec.dequeueInputBuffer(timeoutUs);
    }

    @CalledByNative
    private void flush() {
        mMediaCodec.flush();
        if (mAudioTrack != null) {
            mAudioTrack.flush();
        }
    }

    @CalledByNative
    private void stop() {
        mMediaCodec.stop();
        if (mAudioTrack != null) {
            mAudioTrack.pause();
        }
    }

    @CalledByNative
    private MediaFormat getOutputFormat() {
        return mMediaCodec.getOutputFormat();
    }

    @CalledByNative
    private ByteBuffer getInputBuffer(int index) {
        return mInputBuffers[index];
    }

    @CalledByNative
    private ByteBuffer getOutputBuffer(int index) {
        return mOutputBuffers[index];
    }

    @CalledByNative
    private void queueInputBuffer(
            int index, int offset, int size, long presentationTimeUs, int flags) {
        mMediaCodec.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
    }

    @CalledByNative
    private void releaseOutputBuffer(int index, boolean render) {
        mMediaCodec.releaseOutputBuffer(index, render);
    }

    @CalledByNative
    private void getOutputBuffers() {
        mOutputBuffers = mMediaCodec.getOutputBuffers();
    }

    @CalledByNative
    private DequeueOutputResult dequeueOutputBuffer(long timeoutUs) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int index = mMediaCodec.dequeueOutputBuffer(info, timeoutUs);
        return new DequeueOutputResult(
                index, info.flags, info.offset, info.presentationTimeUs, info.size);
    }

    @CalledByNative
    private void configureVideo(MediaFormat format, Surface surface, MediaCrypto crypto,
            int flags) {
        mMediaCodec.configure(format, surface, crypto, flags);
    }

    @CalledByNative
    private void configureAudio(MediaFormat format, MediaCrypto crypto, int flags,
            boolean playAudio) {
        mMediaCodec.configure(format, null, crypto, flags);
        if (playAudio) {
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int channelConfig = (channelCount == 1) ? AudioFormat.CHANNEL_OUT_MONO :
                    AudioFormat.CHANNEL_OUT_STEREO;
            int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT);
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM);
        }
    }

    @CalledByNative
    private void playOutputBuffer(byte[] buf) {
        if (mAudioTrack != null) {
            if (AudioTrack.PLAYSTATE_PLAYING != mAudioTrack.getPlayState()) {
                mAudioTrack.play();
            }
            int size = mAudioTrack.write(buf, 0, buf.length);
            if (buf.length != size) {
                Log.i(TAG, "Failed to send all data to audio output, expected size: " +
                        buf.length + ", actual size: " + size);
            }
        }
    }
}

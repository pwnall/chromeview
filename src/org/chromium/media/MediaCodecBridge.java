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

    // Error code for MediaCodecBridge. Keep this value in sync with
    // INFO_MEDIA_CODEC_ERROR in media_codec_bridge.h.
    private static final int MEDIA_CODEC_ERROR = -1000;

    // After a flush(), dequeueOutputBuffer() can often produce empty presentation timestamps
    // for several frames. As a result, the player may find that the time does not increase
    // after decoding a frame. To detect this, we check whether the presentation timestamp from
    // dequeueOutputBuffer() is larger than input_timestamp - MAX_PRESENTATION_TIMESTAMP_SHIFT_US
    // after a flush. And we set the presentation timestamp from dequeueOutputBuffer() to be
    // non-decreasing for the remaining frames.
    private static final long MAX_PRESENTATION_TIMESTAMP_SHIFT_US = 100000;

    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;

    private MediaCodec mMediaCodec;
    private AudioTrack mAudioTrack;
    private boolean mFlushed;
    private long mLastPresentationTimeUs;

    private static class DequeueOutputResult {
        private final int mIndex;
        private final int mFlags;
        private final int mOffset;
        private final long mPresentationTimeMicroseconds;
        private final int mNumBytes;

        private DequeueOutputResult(int index, int flags, int offset,
                long presentationTimeMicroseconds, int numBytes) {
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
        mLastPresentationTimeUs = 0;
        mFlushed = true;
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
        try {
            return mMediaCodec.dequeueInputBuffer(timeoutUs);
        } catch(Exception e) {
            Log.e(TAG, "Cannot dequeue Input buffer " + e.toString());
        }
        return MEDIA_CODEC_ERROR;
    }

    @CalledByNative
    private void flush() {
        mMediaCodec.flush();
        mFlushed = true;
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
    private int getOutputHeight() {
        return mMediaCodec.getOutputFormat().getInteger(MediaFormat.KEY_HEIGHT);
    }

    @CalledByNative
    private int getOutputWidth() {
        return mMediaCodec.getOutputFormat().getInteger(MediaFormat.KEY_WIDTH);
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
        resetLastPresentationTimeIfNeeded(presentationTimeUs);
        try {
            mMediaCodec.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
        } catch(IllegalStateException e) {
            Log.e(TAG, "Failed to queue input buffer " + e.toString());
        }
    }

    @CalledByNative
    private void queueSecureInputBuffer(
            int index, int offset, byte[] iv, byte[] keyId, int[] numBytesOfClearData,
            int[] numBytesOfEncryptedData, int numSubSamples, long presentationTimeUs) {
        resetLastPresentationTimeIfNeeded(presentationTimeUs);
        try {
            MediaCodec.CryptoInfo cryptoInfo = new MediaCodec.CryptoInfo();
            cryptoInfo.set(numSubSamples, numBytesOfClearData, numBytesOfEncryptedData,
                    keyId, iv, MediaCodec.CRYPTO_MODE_AES_CTR);
            mMediaCodec.queueSecureInputBuffer(index, offset, cryptoInfo, presentationTimeUs, 0);
        } catch(IllegalStateException e) {
            Log.e(TAG, "Failed to queue secure input buffer " + e.toString());
        }
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
        int index = MEDIA_CODEC_ERROR;
        try {
            index = mMediaCodec.dequeueOutputBuffer(info, timeoutUs);
            if (info.presentationTimeUs < mLastPresentationTimeUs) {
                // TODO(qinmin): return a special code through DequeueOutputResult
                // to notify the native code the the frame has a wrong presentation
                // timestamp and should be skipped.
                info.presentationTimeUs = mLastPresentationTimeUs;
            }
            mLastPresentationTimeUs = info.presentationTimeUs;
        } catch (IllegalStateException e) {
            Log.e(TAG, "Cannot dequeue output buffer " + e.toString());
        }
        return new DequeueOutputResult(
                index, info.flags, info.offset, info.presentationTimeUs, info.size);
    }

    @CalledByNative
    private boolean configureVideo(MediaFormat format, Surface surface, MediaCrypto crypto,
            int flags) {
        try {
            mMediaCodec.configure(format, surface, crypto, flags);
            return true;
        } catch (IllegalStateException e) {
          Log.e(TAG, "Cannot configure the video codec " + e.toString());
        }
        return false;
    }

    @CalledByNative
    private static MediaFormat createAudioFormat(String mime, int SampleRate, int ChannelCount) {
        return MediaFormat.createAudioFormat(mime, SampleRate, ChannelCount);
    }

    @CalledByNative
    private static MediaFormat createVideoFormat(String mime, int width, int height) {
        return MediaFormat.createVideoFormat(mime, width, height);
    }

    @CalledByNative
    private static void setCodecSpecificData(MediaFormat format, int index, ByteBuffer bytes) {
        String name = null;
        if (index == 0) {
            name = "csd-0";
        } else if (index == 1) {
            name = "csd-1";
        }
        if (name != null) {
            format.setByteBuffer(name, bytes);
        }
    }

    @CalledByNative
    private static void setFrameHasADTSHeader(MediaFormat format) {
        format.setInteger(MediaFormat.KEY_IS_ADTS, 1);
    }

    @CalledByNative
    private boolean configureAudio(MediaFormat format, MediaCrypto crypto, int flags,
            boolean playAudio) {
        try {
            mMediaCodec.configure(format, null, crypto, flags);
            if (playAudio) {
                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int channelConfig = (channelCount == 1) ? AudioFormat.CHANNEL_OUT_MONO :
                        AudioFormat.CHANNEL_OUT_STEREO;
                // Using 16bit PCM for output. Keep this value in sync with
                // kBytesPerAudioOutputSample in media_codec_bridge.cc.
                int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT);
                mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM);
            }
            return true;
        } catch (IllegalStateException e) {
            Log.e(TAG, "Cannot configure the audio codec " + e.toString());
        }
        return false;
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

    @CalledByNative
    private void setVolume(double volume) {
        if (mAudioTrack != null) {
            mAudioTrack.setStereoVolume((float) volume, (float) volume);
        }
    }

    private void resetLastPresentationTimeIfNeeded(long presentationTimeUs) {
        if (mFlushed) {
            mLastPresentationTimeUs =
                    Math.max(presentationTimeUs - MAX_PRESENTATION_TIMESTAMP_SHIFT_US, 0);
            mFlushed = false;
        }
    }
}

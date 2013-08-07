// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

@JNINamespace("media")
class AudioManagerAndroid {
    private static final String TAG = "AudioManagerAndroid";

    // Most of Google lead devices use 44.1K as the default sampling rate, 44.1K
    // is also widely used on other android devices.
    private static final int DEFAULT_SAMPLING_RATE = 44100;
    // Randomly picked up frame size which is close to return value on N4.
    // Return this default value when
    // getProperty(PROPERTY_OUTPUT_FRAMES_PER_BUFFER) fails.
    private static final int DEFAULT_FRAME_PER_BUFFER = 256;

    private final AudioManager mAudioManager;
    private final Context mContext;

    private BroadcastReceiver mReceiver;
    private boolean mOriginalSpeakerStatus;

    @CalledByNative
    public void setMode(int mode) {
        try {
            mAudioManager.setMode(mode);
            if (mode == AudioManager.MODE_IN_COMMUNICATION) {
                mAudioManager.setSpeakerphoneOn(true);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "setMode exception: " + e.getMessage());
            logDeviceInfo();
        }
    }

    @CalledByNative
    private static AudioManagerAndroid createAudioManagerAndroid(Context context) {
        return new AudioManagerAndroid(context);
    }

    private AudioManagerAndroid(Context context) {
        mContext = context;
        mAudioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    @CalledByNative
    public void registerHeadsetReceiver() {
        if (mReceiver != null) {
            return;
        }

        mOriginalSpeakerStatus = mAudioManager.isSpeakerphoneOn();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                    try {
                        mAudioManager.setSpeakerphoneOn(
                                intent.getIntExtra("state", 0) == 0);
                    } catch (SecurityException e) {
                        Log.e(TAG, "setMode exception: " + e.getMessage());
                        logDeviceInfo();
                    }
                }
            }
        };
        mContext.registerReceiver(mReceiver, filter);
    }

    @CalledByNative
    public void unregisterHeadsetReceiver() {
        mContext.unregisterReceiver(mReceiver);
        mReceiver = null;
        mAudioManager.setSpeakerphoneOn(mOriginalSpeakerStatus);
    }

    private void logDeviceInfo() {
        Log.i(TAG, "Manufacturer:" + Build.MANUFACTURER +
                " Board: " + Build.BOARD + " Device: " + Build.DEVICE +
                " Model: " + Build.MODEL + " PRODUCT: " + Build.PRODUCT);
    }

    @CalledByNative
    private int getNativeOutputSampleRate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            String sampleRateString = mAudioManager.getProperty(
                    AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            return (sampleRateString == null ?
                    DEFAULT_SAMPLING_RATE : Integer.parseInt(sampleRateString));
        } else {
            return DEFAULT_SAMPLING_RATE;
        }
    }

  /**
   * Returns the minimum frame size required for audio input.
   *
   * @param sampleRate sampling rate
   * @param channels number of channels
   */
    @CalledByNative
    private static int getMinInputFrameSize(int sampleRate, int channels) {
        int channelConfig;
        if (channels == 1) {
            channelConfig = AudioFormat.CHANNEL_IN_MONO;
        } else if (channels == 2) {
            channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        } else {
            return -1;
        }
        return AudioRecord.getMinBufferSize(
                sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) / 2 / channels;
    }

  /**
   * Returns the minimum frame size required for audio output.
   *
   * @param sampleRate sampling rate
   * @param channels number of channels
   */
    @CalledByNative
    private static int getMinOutputFrameSize(int sampleRate, int channels) {
        int channelConfig;
        if (channels == 1) {
            channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        } else if (channels == 2) {
            channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        } else {
            return -1;
        }
        return AudioTrack.getMinBufferSize(
                sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) / 2 / channels;
    }

    @CalledByNative
    private boolean isAudioLowLatencySupported() {
        return mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUDIO_LOW_LATENCY);
    }

    @CalledByNative
    private int getAudioLowLatencyOutputFrameSize() {
        String framesPerBuffer =
                mAudioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        return (framesPerBuffer == null ?
                DEFAULT_FRAME_PER_BUFFER : Integer.parseInt(framesPerBuffer));
    }

}

// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

// This class implements all the listener interface for android mediaplayer.
// Callbacks will be sent to the native class for processing.
@JNINamespace("media")
class MediaPlayerListener implements MediaPlayer.OnPreparedListener,
    MediaPlayer.OnCompletionListener,
    MediaPlayer.OnBufferingUpdateListener,
    MediaPlayer.OnSeekCompleteListener,
    MediaPlayer.OnVideoSizeChangedListener,
    MediaPlayer.OnErrorListener,
    AudioManager.OnAudioFocusChangeListener {
    // These values are mirrored as enums in media/base/android/media_player_bridge.h.
    // Please ensure they stay in sync.
    private static final int MEDIA_ERROR_FORMAT = 0;
    private static final int MEDIA_ERROR_DECODE = 1;
    private static final int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 2;
    private static final int MEDIA_ERROR_INVALID_CODE = 3;

    // These values are copied from android media player.
    public static final int MEDIA_ERROR_MALFORMED = -1007;
    public static final int MEDIA_ERROR_TIMED_OUT = -110;

    // Used to determine the class instance to dispatch the native call to.
    private int mNativeMediaPlayerListener = 0;
    private final Context mContext;

    private MediaPlayerListener(int nativeMediaPlayerListener, Context context) {
        mNativeMediaPlayerListener = nativeMediaPlayerListener;
        mContext = context;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        int errorType;
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                switch (extra) {
                    case MEDIA_ERROR_MALFORMED:
                        errorType = MEDIA_ERROR_DECODE;
                        break;
                    case MEDIA_ERROR_TIMED_OUT:
                        errorType = MEDIA_ERROR_INVALID_CODE;
                        break;
                    default:
                        errorType = MEDIA_ERROR_FORMAT;
                        break;
                }
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                errorType = MEDIA_ERROR_DECODE;
                break;
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                errorType = MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK;
                break;
            default:
                // There are some undocumented error codes for android media player.
                // For example, when surfaceTexture got deleted before we setVideoSuface
                // to NULL, mediaplayer will report error -38. These errors should be ignored
                // and not be treated as an error to webkit.
                errorType = MEDIA_ERROR_INVALID_CODE;
                break;
        }
        nativeOnMediaError(mNativeMediaPlayerListener, errorType);
        return true;
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        nativeOnVideoSizeChanged(mNativeMediaPlayerListener, width, height);
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        nativeOnSeekComplete(mNativeMediaPlayerListener);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        nativeOnBufferingUpdate(mNativeMediaPlayerListener, percent);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        nativeOnPlaybackComplete(mNativeMediaPlayerListener);
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        nativeOnMediaPrepared(mNativeMediaPlayerListener);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            nativeOnMediaInterrupted(mNativeMediaPlayerListener);
        }
    }

    @CalledByNative
    public void releaseResources() {
        if (mContext != null) {
            // Unregister the wish for audio focus.
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.abandonAudioFocus(this);
            }
        }
    }

    @CalledByNative
    private static MediaPlayerListener create(int nativeMediaPlayerListener,
            Context context, MediaPlayer mediaPlayer) {
        final MediaPlayerListener listener =
                new MediaPlayerListener(nativeMediaPlayerListener, context);
        mediaPlayer.setOnBufferingUpdateListener(listener);
        mediaPlayer.setOnCompletionListener(listener);
        mediaPlayer.setOnErrorListener(listener);
        mediaPlayer.setOnPreparedListener(listener);
        mediaPlayer.setOnSeekCompleteListener(listener);
        mediaPlayer.setOnVideoSizeChangedListener(listener);
        if (PackageManager.PERMISSION_GRANTED ==
                context.checkCallingOrSelfPermission(permission.WAKE_LOCK)) {
            mediaPlayer.setWakeMode(context, android.os.PowerManager.FULL_WAKE_LOCK);
        }

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,

                // Request permanent focus.
                AudioManager.AUDIOFOCUS_GAIN);
        return listener;
    }

    /**
     * See media/base/android/media_player_listener.cc for all the following functions.
     */
    private native void nativeOnMediaError(
            int nativeMediaPlayerListener,
            int errorType);

    private native void nativeOnVideoSizeChanged(
            int nativeMediaPlayerListener,
            int width, int height);

    private native void nativeOnBufferingUpdate(
            int nativeMediaPlayerListener,
            int percent);

    private native void nativeOnMediaPrepared(int nativeMediaPlayerListener);

    private native void nativeOnPlaybackComplete(int nativeMediaPlayerListener);

    private native void nativeOnSeekComplete(int nativeMediaPlayerListener);

    private native void nativeOnMediaInterrupted(int nativeMediaPlayerListener);
}

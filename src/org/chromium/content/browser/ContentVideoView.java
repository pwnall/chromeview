// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;
import org.chromium.content.common.IChildProcessService;
import org.chromium.content.R;

@JNINamespace("content")
public class ContentVideoView
        extends FrameLayout
        implements ContentVideoViewControls.Delegate,
        SurfaceHolder.Callback, View.OnTouchListener, View.OnKeyListener {

    private static final String TAG = "ContentVideoView";

    /* Do not change these values without updating their counterparts
     * in include/media/mediaplayer.h!
     */
    private static final int MEDIA_NOP = 0; // interface test message
    private static final int MEDIA_PREPARED = 1;
    private static final int MEDIA_PLAYBACK_COMPLETE = 2;
    private static final int MEDIA_BUFFERING_UPDATE = 3;
    private static final int MEDIA_SEEK_COMPLETE = 4;
    private static final int MEDIA_SET_VIDEO_SIZE = 5;
    private static final int MEDIA_ERROR = 100;
    private static final int MEDIA_INFO = 200;

    /**
     * Keep these error codes in sync with the code we defined in
     * MediaPlayerListener.java.
     */
    public static final int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 2;
    public static final int MEDIA_ERROR_INVALID_CODE = 3;

    // all possible internal states
    private static final int STATE_ERROR              = -1;
    private static final int STATE_IDLE               = 0;
    private static final int STATE_PLAYING            = 1;
    private static final int STATE_PAUSED             = 2;
    private static final int STATE_PLAYBACK_COMPLETED = 3;

    private SurfaceHolder mSurfaceHolder;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mCurrentBufferPercentage;
    private int mDuration;
    private ContentVideoViewControls mControls;
    private boolean mCanPause;
    private boolean mCanSeekBack;
    private boolean mCanSeekForward;

    // Native pointer to C++ ContentVideoView object.
    private int mNativeContentVideoView;

    // webkit should have prepared the media
    private int mCurrentState = STATE_IDLE;

    // Strings for displaying media player errors
    private String mPlaybackErrorText;
    private String mUnknownErrorText;
    private String mErrorButton;
    private String mErrorTitle;
    private String mVideoLoadingText;

    // This view will contain the video.
    private VideoSurfaceView mVideoSurfaceView;

    // Progress view when the video is loading.
    private View mProgressView;

    private Surface mSurface;

    private ContentVideoViewClient mClient;

    private class VideoSurfaceView extends SurfaceView {

        public VideoSurfaceView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (mVideoWidth == 0 && mVideoHeight == 0) {
                setMeasuredDimension(1, 1);
                return;
            }
            int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
            int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
            if (mVideoWidth > 0 && mVideoHeight > 0) {
                if ( mVideoWidth * height  > width * mVideoHeight ) {
                    height = width * mVideoHeight / mVideoWidth;
                } else if ( mVideoWidth * height  < width * mVideoHeight ) {
                    width = height * mVideoWidth / mVideoHeight;
                }
            }
            setMeasuredDimension(width, height);
        }
    }

    private static class ProgressView extends LinearLayout {

        private ProgressBar mProgressBar;
        private TextView mTextView;

        public ProgressView(Context context, String videoLoadingText) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
            setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            mProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleLarge);
            mTextView = new TextView(context);
            mTextView.setText(videoLoadingText);
            addView(mProgressBar);
            addView(mTextView);
        }
    }

    private static class FullScreenControls implements ContentVideoViewControls {

        View mVideoView;
        MediaController mMediaController;

        public FullScreenControls(Context context, View video) {
            mMediaController = new MediaController(context);
            mVideoView = video;
        }

        @Override
        public void show() {
            mMediaController.show();
            if (mVideoView != null) {
                mVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }

        @Override
        public void show(int timeout_ms) {
            mMediaController.show(timeout_ms);
        }

        @Override
        public void hide() {
            if (mVideoView != null) {
                mVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
            mMediaController.hide();
        }

        @Override
        public boolean isShowing() {
            return mMediaController.isShowing();
        }

        @Override
        public void setEnabled(boolean enabled) {
            mMediaController.setEnabled(enabled);
        }

        @Override
        public void setDelegate(Delegate delegate) {
            mMediaController.setMediaPlayer(delegate);
        }

        @Override
        public void setAnchorView(View view) {
            mMediaController.setAnchorView(view);
        }
    }

    private Runnable mExitFullscreenRunnable = new Runnable() {
        @Override
        public void run() {
            exitFullscreen(true);
        }
    };

    private ContentVideoView(Context context, int nativeContentVideoView,
            ContentVideoViewClient client) {
        super(context);
        mNativeContentVideoView = nativeContentVideoView;
        mClient = client;
        initResources(context);
        mCurrentBufferPercentage = 0;
        mVideoSurfaceView = new VideoSurfaceView(context);
        setBackgroundColor(Color.BLACK);
        showContentVideoView();
        setVisibility(View.VISIBLE);
        mClient.onShowCustomView(this);
    }

    private void initResources(Context context) {
        if (mPlaybackErrorText != null) return;
        mPlaybackErrorText = context.getString(
                org.chromium.content.R.string.media_player_error_text_invalid_progressive_playback);
        mUnknownErrorText = context.getString(
                org.chromium.content.R.string.media_player_error_text_unknown);
        mErrorButton = context.getString(
                org.chromium.content.R.string.media_player_error_button);
        mErrorTitle = context.getString(
                org.chromium.content.R.string.media_player_error_title);
        mVideoLoadingText = context.getString(
                org.chromium.content.R.string.media_player_loading_video);
    }

    private void showContentVideoView() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        this.addView(mVideoSurfaceView, layoutParams);
        View progressView = mClient.getVideoLoadingProgressView();
        if (progressView != null) {
            mProgressView = progressView;
        } else {
            mProgressView = new ProgressView(getContext(), mVideoLoadingText);
        }
        this.addView(mProgressView, layoutParams);
        mVideoSurfaceView.setZOrderOnTop(true);
        mVideoSurfaceView.setOnKeyListener(this);
        mVideoSurfaceView.setOnTouchListener(this);
        mVideoSurfaceView.getHolder().addCallback(this);
        mVideoSurfaceView.setFocusable(true);
        mVideoSurfaceView.setFocusableInTouchMode(true);
        mVideoSurfaceView.requestFocus();
    }

    @CalledByNative
    public void onMediaPlayerError(int errorType) {
        Log.d(TAG, "OnMediaPlayerError: " + errorType);
        if (mCurrentState == STATE_ERROR || mCurrentState == STATE_PLAYBACK_COMPLETED) {
            return;
        }

        // Ignore some invalid error codes.
        if (errorType == MEDIA_ERROR_INVALID_CODE) {
            return;
        }

        mCurrentState = STATE_ERROR;
        if (mControls != null) {
            mControls.hide();
        }

        /* Pop up an error dialog so the user knows that
         * something bad has happened. Only try and pop up the dialog
         * if we're attached to a window. When we're going away and no
         * longer have a window, don't bother showing the user an error.
         *
         * TODO(qinmin): We need to review whether this Dialog is OK with
         * the rest of the browser UI elements.
         */
        if (getWindowToken() != null) {
            String message;

            if (errorType == MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                message = mPlaybackErrorText;
            } else {
                message = mUnknownErrorText;
            }

            new AlertDialog.Builder(getContext())
                .setTitle(mErrorTitle)
                .setMessage(message)
                .setPositiveButton(mErrorButton,
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        /* Inform that the video is over.
                         */
                        onCompletion();
                    }
                })
                .setCancelable(false)
                .show();
        }
    }

    @CalledByNative
    private void onVideoSizeChanged(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoWidth != 0 && mVideoHeight != 0) {
            mVideoSurfaceView.getHolder().setFixedSize(mVideoWidth, mVideoHeight);
        }
    }

    @CalledByNative
    private void onBufferingUpdate(int percent) {
        mCurrentBufferPercentage = percent;
    }

    @CalledByNative
    private void onPlaybackComplete() {
        onCompletion();
    }

    @CalledByNative
    private void onUpdateMediaMetadata(
            int videoWidth,
            int videoHeight,
            int duration,
            boolean canPause,
            boolean canSeekBack,
            boolean canSeekForward) {
        mProgressView.setVisibility(View.GONE);
        mDuration = duration;
        mCanPause = canPause;
        mCanSeekBack = canSeekBack;
        mCanSeekForward = canSeekForward;
        mCurrentState = isPlaying() ? STATE_PLAYING : STATE_PAUSED;
        if (mControls != null) {
            mControls.setEnabled(true);
            // If paused , should show the controller for ever.
            if (isPlaying())
                mControls.show();
            else
                mControls.show(0);
        }

        onVideoSizeChanged(videoWidth, videoHeight);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mVideoSurfaceView.setFocusable(true);
        mVideoSurfaceView.setFocusableInTouchMode(true);
        if (isInPlaybackState() && mControls != null) {
            mControls.show();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        openVideo();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mNativeContentVideoView != 0) {
            nativeSetSurface(mNativeContentVideoView, null);
        }
        mSurfaceHolder = null;
        post(mExitFullscreenRunnable);
    }

    private void setControls(ContentVideoViewControls controls) {
        if (mControls != null) {
            mControls.hide();
        }
        mControls = controls;
        attachControls();
    }

    private void attachControls() {
        if (mControls != null) {
            mControls.setDelegate(this);
            mControls.setAnchorView(mVideoSurfaceView);
            mControls.setEnabled(false);
        }
    }

    @CalledByNative
    private void openVideo() {
        if (mSurfaceHolder != null) {
            mCurrentState = STATE_IDLE;
            mCurrentBufferPercentage = 0;
            ContentVideoViewControls controls = mClient.createControls();
            if (controls == null) {
                controls = new FullScreenControls(getContext(), this);
            }
            setControls(controls);
            if (mNativeContentVideoView != 0) {
                nativeUpdateMediaMetadata(mNativeContentVideoView);
                nativeSetSurface(mNativeContentVideoView,
                        mSurfaceHolder.getSurface());
            }
        }
    }

    private void onCompletion() {
        mCurrentState = STATE_PLAYBACK_COMPLETED;
        if (mControls != null) {
            mControls.hide();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (isInPlaybackState() && mControls != null &&
                event.getAction() == MotionEvent.ACTION_DOWN) {
            toggleMediaControlsVisiblity();
        }
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mControls != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK &&
                                     keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                                     keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                                     keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                                     keyCode != KeyEvent.KEYCODE_CALL &&
                                     keyCode != KeyEvent.KEYCODE_MENU &&
                                     keyCode != KeyEvent.KEYCODE_SEARCH &&
                                     keyCode != KeyEvent.KEYCODE_ENDCALL;
        if (isInPlaybackState() && isKeyCodeSupported && mControls != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (isPlaying()) {
                    pause();
                    mControls.show();
                } else {
                    start();
                    mControls.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!isPlaying()) {
                    start();
                    mControls.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (isPlaying()) {
                    pause();
                    mControls.show();
                }
                return true;
            } else {
                toggleMediaControlsVisiblity();
            }
        } else if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            exitFullscreen(false);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SEARCH) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toggleMediaControlsVisiblity() {
        if (mControls.isShowing()) {
            mControls.hide();
        } else {
            mControls.show();
        }
    }

    private boolean isInPlaybackState() {
        return (mCurrentState != STATE_ERROR && mCurrentState != STATE_IDLE);
    }

    @Override
    public void start() {
        if (isInPlaybackState()) {
            if (mNativeContentVideoView != 0) {
                nativePlay(mNativeContentVideoView);
            }
            mCurrentState = STATE_PLAYING;
        }
    }

    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (isPlaying()) {
                if (mNativeContentVideoView != 0) {
                    nativePause(mNativeContentVideoView);
                }
                mCurrentState = STATE_PAUSED;
            }
        }
    }

    // cache duration as mDuration for faster access
    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            if (mDuration > 0) {
                return mDuration;
            }
            if (mNativeContentVideoView != 0) {
                mDuration = nativeGetDurationInMilliSeconds(mNativeContentVideoView);
            } else {
                mDuration = 0;
            }
            return mDuration;
        }
        mDuration = -1;
        return mDuration;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState() && mNativeContentVideoView != 0) {
            return nativeGetCurrentPosition(mNativeContentVideoView);
        }
        return 0;
    }

    @Override
    public void seekTo(int msec) {
        if (mNativeContentVideoView != 0) {
            nativeSeekTo(mNativeContentVideoView, msec);
        }
    }

    @Override
    public boolean isPlaying() {
        return mNativeContentVideoView != 0 && nativeIsPlaying(mNativeContentVideoView);
    }

    @Override
    public int getBufferPercentage() {
        return mCurrentBufferPercentage;
    }

    @Override
    public boolean canPause() {
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    public int getAudioSessionId() {
        return 0;
    }

    @CalledByNative
    private static ContentVideoView createContentVideoView(
            Context context, int nativeContentVideoView, ContentVideoViewClient client) {
        ThreadUtils.assertOnUiThread();
        // The context needs be Activity to create the ContentVideoView correctly.
        if (!(context instanceof Activity)) {
            Log.w(TAG, "Wrong type of context, can't create fullscreen video");
            return null;
        }
        return new ContentVideoView(context, nativeContentVideoView, client);
    }

    private void removeControls() {
        if (mControls != null) {
            mControls.setEnabled(false);
            mControls.hide();
            mControls = null;
        }
    }

    public void removeSurfaceView() {
        removeView(mVideoSurfaceView);
        removeView(mProgressView);
        mVideoSurfaceView = null;
        mProgressView = null;
    }

    public void exitFullscreen(boolean relaseMediaPlayer) {
        destroyContentVideoView(false);
        if (mNativeContentVideoView != 0) {
            nativeExitFullscreen(mNativeContentVideoView, relaseMediaPlayer);
            mNativeContentVideoView = 0;
        }
    }

    /**
     * This method shall only be called by native and exitFullscreen,
     * To exit fullscreen, use exitFullscreen in Java.
     */
    @CalledByNative
    private void destroyContentVideoView(boolean nativeViewDestroyed) {
        if (mVideoSurfaceView != null) {
            mClient.onDestroyContentVideoView();
            removeControls();
            removeSurfaceView();
            setVisibility(View.GONE);
        }
        if (nativeViewDestroyed) {
            mNativeContentVideoView = 0;
        }
    }

    public static ContentVideoView getContentVideoView() {
        return nativeGetSingletonJavaContentVideoView();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            exitFullscreen(false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private static native ContentVideoView nativeGetSingletonJavaContentVideoView();
    private native void nativeExitFullscreen(int nativeContentVideoView, boolean relaseMediaPlayer);
    private native int nativeGetCurrentPosition(int nativeContentVideoView);
    private native int nativeGetDurationInMilliSeconds(int nativeContentVideoView);
    private native void nativeUpdateMediaMetadata(int nativeContentVideoView);
    private native int nativeGetVideoWidth(int nativeContentVideoView);
    private native int nativeGetVideoHeight(int nativeContentVideoView);
    private native boolean nativeIsPlaying(int nativeContentVideoView);
    private native void nativePause(int nativeContentVideoView);
    private native void nativePlay(int nativeContentVideoView);
    private native void nativeSeekTo(int nativeContentVideoView, int msec);
    private native void nativeSetSurface(int nativeContentVideoView, Surface surface);
}

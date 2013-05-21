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
import android.widget.MediaController.MediaPlayerControl;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.common.IChildProcessService;
import org.chromium.content.R;

@JNINamespace("content")
public class ContentVideoView extends FrameLayout implements MediaPlayerControl,
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

    /** The video is streamed and its container is not valid for progressive
     * playback i.e the video's index (e.g moov atom) is not at the start of the
     * file.
     */
    public static final int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 2;

    // all possible internal states
    private static final int STATE_ERROR              = -1;
    private static final int STATE_IDLE               = 0;
    private static final int STATE_PLAYING            = 1;
    private static final int STATE_PAUSED             = 2;
    private static final int STATE_PLAYBACK_COMPLETED = 3;

    private SurfaceHolder mSurfaceHolder = null;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private int mCurrentBufferPercentage;
    private int mDuration;
    private MediaController mMediaController = null;
    private boolean mCanPause;
    private boolean mCanSeekBack;
    private boolean mCanSeekForward;

    // Native pointer to C++ ContentVideoView object.
    private int mNativeContentVideoView = 0;

    // webkit should have prepared the media
    private int mCurrentState = STATE_IDLE;

    // Strings for displaying media player errors
    static String mPlaybackErrorText;
    static String mUnknownErrorText;
    static String mErrorButton;
    static String mErrorTitle;
    static String mVideoLoadingText;

    // This view will contain the video.
    private VideoSurfaceView mVideoSurfaceView;

    // Progress view when the video is loading.
    private View mProgressView;

    private Surface mSurface = null;

    // There are can be at most 1 fullscreen video
    // TODO(qinmin): will change this once  we move the creation of this class
    // to the host application
    private static ContentVideoView sContentVideoView = null;

    // The delegate will follow sContentVideoView. We would need to
    // move this to an instance variable if we allow multiple ContentVideoViews.
    private static ContentVideoViewContextDelegate sDelegate = null;

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

        public ProgressView(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
            setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            mProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleLarge);
            mTextView = new TextView(context);
            mTextView.setText(mVideoLoadingText);
            addView(mProgressBar);
            addView(mTextView);
        }
    }

    private static class FullScreenMediaController extends MediaController {

        View mVideoView;

        public FullScreenMediaController(Context context, View video) {
            super(context);
            mVideoView = video;
        }

        @Override
        public void show() {
            super.show();
            if (mVideoView != null) {
                mVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }

        @Override
        public void hide() {
            if (mVideoView != null) {
                mVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
            super.hide();
        }
    }

    private Runnable mExitFullscreenRunnable = new Runnable() {
        @Override
        public void run() {
            destroyContentVideoView();
        }
    };

    public ContentVideoView(Context context) {
        this(context, 0);
    }

    private ContentVideoView(Context context, int nativeContentVideoView) {
        super(context);
        initResources(context);

        if (nativeContentVideoView == 0) return;
        mNativeContentVideoView = nativeContentVideoView;

        mCurrentBufferPercentage = 0;
        mVideoSurfaceView = new VideoSurfaceView(context);
    }

    private static void initResources(Context context) {
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

    void showContentVideoView() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        this.addView(mVideoSurfaceView, layoutParams);
        View progressView = sDelegate.getVideoLoadingProgressView();
        if (progressView != null) {
            mProgressView = progressView;
        } else {
            mProgressView = new ProgressView(getContext());
        }
        this.addView(mProgressView, layoutParams);
        mVideoSurfaceView.setZOrderOnTop(true);
        mVideoSurfaceView.setOnKeyListener(this);
        mVideoSurfaceView.setOnTouchListener(this);
        mVideoSurfaceView.getHolder().addCallback(this);
        mVideoSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
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

        mCurrentState = STATE_ERROR;
        if (mMediaController != null) {
            mMediaController.hide();
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
    public void onVideoSizeChanged(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoWidth != 0 && mVideoHeight != 0) {
            mVideoSurfaceView.getHolder().setFixedSize(mVideoWidth, mVideoHeight);
        }
    }

    @CalledByNative
    public void onBufferingUpdate(int percent) {
        mCurrentBufferPercentage = percent;
    }

    @CalledByNative
    public void onPlaybackComplete() {
        onCompletion();
    }

    @CalledByNative
    public void updateMediaMetadata(
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
        if (mMediaController != null) {
            mMediaController.setEnabled(true);
            // If paused , should show the controller for ever.
            if (isPlaying())
                mMediaController.show();
            else
                mMediaController.show(0);
        }

        onVideoSizeChanged(videoWidth, videoHeight);
    }

    public void destroyNativeView() {
        if (mNativeContentVideoView != 0) {
            mNativeContentVideoView = 0;
            destroyContentVideoView();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mVideoSurfaceView.setFocusable(true);
        mVideoSurfaceView.setFocusableInTouchMode(true);
        if (isInPlaybackState() && mMediaController != null) {
            mMediaController.show();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        openVideo();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceHolder = null;
        if (mNativeContentVideoView != 0) {
            nativeExitFullscreen(mNativeContentVideoView, true);
            mNativeContentVideoView = 0;
            post(mExitFullscreenRunnable);
        }
        removeMediaController();
    }

    public void setMediaController(MediaController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
        attachMediaController();
    }

    private void attachMediaController() {
        if (mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            mMediaController.setAnchorView(mVideoSurfaceView);
            mMediaController.setEnabled(false);
        }
    }

    @CalledByNative
    public void openVideo() {
        if (mSurfaceHolder != null) {
            mCurrentState = STATE_IDLE;
            setMediaController(new FullScreenMediaController(sDelegate.getContext(), this));
            if (mNativeContentVideoView != 0) {
                nativeUpdateMediaMetadata(mNativeContentVideoView);
            }
            mCurrentBufferPercentage = 0;
            if (mNativeContentVideoView != 0) {
                nativeSetSurface(mNativeContentVideoView,
                                 mSurfaceHolder.getSurface());
            }
        }
    }

    private void onCompletion() {
        mCurrentState = STATE_PLAYBACK_COMPLETED;
        if (mMediaController != null) {
            mMediaController.hide();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (isInPlaybackState() && mMediaController != null &&
                event.getAction() == MotionEvent.ACTION_DOWN) {
            toggleMediaControlsVisiblity();
        }
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
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
        if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (isPlaying()) {
                    pause();
                    mMediaController.show();
                } else {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!isPlaying()) {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (isPlaying()) {
                    pause();
                    mMediaController.show();
                }
                return true;
            } else {
                toggleMediaControlsVisiblity();
            }
        } else if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            if (mNativeContentVideoView != 0) {
                nativeExitFullscreen(mNativeContentVideoView, false);
                destroyNativeView();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SEARCH) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    private boolean isInPlaybackState() {
        return (mCurrentState != STATE_ERROR && mCurrentState != STATE_IDLE);
    }

    public void start() {
        if (isInPlaybackState()) {
            if (mNativeContentVideoView != 0) {
                nativePlay(mNativeContentVideoView);
            }
            mCurrentState = STATE_PLAYING;
        }
    }

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

    public int getCurrentPosition() {
        if (isInPlaybackState() && mNativeContentVideoView != 0) {
            return nativeGetCurrentPosition(mNativeContentVideoView);
        }
        return 0;
    }

    public void seekTo(int msec) {
        if (mNativeContentVideoView != 0) {
            nativeSeekTo(mNativeContentVideoView, msec);
        }
    }

    public boolean isPlaying() {
        return mNativeContentVideoView != 0 && nativeIsPlaying(mNativeContentVideoView);
    }

    public int getBufferPercentage() {
        return mCurrentBufferPercentage;
    }
    public boolean canPause() {
        return mCanPause;
    }

    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    public int getAudioSessionId() {
        return 0;
    }

    @CalledByNative
    public static ContentVideoView createContentVideoView(int nativeContentVideoView) {
        if (sContentVideoView != null)
            return sContentVideoView;

        if (sDelegate != null && sDelegate.getContext() != null) {
            sContentVideoView = new ContentVideoView(sDelegate.getContext(),
                    nativeContentVideoView);

            sDelegate.onShowCustomView(sContentVideoView);
            sContentVideoView.setBackgroundColor(Color.BLACK);
            sContentVideoView.showContentVideoView();
            sContentVideoView.setVisibility(View.VISIBLE);
            return sContentVideoView;
        }
        return null;
    }

    public void removeMediaController() {
        if (mMediaController != null) {
            mMediaController.setEnabled(false);
            mMediaController.hide();
            mMediaController = null;
        }
    }

    public void removeSurfaceView() {
        removeView(mVideoSurfaceView);
        removeView(mProgressView);
        mVideoSurfaceView = null;
        mProgressView = null;
    }

    @CalledByNative
    public static void destroyContentVideoView() {
        sDelegate.onDestroyContentVideoView();
        if (sContentVideoView != null) {
            sContentVideoView.removeMediaController();
            sContentVideoView.removeSurfaceView();
            sContentVideoView.setVisibility(View.GONE);
        }
        sContentVideoView = null;
    }

    public static ContentVideoView getContentVideoView() {
        return sContentVideoView;
    }

    public static void registerContentVideoViewContextDelegate(
       ContentVideoViewContextDelegate delegate) {
        sDelegate = delegate;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            destroyContentVideoView();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

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

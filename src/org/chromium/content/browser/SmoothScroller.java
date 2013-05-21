// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Provides a Java-side implementation for chrome.gpuBenchmarking.smoothScrollBy.
 */
@JNINamespace("content")
public class SmoothScroller {
    private final ContentViewCore mContentViewCore;
    private final boolean mScrollDown;
    private final float mMouseEventX;
    private final float mMouseEventY;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private TimeAnimator mTimeAnimator;

    private int mNativePtr;
    private long mDownTime;
    private float mCurrentY;

    private final byte STATE_INITIAL = 0;
    private final byte STATE_MOVING = 1;
    private final byte STATE_PENDING_UP = 2;
    private final byte STATE_FINAL = 3;
    private byte state = STATE_INITIAL;

    SmoothScroller(ContentViewCore contentViewCore, boolean scrollDown,
            int mouseEventX, int mouseEventY) {
        mContentViewCore = contentViewCore;
        mScrollDown = scrollDown;
        float scale = mContentViewCore.getScale();
        mMouseEventX = mouseEventX * scale;
        mMouseEventY = mouseEventY * scale;
        mCurrentY = mMouseEventY;
    }

    @CalledByNative
    void start(int nativePtr) {
        assert mNativePtr == 0;
        mNativePtr = nativePtr;

        Runnable runnable = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ?
            createJBRunnable() : createPreJBRunnable();
        mHandler.post(runnable);
    }

    boolean sendEvent(long time) {
        switch (state) {
            case STATE_INITIAL: {
                mDownTime = SystemClock.uptimeMillis();
                MotionEvent event = MotionEvent.obtain(mDownTime, mDownTime,
                        MotionEvent.ACTION_DOWN, mMouseEventX, mCurrentY, 0);
                mContentViewCore.onTouchEvent(event);
                event.recycle();
                state = STATE_MOVING;
                break;
            }
            case STATE_MOVING: {
                double delta = nativeGetScrollDelta(mNativePtr, mContentViewCore.getScale());
                if (delta != 0) {
                    mCurrentY += mScrollDown ? -delta : delta;

                    MotionEvent event = MotionEvent.obtain(mDownTime, time,
                            MotionEvent.ACTION_MOVE, mMouseEventX, mCurrentY, 0);
                    mContentViewCore.onTouchEvent(event);
                    event.recycle();
                }
                if (nativeHasFinished(mNativePtr))
                    state = STATE_PENDING_UP;
                break;
            }
            case STATE_PENDING_UP: {
                MotionEvent event = MotionEvent.obtain(mDownTime, time,
                        MotionEvent.ACTION_UP, mMouseEventX, mCurrentY, 0);
                mContentViewCore.onTouchEvent(event);
                event.recycle();
                nativeSetHasSentMotionUp(mNativePtr);
                state = STATE_FINAL;
                break;
            }
        }
        return state != STATE_FINAL;
    }

    private Runnable createJBRunnable() {
        // On JB, we rely on TimeAnimator to send events tied with vsync.
        return new Runnable() {
            @Override
            public void run() {
                mTimeAnimator = new TimeAnimator();
                mTimeAnimator.setTimeListener(new TimeListener() {
                    @Override
                    public void onTimeUpdate(TimeAnimator animation, long totalTime,
                            long deltaTime) {
                        if (!sendEvent(mDownTime + totalTime)) {
                            mTimeAnimator.end();
                        }
                    }
                });
                mTimeAnimator.start();
            }
        };
    }

    private Runnable createPreJBRunnable() {
        // Pre-JB there's no TimeAnimator, so we keep posting messages.
        return new Runnable() {
            @Override
            public void run() {
                if (sendEvent(SystemClock.uptimeMillis())) {
                    mHandler.post(this);
                }
            }
        };
    }

    private native double nativeGetScrollDelta(
            int nativeTouchSmoothScrollGestureAndroid, double scale);
    private native boolean nativeHasFinished(int nativeTouchSmoothScrollGestureAndroid);
    private native void nativeSetHasSentMotionUp(int nativeTouchSmoothScrollGestureAndroid);
}

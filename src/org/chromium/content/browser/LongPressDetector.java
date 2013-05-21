// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.util.Iterator;

/**
 * This class controls long press timers and is owned by a ContentViewGestureHandler.
 *
 * For instance, we may receive a DOWN then UP, so we may need to cancel the
 * timer before the UP completes its roundtrip from WebKit.
 */
class LongPressDetector {
    private MotionEvent mCurrentDownEvent;
    private final LongPressDelegate mLongPressDelegate;
    private final Handler mLongPressHandler;
    private final int mTouchSlopSquare;
    private boolean mInLongPress;

    // The following are used when touch events are offered to native, and not for
    // anything relating to the GestureDetector.
    // True iff a touch_move has exceeded the touch slop distance.
    private boolean mMoveConfirmed;
    // Coordinates of the start of a touch event (i.e. the touch_down).
    private int mTouchInitialX;
    private int mTouchInitialY;

    private static final int LONG_PRESS = 2;

    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();

    LongPressDetector(Context context, LongPressDelegate delegate) {
        mLongPressDelegate = delegate;
        mLongPressHandler = new LongPressHandler();
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        int touchSlop = configuration.getScaledTouchSlop();
        mTouchSlopSquare = touchSlop * touchSlop;
    }

    private class LongPressHandler extends Handler {
        LongPressHandler() {
            super();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case LONG_PRESS:
                dispatchLongPress();
                break;
            default:
                throw new RuntimeException("Unknown message " + msg); //never
            }
        }
    }

    /**
     * This is an interface to execute the LongPress when it receives the onLongPress message.
     */
    interface LongPressDelegate {
        public void onLongPress(MotionEvent event);
    }

    /**
     * Initiates a LONG_PRESS gesture timer if needed.
     */
    void startLongPressTimerIfNeeded(MotionEvent ev) {
        if (!canHandle(ev)) return;

        if (mCurrentDownEvent != null) mCurrentDownEvent.recycle();

        mCurrentDownEvent = MotionEvent.obtain(ev);
        mLongPressHandler.sendEmptyMessageAtTime(LONG_PRESS, mCurrentDownEvent.getDownTime()
                + TAP_TIMEOUT + LONGPRESS_TIMEOUT);
        mInLongPress = false;
    }

    private boolean canHandle(MotionEvent ev) {
        return ev.getAction() == MotionEvent.ACTION_DOWN;
    }

    // Cancel LONG_PRESS timers.
    void cancelLongPressIfNeeded(MotionEvent ev) {
        if (!hasPendingMessage() ||
            mCurrentDownEvent == null || ev.getDownTime() != mCurrentDownEvent.getDownTime()) {
            return;
        }
        final int action = ev.getAction();
        final float y = ev.getY();
        final float x = ev.getX();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE:
                final int deltaX = (int) (x - mCurrentDownEvent.getX());
                final int deltaY = (int) (y - mCurrentDownEvent.getY());
                int distance = (deltaX * deltaX) + (deltaY * deltaY);
                if (distance > mTouchSlopSquare) {
                    mInLongPress = false;
                    mLongPressHandler.removeMessages(LONG_PRESS);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mCurrentDownEvent.getDownTime() + TAP_TIMEOUT + LONGPRESS_TIMEOUT >
                    ev.getEventTime()) {
                    mInLongPress = false;
                    mLongPressHandler.removeMessages(LONG_PRESS);
                }
                break;
            default:
                break;
        }
    }

    // Given a stream of pending events, cancel the LONG_PRESS timer if appropriate.
    void cancelLongPressIfNeeded(Iterator<MotionEvent> pendingEvents) {
        if (mCurrentDownEvent == null)
            return;
        long currentDownTime = mCurrentDownEvent.getDownTime();
        while (pendingEvents.hasNext()) {
            MotionEvent pending = pendingEvents.next();
            if (pending.getDownTime() != currentDownTime) {
                break;
            }
            cancelLongPressIfNeeded(pending);
        }
    }

    void cancelLongPress() {
        if (mLongPressHandler.hasMessages(LONG_PRESS)) {
            mLongPressHandler.removeMessages(LONG_PRESS);
        }
    }

    // Used this to check if a onSingleTapUp is part of a long press event.
    boolean isInLongPress() {
        return mInLongPress;
    }

    private void dispatchLongPress() {
        mInLongPress = true;
        mLongPressDelegate.onLongPress(mCurrentDownEvent);
    }

    boolean hasPendingMessage() {
        return mLongPressHandler.hasMessages(LONG_PRESS);
    }

    void onOfferTouchEventToJavaScript(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mMoveConfirmed = false;
            mTouchInitialX = Math.round(event.getX());
            mTouchInitialY = Math.round(event.getY());
        }
    }

    boolean confirmOfferMoveEventToJavaScript(MotionEvent event) {
        if (!mMoveConfirmed) {
            int deltaX = Math.round(event.getX()) - mTouchInitialX;
            int deltaY = Math.round(event.getY()) - mTouchInitialY;
            if (deltaX * deltaX + deltaY * deltaY >= mTouchSlopSquare) {
                mMoveConfirmed = true;
            }
        }
        return mMoveConfirmed;
    }

    /**
     * This is for testing only.
     * Sends a LongPress gesture. This should always be called after a down event.
     */
    void sendLongPressGestureForTest() {
        if (mCurrentDownEvent == null) return;
        dispatchLongPress();
    }
}

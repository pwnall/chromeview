// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

class SystemMessageHandler extends Handler {

    private static final int TIMER_MESSAGE = 1;
    private static final int DELAYED_TIMER_MESSAGE = 2;

    // Native class pointer set by the constructor of the SharedClient native class.
    private int mMessagePumpDelegateNative = 0;

    // Used to ensure we have at most one TIMER_MESSAGE pending at once.
    private AtomicBoolean mTimerFired = new AtomicBoolean(true);

    // Used to insert TIMER_MESSAGE on the front of the system message queue during startup only.
    // This is a wee hack, to give a priority boost to native tasks during startup as they tend to
    // be on the critical path. (After startup, handling the UI with minimum latency is more
    // important).
    private boolean mStartupComplete = false;
    private final long mStartupCompleteTime = System.currentTimeMillis() + 2000;
    private final boolean startupComplete() {
        if (!mStartupComplete && System.currentTimeMillis() > mStartupCompleteTime) {
            mStartupComplete = true;
        }
        return mStartupComplete;
    }

    private SystemMessageHandler(int messagePumpDelegateNative) {
        mMessagePumpDelegateNative = messagePumpDelegateNative;
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.what == TIMER_MESSAGE) {
            mTimerFired.set(true);
        }
        while (nativeDoRunLoopOnce(mMessagePumpDelegateNative)) {
            if (startupComplete()) {
                setTimer();
                break;
            }
        }
    }

    @CalledByNative
    private void setTimer() {
        if (!mTimerFired.getAndSet(false)) {
            // mTimerFired was already false.
            return;
        }
        if (startupComplete()) {
            sendEmptyMessage(TIMER_MESSAGE);
        } else {
            sendMessageAtFrontOfQueue(obtainMessage(TIMER_MESSAGE));
        }
    }

    // If millis <=0, it'll send a TIMER_MESSAGE instead of
    // a DELAYED_TIMER_MESSAGE.
    @SuppressWarnings("unused")
    @CalledByNative
    private void setDelayedTimer(long millis) {
        if (millis <= 0) {
            setTimer();
        } else {
            removeMessages(DELAYED_TIMER_MESSAGE);
            sendEmptyMessageDelayed(DELAYED_TIMER_MESSAGE, millis);
        }
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void removeTimer() {
        removeMessages(TIMER_MESSAGE);
        removeMessages(DELAYED_TIMER_MESSAGE);
    }

    @CalledByNative
    private static SystemMessageHandler create(int messagePumpDelegateNative) {
        return new SystemMessageHandler(messagePumpDelegateNative);
    }

    private native boolean nativeDoRunLoopOnce(int messagePumpDelegateNative);
}

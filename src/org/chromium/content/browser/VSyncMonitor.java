// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.view.Choreographer;
import android.view.WindowManager;

import org.chromium.content.common.TraceEvent;

/**
 * Notifies clients of the default displays's vertical sync pulses.
 * This class works in "burst" mode: once the update is requested, the listener will be
 * called MAX_VSYNC_COUNT times on the vertical sync pulses (on JB) or on every refresh
 * period (on ICS, see below), unless stop() is called.
 * On ICS, VSyncMonitor relies on setVSyncPointForICS() being called to set a reasonable
 * approximation of a vertical sync starting point; see also http://crbug.com/156397.
 */
public class VSyncMonitor {
    private static final String TAG = VSyncMonitor.class.getSimpleName();

    public interface Listener {
        /**
         * Called very soon after the start of the display's vertical sync period.
         * @param monitor The VSyncMonitor that triggered the signal.
         * @param vsyncTimeMicros Absolute frame time in microseconds.
         */
        public void onVSync(VSyncMonitor monitor, long vsyncTimeMicros);
    }

    private static final long NANOSECONDS_PER_SECOND = 1000000000;
    private static final long NANOSECONDS_PER_MILLISECOND = 1000000;
    private static final long NANOSECONDS_PER_MICROSECOND = 1000;

    private Listener mListener;

    // Display refresh rate as reported by the system.
    private final long mRefreshPeriodNano;

    // Last time requestUpdate() was called.
    private long mLastUpdateRequestNano;

    private boolean mHaveRequestInFlight;

    private int mTriggerNextVSyncCount;
    private static final int MAX_VSYNC_COUNT = 5;

    // Choreographer is used to detect vsync on >= JB.
    private final Choreographer mChoreographer;
    private final Choreographer.FrameCallback mVSyncFrameCallback;

    // On ICS we just post a task through the handler (http://crbug.com/156397)
    private final Handler mHandler;
    private final Runnable mVSyncRunnableCallback;
    private long mGoodStartingPointNano;
    private long mLastPostedNano;

    public VSyncMonitor(Context context, VSyncMonitor.Listener listener) {
        this(context, listener, true);
    }

    VSyncMonitor(Context context, VSyncMonitor.Listener listener, boolean enableJBVSync) {
        mListener = listener;
        float refreshRate = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRefreshRate();
        if (refreshRate <= 0) refreshRate = 60;
        mRefreshPeriodNano = (long) (NANOSECONDS_PER_SECOND / refreshRate);
        mTriggerNextVSyncCount = 0;

        if (enableJBVSync && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // Use Choreographer on JB+ to get notified of vsync.
            mChoreographer = Choreographer.getInstance();
            mVSyncFrameCallback = new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    TraceEvent.instant("VSync");
                    onVSyncCallback(frameTimeNanos);
                }
            };
            mHandler = null;
            mVSyncRunnableCallback = null;
        } else {
            // On ICS we just hope that running tasks is relatively predictable.
            mChoreographer = null;
            mVSyncFrameCallback = null;
            mHandler = new Handler();
            mVSyncRunnableCallback = new Runnable() {
                @Override
                public void run() {
                    TraceEvent.instant("VSyncTimer");
                    onVSyncCallback(System.nanoTime());
                }
            };
            mGoodStartingPointNano = getCurrentNanoTime();
            mLastPostedNano = 0;
        }
    }

    /**
     * Returns the time interval between two consecutive vsync pulses in microseconds.
     */
    public long getVSyncPeriodInMicroseconds() {
        return mRefreshPeriodNano / NANOSECONDS_PER_MICROSECOND;
    }

    /**
     * Determine whether a true vsync signal is available on this platform.
     */
    public boolean isVSyncSignalAvailable() {
        return mChoreographer != null;
    }

    /**
     * Stop reporting vsync events. Note that at most one pending vsync event can still be delivered
     * after this function is called.
     */
    public void stop() {
        mTriggerNextVSyncCount = 0;
    }

    /**
     * Unregister the listener.
     * No vsync events will be reported afterwards.
     */
    public void unregisterListener() {
        stop();
        mListener = null;
    }

    /**
     * Request to be notified of the closest display vsync events.
     * Listener.onVSync() will be called soon after the upcoming vsync pulses.
     * It will be called at most MAX_VSYNC_COUNT times unless requestUpdate() is called again.
     */
    public void requestUpdate() {
        mTriggerNextVSyncCount = MAX_VSYNC_COUNT;
        mLastUpdateRequestNano = getCurrentNanoTime();
        postCallback();
    }

    /**
     * Set the best guess of the point in the past when the vsync has happened.
     * @param goodStartingPointNano Known vsync point in the past.
     */
    public void setVSyncPointForICS(long goodStartingPointNano) {
        mGoodStartingPointNano = goodStartingPointNano;
    }

    private long getCurrentNanoTime() {
        return System.nanoTime();
    }

    private void onVSyncCallback(long frameTimeNanos) {
        assert mHaveRequestInFlight;
        mHaveRequestInFlight = false;
        if (mTriggerNextVSyncCount > 0) {
            mTriggerNextVSyncCount--;
            postCallback();
        }
        if (mListener != null) {
            mListener.onVSync(this, frameTimeNanos / NANOSECONDS_PER_MICROSECOND);
        }
    }

    private void postCallback() {
        if (mHaveRequestInFlight) return;
        mHaveRequestInFlight = true;
        if (isVSyncSignalAvailable()) {
            mChoreographer.postFrameCallback(mVSyncFrameCallback);
        } else {
            postRunnableCallback();
        }
    }

    private void postRunnableCallback() {
        assert !isVSyncSignalAvailable();
        final long currentTime = mLastUpdateRequestNano;
        final long lastRefreshTime = mGoodStartingPointNano +
                ((currentTime - mGoodStartingPointNano) / mRefreshPeriodNano) * mRefreshPeriodNano;
        long delay = (lastRefreshTime + mRefreshPeriodNano) - currentTime;
        assert delay >= 0 && delay < mRefreshPeriodNano;

        if (currentTime + delay <= mLastPostedNano + mRefreshPeriodNano / 2) {
            delay += mRefreshPeriodNano;
        }

        mLastPostedNano = currentTime + delay;
        if (delay == 0) mHandler.post(mVSyncRunnableCallback);
        else mHandler.postDelayed(mVSyncRunnableCallback, delay / NANOSECONDS_PER_MILLISECOND);
    }
}
